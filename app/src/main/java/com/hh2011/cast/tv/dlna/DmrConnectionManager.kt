package com.hh2011.cast.tv.dlna

import org.fourthline.cling.support.connectionmanager.ConnectionManagerService

/**
 * DMR ConnectionManager 服务实现
 *
 * DMR 的连接管理服务，最简实现。
 *
 * Cling 的 ConnectionManagerService.getProtocolInfo() 返回 void（Unit），
 * 不能覆盖成其他返回类型。协议信息由父类默认实现处理，
 * 实际支持的格式由 ExoPlayer + FFmpeg 决定，DMR 端无需精确声明。
 */
class DmrConnectionManager : ConnectionManagerService()
