# HStudy — 家庭学习管理应用（Android + iOS）

![Android Build](https://github.com/yaohehe/hstudy/actions/workflows/android.yml/badge.svg)
![iOS Build](https://github.com/yaohehe/hstudy/actions/workflows/ios.yml/badge.svg)
[![Release](https://img.shields.io/github/v/tag/yaohehe/hstudy?label=release)](https://github.com/yaohehe/hstudy/tags)

HStudy 是一款双角色（家长/孩子）家庭学习管理应用：任务打卡、积分审批、奖励商店、错题本（间隔复习）、番茄专注、日程提醒、拍照凭证、习惯挑战、心愿目标、宠物养成、等级成就与周报。

自 **v3.0.0** 起由纯 Android（Jetpack Compose）重构为 **Kotlin Multiplatform + Compose Multiplatform**，一套共享代码同时支持 Android 与 iOS。

## 技术栈

| 层 | 技术 |
|---|---|
| 语言 | Kotlin 2.1.20（共享业务与 UI） |
| UI | Compose Multiplatform 1.7.3 / Material3 |
| 数据库 | Room 2.7.0 KMP + BundledSQLiteDriver（schema v12，完整保留 v1→v12 迁移） |
| 偏好 | DataStore Preferences 1.1.x（多平台） |
| 图片 | Coil 3 |
| 序列化 | kotlinx-serialization（家庭同步 JSON 格式与旧版兼容） |
| 平台桥接 | `platform/Platform.kt` 统一接口：通知/闹钟、文件分享、相机、设备发现 |

## 工程结构

```
composeApp/
  src/commonMain/   共享 UI（43 个屏幕）、数据层、业务逻辑、平台契约
  src/androidMain/  Android 入口 + AlarmManager/通知/FileProvider/相机/NSD 实现
  src/iosMain/      iOS 桥接（UNUserNotifications/UIActivityViewController/相机/Bonjour）
iosApp/             Xcode 工程（SwiftUI 壳 + ComposeApp.framework）
legacy/android-v2.88/  重构前的纯 Android 工程，仅作对照与回退参考，不参与构建
.github/workflows/  Android 构建、iOS 构建（macOS）、tag 发布
```

## 构建

### Android（Windows/macOS/Linux）
```bash
./gradlew :composeApp:assembleDebug
# APK: composeApp/build/outputs/apk/debug/
```
需要 JDK 17 与 Android SDK（`local.properties` 指向 SDK 目录）。

### iOS（仅 macOS + Xcode 15+）
```bash
open iosApp/iosApp.xcodeproj   # 选择模拟器运行，Xcode 会自动调用 Gradle 生成 framework
```
Windows/Linux 上可编译共享代码与 Android 目标，但 Apple Kotlin/Native 目标的链接与打包必须在 macOS 上完成（CI 的 iOS Build 工作流在 `macos-latest` runner 上自动执行：`linkDebugFrameworkIosSimulatorArm64` + `xcodebuild`）。

共享代码的平台纯净度可本机校验：
```bash
# 期望输出 0：commonMain 不得出现 android./java. 依赖
grep -rl --include=*.kt -E "^import (android\.|java\.)" composeApp/src/commonMain | wc -l
./gradlew :composeApp:compileCommonMainKotlinMetadata   # 元数据编译闸
```

### iOS 侧与原 Android 版的已知差异
平台客观限制导致的「可见降级」，都不是静默丢功能：

- 震动：iOS 无「按毫秒震动」的公开 API，短震动用 `UIImpactFeedbackGenerator`，≥400ms 用系统震动。
- 计时进行中通知：iOS 没有可持续刷新的常驻通知（Live Activity 需额外 entitlement），
  改为图标角标显示进度百分比 + 固定 identifier 的即时本地通知（后台可见）。
- 文件分享（iPad）：新版 SDK 未把 `popoverPresentationController` 暴露给 cinterop，
  分享面板以 pageSheet 形式呈现（`modalPresentationStyle` 由 ObjC 桥接设置），不需要锚点。
- 通知文案赋值：`UNMutableNotificationContent` 的 title/body/sound 在 Kotlin 侧继承为只读属性，
  NSObject 的 KVC 也未暴露；两处均由 `hstudyIosBridge`
  （`composeApp/src/iosMain/nativeInterop/cinterop/hstudyIosBridge.def`）在 ObjC 侧完成赋值。
- 局域网设备发现：cinterop 未暴露 `-[NSData getBytes:length:]`，且
  `NSNetServiceBrowserDelegate` 的 didFind/didRemove 两个回调在 Kotlin 侧签名冲突（只能实现其一），
  因此主机名取 Bonjour 的 `.local` 名称（系统 mDNSResponder 可解析），下线设备由下次扫描前清空兜底。

## 版本管理

- 仓库：`github.com/yaohehe/hstudy`（public，以便 macOS runner 构建 iOS），主分支 `main`。
- 基线 tag：`v2.88-android`（重构前纯 Android 版本，versionCode 90）。
- 跨平台版本自 `v3.0.0`（versionCode 30000，按 `主*10000+次*100+补` 计算）起；语义化版本 `主.次.补丁`。
- `v3.0.0` 指向 CI 全绿提交（Android：GitHub ubuntu runner 上 `:composeApp:assembleDebug` + `:composeApp:testDebugUnitTest` 通过并上传 APK；iOS：macOS runner 上 `linkDebugFrameworkIosSimulatorArm64` + `xcodebuild` 通过并上传模拟器 .app）。
- 每次发布：更新 `composeApp/build.gradle.kts` 的 `versionName/versionCode`、`iosApp/Info.plist` 与 `project.pbxproj` 的 `CFBundleVersion/CURRENT_PROJECT_VERSION`（与 versionCode 一致）以及 `version_history.md`，打 `vX.Y.Z` tag；Release 工作流并行产出 Android APK 与 iOS 模拟器包，再由 publish 任务汇总成 GitHub Release。
- 数据库 schema 变更必须提供 Room Migration 并递增 `AppDatabase.version`（禁止破坏性迁移，迁移前自动备份）。
- 认证：本地推送使用 SSH（`~/.ssh/hstudy_github`，经 ssh.github.com:443）；CI 使用 GitHub 内置 `GITHUB_TOKEN`。**任何个人访问令牌不得提交入库。**

详见 [docs/VERSIONING.md](docs/VERSIONING.md) 与 [version_history.md](version_history.md)。

## 数据兼容性

- Android 老用户升级：数据库文件名（`hstudy_db`）与 DataStore 文件名保持不变，v12 schema 与全部迁移原样保留，积分台账（bizKey 去重、审批幂等、REVERSAL 追加式冲销）业务逻辑不变。
- 家庭同步 JSON 字段名与旧版一致，Android/iOS 设备间可互相同步。