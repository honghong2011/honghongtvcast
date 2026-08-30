package com.hh2011.cast.tv.dlna

import org.fourthline.cling.UpnpServiceConfiguration
import org.fourthline.cling.android.AndroidUpnpServiceConfiguration
import org.fourthline.cling.android.AndroidUpnpServiceImpl
import org.fourthline.cling.binding.xml.ServiceDescriptorBinder
import org.fourthline.cling.binding.xml.UDA10ServiceDescriptorBinderImpl

/**
 * Cling UPnP 服务（DMR 协议引擎）
 *
 * 继承 AndroidUpnpServiceImpl，提供 UPnP 协议栈的 Android 实现。
 * CastService 继承此类，在 onCreate 中注册 DMR 设备。
 *
 * 直接从 HPlayer 的 UpnpService 移植，简化了搜索类型配置
 * （DMR 是接收端，不需要搜索其他设备）。
 */
open class ClingUpnpService : AndroidUpnpServiceImpl() {

    override fun createConfiguration(): UpnpServiceConfiguration {
        return DmrConfiguration()
    }

    /**
     * DMR UPnP 服务配置
     *
     * - registryMaintenanceIntervalMillis: 注册表维护间隔（7 秒）
     * - createServiceDescriptorBinderUDA10: 修复 Cling issue #249
     */
    class DmrConfiguration : AndroidUpnpServiceConfiguration() {

        override fun getRegistryMaintenanceIntervalMillis(): Int {
            return 7000
        }

        override fun createServiceDescriptorBinderUDA10(): ServiceDescriptorBinder {
            // 修复 https://github.com/4thline/cling/issues/249
            return UDA10ServiceDescriptorBinderImpl()
        }
    }
}
