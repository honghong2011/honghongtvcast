package com.hh2011.cast.tv.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Renderer
import com.google.android.exoplayer2.audio.AudioRendererEventListener
import com.google.android.exoplayer2.audio.AudioSink
import com.google.android.exoplayer2.ext.ffmpeg.FfmpegAudioRenderer
import com.google.android.exoplayer2.ext.ffmpeg.FfmpegLibrary
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.hh2011.cast.tv.ui.PlayerActivity
import com.hh2011.cast.tv.util.PreferencesHelper
import okhttp3.OkHttpClient
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

/**
 * 投屏播放器管理器（单例）
 *
 * 职责：
 * 1. 持有 ExoPlayer 实例，对接 FFmpeg + OkHttp 扩展
 * 2. 提供 DMR AVTransport 调用的播放控制接口（线程安全）
 * 3. 管理 Surface 生命周期（与 PlayerActivity 联动）
 * 4. 维护投屏状态，通知 UI 层更新
 *
 * 线程模型：
 * - DMR 服务方法从 Cling 协议线程调用 -> 通过 Handler 切到主线程操作 ExoPlayer
 * - 查询方法（getCurrentPosition 等）直接调用，ExoPlayer 查询是线程安全的
 * - Surface 操作在主线程执行
 */
object CastPlayerManager {

    private const val TAG = "CastPlayerManager"

    // ========================================================================
    // 状态定义
    // ========================================================================

    /**
     * 投屏播放状态
     */
    enum class CastState {
        IDLE,       // 无媒体
        LOADING,    // 加载中
        PLAYING,    // 播放中
        PAUSED,     // 已暂停
        STOPPED,    // 已停止
        ENDED       // 播放结束
    }

    // ========================================================================
    // 核心成员
    // ========================================================================

    /** 主线程 Handler，所有 ExoPlayer 写操作必须切到主线程 */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** ExoPlayer 实例（@Volatile 保证多线程可见性） */
    @Volatile
    private var exoPlayer: ExoPlayer? = null

    /** 应用上下文（用于启动 PlayerActivity） */
    private var appContext: Context? = null

    /** 当前播放的媒体 URL */
    @Volatile
    private var currentUrl: String? = null

    /** 当前播放的媒体标题 */
    @Volatile
    private var currentTitle: String? = null

    /** Surface 就绪前暂存播放请求 */
    @Volatile
    private var pendingUrl: String? = null
    @Volatile
    private var pendingTitle: String? = null

    /** 当前 Surface */
    @Volatile
    private var surface: Surface? = null

    /** Surface 是否可用 */
    @Volatile
    private var surfaceReady = false

    /** 当前投屏状态 */
    @Volatile
    private var castState: CastState = CastState.IDLE

    /** PlayerActivity 弱引用（用于判断播放页是否在前台） */
    private var playerActivityRef: WeakReference<Activity>? = null

    // ========================================================================
    // 对外回调
    // ========================================================================

    /** 投屏状态变化回调（主线程回调） */
    var onCastStateChanged: ((CastState) -> Unit)? = null

    /** 媒体标题变化回调（主线程回调） */
    var onMediaChanged: ((String?) -> Unit)? = null

    /** 播放错误回调（主线程回调） */
    var onError: ((String) -> Unit)? = null

    // ========================================================================
    // 初始化与释放
    // ========================================================================

