# HStudy v3.3.3 Dynamic Hardening — Task 18–19 Acceptance Report

**Status:** TASK 18 ANDROID RUNTIME ACCEPTANCE PASS AND TASK 19 EXACT-VERSION RELEASE GATE PASS — `matrix-1heuy9sin13r8` PASSED 212/212 ON PIXEL 5 / API 30 WITH ALL SIX BENCHMARK DATASETS `NOT_REPRODUCED`; ORCHESTRATOR REMAINS RUNTIME-INACTIVE; NATIVE iOS RUNTIME AND API 33+ FOLLOW-UP REMAIN `ENVIRONMENT_BLOCKED`; SHARED-STATE RELEASE ACTIONS REQUIRE EXPLICIT AUTHORIZATION.

This report covers the local-regression, whole-branch review, and Test Lab portions of Task 18. Robo matrix `matrix-1d5w8wb7wvj9x` dynamically confirmed a release-blocking Android Room transaction crash. The transaction implementation was fixed and retry matrix `matrix-1gigwz99akpdl` passed on Pixel 5 / API 30. The first matching app + instrumentation run, `matrix-3co4wsab0ogs4`, completed with 159/194 passing and 35 failures. Retry `matrix-2b4lezhyln6zm` executed 212 tests but Firebase terminated the process before publishing XML; its complete logcat records 183 passing and 29 failing tests. Fixed3 `matrix-kdxn9iomhu7ma` published XML with 186 passing and 26 failing, and logcat proved Orchestrator was inactive. Fixed4 `matrix-ytvs7t31nc1aa` published XML with 207 passing and 5 failing, with Orchestrator confirmed active. Fixed5 `matrix-17x37qbj9ihok` published XML with 209 passing and 3 failing, also with Orchestrator confirmed active. Fixed6 `matrix-3h11y65ppttqp` published XML with 210 passing and 2 failing, but logcat proved Orchestrator was inactive because no `orchestratorService` argument was supplied. Fixed7 `matrix-2j443fy5uharq` published XML with 208 passing and 4 failing, and logcat again proved Orchestrator was inactive. Fixed8 corrects the password re-entry race, pet gate release order, midnight old-node race and weekly horizontal scrolling; subsequent matrices through fixed17 resolved the remaining test-infrastructure defects and dynamically confirmed then fixed P2-08. Task 19 applied the `3.3.3` / `30303` stamps, reran all local gates, and submitted exact-version matrix `matrix-1heuy9sin13r8`, which passed 212/212 with all six benchmark datasets `NOT_REPRODUCED`. Native iOS runtime remains blocked on Windows; iOS Kotlin compilation passes.

## 1. Source and artifact identity

| Item | Value |
|---|---|
| Baseline | `42f757d595e86883b3aea168315c576bfca0f63a` on `feature/v332-hardening` |
| Fix branch | `feature/v333-dynamic-hardening` |
| Fix commit | None; Tasks 1–19 are an uncommitted worktree diff |
| Tracked diff SHA-256 | `62715d6f962c9344f1d922675d8570cbcb35b57d350c8de7a0f8b40859230199` |
| Android source/config/test/schema fingerprint | `78f34bb8702415c1e0b97827f0f10a6d0cd33bcdac1ab9b5d6833f5249603e35` over 350 files |
| Fingerprint evidence | `.verify-v333/release/post-stamp/source-manifest.txt`, `source-fingerprint.json` |
| Room schema | v14, preserved |
| Schema 14 SHA-256 | `e81eda95a17f1d03e62bcf2da1f1daca0863f7c65533a8cef40315517668528f` |
| App APK stamp | `com.hstudy.app`, versionCode `30303`, versionName `3.3.3` |
| Version note | Task 19 synchronized Android, Info.plist and all Xcode stamps to `3.3.3` / `30303`; exact-version Test Lab evidence is `matrix-1heuy9sin13r8`. |

The historical v3.3.2 Firebase matrix `matrix-24obb1afqhp4o` is baseline context only. It is not reused as v3.3.3 fix verification.

## 2. Final 37-finding verdict summary

