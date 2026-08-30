package com.hh2011.cast.tv.util

/**
 * 时间格式化工具
 *
 * DLNA 协议中时间以 HH:MM:SS.mmm 格式传输，需要与毫秒互转。
 * 直接从 HPlayer 移植，验证可用。
 */
object FormatUtil {

    /**
     * 毫秒 -> 格式化时间字符串
     *
     * @param timeMs 毫秒
     * @param alwaysShowHour 是否总是显示小时位
     * @return "HH:MM:SS" 或 "MM:SS"
     */
    @JvmStatic
    @JvmOverloads
    fun formatTime(timeMs: Long, alwaysShowHour: Boolean = false): String {
        val totalSeconds = (timeMs / 1000).toInt()
        val seconds = totalSeconds % 60
        val minutes = totalSeconds / 60 % 60
        val hours = totalSeconds / 3600
        return if (hours > 0 || alwaysShowHour) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    /**
     * 格式化时间字符串 -> 毫秒
     *
     * DLNA 传输的时间可能带小数秒，如 "0:00:30.123"
     *
     * @param formatTime 格式化的时间
     * @return 毫秒
     */
    @JvmStatic
    fun transformTime(formatTime: String?): Long {
        if (formatTime.isNullOrEmpty()) {
            return 0
        }
        val splitArray = formatTime.split(":")
        return when (splitArray.size) {
            1 -> (splitArray[0].toDouble() * 1000).toLong()
            2 -> ((splitArray[0].toInt() * 60 + splitArray[1].toDouble()) * 1000).toLong()
            3 -> ((splitArray[0].toInt() * 3600 + splitArray[1].toInt() * 60 + splitArray[2].toDouble()) * 1000).toLong()
            else -> 0
        }
    }
}
