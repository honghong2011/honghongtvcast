package com.hh2011.cast.tv.dlna

import com.hh2011.cast.tv.player.CastPlayerManager
import com.hh2011.cast.tv.util.FormatUtil
import org.fourthline.cling.model.types.UnsignedIntegerFourBytes
import org.fourthline.cling.support.avtransport.AbstractAVTransportService
import org.fourthline.cling.support.avtransport.lastchange.AVTransportLastChangeParser
import org.fourthline.cling.support.lastchange.LastChange
import org.fourthline.cling.support.model.*
import org.xml.sax.XMLReader
import javax.xml.parsers.SAXParserFactory

/**
 * DMR AVTransport 服务实现
 *
 * 接收发送端的播放控制指令，对接 CastPlayerManager（ExoPlayer）。
 *
 * 核心方法映射：
 * - setAVTransportURI -> CastPlayerManager.setMediaItem(url)
 * - play              -> CastPlayerManager.play()
 * - pause             -> CastPlayerManager.pause()
 * - stop              -> CastPlayerManager.stop()
 * - seek              -> CastPlayerManager.seekTo(position)
 * - getPositionInfo   -> CastPlayerManager.getCurrentPosition() / getDuration()
 * - getTransportInfo  -> 根据 CastPlayerManager.getCastState() 返回状态
 *
 * 从 HPlayer 的 AVTransportService 移植改造：
 * - 去掉 Context 参数（改为调用 CastPlayerManager 单例）
 * - setAVTransportURI 不再启动 Activity，由 CastPlayerManager 负责启动播放页
 * - getPositionInfo/getTransportInfo 返回真实的 ExoPlayer 状态
 */
class DmrAVTransportService : AbstractAVTransportService(createSecureLastChange()) {

    companion object {
        /**
         * 创建安全的 LastChange（XXE 防护）
         *
         * 复用 HPlayer 的 XXE 防护方案，禁用外部实体注入。
         */
        private fun createSecureLastChange(): LastChange {
            return LastChange(object : AVTransportLastChangeParser() {
                override fun create(): XMLReader {
                    return try {
                        val factory = SAXParserFactory.newInstance()
                        // 禁用外部实体，防止 XXE 攻击
                        factory.setFeature(
                            "http://xml.org/sax/features/external-general-entities",
                            false
                        )
                        factory.setFeature(
                            "http://xml.org/sax/features/external-parameter-entities",
                            false
                        )
                        if (schemaSources != null) {
                            factory.schema = createSchema(schemaSources)
                        }
                        val xmlReader = factory.newSAXParser().xmlReader
                        xmlReader.errorHandler = errorHandler
                        xmlReader
                    } catch (ex: Exception) {
                        throw RuntimeException(ex)
                    }
                }
            })
        }
    }

    /** 当前实例 ID */
    private var currentInstanceId: UnsignedIntegerFourBytes? = null

    override fun getCurrentInstanceIds(): Array<UnsignedIntegerFourBytes> {
        return arrayOf(currentInstanceId ?: UnsignedIntegerFourBytes(0))
    }

    // ========================================================================
    // 核心控制方法
    // ========================================================================

    /**
     * 设置媒体 URI（发送端推送投屏时调用）
     *
     * 对接 CastPlayerManager.setMediaItem，由 CastPlayerManager 负责启动播放页。
     */
    override fun setAVTransportURI(
        instanceId: UnsignedIntegerFourBytes?,
        currentURI: String?,
        currentURIMetaData: String?
    ) {
        currentInstanceId = instanceId
        val url = currentURI ?: return
        // 标题暂用 URL，后续可从 currentURIMetaData（DIDL-Lite）解析
        val title = extractTitleFromMetaData(currentURIMetaData) ?: url
        CastPlayerManager.setMediaItem(url, title)
    }

    override fun setNextAVTransportURI(
        instanceId: UnsignedIntegerFourBytes?,
        nextURI: String?,
        nextURIMetaData: String?
    ) {
        // 播放下一个（暂不实现）
    }

    /**
     * 播放
     */
    override fun play(instanceId: UnsignedIntegerFourBytes?, speed: String?) {
        CastPlayerManager.play()
    }

    /**
     * 暂停
     */
    override fun pause(instanceId: UnsignedIntegerFourBytes?) {
        CastPlayerManager.pause()
    }

    /**
     * 停止
     */
    override fun stop(instanceId: UnsignedIntegerFourBytes?) {
        CastPlayerManager.stop()
    }

