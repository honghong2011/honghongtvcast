package com.hh2011.cast.tv.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.hh2011.cast.tv.MainActivity
import com.hh2011.cast.tv.R
import com.hh2011.cast.tv.dlna.ClingUpnpService
import com.hh2011.cast.tv.dlna.DmrAVTransportService
import com.hh2011.cast.tv.dlna.DmrConnectionManager
import com.hh2011.cast.tv.dlna.DmrRenderingControl
import com.hh2011.cast.tv.player.CastPlayerManager
import com.hh2011.cast.tv.util.PreferencesHelper
import org.fourthline.cling.binding.annotations.AnnotationLocalServiceBinder
import org.fourthline.cling.model.DefaultServiceManager
import org.fourthline.cling.model.meta.DeviceDetails
import org.fourthline.cling.model.meta.DeviceIdentity
import org.fourthline.cling.model.meta.LocalDevice
import org.fourthline.cling.model.meta.LocalService
import org.fourthline.cling.model.types.UDADeviceType
import org.fourthline.cling.model.types.UDN
import org.fourthline.cling.support.avtransport.AbstractAVTransportService
import org.fourthline.cling.support.connectionmanager.ConnectionManagerService
import org.fourthline.cling.support.renderingcontrol.AbstractAudioRenderingControl
import java.util.UUID

/**
 * 投屏前台常驻服务
 *
 * 职责：
 * 1. 继承 ClingUpnpService（UPnP 协议引擎），在其基础上注册 DMR 设备
 * 2. 作为前台服务运行，确保退后台/屏幕关闭时不被系统杀死
 * 3. 持有 WakeLock + WiFiLock + MulticastLock，保持网络监听不中断
 * 4. 初始化 CastPlayerManager（ExoPlayer）
 *
 * 生命周期：
 * - onCreate: 启动前台通知 -> 获取锁 -> 初始化播放器 -> 注册 DMR 设备
 * - onDestroy: 移除 DMR 设备 -> 释放播放器 -> 释放锁 -> 停止前台
 *
 * 架构参考：HPlayer 的 UpnpDMSService（设备注册模式）+ HttpServerService（前台通知模式）
 */
class CastService : ClingUpnpService() {

    companion object {
        private const val TAG = "CastService"
        private const val NOTIFICATION_ID = 8888
        private const val CHANNEL_ID = "cast_service_channel"

        /** 服务是否正在运行（供外部查询） */
        @Volatile
        private var isRunning = false

        /**
         * 启动投屏服务
         */
        fun start(context: Context) {
            val intent = Intent(context, CastService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止投屏服务
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, CastService::class.java))
        }

