# HStudy v3.3.3 Business, Platform, and Security Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the state-machine, UI, Android platform, password-security, and performance findings with targeted runtime evidence.

**Architecture:** Move stale-state decisions into repositories/CAS APIs, use an injectable clock for date transitions, wire Android navigation and notification routes, and replace plaintext parent passwords with a versioned PBKDF2 format.

**Tech Stack:** Compose Multiplatform 1.7.3, AndroidX Activity Compose 1.9.3, AlarmManager, DataStore 1.1.1, PBKDF2-HMAC-SHA256, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-11-25-hstudy-v333-dynamic-hardening-design.md`

**Depends on:** `docs/superpowers/plans/2026-11-25-v333-foundation.md`, `docs/superpowers/plans/2026-11-25-v333-data-integrity.md`

## Global Constraints

- Do not commit or push.
- Platform findings require Android instrumentation or Test Lab evidence; common tests alone are insufficient.
- iOS notification runtime is `ENVIRONMENT_BLOCKED` on this host unless a macOS runner becomes available; compile evidence is still required.
- Do not advertise real-time device discovery in v3.3.3.
- Preserve existing bilingual copy gates and storage-boundary rules.
- Every task updates `.verify-v333/evidence-ledger.md`.

---

### Task 11: Harden Pomodoro, focus training, memory training, and achievements (P2-04, P2-05, P2-12)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/preferences/UserPreferences.kt:317-323,417-485`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/focus/PomodoroFocusSettlement.kt:43-82`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/common/PomodoroTimer.kt:405-435`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/focus/ChildFocusTrainingScreen.kt:38-79`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/focus/ChildMemoryTrainingScreen.kt:23-36`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/common/AchievementManager.kt:118-140`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/child/ChildAchievementsScreen.kt:33-41`
- Test: `composeApp/src/androidTest/kotlin/com/hstudy/app/data/focus/PomodoroStaleSnapshotTest.kt`
- Test: `composeApp/src/androidTest/kotlin/com/hstudy/app/ui/focus/FocusTrainingAndAchievementTest.kt`

**Interfaces:**
- Produces:
  - `data class PomodoroWriteExpectation(val startEpochMs: Long, val settled: Boolean, val version: Long)`
  - `UserPreferences.savePomodoroSession(state, expectation): Boolean`
  - `UserPreferences.tryUpdateFocusTrainingBest(candidateMs: Long): Boolean`
  - `UserPreferences.saveMemoryTrainingState(state)` / `memoryTrainingState: Flow<MemoryTrainingState?>`
  - `AchievementManager.progress(app): Flow<List<AchievementProgress>>`

- [ ] **Step 1: Write stale-settlement test**

Settle a session, retain the old `settled=false` snapshot, then call pause/resume with the same start timestamp. Assert the write returns false and the stored session remains settled with one focus row.

- [ ] **Step 2: Write focus/achievement tests**

- First Schulte result with `bestMs=0` reports a new record.
- A slower concurrent result cannot overwrite a faster best.
- Memory training state survives screen removal and DataStore recreation.
- Perfect-day and habit-complete progress become `1 to 1` after their real conditions are met and update while the screen remains open.

- [ ] **Step 3: Run red**

Run both instrumentation classes. Record stale overwrite, false first-record result, reset memory state, and hardcoded achievement progress.

- [ ] **Step 4: Implement transition CAS and persistence**

- Add a monotonically increasing `version` to `PomodoroSessionState` persistence.
- Require expected start, expected settled state, and expected version for pause/resume writes.
- Reject any transition when the current session is settled unless the operation is the explicit settlement transition.
- Replace focus-best write with a DataStore CAS that keeps the minimum.
- Persist memory-training state in DataStore with a schema version.
- Replace hardcoded achievement progress with DAO queries and expose a Flow; collect it with `collectAsState`.

- [ ] **Step 5: Run green**

Run both classes, existing Pomodoro tests, full unit suite, and iOS compile. Update P2-04/05/12.

### Task 12: Make daily-target reset safe and task deletion unlink schedules (P2-03, P2-19)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/DailyTargetRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/target/ChildDailyTargetScreen.kt:152-161,211-255`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/TaskRepository.kt:20,33-40`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/dao/DailyScheduleDao.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/AppGraph.kt`
- Test: `composeApp/src/androidTest/kotlin/com/hstudy/app/data/repository/DailyTargetResetTest.kt`
- Test: `composeApp/src/androidTest/kotlin/com/hstudy/app/data/repository/TaskDeleteScheduleTest.kt`

