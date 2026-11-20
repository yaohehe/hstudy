# HStudy v3.2.1 — 签名密钥轮换（v2 直接升级 v3）实施计划

日期：2026-11-20
状态：方案 B 已获用户批准（AskUserQuestion 2026-11-20 早）；执行排入当晚 23:00 续跑（先完成 v3.2.0 发布核验，再执行本计划）
分支：feature/v321-key-rotation（基于 origin/main dbfca9d）

## 目标

让装着 v2（本机 debug key 签名）的设备**不卸载直接覆盖升级**到 v3 正式签名包，同时不破坏已装 v3.x（Android 9+）设备的升级链。

## 已查证事实（2026-11-20 早，证据在本机）

- v2 包名 = v3 包名 = `com.hstudy.app`；v2.86 APK versionCode=88 < 30201。
- v2.86 APK（D:/game/hstudy/app/build/outputs/apk/debug/HStudy-v2.86-debug.apk）签名者证书 SHA-256 = `254912536bf42a735846cd20bb2d68733f99e3a11b2298daa920057b42374d3d`，与本机 `C:/Users/xuany/.android/debug.keystore`（storepass/keypass `android`，alias `androiddebugkey`）指纹逐字节一致 → **v2 的原始私钥仍在手**。
- v2 APK 带 APK Signature Scheme v2（apksigner verify -v：v2=true, v3=false）→ 轮换 lineage 可被 Android 9+ 校验。
- v3 正式 key：`D:/game/hstudy-1-signing/hstudy-release.jks`，证书 SHA-256 前缀 `703e2991…2f00`（与 v3.1.1/v3.2.0 发布资产一致）；凭据在 `D:/game/hstudy-1-signing/BACKUP-README.txt`（**只读本机文件，绝不入库/入日志**）。
- CI 现有 secrets：ANDROID_KEYSTORE_BASE64 / ANDROID_KEYSTORE_PASSWORD / ANDROID_KEY_ALIAS / ANDROID_KEY_PASSWORD（release.yml:29-36）。
- apksigner（build-tools 35.0.0，`java -jar lib/apksigner.jar`）支持 `rotate`（--old-signer/--new-signer 生成 lineage）与 `sign --lineage`（v1/v2 启用时 lineage 最旧 entry 必须提供 signer）。

## 签名设计（关键决策）

- lineage：debug key（旧）→ release key（新），`apksigner rotate` 生成。
- 签名：**第一 signer = debug key（供 v1/v2 块），--next-signer = release key（供 v3/v3.1 块）**，`--lineage` 附上。
  - Android 9+（API 28+）：走 v3 块 = release key + lineage → v2 设备凭 lineage 接受升级；已装 v3.x 设备 release key 直接匹配。
  - Android 8.x（API 26–27，无轮换能力）：走 v2 块 = debug key → v2 设备签名一致，直接升级。
  - **已知代价（写进 README/version_history）**：Android 8.x 且**已装 v3.0.0–v3.2.0**（release key）的设备无法直接升 v3.2.1（v2 块变回 debug key），需备份→卸载→安装→恢复。9+ 设备无此问题。
- 版本：**v3.2.1 / versionCode 30201**（不再第三次移动 v3.2.0 tag；诚实记录轮换变更）。

## 全局约束

- debug.keystore、release.jks、任何口令/base64 密钥材料**绝不进入仓库、文档、日志、CI 日志**；CI 用新 secret `ANDROID_DEBUG_KEYSTORE_BASE64`（debug.keystore 的 base64；口令 android/alias androiddebugkey 是公开默认值，可明文写在 workflow）。
- 业务代码零改动：本计划只动 release.yml、四处版本戳、version_history.md、README.md。
- 共享状态操作（设 secret、push 分支、开 PR、合并、打 tag、发布）逐项经用户确认；23:00 续跑会话里先报告再执行。
- 秘密扫描：合并前 `git diff dbfca9d..HEAD | grep -nE "ghp_[A-Za-z0-9]{36}"` 及 keystore 材料检查。
- 环境：Git Bash；JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"；GRADLE_USER_HOME="D:/gradle-home"；PYTHONIOENCODING=utf-8；apksigner/aapt2 在 /c/Users/xuany/AppData/Local/Android/Sdk/build-tools/35.0.0/。

## Task 1：本地轮换签名实证（先证明，后改 CI）