Final counts after fixed17 and the exact-version v3.3.3 rerun: **CONFIRMED 2 / NOT_REPRODUCED 0 / ENVIRONMENT_BLOCKED 35 / NOT_A_DEFECT 0**. P2-29 has a behavioral copy-test RED/GREEN; P2-08 was dynamically confirmed by the fixed15/fixed16 Pixel 5 benchmark and is green after fixed17 and exact-version `matrix-1heuy9sin13r8`. The exact-version matrix executed 212/212 on Pixel 5 / API 30 and all six benchmark datasets are `NOT_REPRODUCED`.

- All 8 P1 findings remain `ENVIRONMENT_BLOCKED` for original pre-fix behavioral reproduction, but their post-fix Room/filesystem/Compose regressions passed in fixed17. State describes the original finding evidence, not whether the fix is green.
- P2-29 has a behavioral copy-test RED/GREEN. P2-08 is dynamically confirmed by the fixed15 Pixel 5 benchmark (`points_5000` p95 377.10ms; `mistakes_1000` p95 121.33ms).
- No finding is labelled `NOT_REPRODUCED` or `NOT_A_DEFECT`; neither verdict has supporting runtime evidence.
- `ENVIRONMENT_BLOCKED` is not a pass and not a claim of a completed fix. Compile-only RED, Fake DAO tests, APK assembly, extracted SQL harnesses and Windows iOS `SKIPPED` tasks are not promoted to runtime proof.

| ID | Final state | Rationale and covering evidence |
|---|---|---|
| P1-01 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed PointApprovalConcurrencyTest 6 + SpendPointsConcurrencyTest 3; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P1-02 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed TemplateWishPetAtomicityTest 10; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P1-03 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed TemplateWishPetAtomicityTest 10 + PetScreenDoubleClickTest 2; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P1-04 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed NotificationSchedulerTest 7; original pre-fix behavioral reproduction was compile-only/unavailable. API 33+ exact alarms remain outstanding. |
| P1-05 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed BackupFailureSafetyTest 18 + SqliteBackupValidatorInstrumentationTest 9 + DatabaseRestoreRecoveryTest 8; original pre-fix behavioral reproduction was compile-only/unavailable. Power-loss fsync remains unmeasured. |
| P1-06 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed FamilySyncSafetyTest 20; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P1-07 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed FrequencyBonusAssignmentTest 13; original pre-fix behavioral reproduction was compile-only/unavailable. iOS runtime remains blocked. |
| P1-08 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed SpendPointsConcurrencyTest 3 + TemplateWishPetAtomicityTest 10 + PetScreenDoubleClickTest 2; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P2-01 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed FrequencyBonusAssignmentTest 13; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P2-02 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed HabitMistakeRewardAtomicityTest 18; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P2-03 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed DailyTargetResetTest 11 + ChildDailyTargetScreenTest 1; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P2-04 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed PomodoroStaleSnapshotTest 3; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P2-05 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed FocusTrainingAndAchievementTest 5; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P2-06 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed ReportHistoryTest 23; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P2-07 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed AndroidBackAndDeepLinkTest 8; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P2-08 | CONFIRMED | Fixed15 confirmed 377.10ms/121.33ms. Fixed16 improved to 108.71ms/51.96ms. Fixed17 and exact-version v3.3.3 are GREEN: exact `points_5000` 99.33ms, `mistakes_1000` 49.79ms; all six datasets NOT_REPRODUCED and 212/212 passed. |
| P2-09 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed DangerousApprovalApiAbsentTest 3; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P2-10 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed NotificationSchedulerTest 7; original pre-fix behavioral reproduction was compile-only/unavailable. iOS calendar runtime remains blocked. |
| P2-11 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed ReportHistoryTest 23 + WeeklyTrackerHistoryTest 3; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P2-12 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed FocusTrainingAndAchievementTest 5; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P2-13 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed FrequencyBonusAssignmentTest 13; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P2-14 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed HabitMistakeRewardAtomicityTest 18; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P2-15 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed HabitMistakeRewardAtomicityTest 18; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P2-16 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed HabitMistakeRewardAtomicityTest 18 + MandatoryPenaltyConcurrencyTest 3; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P2-17 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed MandatoryPenaltyHelperTest JVM 3; original pre-fix behavioral reproduction was compile-only/unavailable. Original harmful behavior remains unadjudicated. |
| P2-18 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed AndroidBackAndDeepLinkTest 8 + NotificationSchedulerTest 7; original pre-fix behavioral reproduction was compile-only/unavailable. iOS delegate callbacks remain blocked. |
| P2-19 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed TaskDeleteScheduleTest 5 + TaskDeleteReminderTest 2; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P2-20 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed MidnightRefreshTest 1; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P2-21 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed ReportHistoryTest 23 + WeeklyTrackerHistoryTest 3; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P2-22 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed ParentPasswordSecurityTest 10; original pre-fix behavioral reproduction was compile-only/unavailable. iOS CommonCrypto remains blocked. |
| P2-23 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed MigrationV13To14Test 3 + PhysicalMigrationChainTest 3; original pre-fix behavioral reproduction was compile-only/unavailable. v1–v11 fixtures are hand-built. |
| P2-24 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed HabitMistakeRewardAtomicityTest 18; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P2-25 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed FamilySyncSafetyTest 20; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P2-26 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed FamilySyncSafetyTest 20; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P2-27 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed FamilyShareResultTest 7; original pre-fix behavioral reproduction was compile-only/unavailable. Real chooser delivery and iOS callbacks remain unverified. |
| P2-28 | ENVIRONMENT_BLOCKED | Fixed17 Pixel 5/API 30 passed ReportHistoryTest 23 + WeeklyTrackerHistoryTest 3; original pre-fix behavioral reproduction was compile-only/unavailable. |
| P2-29 | CONFIRMED | Manual-sync copy/documentation was misleading; the pre-fix common copy guard failed 3/3 and the fresh guard passes 4/4. |

