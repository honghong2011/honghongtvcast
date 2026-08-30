package com.hh2011.cast.tv

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.hh2011.cast.tv.player.CastPlayerManager
import com.hh2011.cast.tv.service.CastService
import com.hh2011.cast.tv.util.PreferencesHelper

/**
 * 投屏接收端主页面
 *
 * 展示设备信息与投屏状态，提供设置入口。
 * 监听 CastPlayerManager 的状态/媒体/错误回调实时更新 UI。
 *
 * 焦点适配：底部设置按钮可获焦，遥控器确认键进入设置；
 * 菜单键同样进入设置；返回键双击退出。
 */
class MainActivity : FragmentActivity() {

    private lateinit var tvDeviceName: TextView
    private lateinit var tvWifiName: TextView
    private lateinit var tvCastStatus: TextView

    /** 上次按返回键的时间戳，用于双击退出判定 */
    private var lastBackPressTime = 0L

    companion object {
        /** 双击退出的时间窗口（毫秒） */
        private const val BACK_PRESS_INTERVAL_MS = 2000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 启动投屏前台服务（UPnP 协议引擎 + DMR 设备注册）
        // 仅在 DLNA 开关开启时启动，关闭时服务不运行
        if (PreferencesHelper.isDlnaEnabled(this)) {
            CastService.start(this)
        }

        initViews()
        showDeviceInfo()
        bindSettings()
    }

    /** 每次回到前台重新注册回调并刷新状态
     *  （播放页返回后回调已被其 onDestroy 清空，这里恢复） */
    override fun onResume() {
        super.onResume()
        registerCastCallbacks()
        refreshCastStatus()
    }

    /** 初始化视图引用 */
    private fun initViews() {
        tvDeviceName = findViewById(R.id.tvDeviceName)
        tvWifiName = findViewById(R.id.tvWifiName)
        tvCastStatus = findViewById(R.id.tvCastStatus)
    }

    /** 显示设备名称与 WiFi 名称 */
    private fun showDeviceInfo() {
        tvDeviceName.text = PreferencesHelper.getDeviceName(this)
        tvWifiName.text = getWifiSsid()
    }

    /** 获取当前连接的 WiFi SSID，失败或未连接时返回兜底文案 */
    private fun getWifiSsid(): String {
        return try {
            val wifiManager = applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ssid = wifiManager?.connectionInfo?.ssid
            // 未连接或无权限时可能返回 <unknown ssid> 或 null
            if (ssid.isNullOrBlank() || ssid == "<unknown ssid>") {
                "未连接 WiFi"
            } else {
                ssid
            }
        } catch (e: Exception) {
            "未连接 WiFi"
        }
    }

    /** 绑定设置按钮点击 */
    private fun bindSettings() {
        findViewById<MaterialButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    /** 注册播放器回调，实时更新 UI */
    private fun registerCastCallbacks() {
        CastPlayerManager.onCastStateChanged = { state ->
            tvCastStatus.text = castStateText(state)
        }
        CastPlayerManager.onMediaChanged = { title ->
            // 主页仅提示正在投屏，具体标题由播放页展示
            if (!title.isNullOrBlank()) {
                tvCastStatus.text = getString(R.string.status_connected)
            }
        }
        CastPlayerManager.onError = { msg ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    /** 根据投屏状态生成展示文案 */
    private fun castStateText(state: CastPlayerManager.CastState): String {
        return when (state) {
            CastPlayerManager.CastState.IDLE -> getString(R.string.status_waiting)
            CastPlayerManager.CastState.LOADING -> getString(R.string.player_loading)
            CastPlayerManager.CastState.PLAYING -> getString(R.string.status_connected)
            CastPlayerManager.CastState.PAUSED -> getString(R.string.status_connected)
            CastPlayerManager.CastState.STOPPED -> getString(R.string.status_waiting)
            CastPlayerManager.CastState.ENDED -> getString(R.string.status_waiting)
        }
    }

    /** 用当前状态刷新一次文案 */
    private fun refreshCastStatus() {
        tvCastStatus.text = castStateText(CastPlayerManager.getCastState())
    }

    /** 菜单键：进入设置页 */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /** 返回键：双击退出应用 */
    override fun onBackPressed() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackPressTime < BACK_PRESS_INTERVAL_MS) {
            super.onBackPressed()
            return
        }
        lastBackPressTime = now
        Toast.makeText(this, R.string.btn_exit_confirm, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清除回调避免持有已销毁的 Activity 造成泄漏
        CastPlayerManager.onCastStateChanged = null
        CastPlayerManager.onMediaChanged = null
        CastPlayerManager.onError = null
    }
}
