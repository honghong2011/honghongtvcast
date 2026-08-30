package com.hh2011.cast.tv.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hh2011.cast.tv.service.CastService
import com.hh2011.cast.tv.util.PreferencesHelper

/**
 * 开机自启动广播接收器
 *
 * 监听 ACTION_BOOT_COMPLETED 广播，开机后自动启动投屏服务。
 *
 * 纯非 root 标准实现：
 * - 在 AndroidManifest 注册 RECEIVE_BOOT_COMPLETED 权限
 * - 声明 BOOT_COMPLETED intent-filter
 * - 开机后系统自动发送广播，接收后启动 CastService
 *
 * 适配 Android 5.0-9.0：
 * - Android 5.0+: 应用安装后即可接收开机广播
 * - Android 8.0+ 对隐式广播有限制，但 BOOT_COMPLETED 豁免
 * - 用户可在设置页开关控制是否开机自启
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        Log.d(TAG, "收到开机广播")

        // 检查用户是否开启了开机自启动
        if (PreferencesHelper.isBootAutoStartEnabled(context)) {
            Log.d(TAG, "开机自启动已开启，启动投屏服务")
            CastService.start(context)
        } else {
            Log.d(TAG, "开机自启动未开启，跳过")
        }
    }
}