### Confirmed P1

None. No P1 has the required real runtime evidence.

### Confirmed P2: P2-29 manual-sync capability/copy

- **RED artifact:** `.superpowers/sdd/2026-11-25-v333-business-platform/task-14-report.md`, §TDD evidence, records `ManualSyncCopyTest` failing 3/3 before the copy changes because Settings/ParentHome/README still advertised remote control, pairing or non-JSON sync. No separate raw RED log was preserved; the accepted task report is the red artifact.
- **Fix:** ZH/EN Settings, ParentHome and README now describe only manual JSON export, system sharing and manual import; the analytics event is `sync_config_imported`; no discovery/transport flow was added.
- **GREEN artifact:** `.verify-v333/runtime-block/unit-xml/TEST-com.hstudy.app.i18n.ManualSyncCopyTest.xml` — 4 tests, 0 failures/errors/skips, including the direct forbidden-claim predicate regression.
- **Supporting gates:** storage boundary 200 files/0 hits; default templates 118/118; branch templates 969/969; analytics 21 site checks; duplicate-API gate 321 files/0 hits.

## 3. Fresh local gate matrix

All Gradle commands used `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"` and `GRADLE_USER_HOME="D:/gradle-home"`.

| Gate | Exit | Observed result | Evidence |
|---|---:|---|---|
| `:composeApp:testDebugUnitTest` | 0 | 54 XML files, 404 tests, 0 failures/errors/skips | `.verify-v333/release/post-stamp/unit.log`, `unit.exit`, `summary.json` |
| `:composeApp:assembleDebug` | 0 | v3.3.3 debug APK built; SHA-256 below | `.verify-v333/release/apks/`, `.verify-v333/release/signing-status.json` |
| `:composeApp:assembleDebugAndroidTest` | 0 | Matching v3.3.3 instrumentation APK built; SHA-256 below | `.verify-v333/release/apks/`, `.verify-v333/release/signing-status.json` |
| `:composeApp:connectedDebugAndroidTest` | 1 | Local `ENVIRONMENT_BLOCKED`: no connected device/complete AVD image; Firebase Test Lab exact-version `matrix-1heuy9sin13r8` executed 212/212 instead | `.verify-v333/release/testlab-matrix-1heuy9sin13r8/` |
| `:composeApp:lintDebug` | 0 | 0 errors | `.verify-v333/release/post-stamp/lint-debug.log`, `lint-debug.exit` |
| `:composeApp:compileKotlinIosX64` | 0 | Kotlin compilation passed; native iOS runtime remains `ENVIRONMENT_BLOCKED` on Windows | `.verify-v333/release/post-stamp/ios-compile.log`, `ios-compile.exit` |
| `:composeApp:compileKotlinIosArm64` | 0 | Kotlin compilation passed; native iOS runtime remains `ENVIRONMENT_BLOCKED` on Windows | `.verify-v333/release/post-stamp/ios-compile.log`, `ios-compile.exit` |
| `:composeApp:compileKotlinIosSimulatorArm64` | 0 | Kotlin compilation passed; native iOS runtime remains `ENVIRONMENT_BLOCKED` on Windows | `.verify-v333/release/post-stamp/ios-compile.log`, `ios-compile.exit` |
| Analytics call-site gate | 0 | Passed | `.verify-v333/release/post-stamp/analytics-call-sites.log` |
| Storage boundary | 0 | Passed | `.verify-v333/release/post-stamp/i18n-storage.log` |
| Default template equivalence | 0 | Passed | `.verify-v333/release/post-stamp/i18n-template.log` |
| Branch template equivalence | 0 | Passed | `.verify-v333/release/post-stamp/i18n-template-branch.log` |
| Storage boundary self-test | 0 | Passed | `.verify-v333/release/post-stamp/i18n-storage-selftest.log` |
| Template disclosure self-test | 0 | Passed | `.verify-v333/release/post-stamp/i18n-template-selftest.log` |
| Duplicate approval API gate | 0 | Passed | `.verify-v333/release/post-stamp/no-duplicate-approval.log` |
| Duplicate approval API self-test | 0 | Passed | `.verify-v333/release/post-stamp/no-duplicate-approval-selftest.log` |
| Release version gate + self-test | 0 | Android, Info.plist and Xcode stamps match `v3.3.3` / `30303`; self-test passed | `.verify-v333/release/post-stamp/version-gate.log`, `version-selftest.log` |
| Secret scan | 0 | 0 high-confidence hits across tracked diff and 390 source/config/document files | `.verify-v333/release/post-stamp/secret-scan.json` |
| Workflow YAML parse | 0 | Android, iOS and release workflows parse successfully | `.verify-v333/regression/18-workflow-yaml.log` |
| `git diff --check` | 0 | No whitespace diagnostics | `.verify-v333/regression/22-diff-check.log` |