1. 取 v3.2.0 已发布 APK（gh release download v3.2.0 --repo yaohehe/hstudy --pattern "*.apk" --dir build/rotation-proof；若 v3.2.0 release 失败无资产，则本地 `./gradlew :composeApp:assembleRelease` 用 BACKUP-README 凭据经 -P 参数签出等效包）。
2. 读 BACKUP-README.txt 取 release keystore 口令/alias（只进 shell 变量，不回显）。
3. 生成 lineage（产物放 build/rotation-proof/，gitignore 覆盖）：
   ```bash
   java -jar "$BT/lib/apksigner.jar" rotate \
     --old-signer --ks /c/Users/xuany/.android/debug.keystore --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey \
     --new-signer --ks "$RELEASE_JKS" --ks-pass pass:"$RELEASE_PW" --key-pass pass:"$KEY_PW" --ks-key-alias "$ALIAS" \
     --out build/rotation-proof/lineage.bin
   ```
4. 双 signer 重签：
   ```bash
   java -jar "$BT/lib/apksigner.jar" sign \
     --ks /c/Users/xuany/.android/debug.keystore --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey \
     --next-signer --ks "$RELEASE_JKS" --ks-pass pass:"$RELEASE_PW" --key-pass pass:"$KEY_PW" --ks-key-alias "$ALIAS" \
     --lineage build/rotation-proof/lineage.bin \
     --min-sdk-version 26 \
     --out build/rotation-proof/HStudy-v321-rotated.apk <输入APK>
   ```
5. 验证（必须全中才算过）：
   - `apksigner verify -v --print-certs`：v2=true 且 v2 signer 证书 = 25491253…4d3d；v3=true 且 v3 signer = 703e2991…2f00；输出含 lineage/proof-of-rotation 信息。
   - `aapt2 dump badging`：versionCode/versionName 与输入一致（重签不改版本；版本戳在 Task 3 才改）。
6. 失败处理：语法/参数错查 `apksigner sign --help`；lineage 校验错核对 alias/口令；不得用关闭 v2/v3 方案的方式绕过。

## Task 2：CI 接线（release.yml）

1. 用户确认后设 secret：`ANDROID_DEBUG_KEYSTORE_BASE64` = debug.keystore 的 base64（gh api --method PUT repos/yaohehe/hstudy/actions/secrets/ANDROID_DEBUG_KEYSTORE_BASE64，inline PAT，nacl 加密按 gh 提示；或 gh secret set）。
2. release.yml android-release job 在 gradle 签名构建之后加步骤：
   - decode debug keystore 到 $RUNNER_TEMP/debug.keystore（base64 -d，不回显）；
   - 用与 Task 1 相同的 rotate + sign 命令重签 APK（release 凭据复用现有 env；debug 口令明文 android）；
   - `apksigner verify -v` 断言 v2/v3 双方案与 lineage 存在（grep 证书指纹前缀）；
   - 上传/发布重签后的 APK（替换 gradle 直签产物路径）。
3. iOS job 不动。

## Task 3：版本戳 + 文档

- 四处版本戳 3.2.1/30201：composeApp/build.gradle.kts、iosApp/Info.plist（两键）、pbxproj（MARKETING_VERSION ×2、CURRENT_PROJECT_VERSION ×2）。
- version_history.md 顶部新条目（含 bold 摘要行，沿既有风格）：签名密钥轮换（debug→release lineage）、v2 直接升级支持、Android 8.x 已装 v3.x 的例外与备份路径。
- README.md 双语各加一小节「从 v2 升级 / Upgrading from v2」：v2（debug 签名）可直接覆盖安装 v3.2.1+，数据保留；Android 8.x 已装 v3.0–v3.2.0 的设备走备份→卸载→安装→恢复；附签名轮换一句说明（正式 key 不变，lineage 授权旧 key）。
- 本地验证：`./gradlew :composeApp:testDebugUnitTest`（237 基线）+ assembleDebug + 三个 python 闸门（analytics/storage/template --branch）。

## Task 4：发布闸门（全部经用户确认）

1. push feature/v321-key-rotation + 开 PR（inline PAT）→ 双端 CI 绿（gh run view 核实 conclusion，不信 watch 退出码）。
2. 秘密扫描 + diff 审查（只含 workflow/版本/文档）。
3. 用户确认 → gh pr merge --merge → annotated tag v3.2.1 → release.yml。
4. 发布资产核验：aapt2 30201/3.2.1；apksigner verify -v 双方案 + lineage + 两证书指纹；iOS plist 3.2.1/30201。
5. 交付用户真机验证步骤：v2 设备直接覆盖安装 v3.2.1 APK（不卸载），确认数据完整、可正常打开；如有 Android 9+ 已装 v3.x 设备同样覆盖安装验证。
6. ledger + memory 收尾。

## 风险

- 用户设备上的 v2 若非本机 debug 构建（别的机器 debug key），轮换救不了该设备 → 真机验证步骤会暴露，回退备份路径。
- debug.keystore 进 CI secret 后，任何能改 workflow 的人可取用该 key——它本就是公开默认口令的 debug key，安全边界不高于现状；正式 key 不受影响。
- Android 8.x + 已装 v3.x 的升级断裂是设计内代价，已文档化。