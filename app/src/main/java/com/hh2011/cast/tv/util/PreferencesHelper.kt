package com.hh2011.cast.tv.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings

/**
 * SharedPreferences 工具类
 *
 * 管理投屏接收端的配置项：
 * - 开机自启动开关
 * - 设备名称（DLNA 发现时的设备名）
 * - DLNA 协议开关
 * - 硬解/软解切换
 * - 屏幕常亮开关
 */
object PreferencesHelper {

    private const val PREF_NAME = "tvcast_prefs"

    // ==========================================
    // 支持的投屏协议（命名用，后续新增协议在此登记）
    // ==========================================
    const val PROTOCOL_DLNAC = "DLNA"

    // ==========================================
    // 配置项 Key
    // ==========================================
    const val KEY_BOOT_AUTO_START = "boot_auto_start"
    const val KEY_DEVICE_NAME = "device_name"
    const val KEY_DLNA_ENABLED = "dlna_enabled"
    const val KEY_HARDWARE_DECODING = "hardware_decoding"
    const val KEY_KEEP_SCREEN_ON = "keep_screen_on"

    // ==========================================
    // 默认值
    // ==========================================
    /** 设备名中的产品标识部分 */
    private const val PRODUCT_NAME = "宏宏投屏"
    private const val DEFAULT_DLNA_ENABLED = true
    private const val DEFAULT_HW_DECODING = true
    private const val DEFAULT_KEEP_SCREEN_ON = true
    private const val DEFAULT_BOOT_AUTO_START = false

    // ==========================================
    // 内部方法
    // ==========================================
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private fun getEditor(context: Context): SharedPreferences.Editor {
        return getPrefs(context).edit()
    }

    // ==========================================
    // 开机自启动
    // ==========================================
    fun isBootAutoStartEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BOOT_AUTO_START, DEFAULT_BOOT_AUTO_START)
    }

    fun setBootAutoStartEnabled(context: Context, enabled: Boolean) {
        getEditor(context).putBoolean(KEY_BOOT_AUTO_START, enabled).apply()
    }

    // ==========================================
    // 设备名称
    // ==========================================

    /**
     * 获取设备名称
     *
     * 首次调用（SharedPreferences 无存储值）时生成默认名称并立即持久化：
     * [系统设备名]-[宏宏投屏]-[协议名]
     *
     * 立即持久化的原因：锁定首次生成的结果，之后系统设备名即使变化，
     * 投屏设备名也保持稳定（用户可在设置页手动修改）。
     */
    fun getDeviceName(context: Context): String {
        val saved = getPrefs(context).getString(KEY_DEVICE_NAME, null)
        if (saved != null) return saved

        // 首次：生成默认名并写入
        val defaultName = generateDefaultName(context)
        getEditor(context).putString(KEY_DEVICE_NAME, defaultName).apply()
        return defaultName
    }

    fun setDeviceName(context: Context, name: String) {
        getEditor(context).putString(KEY_DEVICE_NAME, name).apply()
    }

    /**
     * 生成默认设备名称
     *
     * 格式：[系统设备名]-宏宏投屏-DLNA
     */
    private fun generateDefaultName(context: Context): String {
        return "${getSystemDeviceName(context)}-$PRODUCT_NAME-$PROTOCOL_DLNAC"
    }

    /**
     * 获取系统设备名（用于默认名称的[设备名]部分）
     *
     * 取值优先级：
     * 1. Settings.Global "device_name" —— 用户在系统设置里自定义的设备名
     *    （Android 4.2+，读取无需权限；TV 设备上可能未设置，返回 null）
     * 2. Build.MODEL —— 出厂型号名（必有值，如 "MiBOX4"/"HUAWEI Vision"）
     * 3. "TV" —— 终极兜底（MODEL 理论上非空，纯防御）
     */
    private fun getSystemDeviceName(context: Context): String {
        return try {
            val globalName = Settings.Global.getString(
                context.contentResolver, "device_name"
            )
            when {
                !globalName.isNullOrBlank() -> globalName
                !Build.MODEL.isNullOrBlank() -> Build.MODEL
                else -> "TV"
            }
        } catch (e: Exception) {
            // 个别 ROM 读取 Settings.Global 可能异常，兜底走型号
            Build.MODEL ?: "TV"
        }
    }

    // ==========================================
    // DLNA 开关
    // ==========================================
    fun isDlnaEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DLNA_ENABLED, DEFAULT_DLNA_ENABLED)
    }

    fun setDlnaEnabled(context: Context, enabled: Boolean) {
        getEditor(context).putBoolean(KEY_DLNA_ENABLED, enabled).apply()
    }

    // ==========================================
    // 硬件解码
    // ==========================================
    fun isHardwareDecodingEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_HARDWARE_DECODING, DEFAULT_HW_DECODING)
    }

    fun setHardwareDecodingEnabled(context: Context, enabled: Boolean) {
        getEditor(context).putBoolean(KEY_HARDWARE_DECODING, enabled).apply()
    }

    // ==========================================
    // 屏幕常亮
    // ==========================================
    fun isKeepScreenOnEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_KEEP_SCREEN_ON, DEFAULT_KEEP_SCREEN_ON)
    }

    fun setKeepScreenOnEnabled(context: Context, enabled: Boolean) {
        getEditor(context).putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
    }
}