**Interfaces:**
- Produces:
  - `DailyTargetRepository(preferences, pointRepository, clock)`
  - `DailyTargetRepository.reset(date, targetIds): Result<Unit>`
  - `DailyTargetRepository.setDone(date, targetId, done): Result<Unit>`
  - `DailyScheduleDao.unlinkTask(taskId): Int`
  - `TaskRepository(taskDao, dailyScheduleDao, transactionRunner)`

- [ ] **Step 1: Write reset and race tests**

- Create a pending `TARGET:<id>:<date>` approval and claimed `TARGET_ALL:<date>` bonus, then reset targets. Assert reset is rejected with a actionable result and old approvals/bonus cannot be reused.
- With no pending approval, reset succeeds.
- Fire two checkbox completions concurrently; assert all-complete bonus is created once.
- Soft-delete a task linked to a schedule; assert `linkedTaskId` becomes null in the same transaction.

- [ ] **Step 2: Run red**

Run both classes and record duplicate target eligibility and stale schedule links.

- [ ] **Step 3: Implement safe reset and unlink**

- Add `unlinkTask(taskId)` to `DailyScheduleDao`.
- Wrap task soft-delete and schedule unlink in one transaction.
- `DailyTargetRepository` serializes target mutations with a `Mutex`.
- Reset returns `Result.failure` when same-day pending target approvals or an already-claimed all-complete bonus exist; the UI directs the user to resolve them first.
- Compute all-complete from the post-update target set inside the repository, not `doneTargets.size + 1` in the composable.

- [ ] **Step 4: Run green**

Run both classes, target/point common tests, full unit suite, and iOS compile. The Android reminder cancellation portion is completed in Task 13. Update P2-03 and P2-19.

### Task 13: Fix Android Back, weekly rescheduling, repeatDays, and notification deep links (P1-04, P2-07, P2-10, P2-18, P2-19 reminder)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/platform/Platform.kt:15-61`
- Modify: `composeApp/src/androidMain/kotlin/com/hstudy/app/platform/AndroidNotificationScheduler.kt:54-127,187-237`
- Modify: `composeApp/src/androidMain/kotlin/com/hstudy/app/MainActivity.kt:17-31`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/App.kt:47-96`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/schedule/ScheduleAlarmHelper.kt:47-92`
- Modify: `composeApp/src/androidMain/kotlin/com/hstudy/app/platform/AndroidReminderBootstrap.kt:77-101`
- Modify: `composeApp/src/iosMain/kotlin/com/hstudy/app/platform/IosNotificationScheduler.kt`
- Modify: `composeApp/src/iosMain/kotlin/com/hstudy/app/platform/IosPlatform.kt`
- Modify: `composeApp/src/iosApp/iosApp.swift`
- Test: `composeApp/src/androidTest/kotlin/com/hstudy/app/platform/NotificationSchedulerTest.kt`
- Test: `composeApp/src/androidTest/kotlin/com/hstudy/app/ui/navigation/AndroidBackAndDeepLinkTest.kt`
- Test: `composeApp/src/androidTest/kotlin/com/hstudy/app/data/repository/TaskDeleteReminderTest.kt`

**Interfaces:**
- Produces:
  - `ScheduledReminder(..., repeatDays: Set<Int> = emptySet())`
  - `Platform.pendingRoute: String?`
  - `Platform.consumePendingRoute(): String?`
  - Android notification click intents include `extra_route`.
  - `IosNotificationScheduler` installs a `UNUserNotificationCenterDelegate` and writes `Platform.pendingRoute` from `userInfo`.

- [ ] **Step 1: Write scheduler tests**

- Weekly intent with `repeatDaily=false` registers a new exact weekly alarm.
- One-shot nonweekly intent still does not reschedule.
- Weekend-only `repeatDays={6,7}` schedule from Monday registers Saturday, not Monday.
- Notification click PendingIntent contains the expected route extra.
- Cold and warm `MainActivity` intents consume the route and navigate after the role/password gate.
- Android Back at stack depth 3 pops to depth 2; root Back finishes normally.
- Deleting a linked task cancels its schedule reminder.

- [ ] **Step 2: Run red**

Run all three instrumentation classes on an emulator/device. Record missing next alarm, weekday alarm for weekend-only schedule, missing extras, activity finish on Back, and leftover reminder.

- [ ] **Step 3: Implement platform fixes**

