# 宏宏tv投屏 (HongHong TV Cast)

一款专为 Android TV / 安卓盒子打造的**投屏接收端**（DLNA DMR 媒体渲染器）应用。

![License](https://img.shields.io/badge/license-Apache--2.0-blue)
![Android](https://img.shields.io/badge/Android-5.0%2B-green)
![Kotlin](https://img.shields.io/badge/Kotlin-1.3.72-orange)
![ExoPlayer](https://img.shields.io/badge/ExoPlayer-2.16.0-purple)
![DLNA](https://img.shields.io/badge/DLNA-Cling%202.1.2-blueviolet)

## 项目简介

宏宏tv投屏是 [宏宏TV](https://github.com/honghong2011/honghongtv) 的姊妹项目。安装到 TV/盒子后，同一个 WiFi 局域网内的手机、平板、电脑便可通过 DLNA 协议把视频、音频、图片「投」到电视上播放。本应用**仅做接收端**，不做发送端，纯局域网运行，无联网、无广告、无数据收集。

应用基于 **Cling 2.1.2** 协议栈完整实现 `AVTransport` / `RenderingControl` / `ConnectionManager` 三个 UPnP 服务，可被手机系统相册、B 站、腾讯视频等主流 DLNA 发送端发现与操控；`CastService` 前台服务常驻，配合 `WakeLock` + `WifiLock` + `MulticastLock`，屏幕关闭、APP 退后台时投屏依然可用；播放核心沿用宏宏TV 的 **ExoPlayer 2.16.0 + FFmpeg 扩展 + OkHttp 网络扩展**方案，默认硬解，可在设置中切换软解。

整个项目都是我 vibe coding 出来的，和宏宏TV 一样是为了解决自己 TV/盒子上没有合适投屏接收端的问题，所以可能会有很多问题，欢迎提交 Issue 和 Pull Request，希望这个项目可以解决你的问题。

## 截图

> TODO: 补充真机截图（主界面、投屏播放中、设置界面等）

## 功能特性

**投屏（DLNA DMR）**
- 基于 Cling 2.1.2 完整实现 `AVTransport` / `RenderingControl` / `ConnectionManager` 三个 UPnP 服务
- 可被手机系统相册、B 站、腾讯视频等主流 DLNA 发送端发现与操控
- 播放 / 暂停 / 停止 / 进度调节 / 音量均由发送端遥控，进度与标题实时回传
- 主界面实时显示设备名、当前 WiFi、投屏状态与连接信息

**播放**
- ExoPlayer 2.16.0 核心，默认硬件解码，可在设置中切换硬解/软解
- FFmpeg 扩展（本地 AAR），支持 MP2 等更多音频格式，音频软解渲染器置于队首
- OkHttp 网络扩展，连接池复用
- RTMP 扩展，支持 `rtmp://` 直播流（缺该扩展时会降级为 OkHttp 报 Malformed URL）
- 投屏推送的视频 / 音频 / 图片均可播放，播放页 Surface 渲染

**后台常驻**
- `CastService` 前台服务常驻，APP 退后台、屏幕关闭时仍可被局域网设备发现与投屏
- `WakeLock` + `WifiLock` + `MulticastLock` 保障息屏与多播发现
- 开机自启动：监听 `BOOT_COMPLETED`，纯非 root 原生实现（Android 5.0-9.0）

**设置**
- DLNA 投屏开关、设备名称修改（自动重启 DLNA 服务生效）
- 开机自启、优先硬件解码、屏幕常亮

**TV 适配**
- 横屏布局、遥控器焦点操作，`LEANBACK_LAUNCHER` + `LAUNCHER` 双入口（TV 桌面与普通桌面均可启动）

## 如何使用

### 安装

1. 编译获取 APK（见下方「开发」章节），通过 U 盘或 `adb install` 安装到 TV/盒子（需 Android 5.0+）
2. 确保 TV 与手机**连接同一 WiFi** 局域网
3. 打开「宏宏tv投屏」，界面显示设备名与当前 WiFi，处于「等待同局域网设备投屏」状态

### 投屏播放

1. 在手机系统相册、B 站、腾讯视频等 App 中点「投屏」按钮
2. 选择设备「宏宏投屏」即可开始播放
3. 播放 / 暂停 / 进度 / 音量均可从手机遥控；也可直接用电视遥控器操作（见下表）
4. 开启「开机自启」后，盒子重启会自动拉起投屏服务

### 遥控器操作

**主界面**

| 按键 | 功能 |
|------|------|
| 菜单 | 打开设置界面 |
| 返回 | 双击退出应用 |
| OK | 进入设置/切换选项 |

**投屏播放界面**

| 按键 | 功能 |
|------|------|
| OK | 播放 / 暂停切换 |
| 左/右 | 快退 / 快进 10 秒 |
| 上/下 | 音量增 / 减 |
| 返回 | 退出播放页 |

**设置界面**

| 按键 | 功能 |
|------|------|
| 上/下 | 选择设置项 |
| OK | 切换开关/进入修改 |
| 返回 | 返回并保存设置 |

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| **Cling** | 2.1.2（本地 JAR） | UPnP/DLNA 协议栈，DMR 设备注册与三个服务实现 |
| **Jetty** | 8.1.22.v20160922 | Cling 底层 HTTP 服务组件（依赖链禁止 exclude） |
| **NanoHTTPD** | 2.3.1 | Cling 配套 HTTP 组件 |
| **ExoPlayer** | 2.16.0 | 视频播放核心（谷歌官方播放器） |
| **extension-okhttp** | 2.16.0 | ExoPlayer OkHttp 网络扩展，连接池复用 |
| **extension-rtmp** | 2.16.0 | RTMP 直播流扩展（`rtmp://` 协议自动反射加载） |
| **FFmpeg 扩展** | 本地 AAR | FFmpeg 解码扩展（支持 MP2 等格式） |
| OkHttp | 4.9.3 | 网络请求框架 |
| Material Components | 1.2.1 | Material Design 2 UI 库 |
| AndroidX AppCompat | 1.3.1 | 兼容支持 |
| AndroidX Leanback | 1.0.0 | TV 框架（主题） |

> 注意：Cling/Jetty 同依赖链相关模块均**不允许 exclude**（排除 `jetty-*` 会导致运行时 `NoClassDefFoundError`），`packagingOptions` 仅排除 `META-INF/beans.xml` 冲突。

## 项目结构

```
tvcast/
├── app/                          # 主应用模块（唯一 Gradle 模块）
│   ├── src/main/java/com/hh2011/cast/tv/
│   │   ├── MainActivity.kt         # 主页面：设备信息 + 投屏状态 + 双击返回退出
│   │   ├── SettingsActivity.kt     # 设置页：DLNA/自启/解码/常亮
│   │   ├── ui/PlayerActivity.kt    # 投屏播放页（Surface 渲染 + 遥控器按键）
│   │   ├── service/CastService.kt  # 前台常驻服务 + DMR 设备注册
│   │   ├── receiver/BootReceiver.kt# 开机自启广播接收器
│   │   ├── dlna/                   # DLNA DMR 协议实现
│   │   │   ├── ClingUpnpService.kt     # UPnP 协议引擎（AndroidUpnpServiceImpl）
│   │   │   ├── DmrAVTransportService.kt# 播放控制（setURI/play/pause/stop/seek）
│   │   │   ├── DmrRenderingControl.kt  # 音量控制（对接 AudioManager）
│   │   │   └── DmrConnectionManager.kt # 连接管理（协议能力信息）
│   │   ├── player/CastPlayerManager.kt # ExoPlayer 单例封装（DMR 与 UI 共用）
│   │   └── util/                   # PreferencesHelper / FormatUtil
│   └── libs/                       # Cling 2.1.2 JAR + seamless JAR + FFmpeg AAR（本地引入）
├── build.gradle                   # 根构建文件（AGP 3.5.3、Kotlin 1.3.72、阿里云镜像）
└── gradle/                        # Gradle Wrapper（5.4.1，腾讯云镜像分发）
```

## 开发

### 环境要求

- **Android Studio**: 3.5.3（项目锁定版本，对低配机友好）
- **Gradle**: 5.4.1（wrapper 锁定，腾讯云镜像分发）
- **AGP**: 3.5.3
- **Android SDK**: compileSdk 28（Android 9.0），minSdk 21（Android 5.0）
- **Kotlin**: 1.3.72
- **JDK**: 1.8

### 编译

```bash
git clone <本仓库地址>
cd tvcast
```

> **镜像说明**：项目使用阿里云 / 腾讯云国内镜像加速依赖下载，海外用户若构建失败，请将 `build.gradle` 与 `gradle/wrapper/gradle-wrapper.properties` 改回官方源。`gradle.properties` 已针对 4GB 内存低配电脑优化（Gradle 堆 1536m、DEX 堆 1024m）。

在 Android Studio 中打开项目根目录，`Sync Now` 同步 Gradle（首次会从阿里云镜像拉取依赖），然后 `Build -> Build APK(s)`，或命令行：

```bash
gradlew assembleDebug
```

安装到设备：

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

> 本应用无需模拟器，直接在 TV/盒子真机（建议安卓 9，targetSdk 28）调试，需开启 USB 调试。

### 权限说明

- `INTERNET` / `ACCESS_WIFI_STATE` / `ACCESS_NETWORK_STATE`：DLNA 协议交互、媒体流下载与网络状态查询
- `CHANGE_WIFI_MULTICAST_STATE`：SSDP 多播设备发现必需
- `WAKE_LOCK`：屏幕关闭时保持投屏服务运行
- `FOREGROUND_SERVICE`：前台服务声明（Android 9.0+）
- `RECEIVE_BOOT_COMPLETED`：开机自启动

## 参考项目 & 致谢

### 使用及参考项目
- [Cling](https://github.com/4thline/cling) - UPnP/DLNA 协议栈（本地 JAR 引入）
- [ExoPlayer](https://github.com/google/ExoPlayer) - 谷歌官方播放器
- [OkHttp](https://github.com/square/okhttp) - 网络请求框架
- [HPlayer](https://github.com/hezhubo/HPlayer) - DMR 实现参考
- [宏宏TV](https://github.com/honghong2011/honghongtv) - 姊妹项目，播放器方案同源
- [AndroidX](https://developer.android.com/jetpack/androidx) - Android 扩展库
- [Material Components](https://github.com/material-components/material-components-android) - Material Design 2 UI 库

### 致谢
各个 ai 工具和模型，项目使用了 GLM deepseek trae等模型和工具进行开发。

## 路线图

计划共 4 个阶段，当前**已完成阶段 1（项目基础框架 + 后台常驻 + 开机自启）与阶段 2（DLNA 协议接入 + 核心播放功能 MVP）**：

- [x] **阶段 1**：项目初始化、依赖锁定、后台常驻服务、开机自启、UI 与 TV 适配
- [x] **阶段 2**：DLNA DMR 全逻辑、ExoPlayer 核心播放、投屏状态实时同步
- [ ] **阶段 3（后置可选）**：AirPlay 协议接入（无成熟库、成本最高，风险高，待 DLNA 稳定后再评估）
- [ ] **阶段 4**：DIAL 协议、播放器进阶设置、投屏历史、兼容性与稳定性优化、新手文档

当前代码已具备稳定的 DLNA 单协议投屏能力，可在 TV 盒子上正常使用。阶段 3/4 尚未启动。

## 贡献

欢迎提交 Issue 和 Pull Request。

- 提交 Issue 请描述清楚问题现象、复现步骤、发送端 App、设备型号与系统版本
- 提交 PR 请保持单一职责，一个 PR 只解决一个问题
- 代码风格遵循现有风格：函数短小、命名直白、注释用中文

## 许可证

本项目基于 [Apache-2.0](LICENSE) 协议开源，无内置广告、无收费、无用户数据收集。
