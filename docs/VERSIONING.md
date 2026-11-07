# 版本管理规范

## 分支策略
- `main`：唯一长期分支，保持可构建；所有变更经 PR 或直接提交（个人项目）合入。
- 大功能可用 `feature/<名称>` 短分支，完成后合入 main 并删除。

## 版本号
- `versionName`：语义化 `MAJOR.MINOR.PATCH`。
  - MAJOR：架构级变更（如 v3.0.0 KMP 重构）。
  - MINOR：新功能。
  - PATCH：缺陷修复。
- `versionCode`：Android 单调递增整数，约定 `MAJOR*10000 + MINOR*100 + PATCH`（v3.0.0 = 30000）。
- iOS 版本号与 Android 对齐：`CFBundleShortVersionString` = versionName，`CFBundleVersion` = versionCode
  （`iosApp/Info.plist` 与 `project.pbxproj` 的 `CURRENT_PROJECT_VERSION` 都要改，工程里硬编码引用的是 plist）。

## 发布流程
1. 更新 `composeApp/build.gradle.kts`（versionName/versionCode）、`iosApp` 两处版本号、`version_history.md`。
2. 提交：`release: vX.Y.Z — <摘要>`。
3. 打 tag：`git tag -a vX.Y.Z -m "..."` 并推送；Release 工作流的 `android-release`（未签名 APK）与
   `ios-release`（未签名模拟器 .app 压缩包）并行构建，`publish` 任务汇总两类工件创建 GitHub Release。
4. iOS TestFlight/App Store 分发需在 macOS 上用 Apple 开发者证书重新归档签名（CI 只产出未签名模拟器构建供验证）。

## 历史锚点
- `v2.88-android`：重构前纯 Android 基线（versionCode 90，DB v12）。
- 重构期间提交按层拆分（数据层 / UI 层 / 平台桥接 / 构建修复），便于回溯。

## 数据库变更纪律
- 只增不删列（追加式）；必须写 Migration 并加入 `Migrations.ALL`。
- 迁移前自动备份（MigrationSafetyHelper），迁移后完整性校验。
- 积分规则变更参照《积分规则与审批逻辑说明.md》五大不变量。

## 凭据安全
- GitHub 推送：SSH 密钥（本机 `~/.ssh/hstudy_github`，账号级部署用途）。
- CI：仅用仓库内置 GITHUB_TOKEN；如需签名密钥用 GitHub Secrets。
- PAT/令牌一律不进代码、不进提交、不进日志。
## 旧版安卓工程归档
`legacy/android-v2.88/` 保留 v2.88 单平台 Android 源码作为迁移对照与回退参考，
不再参与构建（`settings.gradle.kts` 只包含 `:composeApp`）。
