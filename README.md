# HStudy

[![Android Build](https://github.com/yaohehe/hstudy/actions/workflows/android.yml/badge.svg)](https://github.com/yaohehe/hstudy/actions/workflows/android.yml)
[![iOS Build](https://github.com/yaohehe/hstudy/actions/workflows/ios.yml/badge.svg)](https://github.com/yaohehe/hstudy/actions/workflows/ios.yml)
[![Release](https://img.shields.io/github/v/release/yaohehe/hstudy)](https://github.com/yaohehe/hstudy/releases/latest)
[![Sponsor](https://img.shields.io/badge/Sponsor-GitHub%20Sponsors-ea4aaa)](https://github.com/sponsors/yaohehe)

**A family learning companion for parents and kids — tasks, focus, review and rewards in one bilingual app.**
**家长与孩子共用的学习伙伴：任务、专注、复习与奖励，一个双语应用搞定。**

[English](#english) | [中文](#中文)

---

## English

### What is HStudy

HStudy provides a **parent** role and a **child** role on one device:

- **Tasks & check-ins** — kids check in completed tasks; points are awarded **only after parent approval** (append-only ledger, idempotent approvals).
- **Focus** — pomodoro timer with breathing guide, break cycles and a Schulte-grid focus trainer.
- **Mistake review** — spaced review with daily reminders and active-recall flows.
- **Rewards** — lucky draw, pet care, wish goals with point deposits, weekly learning report.
- **Manual JSON file sync** — export and share a parent-settings JSON file, then import it on the child device; export and share a child-progress JSON file, then import it on the parent device. No live connection is required. Full backup & restore are also available.
- **Bilingual UI** — 中文 / English, switchable at runtime (parent settings), system-default aware.

### Platforms & downloads

One Kotlin Multiplatform + Compose Multiplatform codebase ships both platforms:

- **Android** — signed release APK on the [Releases page](https://github.com/yaohehe/hstudy/releases/latest). Sideload it (allow "unknown sources"); uninstall first if a debug/other-signature build is installed.
- **iOS** — simulator `.app` bundle (unsigned) on the same page. Installing on a real device requires an Apple Developer Program membership and signing certificates.

### Build from source

Requirements: JDK 17, Android SDK (for Android), macOS + Xcode (for iOS).

```bash
./gradlew :composeApp:assembleDebug        # Android debug APK
./gradlew :composeApp:testDebugUnitTest    # shared-unit tests (runs on Android JVM)
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64   # iOS framework (macOS)
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build            # iOS simulator app (macOS)
```

Release APKs are signed in CI from repository secrets; the keystore lives outside the repo.

### Analytics & privacy

HStudy uses **Google Analytics for Firebase** to understand which features are used:

- Sent: screen names (route names only), feature-usage counts, reminder scheduling, language
  changes, and backup export, plus SDK-provided device model / OS / country / timezone.
  User properties: `app_language`, `user_role`, `app_channel` (debug/release).
- Manual JSON export/share emits no analytics event. A parameterless `sync_config_imported`
  event is sent only after a successful parent-settings JSON import on the child device;
  this event does not upload file contents.
- **Never sent to analytics:** names, task titles, mistake content, point values, study durations,
  device names, file contents, or any free text.
- Firebase assigns a stable pseudonymous `app_instance_id`. It is not personal information, but
  it **can link events from the same device across sessions** — "anonymous" here means
  pseudonymous, not unlinkable.
- **Opt out anytime:** Parent settings → "Share usage data". Off stops reporting immediately
  (both our call sites and SDK auto-collection), persists across restarts, and nothing collected
  while off is sent later.

### Support / donate

HStudy is free. If it helps your family, you can support development via
[GitHub Sponsors](https://github.com/sponsors/yaohehe) — entirely optional, no paywalls.

### Versioning & data compatibility

`versionCode = MAJOR*10000 + MINOR*100 + PATCH` (see [docs/VERSIONING.md](docs/VERSIONING.md));
history in [version_history.md](version_history.md). Room schema v14 with v1→v14 migrations;
upgrades preserve all existing data.

**Upgrading from v2 (v3.2.1+):** releases since v3.2.1 carry a signing-key rotation
(original v2 debug key → current release key, APK Signature Scheme v3 lineage). Devices
running v2 (debug-signed) or v3.x on Android 9+ can install over the existing app without
uninstalling; all data is preserved. Exception: Android 8.x devices already running
v3.0.0–v3.2.0 must export a backup, uninstall, install, then restore.

---

## 中文

### HStudy 是什么

HStudy 在同一台设备上提供**家长**与**孩子**两种身份：

- **任务与打卡**——孩子完成打卡，积分**只在家长审批通过后**写入一次（append-only 账本、幂等审批）。
- **专注**——番茄钟（呼吸引导、休息循环）与舒尔特方格专注训练。
- **错题复习**——间隔复习、每日提醒、主动回忆流程。
- **奖励**——每日抽奖、宠物照料、心愿目标存分、每周学习周报。
- **手动 JSON 文件同步**——导出并分享家长设置 JSON 文件，再在孩子设备导入；导出并分享孩子进度 JSON 文件，再在家长设备导入。无需实时连接，另支持完整备份与恢复。
- **双语界面**——中文 / English 运行中切换（家长设置），默认跟随系统。

### 平台与下载

一套 Kotlin Multiplatform + Compose Multiplatform 代码同时出两个平台：

- **Android**——[Releases 页](https://github.com/yaohehe/hstudy/releases/latest)提供已签名 release APK，
  可直接侧载（允许"未知来源"）；装过调试版或其他签名版本需先卸载。
- **iOS**——同页提供模拟器 `.app` 包（未签名）。真机安装需 Apple Developer Program 会员与签名证书。

### 从源码构建

环境：JDK 17、Android SDK（Android）；macOS + Xcode（iOS）。

```bash
./gradlew :composeApp:assembleDebug        # Android debug APK
./gradlew :composeApp:testDebugUnitTest    # 共享单测（Android JVM 执行）
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64   # iOS framework（macOS）
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build            # iOS 模拟器包（macOS）
```

release APK 由 CI 用仓库 secrets 签名；keystore 不在仓库内。

### 统计与隐私

HStudy 使用 **Google Analytics for Firebase** 了解功能使用情况：

- 发送：界面路由名、功能使用次数、提醒排程、语言切换和备份导出，以及 SDK 自带的
  设备型号/系统版本/国家/时区。用户属性：`app_language`、`user_role`、`app_channel`（debug/release）。
- 手动 JSON 导出/分享不会触发统计事件。只有在孩子设备成功导入家长设置 JSON 后，
  才会发送无参数的 `sync_config_imported` 事件；该事件不会上传文件内容。
- **绝不发送给统计服务**：姓名、任务标题、错题内容、积分数值、学习时长、设备名、文件内容或任何自由文本。
- Firebase 会生成稳定的伪匿名 `app_instance_id`：它不是个人信息，但**可在同一设备上跨事件
  关联**——这里的"匿名"止于伪匿名，不是不可关联。
- **随时关闭**：家长设置 →「分享使用数据」。关闭立即停止上报（调用点与 SDK 自动采集两层），
  重启后仍关闭，关闭期间的数据不会补发。

### 支持 / 捐赠

HStudy 免费。如果它对您的家庭有帮助，欢迎通过
[GitHub Sponsors](https://github.com/sponsors/yaohehe) 支持开发——完全自愿，无任何付费墙。

### 版本与数据兼容

`versionCode = MAJOR*10000 + MINOR*100 + PATCH`（见 [docs/VERSIONING.md](docs/VERSIONING.md)）；
历史见 [version_history.md](version_history.md)。Room schema v14，含 v1→v14 迁移，升级保留全部数据。

**从 v2 升级（v3.2.1 起）**：v3.2.1 起的发布包含签名密钥轮换（v2 原始 debug key → 现正式 key，
APK Signature Scheme v3 lineage）。装有 v2（debug 签名）或 v3.x（Android 9+）的设备可
不卸载直接覆盖安装，数据完整保留。例外：Android 8.x 且已装 v3.0.0–v3.2.0 的设备需
导出备份→卸载→安装→恢复。
