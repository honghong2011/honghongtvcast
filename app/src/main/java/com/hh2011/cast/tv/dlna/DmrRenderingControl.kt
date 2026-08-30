package com.hh2011.cast.tv.dlna

import android.app.Service
import android.content.Context
import android.media.AudioManager
import org.fourthline.cling.model.types.UnsignedIntegerFourBytes
import org.fourthline.cling.model.types.UnsignedIntegerTwoBytes
import org.fourthline.cling.support.lastchange.LastChange
import org.fourthline.cling.support.model.Channel
import org.fourthline.cling.support.renderingcontrol.AbstractAudioRenderingControl
import org.fourthline.cling.support.renderingcontrol.lastchange.RenderingControlLastChangeParser
import org.xml.sax.XMLReader
import javax.xml.parsers.SAXParserFactory

/**
 * DMR RenderingControl 服务实现
 *
 * 对接系统 AudioManager，控制媒体音量和静音。
 *
 * 直接从 HPlayer 的 AudioRenderingControl 移植，逻辑基本不变：
 * - getVolume/setVolume: 通过 AudioManager 控制 STREAM_MUSIC 音量
 * - getMute/setMute: 音量为 0 即视为静音
 */
class DmrRenderingControl(context: Context) :
    AbstractAudioRenderingControl(createSecureLastChange()) {

    companion object {
        /**
         * 创建安全的 LastChange（XXE 防护）
         */
        private fun createSecureLastChange(): LastChange {
            return LastChange(object : RenderingControlLastChangeParser() {
                override fun create(): XMLReader {
                    return try {
                        val factory = SAXParserFactory.newInstance()
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

    /** 系统音频管理器 */
    private val audioManager =
        context.applicationContext.getSystemService(Service.AUDIO_SERVICE) as AudioManager

    /** 最大音量 */
    private val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    /** 静音前的音量值（用于取消静音时恢复） */
    private var volumeBeforeMute: Int = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    override fun getCurrentInstanceIds(): Array<UnsignedIntegerFourBytes> {
        return arrayOf(UnsignedIntegerFourBytes(0))
    }

    /**
     * 获取静音状态
     *
     * 音量为 0 即视为静音。
     */
    override fun getMute(instanceId: UnsignedIntegerFourBytes?, channelName: String?): Boolean {
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
    }

    /**
     * 设置静音
     */
    override fun setMute(
        instanceId: UnsignedIntegerFourBytes?,
        channelName: String?,
        desiredMute: Boolean
    ) {
        if (desiredMute) {
            // 静音前保存当前音量
            volumeBeforeMute = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        } else {
            // 取消静音，恢复之前音量
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeBeforeMute, 0)
        }
    }

    /**
     * 获取音量
     *
     * @return 当前音量（0-100）
     */
    override fun getVolume(
        instanceId: UnsignedIntegerFourBytes?,
        channelName: String?
    ): UnsignedIntegerTwoBytes {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        // 将系统音量映射到 0-100 范围
        val normalized = if (maxVolume > 0) (current * 100) / maxVolume else 0
        return UnsignedIntegerTwoBytes(normalized.toLong())
    }

    /**
     * 设置音量
     *
     * @param desiredVolume 0-100 的音量值
     */
    override fun setVolume(
        instanceId: UnsignedIntegerFourBytes?,
        channelName: String?,
        desiredVolume: UnsignedIntegerTwoBytes?
    ) {
        // 将 0-100 映射回系统音量范围
        val targetPercent = desiredVolume?.value?.toInt() ?: 0
        val systemVolume = if (maxVolume > 0) (targetPercent * maxVolume) / 100 else 0
        val clamped = systemVolume.coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0)
    }

    override fun getCurrentChannels(): Array<Channel> {
        return arrayOf(Channel.Master)
    }
}