    /**
     * 初始化播放器
     *
     * 由 CastService.onCreate 调用，创建 ExoPlayer 实例。
     * 必须在主线程调用（CastService.onCreate 已在主线程）。
     *
     * 同步创建，确保 DMR 设备注册前 ExoPlayer 已就绪，
     * 避免投屏命令到达时 ExoPlayer 为 null 的竞态条件。
     *
     * @param context 上下文（CastService）
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        createExoPlayer(context)
    }

    /**
     * 创建 ExoPlayer 实例
     *
     * 集成 FFmpeg 音频软解 + OkHttp 网络栈
     */
    private fun createExoPlayer(context: Context) {
        if (exoPlayer != null) return

        try {
            val hwDecoding = PreferencesHelper.isHardwareDecodingEnabled(context)
            Log.d(TAG, "初始化 ExoPlayer, 硬解=$hwDecoding")

            // 自定义渲染器工厂：FFmpeg 音频软解优先（支持 MP2 等格式）
            val renderersFactory = object : DefaultRenderersFactory(context) {
                override fun buildAudioRenderers(
                    context: Context,
                    extensionRendererMode: Int,
                    mediaCodecSelector: MediaCodecSelector,
                    enableDecoderFallback: Boolean,
                    audioSink: AudioSink,
                    eventHandler: Handler,
                    eventListener: AudioRendererEventListener,
                    out: ArrayList<Renderer>
                ) {
                    // FFmpeg 音频渲染器置于最前
                    if (FfmpegLibrary.isAvailable()) {
                        out.add(FfmpegAudioRenderer(eventHandler, eventListener, audioSink))
                        Log.d(TAG, "已添加 FfmpegAudioRenderer")
                    }
                    // 追加系统 MediaCodec 硬解作为备选
                    super.buildAudioRenderers(
                        context, extensionRendererMode, mediaCodecSelector,
                        enableDecoderFallback, audioSink, eventHandler, eventListener, out
                    )
                }
            }.apply {
                // 硬解优先: EXTENSION_RENDERER_MODE_ON (MediaCodec 主解, FFmpeg 备选)
                // 软解优先: EXTENSION_RENDERER_MODE_PREFER (FFmpeg 主解, MediaCodec 备选)
                setExtensionRendererMode(
                    if (hwDecoding) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                )
                setEnableDecoderFallback(true)
            }

            // OkHttp 数据源工厂（连接池复用）
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
            val okHttpFactory = OkHttpDataSource.Factory(okHttpClient)
                .setUserAgent("HongHong-Cast/1.0")
            val dataSourceFactory = DefaultDataSource.Factory(context, okHttpFactory)

            // 媒体源工厂：按 URI 自动识别媒体类型，格式支持矩阵——
            // - http/https 渐进式流（mp4/mkv/flv/mp3 等）→ OkHttp 数据源
            // - HLS(m3u8) / DASH(mpd) / SmoothStreaming(ism) → exoplayer 完整包自带
            // - rtsp:// → exoplayer 完整包自带 RtspMediaSource
            // - rtmp:// → 依赖 extension-rtmp（DefaultDataSource 自动反射接入 RtmpDataSource）
            // - file/asset/content → DefaultDataSource 本地处理
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

            exoPlayer = ExoPlayer.Builder(context)
                .setRenderersFactory(renderersFactory)
                .setTrackSelector(DefaultTrackSelector(context))
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .also { it.addListener(playerListener) }

            Log.d(TAG, "ExoPlayer 初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "ExoPlayer 初始化失败，降级到默认播放器", e)
            // 降级：纯默认 ExoPlayer（无 FFmpeg）
            try {
                exoPlayer = ExoPlayer.Builder(context).build()
                    .also { it.addListener(playerListener) }
            } catch (e2: Exception) {
                Log.e(TAG, "ExoPlayer 初始化完全失败", e2)
            }
        }
    }

    /**
     * 释放播放器
     *
     * 由 CastService.onDestroy 调用（主线程），同步释放。
     */
    fun release() {
        exoPlayer?.let {
            it.removeListener(playerListener)
            it.release()
        }
        exoPlayer = null
        surface = null
        surfaceReady = false
        currentUrl = null
        currentTitle = null
        pendingUrl = null
        pendingTitle = null
        updateCastState(CastState.IDLE)
        Log.d(TAG, "ExoPlayer 已释放")
    }

    // ========================================================================
    // DMR 播放控制接口（从 Cling 协议线程调用）
    // ========================================================================

    /**
     * 设置媒体源（对应 DMR setAVTransportURI）
     *
     * @param url 媒体地址
     * @param title 媒体标题（可选，从 DIDL-Lite 元数据解析）
     */
    fun setMediaItem(url: String, title: String? = null) {
        Log.d(TAG, "setMediaItem: url=$url, title=$title")
        mainHandler.post {
            currentUrl = url
            currentTitle = title
            onMediaChanged?.invoke(title)

            if (surfaceReady) {
                // Surface 已就绪，直接播放
                playInternal(url)
            } else {
                // Surface 未就绪，暂存并启动播放页
                pendingUrl = url
                pendingTitle = title
                startPlayerActivity()
            }
        }
    }

    /**
     * 播放（对应 DMR play）
     */
    fun play() {
        Log.d(TAG, "play")
        mainHandler.post {
            exoPlayer?.play()
        }
    }

    /**
     * 暂停（对应 DMR pause）
     */
    fun pause() {
        Log.d(TAG, "pause")
        mainHandler.post {
            exoPlayer?.pause()
        }
    }

    /**
     * 停止（对应 DMR stop）
     */
    fun stop() {
        Log.d(TAG, "stop")
        mainHandler.post {
            exoPlayer?.stop()
            updateCastState(CastState.STOPPED)
        }
    }

    /**
     * 跳转（对应 DMR seek）
     *
     * @param positionMs 目标位置（毫秒）
     */
    fun seekTo(positionMs: Long) {
        Log.d(TAG, "seekTo: $positionMs ms")
        mainHandler.post {
            exoPlayer?.seekTo(positionMs)
        }
    }

    // ========================================================================
    // DMR 状态查询接口（线程安全，可直接从协议线程调用）
    // ========================================================================

    /**
     * 当前播放位置（毫秒）
     */
    fun getCurrentPosition(): Long {
        return exoPlayer?.currentPosition ?: 0
    }

    /**
     * 媒体总时长（毫秒）
     */
    fun getDuration(): Long {
        val dur = exoPlayer?.duration ?: C.TIME_UNSET
        return if (dur == C.TIME_UNSET) 0 else dur
    }

    /**
     * 是否正在播放
     */
    fun isPlaying(): Boolean {
        return exoPlayer?.isPlaying ?: false
    }

    /**
     * 当前播放的媒体 URL
     */
    fun getCurrentUrl(): String? = currentUrl

    /**
     * 当前播放的媒体标题
     */
    fun getCurrentTitle(): String? = currentTitle

    /**
     * 当前投屏状态
     */
    fun getCastState(): CastState = castState

    // ========================================================================
    // Surface 生命周期管理（由 PlayerActivity 调用，主线程）
    // ========================================================================

    /**
     * Surface 创建回调
     *
     * 由 PlayerActivity.surfaceCreated 调用。
     * 如果有待播放的 URL，自动开始播放。
     */
    fun onSurfaceCreated(surf: Surface) {
        Log.d(TAG, "Surface 创建")
        surface = surf
        surfaceReady = true
        exoPlayer?.setVideoSurface(surf)

        // 消费待播放请求
        pendingUrl?.let { url ->
            val title = pendingTitle
            pendingUrl = null
            pendingTitle = null
            playInternal(url)
        }
    }

    /**
     * Surface 销毁回调
     *
     * 由 PlayerActivity.surfaceDestroyed 调用。
     * 必须先解绑 Surface，否则向已销毁的 Surface 写数据会崩溃。
     */
    fun onSurfaceDestroyed() {
        Log.d(TAG, "Surface 销毁")
        surfaceReady = false
        exoPlayer?.setVideoSurface(null)
        surface = null
    }

    // ========================================================================
    // PlayerActivity 注册
    // ========================================================================

    /**
     * 注册 PlayerActivity（用于判断播放页是否在前台）
     */
    fun registerPlayerActivity(activity: Activity) {
        playerActivityRef = WeakReference(activity)
    }

    /**
     * 注销 PlayerActivity
     */
    fun unregisterPlayerActivity() {
        playerActivityRef?.clear()
        playerActivityRef = null
    }

    /**
     * PlayerActivity 是否在前台
     */
    fun isPlayerActivityActive(): Boolean {
        return playerActivityRef?.get() != null
    }

    // ========================================================================
    // 内部实现
    // ========================================================================

    /**
     * 启动 PlayerActivity
     */
    private fun startPlayerActivity() {
        appContext?.let { ctx ->
            val intent = Intent(ctx, PlayerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        }
    }

    /**
     * 内部播放实现
     *
     * 设置 MediaItem -> prepare -> play
     */
    private fun playInternal(url: String) {
        val player = exoPlayer
        if (player == null) {
            Log.e(TAG, "ExoPlayer 为空，无法播放")
            onError?.invoke("播放器未初始化")
            return
        }

        updateCastState(CastState.LOADING)

        try {
            val mediaItem = MediaItem.fromUri(url)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
            Log.d(TAG, "开始播放: $url")
        } catch (e: Exception) {
            Log.e(TAG, "播放启动失败", e)
            onError?.invoke("播放启动失败: ${e.message}")
        }
    }

    /**
     * 更新投屏状态并通知回调
     */
    private fun updateCastState(state: CastState) {
        if (castState != state) {
            castState = state
            Log.d(TAG, "投屏状态变更: $state")
            onCastStateChanged?.invoke(state)
        }
    }

    // ========================================================================
    // ExoPlayer 事件监听器
    // ========================================================================

    /**
     * ExoPlayer 事件监听器
     *
     * 监听播放状态变化和错误，同步到 CastState
     */
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    if (castState != CastState.PLAYING) {
                        updateCastState(CastState.LOADING)
                    }
                }
                Player.STATE_READY -> {
                    if (exoPlayer?.isPlaying == true) {
                        updateCastState(CastState.PLAYING)
                    } else {
                        updateCastState(CastState.PAUSED)
                    }
                }
                Player.STATE_ENDED -> {
                    updateCastState(CastState.ENDED)
                }
                Player.STATE_IDLE -> {
                    if (castState != CastState.STOPPED) {
                        updateCastState(CastState.IDLE)
                    }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                updateCastState(CastState.PLAYING)
            } else {
                val state = exoPlayer?.playbackState
                if (state == Player.STATE_READY && castState != CastState.STOPPED) {
                    updateCastState(CastState.PAUSED)
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "播放错误: ${error.message}", error)
            updateCastState(CastState.IDLE)
            onError?.invoke("播放错误: ${error.message}")
        }
    }
}