- In `rescheduleNextFromReceiver`, return only when `!repeatDaily && kind != WEEKLY_REPORT`.
- Extend `ScheduledReminder` with `repeatDays`; parse `DailyScheduleEntity.repeatDays` and compute the next matching ISO weekday.
- Add route extras to notification click intents and a stable `Platform.pendingRoute` slot. `App()` consumes the pending route only after normal role/password authorization.
- Add `BackHandler(enabled = navigator.backStack.size > 1) { navigator.popBackStack() }` in the Android Compose content.
- Inject a reminder canceller into `TaskRepository` or call a platform synchronizer immediately after the DB transaction; cancellation must use the unlinked schedule IDs.
- Add the iOS notification delegate and route userInfo. Compile all iOS targets; record runtime click tests as `ENVIRONMENT_BLOCKED` without macOS.

- [ ] **Step 4: Run green**

Run all three classes, existing schedule/notification copy tests, full unit suite, and all iOS compile tasks. Update P1-04, P2-07/10/18/19.

### Task 14: Refresh midnight UI state and correct manual-sync copy (P2-20, P2-29)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/time/RememberAppTime.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/focus/ChildDailyPlanScreen.kt:26-34`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/schedule/ChildScheduleTimelineScreen.kt:37-54,108-115`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/SettingsStrings.kt:394-405,484-495`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ParentHomeStrings.kt:154-155,187,242-246,278`
- Modify: `README.md:19,25,89,95`
- Test: `composeApp/src/androidTest/kotlin/com/hstudy/app/ui/time/MidnightRefreshTest.kt`
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/ManualSyncCopyTest.kt`

**Interfaces:**
- Produces:
  - `@Composable fun rememberAppDate(clock: AppClock): String`
  - `@Composable fun rememberAppMinute(clock: AppClock): Int`
  - Copy explicitly states manual JSON export/import/share and does not claim pairing or live discovery.

- [x] **Step 1: Write controlled-clock Compose test**

Provide `FixedAppClock` through a test-only `LocalAppClock`. Set 23:59, compose daily plan/timeline, advance to 00:01, and assert date/query/highlight all change without recomposition recreation.

- [x] **Step 2: Write copy guard**

Assert ZH and EN settings strings contain manual file-sync wording and do not contain “paired”, “once paired”, “real-time discovery”, or equivalent claims. Assert README no longer describes two paired devices as a shipped feature.

- [x] **Step 3: Run red**

Run both tests and record stale date/highlight and current pairing copy.

- [x] **Step 4: Implement ticking state and copy correction**

- Add a minute ticker driven by `AppClock`; derive date and minute from the same tick.
- Key date queries by the observed date so Room emits the new day's data.
- Replace remote-control/pairing copy with manual JSON file sync instructions.
- Keep `DeviceDiscovery` code out of the production flow and do not expose it as available.

- [x] **Step 5: Run green**

Run both tests, all i18n/template gates, full unit suite, and iOS compile. Update P2-20/29.

### Task 15: Replace plaintext parent passwords with versioned PBKDF2 (P2-22)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/security/ParentPasswordHasher.kt`
- Create: `composeApp/src/androidMain/kotlin/com/hstudy/app/security/ParentPasswordHasher.android.kt`
- Create: `composeApp/src/iosMain/kotlin/com/hstudy/app/security/ParentPasswordHasher.ios.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/preferences/UserPreferences.kt:36,85-86,137-149`
- Modify: `composeApp/src/iosMain/nativeInterop/cinterop/hstudyIosBridge.def`
- Modify: `composeApp/src/iosMain/nativeInterop/cinterop/hstudyIosBridge.h`
- Modify: `composeApp/src/iosMain/nativeInterop/cinterop/hstudyIosBridge.m`（计划原名；最终实现位于 `hstudyIosBridge.h` 的 static-inline CommonCrypto helpers，原因见 Task 15 报告）
- Test: `composeApp/src/androidTest/kotlin/com/hstudy/app/security/ParentPasswordSecurityTest.kt`

**Interfaces:**
- Produces:
  - `expect object ParentPasswordHasher`
  - `ParentPasswordHasher.hash(password: String): String`
  - `ParentPasswordHasher.verify(password: String, encoded: String): Boolean`
  - Encoded format: `pbkdf2-sha256$600000$<base64 salt>$<base64 hash>`.
  - DataStore key `parent_password` stores only the encoded hash after migration.

- [x] **Step 1: Write security tests**

- Hash the same password twice; salts and encoded values differ.
- Stored value never equals plaintext and does not contain it.
- Correct password verifies; wrong password fails.
- Legacy plaintext key migrates on successful verification; wrong password leaves it unchanged; after success the plaintext key is absent.
- Known PBKDF2 test vector verifies on Android.
- iOS implementation compiles against CommonCrypto.