The first secret-scan attempt used an invalid `git grep <range>` form and is preserved separately as `.verify-v333/regression/15a-secret-scan-invalid-range.log`; it is a command error, not a secret finding. The corrected scan above is the release-relevant result.

### Test-source audit

- 81 test source files contain 612 source-declared `@Test` methods across common, Android unit and Android instrumentation sources; every file has assertion-like checks.
- Android instrumentation: 31 classes / 212 source-declared `@Test` methods; exact-version Firebase Test Lab executed 212/212 on Pixel 5 / API 30.
- Executed post-stamp JVM suite: 54 XML files / 404 tests / 0 failures / 0 errors / 0 skipped. Evidence: `.verify-v333/release/post-stamp/summary.json`.

## 4. Matching APK artifacts

Both APKs were rebuilt together after the `3.3.3` / `30303` stamp from the same Android source fingerprint and signed by the same debug certificate.

| Property | App APK | Instrumentation APK |
|---|---|---|
| Path | `composeApp/build/outputs/apk/debug/HStudy-v3.3.3-debug.apk` | `composeApp/build/outputs/apk/androidTest/debug/HStudy-v3.3.3-debug-androidTest.apk` |
| Package | `com.hstudy.app` | `com.hstudy.app.test` |
| Target package | `com.hstudy.app` | `com.hstudy.app` |
| versionCode | `30303` | Not declared in test manifest |
| versionName | `3.3.3` | Not declared in test manifest |
| Size | 29,645,217 bytes | 1,621,521 bytes |
| SHA-256 | `72b5b1128072cb1f69be9b5bedfe118518a7c909cb3b861505d48859751456a8` | `1266c89abfde645ba7783d0276a1a0ed507aee5389c4df125af875fd89298996` |
| Signer SHA-256 | `254912536bf42a735846cd20bb2d68733f99e3a11b2298daa920057b42374d3d` | Same |
| Signature verification | APK Signature Scheme v2 verified | APK Signature Scheme v2 verified |

