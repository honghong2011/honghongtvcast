package com.hh2011.cast.tv.ui

import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.hh2011.cast.tv.R
import com.hh2011.cast.tv.player.CastPlayerManager
import com.hh2011.cast.tv.util.PreferencesHelper

/**
 * 投屏播放页
 *
 * 持有 SurfaceView 供 ExoPlayer 输出画面，转发 Surface 生命周期给 CastPlayerManager。
 *
 * 遥控器按键映射：
 * - OK/ENTER：播放与暂停切换
 * - 左右键：快退/快进 10 秒
 * - 上下键：音量增大/减小
 * - 返回键：退出播放页
 *
 * 屏幕常亮由用户设置项（PreferencesHelper.isKeepScreenOnEnabled）控制。
 */
class PlayerActivity : FragmentActivity(), SurfaceHolder.Callback {

    private lateinit var surfaceView: SurfaceView
    private lateinit var tvMediaTitle: TextView
    private lateinit var tvPlayerInfo: TextView

    /** 系统音频管理器，用于遥控器上下键调音量 */
    private lateinit var audioManager: AudioManager

    companion object {
        /** 单次快进/快退步长（毫秒） */
        private const val SEEK_STEP_MS = 10_000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // 屏幕唤醒：投屏到达时唤醒 TV 屏幕（屏幕关闭时也能亮屏播放）
        window.addFlags(
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )
        // 屏幕常亮：根据用户设置决定播放期间是否保持屏幕常亮
        if (PreferencesHelper.isKeepScreenOnEnabled(this)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        initViews()

        // 获取系统音频服务（遥控器上下键调音量用）
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        // 注册当前 Activity，便于播放器判断播放页是否在前台
        CastPlayerManager.registerPlayerActivity(this)

        // Surface 回调转发给播放器管理器
        surfaceView.holder.addCallback(this)

        // 显示已有标题（播放页可能由 DMR 在后台启动，此时标题已存在）
        updateMediaTitle(CastPlayerManager.getCurrentTitle())

        // 注册播放器回调
        CastPlayerManager.onMediaChanged = { title -> updateMediaTitle(title) }
        CastPlayerManager.onError = { msg -> showInfo(msg) }
    }

    /** 初始化视图引用 */
    private fun initViews() {
        surfaceView = findViewById(R.id.surfaceView)
        tvMediaTitle = findViewById(R.id.tvMediaTitle)
        tvPlayerInfo = findViewById(R.id.tvPlayerInfo)
    }

    /** 更新右上角媒体标题，空则隐藏 */
    private fun updateMediaTitle(title: String?) {
        if (title.isNullOrBlank()) {
            tvMediaTitle.visibility = View.GONE
            tvMediaTitle.text = null
        } else {
            tvMediaTitle.text = title
            tvMediaTitle.visibility = View.VISIBLE
        }
    }

    /** 显示底部提示文案（加载/错误） */
    private fun showInfo(text: String) {
        tvPlayerInfo.text = text
        tvPlayerInfo.visibility = View.VISIBLE
    }

    // ========================================================================
    // SurfaceHolder.Callback：转发给 CastPlayerManager
    // ========================================================================

    override fun surfaceCreated(holder: SurfaceHolder) {
        CastPlayerManager.onSurfaceCreated(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Surface 尺寸变化由系统处理，无需额外操作
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        CastPlayerManager.onSurfaceDestroyed()
    }

    // ========================================================================
    // 遥控器按键处理
    // ========================================================================

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                // OK/ENTER：播放与暂停切换
                if (CastPlayerManager.getCastState() == CastPlayerManager.CastState.PLAYING) {
                    CastPlayerManager.pause()
                } else {
                    CastPlayerManager.play()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                // 后退 10 秒（不低于 0）
                val target = (CastPlayerManager.getCurrentPosition() - SEEK_STEP_MS)
                    .coerceAtLeast(0)
                CastPlayerManager.seekTo(target)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                // 前进 10 秒（不超过总时长）
                val duration = CastPlayerManager.getDuration()
                val target = CastPlayerManager.getCurrentPosition() + SEEK_STEP_MS
                CastPlayerManager.seekTo(
                    if (duration > 0) target.coerceAtMost(duration) else target
                )
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                // 上键：音量增大
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0
                )
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                // 下键：音量减小
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0
                )
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        // 退出播放页时停止播放，避免"画面消失声音继续"
        // （符合开发计划：播放退出后释放常亮锁、恢复待投屏状态）
        if (CastPlayerManager.getCastState() != CastPlayerManager.CastState.IDLE) {
            CastPlayerManager.stop()
        }
        super.onDestroy()
        // 清除回调，避免持有已销毁的 Activity
        CastPlayerManager.onMediaChanged = null
        CastPlayerManager.onError = null
        CastPlayerManager.unregisterPlayerActivity()
    }
}