    /**
     * 跳转
     *
     * DLNA seek 的 unit 通常是 REL_TIME，target 是 "HH:MM:SS" 格式。
     */
    override fun seek(instanceId: UnsignedIntegerFourBytes?, unit: String?, target: String?) {
        try {
            val seekMode = SeekMode.valueOrExceptionOf(unit)
            if (seekMode == SeekMode.REL_TIME) {
                val positionMs = FormatUtil.transformTime(target)
                CastPlayerManager.seekTo(positionMs)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ========================================================================
    // 状态查询方法
    // ========================================================================

    /**
     * 获取媒体信息
     */
    override fun getMediaInfo(instanceId: UnsignedIntegerFourBytes?): MediaInfo {
        val url = CastPlayerManager.getCurrentUrl() ?: ""
        return MediaInfo(url, "")
    }

    /**
     * 获取传输状态
     *
     * 将 CastPlayerManager.CastState 映射为 DLNA TransportState。
     */
    override fun getTransportInfo(instanceId: UnsignedIntegerFourBytes?): TransportInfo {
        val state = when (CastPlayerManager.getCastState()) {
            CastPlayerManager.CastState.PLAYING -> TransportState.PLAYING
            CastPlayerManager.CastState.PAUSED -> TransportState.PAUSED_PLAYBACK
            CastPlayerManager.CastState.STOPPED -> TransportState.STOPPED
            CastPlayerManager.CastState.ENDED -> TransportState.STOPPED
            CastPlayerManager.CastState.LOADING -> TransportState.TRANSITIONING
            CastPlayerManager.CastState.IDLE -> TransportState.NO_MEDIA_PRESENT
        }
        return TransportInfo(state)
    }

    /**
     * 获取播放位置信息
     *
     * 返回 ExoPlayer 的当前进度和总时长。
     * 使用 HPlayer 验证过的方式：创建空 PositionInfo 再赋值字段。
     */
    override fun getPositionInfo(instanceId: UnsignedIntegerFourBytes?): PositionInfo {
        val positionInfo = PositionInfo()
        val position = CastPlayerManager.getCurrentPosition()
        val duration = CastPlayerManager.getDuration()
        if (duration > 0) {
            positionInfo.trackDuration = FormatUtil.formatTime(duration, true)
            positionInfo.relTime = FormatUtil.formatTime(position, true)
        }
        return positionInfo
    }

    /**
     * 获取设备能力
     */
    override fun getDeviceCapabilities(instanceId: UnsignedIntegerFourBytes?): DeviceCapabilities {
        return DeviceCapabilities(arrayOf(StorageMedium.NETWORK))
    }

    /**
     * 获取传输设置
     */
    override fun getTransportSettings(instanceId: UnsignedIntegerFourBytes?): TransportSettings {
        return TransportSettings(PlayMode.NORMAL)
    }

    /**
     * 获取当前可执行的播放操作
     */
    override fun getCurrentTransportActions(instanceId: UnsignedIntegerFourBytes?): Array<TransportAction> {
        return when (CastPlayerManager.getCastState()) {
            CastPlayerManager.CastState.PLAYING -> {
                arrayOf(TransportAction.Stop, TransportAction.Pause, TransportAction.Seek)
            }
            CastPlayerManager.CastState.PAUSED -> {
                arrayOf(TransportAction.Stop, TransportAction.Play, TransportAction.Seek)
            }
            CastPlayerManager.CastState.STOPPED,
            CastPlayerManager.CastState.ENDED -> {
                arrayOf(TransportAction.Play)
            }
            else -> arrayOf()
        }
    }

    // ========================================================================
    // 未实现的方法
    // ========================================================================

    override fun record(instanceId: UnsignedIntegerFourBytes?) {
        // 录制（不实现）
    }

    override fun next(instanceId: UnsignedIntegerFourBytes?) {
        // 下一个（不实现）
    }

    override fun previous(instanceId: UnsignedIntegerFourBytes?) {
        // 上一个（不实现）
    }

    override fun setPlayMode(instanceId: UnsignedIntegerFourBytes?, newPlayMode: String?) {
        // 播放模式（不实现）
    }

    override fun setRecordQualityMode(
        instanceId: UnsignedIntegerFourBytes?,
        newRecordQualityMode: String?
    ) {
        // 录制质量（不实现）
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    /**
     * 从 DIDL-Lite 元数据中提取标题
     *
     * 简单实现：从 XML 中搜索 <dc:title> 标签内容。
     * 完整实现需要 DIDL-Lite 解析器，后续优化。
     */
    private fun extractTitleFromMetaData(metaData: String?): String? {
        if (metaData.isNullOrEmpty()) return null
        return try {
            val regex = "<dc:title>(.*?)</dc:title>".toRegex()
            regex.find(metaData)?.groupValues?.getOrNull(1)
        } catch (e: Exception) {
            null
        }
    }
}