Copies and metadata are under `.verify-v333/release/apks/` and `.verify-v333/release/signing-status.json`. The local release APK is intentionally unsigned because signing environment variables are absent; CI signing and key rotation remain gated on explicit push/tag authorization.

## 5. Migration, backup, security and benchmark evidence

### Schema v14 and migrations

- `APP_SCHEMA_VERSION = 14`; `exportSchema = true`; schema hash is recorded above.
- `MIGRATION_13_14` only adds nullable columns, unique indexes and two tables. It contains no `DELETE` or `DROP`; duplicate approval/achievement/reward rows remain present with only the canonical row keyed.
- Historical v4→v5 table rebuilds copy all rows before dropping/renaming the old table; they are not v14 history deletion.
- `MigrationV13To14Test` (3) and `PhysicalMigrationChainTest` (3) passed on Pixel 5 / API 30 in fixed17; `MigrationVerifyTest` passes 8/8 on JVM.
- Exported v1–v11 schema JSON is unavailable; the early chain uses a hand-built populated v1 fixture plus actual migration objects. This remains a disclosed coverage limitation.

### Backup and restore

- `BackupFailureSafetyTest` contains 18 tests for staged backup, corrupt/wrong-schema import, copy/rename/reopen/close/cleanup failures, rollback, hashes and successful fresh reopen; all passed in fixed17.
- Fixed17 executed the class on real Android filesystem/SQLite; `SqliteBackupValidatorInstrumentationTest` 9 and `DatabaseRestoreRecoveryTest` 8 also passed.
- `DatabaseRestoreRecovery` and startup `.bak` recovery are implemented for abrupt termination between restore steps. Explicit power-loss `fsync` durability has not been measured and remains a residual.

### Parent password

- JVM hasher tests: 3/3.
- `ParentPasswordSecurityTest`: all 10 real DataStore/Android crypto tests passed in fixed17, including legacy migration, malformed-value fail-closed and raw-byte preservation.
- iOS Kotlin compilation passes; native CommonCrypto/runtime remains `ENVIRONMENT_BLOCKED` on Windows.

### List-flow benchmark

- Exact-version v3.3.3 matrix `matrix-1heuy9sin13r8` measured six datasets on Pixel 5 / API 30 with 20 steady-state samples after two warm-up insert cycles. Final p95: 17.00, 99.33, 32.52, 49.79, 24.05, 42.67ms; all `NOT_REPRODUCED`.
- Current result is `NOT_REPRODUCED`; maximum p95 is 99.327719ms and maximum retained-heap delta is 6,016 bytes. Evidence: `.verify-v333/release/testlab-matrix-1heuy9sin13r8/list-flow-benchmark-summary.json`.

## 6. Firebase Test Lab submission attempt