- [x] **Step 2: Run red**

Run the Android instrumentation class. Baseline fails because the value equals plaintext and no migration exists.

- [x] **Step 3: Implement platform hashers and migration**

- Android: `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")`, 600,000 iterations, 16-byte `SecureRandom` salt, 32-byte key.
- iOS: CommonCrypto `CCKeyDerivationPBKDF` through the existing cinterop bridge.
- Constant-time compare decoded bytes.
- In `verifyParentPassword`, use one `dataStore.edit` block: read the value, detect legacy plaintext, verify, write the encoded hash, and remove the plaintext representation atomically.
- `setParentPassword` writes only the encoded hash.

- [x] **Step 4: Run green**

Run the security class, all DataStore common tests, full unit suite, and all iOS compile tasks. Update P2-22.

### Task 16: Remove the dangerous duplicate approval API and pin route coverage (P2-09)

**Files:**
- Modify/Delete: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/PointApprovalRepository.kt`
- Create: `scripts/tests/no_duplicate_approval_api.py`
- Modify: `scripts/analytics_call_sites.py` only if a shared scan-runner pattern is required; otherwise keep the new gate standalone.
- Create: `composeApp/src/commonTest/kotlin/com/hstudy/app/ui/navigation/NavigationRouteCoverageTest.kt`
- Test: `composeApp/src/androidTest/kotlin/com/hstudy/app/data/repository/DangerousApprovalApiAbsentTest.kt`

**Interfaces:**
- Produces:
  - No callable approval method can transition status without ledger linkage.
  - `python scripts/tests/no_duplicate_approval_api.py` exits nonzero on the forbidden class/method pattern.

- [x] **Step 1: Write the static red gate**

The script scans `.kt` files for `class PointApprovalRepository`, `fun approve(`, and `fun approveAll(` in that class and fails while the dangerous API exists.

- [x] **Step 2: Write route coverage test**

Reflect over `Screen` data objects or use the existing closed route registry and assert every route appears in `AppNavGraph`. Include parameterized route encoding cases for `/`, `%`, empty, and invalid numeric values.

- [x] **Step 3: Run red**

Run the script and unit test. Expected: duplicate API gate fails; route test may pass and becomes regression protection.

- [x] **Step 4: Delete or neutralize the duplicate API**

Search all call sites. If unused, delete `PointApprovalRepository`. If a caller exists, replace it with `PointRepository.approveAndAward`/`rejectApproval` before deletion. Do not leave a deprecated method that can bypass the ledger.

- [x] **Step 5: Run green**

Run the script, route test, full unit suite, and all existing static gates. Update P2-09.

### Task 17: Benchmark unbounded list flows before deciding on paging (P2-08)

**Files:**
- Create: `composeApp/src/androidTest/kotlin/com/hstudy/app/performance/ListFlowBenchmarkTest.kt`
- Create: `.verify-v333/performance/list-flow-benchmark.json`
- Modify: DAOs/screens only if the threshold is met.

**Interfaces:**
- Consumes: `PointDao`, `MistakeDao`, `FocusDao`.
- Produces: JSON containing dataset sizes, 20 emission timings, p95, retained-heap delta, device model, API level, and verdict.

- [ ] **Step 1: Write the benchmark**

Populate real SQLite with 500 and 5,000 point rows, 500 and 1,000 mistake rows, and 500 and 1,000 focus rows. For each dataset, trigger 20 inserts while collecting the corresponding `Flow<List<...>>`, map to the same UI model used by the screen, and measure end-to-end emission/mapping time. Force GC between datasets and measure retained heap delta.

- [ ] **Step 2: Run on the reference device**

Run on Pixel 5 / API 30 through Test Lab or a connected equivalent. Save raw JSON and logcat.

Decision rule from the spec:

```text
CONFIRMED if p95 > 100 ms OR retained heap growth > 64 MiB
otherwise NOT_REPRODUCED
```

- [ ] **Step 3: Implement paging only if confirmed**

If confirmed, add bounded DAO queries with `LIMIT`/`OFFSET` and keyset pagination for points, mistakes, and focus. Update screens to collect paged flows. If not confirmed, make no production paging change and record the benchmark as `NOT_REPRODUCED`.

- [ ] **Step 4: Verify**

Re-run the benchmark after any change and run full unit/instrumentation suites. Update P2-08 with measured numbers, not a speculative crash claim.