        /**
         * 服务是否正在运行
         */
        fun isServiceRunning(): Boolean = isRunning
    }

    // ========================================================================
    // 锁资源
    // ========================================================================

    /** 唤醒锁：屏幕关闭后保持 CPU 运行 */
    private var wakeLock: PowerManager.WakeLock? = null

    /** WiFi 锁：保持 WiFi 高性能模式 */
    private var wifiLock: WifiManager.WifiLock? = null

    /** 多播锁：允许接收 SSDP 多播包（DLNA 设备发现必需） */
    private var multicastLock: WifiManager.MulticastLock? = null

    // ========================================================================
    // 生命周期
    // ========================================================================

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "CastService onCreate")

        // 1. 启动前台通知（必须在 5 秒内调用 startForeground）
        startForegroundNotification()

        // 2. 获取锁资源
        acquireLocks()

        // 3. 初始化播放器
        CastPlayerManager.init(this)

        // 4. 注册 DMR 设备
        registerDmrDevice()

        isRunning = true
        Log.d(TAG, "CastService 启动完成")
    }

    override fun onDestroy() {
        Log.d(TAG, "CastService onDestroy")

        isRunning = false

        // 1. 释放播放器
        CastPlayerManager.release()

        // 2. 释放锁资源
        releaseLocks()

        // 3. 停止前台
        stopForeground(true)

        // 4. super.onDestroy() 会关闭 UPnP 协议引擎
        super.onDestroy()
        Log.d(TAG, "CastService 已销毁")
    }

    override fun onBind(intent: Intent?): IBinder? {
        // 返回父类的 AndroidUpnpService binder
        return super.onBind(intent)
    }

    // ========================================================================
    // 前台通知
    // ========================================================================

    /**
     * 创建通知渠道并启动前台服务
     */
    private fun startForegroundNotification() {
        // Android 8.0+ 需要创建通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW // 低优先级，不打扰用户
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        // 点击通知回到 MainActivity
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        // FLAG_IMMUTABLE 在 API 23+ 可用，compileSdk 28 无 VERSION_CODES.S
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        // 构建通知
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        val notification = builder
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setWhen(System.currentTimeMillis())
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        // 启动前台服务
        // 注意：compileSdk 28 不支持 startForeground(int, Notification, int) 重载
        // （FOREGROUND_SERVICE_TYPE_* 常量在 API 29+ 才有）
        // 使用基础重载即可，Android 10+ 会用默认类型
        startForeground(NOTIFICATION_ID, notification)
    }

    // ========================================================================
    // 锁资源管理
    // ========================================================================

    /**
     * 获取 WakeLock + WiFiLock + MulticastLock
     *
     * - PARTIAL_WAKE_LOCK: 屏幕关闭后保持 CPU 运行
     * - WIFI_MODE_FULL_HIGH_PERF: WiFi 高性能模式，不进入省电
     * - MulticastLock: 允许接收 SSDP 多播包（DLNA 发现必需）
     */
    private fun acquireLocks() {
        // WakeLock
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "tvcast:cast_service"
        ).apply { acquire() }

        // WiFiLock + MulticastLock
        val wifiManager = getSystemService(WIFI_SERVICE) as WifiManager

        wifiLock = wifiManager.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "tvcast:wifi_lock"
        ).apply { acquire() }

        multicastLock = wifiManager.createMulticastLock("tvcast:multicast").apply {
            acquire()
        }

        Log.d(TAG, "锁资源已获取: WakeLock + WiFiLock + MulticastLock")
    }

    /**
     * 释放所有锁资源
     */
    private fun releaseLocks() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null

        wifiLock?.let {
            if (it.isHeld) it.release()
        }
        wifiLock = null

        multicastLock?.let {
            if (it.isHeld) it.release()
        }
        multicastLock = null

        Log.d(TAG, "锁资源已释放")
    }

    // ========================================================================
    // DMR 设备注册
    // ========================================================================

    /**
     * 注册 DMR（MediaRenderer）设备到 UPnP 注册表
     *
     * 从 HPlayer 的 UpnpDMSService.addLocalDevice 移植改造：
     * - 设备类型改为 MediaRenderer（HPlayer 是 MediaServer）
     * - 服务改为 AVTransport + RenderingControl + ConnectionManager（去掉 ContentDirectory）
     * - 添加 DLNADoc 标识为 DMR
     */
    private fun registerDmrDevice() {
        try {
            val binder = AnnotationLocalServiceBinder()
            val deviceName = PreferencesHelper.getDeviceName(this)

            // --- AVTransport 服务 ---
            val avTransportService: LocalService<AbstractAVTransportService> =
                binder.read(AbstractAVTransportService::class.java)
                    as LocalService<AbstractAVTransportService>
            avTransportService.manager =
                object : DefaultServiceManager<AbstractAVTransportService>(avTransportService) {
                    override fun createServiceInstance(): AbstractAVTransportService {
                        return DmrAVTransportService()
                    }
                }

            // --- RenderingControl 服务 ---
            val renderingControl: LocalService<AbstractAudioRenderingControl> =
                binder.read(AbstractAudioRenderingControl::class.java)
                    as LocalService<AbstractAudioRenderingControl>
            renderingControl.manager =
                object : DefaultServiceManager<AbstractAudioRenderingControl>(renderingControl) {
                    override fun createServiceInstance(): AbstractAudioRenderingControl {
                        return DmrRenderingControl(this@CastService)
                    }
                }

            // --- ConnectionManager 服务 ---
            // 读取父类 ConnectionManagerService（与 HPlayer 读取 AbstractAVTransportService 模式一致）
            val connectionManager: LocalService<ConnectionManagerService> =
                binder.read(ConnectionManagerService::class.java)
                    as LocalService<ConnectionManagerService>
            connectionManager.manager =
                object : DefaultServiceManager<ConnectionManagerService>(connectionManager) {
                    override fun createServiceInstance(): ConnectionManagerService {
                        return DmrConnectionManager()
                    }
                }

            // --- 创建 DMR 设备 ---
            // UDN: 基于设备型号生成稳定 UUID（重启后不变，避免重复发现）
            val deviceIdentifier = "hh2011-cast-tv-" + android.os.Build.MODEL
            val udn = UDN(UUID.nameUUIDFromBytes(deviceIdentifier.toByteArray()))

            val deviceType = UDADeviceType("MediaRenderer", 1)
            // DeviceDetails 简化为只传设备名（参照 HPlayer UpnpDMSService）
            // DLNA 标识由设备类型 UDADeviceType("MediaRenderer", 1) 和服务类型提供
            val deviceDetails = DeviceDetails(deviceName)

            val localDevice = LocalDevice(
                DeviceIdentity(udn),
                deviceType,
                deviceDetails,
                arrayOf<LocalService<*>>(avTransportService, renderingControl, connectionManager)
            )

            // 注册到 UPnP 注册表
            upnpService.registry.addDevice(localDevice)
            Log.d(TAG, "DMR 设备已注册: $deviceName (MediaRenderer)")

        } catch (e: Exception) {
            Log.e(TAG, "DMR 设备注册失败", e)
        }
    }
}