The user manually uploaded `HStudy-v3.3.2-debug.apk`. Robo ran on Pixel 5 / API 30, en-US, portrait with a five-minute timeout as [`matrix-1d5w8wb7wvj9x`](https://console.firebase.google.com/project/hstudy-ad3f2/testlab/histories/bh.580e0f30f049504f/matrices/8764472240591726344). It failed after 1m10s with a real `IllegalStateException`: `Cannot return a SupportSQLiteOpenHelper since no SupportSQLiteOpenHelper.Factory was configured with Room.` The root cause was Android-only `RoomDatabase.withTransaction` on a `BundledSQLiteDriver` database. `DbTransactionRunner.android.kt` now uses Room KMP `useWriterConnection` / `immediateTransaction`; a driver-backed commit regression was added. Fixed APK SHA-256 is `9cfd76085f889a3f297a96fca941788f1de23e425aa84af8098434d626b4840b`; Robo retry [`matrix-1gigwz99akpdl`](https://console.firebase.google.com/project/hstudy-ad3f2/testlab/histories/bh.580e0f30f049504f/matrices/6613925521245505885) passed at `2026-11-26T01:24:40Z`: 1 device passed, 0 failed, 103 actions, 3 activities, 39 screens, 5m13s.

The first app + instrumentation run, [`matrix-3co4wsab0ogs4`](https://console.firebase.google.com/project/hstudy-ad3f2/testlab/histories/bh.580e0f30f049504f/matrices/7081331114139327552/details), executed 194 tests with 159 passing and 35 failures in 292.603 seconds before the short default timeout ended the run. The failures exposed the `sqlite_sequence` validator defect, empty-control export defect, benchmark transaction API misuse, and test-harness/assertion issues. Those causes were fixed locally; the rebuilt app APK SHA-256 is `a7cc1a728c2ff87ed7da257de10ca63b338c50703d4bb783608724f2340944cc` and test APK SHA-256 is `a9113eb7511f75a1fed9037e9db21d935c19d67320a8cd5307123345d84a4965`. The user uploaded this pair. The controller verified Pixel 5 / API 30, en-US, portrait, the 15-minute timeout and label `v333-instrumentation-fix2-pixel5-api30`, then submitted [`matrix-2b4lezhyln6zm`](https://console.firebase.google.com/project/hstudy-ad3f2/testlab/histories/bh.580e0f30f049504f/matrices/8155916387863129892/details), execution `bs.259e2e681c989466`. Firebase displayed a device failure with zero test cases because the process was killed before XML publication. The complete [`logcat.txt`](.verify-v333/testlab/matrix-2b4lezhyln6zm/logcat.txt) proves 212 tests started and finished, with 29 failures and 183 passing events; its SHA-256 is `eb91e6caffb460e0be0e1a5966f2983f8189ac544e970fe3185c3ba699132bd5`. Nineteen failures were caused by the missing Compose `ComponentActivity` manifest entry, five by navigation test teardown/selection, and five by migration, rollback, backup, benchmark or reminder-bookkeeping assertions. Fixed3 `matrix-kdxn9iomhu7ma` then published a full XML: 186 passing and 26 failing. Logcat proves the requested Android Test Orchestrator was not active because no `orchestratorService` argument was supplied. Nineteen failures came from `ComponentActivity` being packaged in the test APK/process, six from navigation test lifecycle/UI synchronization, and one from the reminder test's explicit-ID fixture. Fixed4 moves `ui-test-manifest` to `debugImplementation`, uses generated schedule IDs, restores the ActivityScenario launch intent after warm intent delivery, scrolls the task into view, and extends one navigation timeout. Its app/test SHA-256 values are `ea6c1c24a4e632e136aef1449f6e27527a146ecf1121eb9c99a9cc4e69179f43` and `401f8587405b9439f61e3f9265b4ccc11c88615ac9bf10ad1fd118d764071995`; fresh build, 400 JVM tests, lint, manifest placement and signature verification pass. The fixed4 pair was uploaded and submitted as `matrix-ytvs7t31nc1aa`, which completed with 207/212 passing and 5 failing with Orchestrator active. Fixed5 `matrix-17x37qbj9ihok` later completed with 209/212 passing and 3 failing, also with Orchestrator confirmed active; subsequent retries through fixed17 resolved the remaining harness and performance findings. Task 19 then submitted exact-version `matrix-1heuy9sin13r8`, which passed 212/212 with all six benchmark datasets `NOT_REPRODUCED`. Exact states are in `.verify-v333/testlab/matrix-status.json`.

| Matrix | Status | Configuration | Required artifacts and current state |
|---|---|---|---|
| v3.3.3 candidate Robo | FAILED | Pixel 5 / API 30, en-US, portrait, five-minute crawl; `matrix-1d5w8wb7wvj9x` | Real Room transaction crash; `actions.json`, rendered failure page and log view saved; full logcat download unavailable |
| v3.3.3 transaction-fix Robo retry | PASSED | Same device/locale/orientation/timeout; `matrix-1gigwz99akpdl` | 1 device passed, 0 failed; 103 actions / 3 activities / 39 screens; success page saved |
| v3.3.3 candidate app + instrumentation | FAILED | Pixel 5 / API 30; `matrix-3co4wsab0ogs4` | 159 passed / 35 failed; XML and failure stacks saved |
| v3.3.3 instrumentation retry 1 | FAILED | Pixel 5 / API 30, en-US, portrait, 15-minute timeout; `matrix-2b4lezhyln6zm` | Firebase published no XML; complete logcat derives 212 finished / 29 failed / 183 passed; process killed after 5m46s |
| v3.3.3 instrumentation retry 2 | FAILED | Pixel 5 / API 30, en-US, portrait, 20-minute timeout, Orchestrator requested; `matrix-kdxn9iomhu7ma` | XML: 212 tests / 186 passed / 26 failed; logcat proves Orchestrator inactive |
| v3.3.3 instrumentation retry 3 | FAILED | Pixel 5 / API 30, en-US, portrait, 20-minute timeout, Orchestrator active, 1 shard; `matrix-ytvs7t31nc1aa` | XML: 212 tests / 207 passed / 5 failed; logcat confirms Orchestrator service connection |
| v3.3.3 instrumentation retry 4 | FAILED | Pixel 5 / API 30, en-US, portrait, 20-minute timeout, Orchestrator active, 1 shard; `matrix-17x37qbj9ihok` | XML: 212 tests / 209 passed / 3 failed; logcat confirms Orchestrator service connection |
| v3.3.3 instrumentation retry 5 | FAILED | Pixel 5 / API 30, en-US, portrait, 20-minute timeout, Orchestrator form enabled, 1 shard; `matrix-3h11y65ppttqp` | XML: 212 tests / 210 passed / 2 failed; logcat proves Orchestrator was inactive (`No service name argument was given`) |
| v3.3.3 instrumentation retry 6 | FAILED | Pixel 5 / API 30, en-US, portrait, 20-minute timeout, Orchestrator form enabled, 1 shard; `matrix-2j443fy5uharq` | XML: 212 tests / 208 passed / 4 failed; logcat proves Orchestrator was inactive |
| v3.3.3 instrumentation retry 7 | FAILED | Pixel 5 / API 30, en-US, portrait, 20-minute timeout, Orchestrator form enabled, 1 shard; `matrix-144h1trsqysen` | XML: 212 tests / 211 passed / 1 failed; logcat proves Orchestrator was inactive; sole failure is the weekly day-cell assertion addressed in fixed9 |
| v3.3.3 instrumentation retry 8 | FAILED | Pixel 5 / API 30, en-US, portrait, 20-minute timeout, Orchestrator form enabled, 1 shard; `matrix-2o320qfk78o1w` | XML: 212 tests / 210 passed / 2 failed; logcat proves Orchestrator was inactive; failures were cold Activity recreation password-gate synchronization and weekly day-cell visibility, addressed in fixed10 |
| v3.3.3 instrumentation retry 9 | FAILED | Pixel 5 / API 30, en-US, portrait, 20-minute timeout, Orchestrator form enabled, 1 shard; `matrix-3vcazvn62tz6c` | XML: 212 tests / 210 passed / 2 failed; logcat proves Orchestrator was inactive; cold Activity recreation passed, while midnight refresh and weekly history remain, addressed in fixed11 |
| v3.3.3 instrumentation retry 10 | FAILED | Pixel 5 / API 30; `matrix-1heb6few09lio` | 211/212; weekly test-expectation failure; midnight refresh passed |
| v3.3.3 instrumentation pre-stamp final | PASSED | Pixel 5 / API 30, en-US, portrait, 20-minute timeout, 1 shard; `matrix-3l9eml2clqne3` | 212/212; six benchmark datasets `NOT_REPRODUCED`; XML/logcat/summary saved |
| v3.3.3 exact-version release candidate | PASSED | Pixel 5 / API 30, en-US, portrait, 20-minute timeout, 1 shard; `matrix-1heuy9sin13r8` | 212/212; six benchmark datasets `NOT_REPRODUCED`; max p95 99.327719ms; raw status/logcat, derived XML, benchmark JSON/summary and hashes saved |
| API 33+ physical follow-up | ENVIRONMENT_BLOCKED | One available physical device | Notification permission, exact alarms, Back gesture, process recreation, cold/warm clicks: not executed; upload blocker and device availability prevent submission |

A Robo timeout, if one later occurs, must be recorded as crawl incompleteness rather than a product failure. A successful Robo crawl would not prove transaction, migration, DataStore, backup or notification correctness.

## 7. CI workflow coverage

- `.github/workflows/android.yml` now builds both debug app and instrumentation APKs and uploads both. Existing blocking static gates, including the duplicate-API gate and its self-test, were already present. Runtime instrumentation remains assigned to Test Lab rather than an unconfigured local emulator.
- `.github/workflows/ios.yml` now invokes all three Kotlin compile targets: `iosX64`, `iosArm64`, and `iosSimulatorArm64`, while retaining the existing simulator framework/app build and smoke test.
- Both workflow files parse successfully locally. No GitHub Actions run was triggered or claimed.

## 8. Whole-branch evidence audit

- The ledger contains exactly the 37 IDs from `docs/antigravity-post-v332-review.md`; no missing, extra, pending or invalid state.
- Every `ENVIRONMENT_BLOCKED` row identifies the original pre-fix behavioral evidence gap or platform residual; fixed17 and exact-version v3.3.3 post-fix Android runtime evidence is green and is not hidden by that state.
- Every test source file has assertion-like checks; no empty test body was found by the source audit.
- No Fake DAO, compile-only test, host SQL harness, APK build or Windows iOS skip is labelled real device/runtime evidence.
- Schema remains v14; the v14 migration does not delete history.
- Fresh post-stamp unit suite (404/404), both APK builds, lint, all static gates/Python self-tests, workflow parse, secret scan and iOS Kotlin compilation are green. Firebase exact-version `matrix-1heuy9sin13r8` instrumentation is 212/212; native iOS runtime remains unverified.
- No commit, push, stash, reset, clean, branch switch or user-file deletion was performed.

## 9. Known residuals and release blockers

1. **Android runtime:** local emulator boot is blocked by an incomplete system image, but Firebase Test Lab exact-version `matrix-1heuy9sin13r8` executed all 212 instrumentation tests successfully on Pixel 5 / API 30.
2. **Firebase Test Lab:** Robo `matrix-1d5w8wb7wvj9x` confirmed RUNTIME-01 and failed; retry `matrix-1gigwz99akpdl` passed. Instrumentation progressed from 159/194 to repeated 210–211/212 results as test-harness races, matcher errors, lazy-list scrolling and manual-clock frame pumping were corrected. Fixed15 passed 212/212 but its benchmark dynamically confirmed P2-08 (`points_5000` 377.10ms; `mistakes_1000` 121.33ms). Fixed16 reduced these to 108.71ms/51.96ms; fixed17 removed grouped-list copies and added steady-state warmup. Exact-version v3.3.3 `matrix-1heuy9sin13r8` then passed 212/212 with six `NOT_REPRODUCED` benchmark datasets (max p95 99.327719ms). Orchestrator remained runtime-inactive despite form Value 1; tests are deterministic without process isolation. Final evidence: `.verify-v333/release/testlab-matrix-1heuy9sin13r8/`.
3. **iOS:** all three Kotlin compile targets pass locally; native iOS runtime remains `ENVIRONMENT_BLOCKED` on Windows. macOS CI/runtime notification, transaction and password checks remain outstanding.
4. **Version stamps:** Android, Info.plist and all Xcode build configurations are synchronized to `3.3.3` / `30303`; the release version gate and self-test pass.
5. **P2-17:** current pure helper tests are green, but the preserved pre-fix RED was compile-only; original behavior is not dynamically adjudicated.
6. **Android share delivery:** `Presented` proves the chooser launched, not that a target received the file. Delivery confirmation is not claimed; iOS completion runtime is also blocked.
7. **Backup power loss:** abrupt-termination journal/startup recovery is implemented; explicit power-loss `fsync` durability is unproven.
8. **Historical fixtures:** v1–v11 exported schema JSON is unavailable; physical early-chain coverage uses hand-built fixtures.
9. **Release state:** the local v3.3.3 release gate is satisfied: post-stamp JVM/build/lint/iOS/static/version/secret gates and exact-version Test Lab instrumentation are green. Local release signing remains `ENVIRONMENT_BLOCKED`; CI-signed APK publication, tag creation and website deployment still require explicit shared-state authorization.
