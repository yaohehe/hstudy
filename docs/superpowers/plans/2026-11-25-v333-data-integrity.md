# HStudy v3.3.3 Data Integrity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reproduce and close the data-integrity findings with deterministic fault injection and real Room/SQLite/filesystem evidence.

**Architecture:** Wrap each multi-write business operation in the shared Room transaction runner, add CAS/unique guards at the DAO boundary, and keep historical rows intact. Backup and family sync use staged, validated operations rather than delete-first writes.

**Tech Stack:** Kotlin 2.1.20, Room 2.7.0, BundledSQLite 2.5.0, Okio 3.9.1, kotlinx-coroutines-test 1.9.0, AndroidX Test.

**Spec:** `docs/superpowers/specs/2026-11-25-hstudy-v333-dynamic-hardening-design.md`

**Depends on:** `docs/superpowers/plans/2026-11-25-v333-foundation.md`

## Global Constraints

- Do not commit or push.
- Every confirmation for a database finding must use real Room/SQLite; Fake DAO tests are supporting evidence only.
- Preserve historical audit rows and user-created rewards.
- A red test must fail for the claimed invariant, not setup.
- Each task updates `.verify-v333/evidence-ledger.md` with red evidence, state, fix, and green evidence.
- Keep existing public behavior unless the spec explicitly changes it.

---

### Task 4: Make point approval, check-in, undo, and spending atomic (P1-01, P1-08)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/dao/PointApprovalDao.kt:54-60`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/PointRepository.kt:117-248,482-493`
- Create: `composeApp/src/androidTest/kotlin/com/hstudy/app/data/repository/PointApprovalConcurrencyTest.kt`
- Create: `composeApp/src/androidTest/kotlin/com/hstudy/app/data/repository/SpendPointsConcurrencyTest.kt`

**Interfaces:**
- Consumes: `DbTransactionRunner`, `PointApprovalEntity.activeBizKey`.
- Produces:
  - `PointApprovalDao.updateReview(...): Int`
  - `PointRepository.spendPoints(points, note, requireSufficientBalance = true): Boolean`
  - All single-item approval/check-in/undo/refund paths join the injected transaction.

- [ ] **Step 1: Write a deterministic approval race test**

Create a delegating `PointApprovalDao` wrapper in the test that blocks the first `getById` until both coroutines have read PENDING. Use a real in-memory Room database and `roomTransactionRunner(db)`.

Test body:

```kotlin
@Test fun concurrentApproveAwardsExactlyOnce() = runBlocking {
    val db = testDatabase(context)
    try {
        val approvalId = db.pointApprovalDao().insert(
            PointApprovalEntity(
                actionType = "TASK_COMPLETE",
                description = "race",
                requestedPoints = 5,
                bizKey = "TASK:1:2026-11-25",
                activeBizKey = "TASK:1:2026-11-25"
            )
        )
        val repo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
        val results = coroutineScope {
            val gate = CompletableDeferred<Unit>()
            val firstRead = CompletableDeferred<Unit>()
            val gatedDao = object : PointApprovalDao by db.pointApprovalDao() {
                var reads = 0
                override suspend fun getById(id: Long): PointApprovalEntity? {
                    val row = db.pointApprovalDao().getById(id)
                    reads += 1
                    if (reads == 2) firstRead.complete(Unit)
                    gate.await()
                    return row
                }
            }
            val gatedRepo = PointRepository(db.pointDao(), gatedDao, roomTransactionRunner(db))
            val a = async { gatedRepo.approveAndAward(approvalId) }
            firstRead.await()
            val b = async { gatedRepo.approveAndAward(approvalId) }
            gate.complete(Unit)
            listOf(a.await(), b.await())
    }
        assertEquals(listOf(5, 0), results.sortedDescending())
        assertEquals(5, db.pointDao().getTotalPoints().first())
        assertEquals(1, db.pointDao().getAllPointRecords().first().size)
    } finally {
        db.close()
    }
}
```

Because real Room transactions serialize the gated read/write section, the baseline test should expose either duplicate awards or a loser that does not re-read terminal state. Record the actual observed failure.

- [ ] **Step 2: Write a real concurrent spend test**

```kotlin
@Test fun concurrentTenPointSpendsCannotOverdraw() = runBlocking {
    val db = testDatabase(context)
    try {
        db.pointDao().insertPointRecord(PointRecordEntity(type = PointRecordType.ADJUSTMENT, points = 10))
        val repo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
        val results = (1..20).map { async { repo.spendPoints(10, "pet", true) } }.awaitAll()
        assertEquals(1, results.count { it })
        assertEquals(0, db.pointDao().getTotalPoints().first())
    } finally {
        db.close()
    }
}
```

- [ ] **Step 3: Run red**

Run both instrumentation classes. Expected: the approval race can create two awards, and/or concurrent spending can create a negative balance. Save XML and logcat.

- [ ] **Step 4: Add DAO CAS and transaction boundaries**

Change `updateReview` to return affected-row count and include `AND status = 'PENDING'`. Wrap `checkIn`, `checkInForTask`, `undoCheckIn`, `approveAndAward`, `rejectApproval`, `spendPoints`, and `refundSpentPoints` in `transactionRunner.runInTransaction`.

Inside each transaction:

1. Re-read the approval/session/balance.
2. Return the defined no-op result when the row is no longer PENDING or funds are insufficient.
3. Set `activeBizKey` when creating the canonical active approval.
4. Clear `activeBizKey` when the row becomes terminal and no newer active row owns the key.
5. Insert ledger rows only after the CAS succeeds.

Change `spendPoints` default to `requireSufficientBalance = true` only after auditing every caller; callers that intentionally permit negative balances must pass `false` explicitly.

- [ ] **Step 5: Run green and regression**

Run both new classes plus all existing `PointRepository*` common tests and `testDebugUnitTest`. Expected: one award, one spend, nonnegative balance, all existing ledger invariants pass. Update P1-01 and P1-08 ledger rows.

### Task 5: Make template replacement, wish deposits/refunds, and pet purchases atomic (P1-02, P1-03, P1-08 UI)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/TaskRepository.kt:20,144-150`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/WishGoalRepository.kt:32-35,56-87`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/wish/ChildWishGoalScreen.kt:305-341`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/pet/ChildPetScreen.kt:44,213-244,373-382`
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/entity/PetScenePurchaseEntity.kt`
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/dao/PetScenePurchaseDao.kt`
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/PetRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/AppDatabase.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/Migrations.kt`
- Test: `composeApp/src/androidTest/kotlin/com/hstudy/app/data/repository/TemplateWishPetAtomicityTest.kt`
- Test: `composeApp/src/androidTest/kotlin/com/hstudy/app/ui/pet/PetScreenDoubleClickTest.kt`

**Interfaces:**
- Produces:
  - `TaskRepository(taskDao, transactionRunner)`
  - `WishGoalRepository(wishGoalDao, pointRepository, transactionRunner)`
  - `PetRepository(pointDao, scenePurchaseDao, transactionRunner)`
  - `PetRepository.feed(points: Int, note: String): Boolean`
  - `PetRepository.purchaseScene(sceneKey: String, points: Int, note: String): Boolean`
  - `PetScenePurchaseEntity(sceneKey: String, purchasedAt: Long)` with unique primary key.

- [ ] **Step 1: Write the template rollback test**

Use a delegating `TaskDao` that throws on the second template insert after real soft deletes. Assert the original active task count and IDs are unchanged.

- [ ] **Step 2: Write wish limit and rollback tests**

```kotlin
@Test fun depositAboveRemainingIsRejected() = runBlocking {
    // target 50, saved 0, balance 100
    val accepted = wishRepo.depositPoints(goalId, 100)
    assertEquals(false, accepted)
    assertEquals(100, pointRepo.getTotalPoints().first())
    assertEquals(0, wishDao.getGoalById(goalId)?.savedPoints)
}

@Test fun failedWishProgressUpdateRollsBackDebit() = runBlocking {
    val dao = object : WishGoalDao by realDao {
        override suspend fun addSavedPoints(id: Long, amount: Int) = error("injected")
    }
    val repo = WishGoalRepository(dao, pointRepo, roomTransactionRunner(db))
    assertEquals(false, runCatching { repo.depositPoints(goalId, 10) }.isSuccess)
    assertEquals(100, pointRepo.getTotalPoints().first())
    assertEquals(0, realDao.getGoalById(goalId)?.savedPoints)
}
```

- [ ] **Step 3: Write real pet purchase and double-click tests**

Repository test: balance 10, two concurrent `purchaseScene("space", 10)` calls; assert one purchase row, one -10 ledger row, balance 0. Compose test: tap feed twice rapidly; assert one debit and no negative balance.

- [ ] **Step 4: Run red**

Run `TemplateWishPetAtomicityTest` and `PetScreenDoubleClickTest`. Save the observed partial states.

- [ ] **Step 5: Implement transactions and guards**

- Wrap `applyTemplate` in the runner.
- In `depositPoints`, reject `amount <= 0`, `amount > balance`, or `amount > targetPoints - savedPoints`; perform debit and progress update in one transaction.
- Wrap `deleteGoal` refund and goal update/delete in one transaction.
- Change dialog validation to `amount in 1..minOf(currentPoints, remaining)`.
- Add `pet_scene_purchases` to schema v14 with `sceneKey` primary key.
- Implement `PetRepository.purchaseScene` as a transaction that checks balance, inserts the unique scene purchase, and inserts the negative ledger row.
- Add a `Mutex`-backed in-flight guard to pet feed/play/scene click handlers and honor every `Boolean` result.

- [ ] **Step 6: Run green**

Run both new classes, all wish/point common tests, full `testDebugUnitTest`, and iOS compile. Update P1-02, P1-03, and P1-08 rows.

### Task 6: Make backup, restore, and import failure-safe (P1-05)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/backup/BackupHelper.kt:34-140`
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/backup/SqliteBackupValidator.kt`
- Create: `composeApp/src/androidTest/kotlin/com/hstudy/app/testutil/FaultInjectingFileSystem.kt`
- Create: `composeApp/src/androidTest/kotlin/com/hstudy/app/data/backup/BackupFailureSafetyTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/AppGraph.kt`

**Interfaces:**
- Produces:
  - `class BackupHelper(fs: FileSystem, liveDbPath: Path, backupPath: Path, validator: SqliteBackupValidator, closeDatabase: () -> Unit, reopenDatabase: () -> Unit)`
  - `SqliteBackupValidator.validate(path: Path): Boolean`
  - Production no-argument construction remains available through a factory that reads `Platform` paths and `AppGraph`.

- [ ] **Step 1: Write real-file failure tests**

Create a temporary directory and a real SQLite database file. Use `ForwardingFileSystem` to throw on the selected `copy`, `delete`, or `rename` call.

Tests:

1. `backupFailureKeepsPreviousBackup`: seed old valid backup, fail `VACUUM INTO`, assert old backup hash unchanged.
2. `restoreCopyFailureKeepsLiveDatabase`: fail candidate copy after live rename, assert `.bak` is restored and live opens.
3. `corruptImportNeverReplacesBackupOrLive`: import bytes `not sqlite`, assert failure and both hashes unchanged.
4. `wrongSchemaImportIsRejected`: valid SQLite with wrong `user_version`, assert failure.
5. `successfulRestoreReopensAndPassesIntegrityCheck`: assert `PRAGMA integrity_check == ok`.

- [ ] **Step 2: Run red**

Run `BackupFailureSafetyTest`. Expected baseline failures include deleted old backup, missing live DB after copy failure, and accepted corrupt import.

- [ ] **Step 3: Implement staged operations**

- `backup()`: write unique `.tmp`, validate, then atomically replace the previous backup.
- `importBackup()`: decode to `.import.tmp`, validate magic/header/schema/integrity, then replace backup; never touch live DB.
- `restore()`: close DB, rename live/WAL/SHM to `.bak`, copy validated candidate to live, reopen, validate; on any failure delete the partial candidate and rename `.bak` files back.
- Remove `.bak` only after successful reopen and validation.

`SqliteBackupValidator` must check the `SQLite format 3\0` header, `PRAGMA user_version`, required table names, and `PRAGMA integrity_check`.

- [ ] **Step 4: Run green**

Run the full backup class twice to prove cleanup/idempotency, then run common regression. Update P1-05 with real file hashes before/after each failure.

### Task 7: Make family control/progress sync validated, consistent, and observable (P1-06, P2-25, P2-26, P2-27)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/sync/FamilySyncHelper.kt:68-259`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/platform/Platform.kt:63-91`
- Modify: `composeApp/src/androidMain/kotlin/com/hstudy/app/platform/AndroidFileSharer.kt:21-62`
- Modify: `composeApp/src/iosMain/kotlin/com/hstudy/app/platform/IosFileSharer.kt:27-82`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentSettingsScreen.kt:251-270`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/child/ChildHomeScreen.kt:536-548`
- Create: `composeApp/src/androidTest/kotlin/com/hstudy/app/data/sync/FamilySyncSafetyTest.kt`
- Create: `composeApp/src/androidTest/kotlin/com/hstudy/app/ui/settings/FamilyShareResultTest.kt`

**Interfaces:**
- Produces:
  - `sealed interface ShareResult { data object Presented; data object Completed; data object Cancelled; data class Failed(val message: String) }`
  - `FileSharer.shareTextFile(...): ShareResult`
  - `FamilySyncHelper.importControlConfig(text): Result<String>` rejects empty/invalid replacement unless `allowEmpty = true` is explicit.
  - `ChildProgressSummary` includes `schemaVersion` and `exportedAt`.

- [ ] **Step 1: Write control-import tests**

Against real Room with 10 active tasks and rewards:

```kotlin
assertEquals(false, helper.importControlConfig("""{"type":"parent_control"}""").isSuccess)
assertEquals(10, db.taskDao().getAllActiveTasksSnapshot().size)
```

Then inject a failure on the second replacement insert and assert all original tasks/rewards remain.

- [ ] **Step 2: Write progress snapshot and validation tests**

- Inject a point write between aggregate reads; assert export fails or returns one transactionally consistent snapshot.
- Reject negative points/focus, `masteredMistakes > totalMistakes`, `unmasteredMistakes != total - mastered`, wrong type, and wrong schema version.
- Inject `FileSharer` failure; assert UI displays failure, not export success.

- [ ] **Step 3: Run red**

Run both classes and record partial deletions, mixed snapshots, accepted invalid DTOs, and false success messages.

- [ ] **Step 4: Implement validation and atomic replacement**

- Add `schemaVersion`, `exportedAt`, and explicit `allowEmpty` to sync DTOs.
- Validate all fields before obtaining a transaction or changing UI state.
- Replace tasks/rewards inside one `DbTransactionRunner` transaction.
- Add a single aggregate DAO query for progress export; include DataStore values with a disclosed sample timestamp.
- Change `FileSharer.shareTextFile` to return `ShareResult`. Android returns `Presented` after a successful chooser launch and `Failed` on exception; it must not claim delivery. iOS maps completion/cancel/failure through `UIActivityViewController` completion.
- Split UI messages into exported, share presented/completed, cancelled, and failed.

- [ ] **Step 5: Run green**

Run both classes and all sync copy tests. Update P1-06 and P2-25/26/27 rows.

### Task 8: Enforce task frequency, award bonus points, and make assignment retry safe (P1-07, P2-01, P2-13)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/TaskFrequencyPolicy.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/dao/PointDao.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/PointRepository.kt:117-157`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/child/ChildTaskListScreen.kt:157-180`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/AssignmentRepository.kt:42-55`
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/data/repository/TaskFrequencyPolicyTest.kt`
- Test: `composeApp/src/androidTest/kotlin/com/hstudy/app/data/repository/FrequencyBonusAssignmentTest.kt`

**Interfaces:**
- Produces:
  - `enum class FrequencyPeriod { DAY, WEEK, MONTH, ONCE }`
  - `fun TaskFrequency.periodKey(date: LocalDate): String`
  - `PointRepository.checkInForTask(task: TaskEntity, date: String, clock: AppClock): CheckInResult`
  - `AssignmentRepository(assignmentDao, transactionRunner)`

- [ ] **Step 1: Write pure period-boundary tests**

```kotlin
@Test fun weeklyTaskUsesIsoWeekAcrossMonthBoundary() {
    assertEquals(TaskFrequency.WEEKLY.periodKey(LocalDate(2026, 3, 30)),
                 TaskFrequency.WEEKLY.periodKey(LocalDate(2026, 4, 5)))
    assertNotEquals(TaskFrequency.WEEKLY.periodKey(LocalDate(2026, 4, 5)),
                    TaskFrequency.WEEKLY.periodKey(LocalDate(2026, 4, 6)))
}

@Test fun onceTaskUsesLifetimeKey() {
    assertEquals("ONCE", TaskFrequency.ONCE.periodKey(LocalDate(2026, 11, 25)))
}
```

- [ ] **Step 2: Write real DB behavior tests**

- WEEKLY task completed Monday and Tuesday: second check-in returns duplicate and creates no second approval.
- MONTHLY task across month-end: allowed once per month.
- ONCE task: second attempt rejected.
- Task with base 5 and bonus 2: approval requests 7.
- Assignment `onPoints` throws once: first call rolls back completion; second call succeeds with one approval.

- [ ] **Step 3: Run red**

Run the pure and instrumentation classes. Record the baseline daily-only behavior and missing bonus.

- [ ] **Step 4: Implement policy and atomic retry**

- Add period-aware DAO queries using date ranges and `LIMIT 1`.
- Use the period key in check-in idempotency and UI completed-state lookup.
- Pass `task.effectivePoints() + task.bonusPoints` to check-in.
- Wrap `AssignmentRepository.completeAssignment` in the transaction runner and invoke `onPoints` inside that transaction. A thrown callback rolls back `isCompleted`, allowing retry.

- [ ] **Step 5: Run green**

Run both classes, all point/assignment common tests, full unit suite, and iOS compile. Update P1-07, P2-01, and P2-13.

### Task 9: Close proof, habit, mistake, reward, preset, and penalty split writes (P2-02, P2-14, P2-15, P2-16, P2-17, P2-24)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/dao/HabitDao.kt:18,43-49,73-74`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/HabitRepository.kt:39-55,103-112`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/MistakeRepository.kt:58-78`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/RewardRepository.kt:8-82`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/child/ChildRewardStoreScreen.kt:214-233`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/MandatoryPenaltyHelper.kt:76-88`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/dao/RewardDao.kt`
- Test: `composeApp/src/androidTest/kotlin/com/hstudy/app/data/repository/HabitMistakeRewardAtomicityTest.kt`
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/data/repository/MandatoryPenaltyHelperTest.kt`

**Interfaces:**
- Produces:
  - `HabitDao.incrementChallengeProgress(id, expectedCompletedDays, newCompletedDays, lastCheckInDate): Int`
  - `HabitDao.reviewProof(...): Int`
  - `RewardRepository(rewardDao, pointRepository, transactionRunner)`
  - `RewardRepository.exchangeReward(rewardId, pointsSpent, note): Long`
  - `RewardRepository.ensureDefaultRewards()` uses stable `presetKey` upsert.
  - `MandatoryPenaltyHelper.cappedPenalty(balance: Int, penalty: Int): Int`

- [ ] **Step 1: Write real DB tests**

- Concurrent approve/reject of one proof; assert only the first PENDING transition succeeds and the terminal state is not overwritten.
- Stale habit snapshot writes after a newer progress update; assert no regression.
- Twenty concurrent unlocks of `perfect_day`; assert one canonical row.
- Mistake review fails at history or approval insert; assert all three tables roll back; retry creates one of each.
- Reward exchange fails at debit; assert no exchange row. Insufficient balance writes neither row.
- Two concurrent `initDefaultRewards()` calls with a custom reward present; assert 27 canonical presets and the custom reward unchanged.
- Pure penalty test: balance 10, penalty 25 returns 10.

- [ ] **Step 2: Run red**

Run both classes. Save partial-state evidence.

- [ ] **Step 3: Implement CAS and transactions**

- Replace whole-entity habit progress updates with field-level conditional updates and affected-row checks.
- Add `AND status = 'PENDING'` to proof review and return affected rows.
- Use `canonicalType` and `INSERT ... ON CONFLICT DO NOTHING` for achievements.
- Wrap mistake progress/history/approval in one transaction.
- Move reward exchange debit into `RewardRepository.exchangeReward`; the UI only calls that method.
- Make preset initialization transactional and upsert by `presetKey`.
- Return the actual capped deduction from `MandatoryPenaltyHelper`.

- [ ] **Step 4: Run green**

Run both classes, existing habit/mistake/wish/point tests, full unit suite, and iOS compile. Update P2-02/14/15/16/17/24.

### Task 10: Make reports and completion rates use authoritative history (P2-06, P2-11, P2-21, P2-28)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/dao/PointDao.kt:24-26`
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/ReportRepository.kt`
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/dao/ReportDao.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentPointsScreen.kt:160-171`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/habit/ChildEfficiencyReportScreen.kt:98-119,436-443`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/report/GrowthReportScreen.kt:45-103`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentDashboardScreen.kt:151-165`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/habit/ChildGoalTrackerScreen.kt:28-38`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/weekly/WeeklyTrackerScreen.kt:31-67,249-307`
- Test: `composeApp/src/androidTest/kotlin/com/hstudy/app/data/repository/ReportHistoryTest.kt`
- Test: `composeApp/src/androidTest/kotlin/com/hstudy/app/ui/weekly/WeeklyTrackerHistoryTest.kt`

**Interfaces:**
- Produces:
  - `ReportRepository.historicalTaskPoints(range): Flow<Map<Long, Int>>`
  - `ReportRepository.completionRate(date): Flow<Int>`
  - `ReportRepository.weeklyPoints(weekStart: LocalDate): Flow<Map<LocalDate, Int>>`
  - `PointDao.getLifetimeEarnedPoints()` excludes `REWARD_EXCHANGE` refunds.

- [ ] **Step 1: Write history immutability tests**

Seed a task worth 2, create an approved completion worth 2, then change the task to 5 and soft-delete it. Assert historical ranking remains 2. Add a wish refund and assert lifetime earned points do not increase. Complete three tasks, delete one, and assert completion rate is 100, not 150. Add PENDING/REJECTED records and bonus/penalty; assert weekly awarded points match the ledger. Cross a controlled week boundary and assert header and summary use the same week.

- [ ] **Step 2: Run red**

Run both instrumentation classes and record current-task reconstruction and unclamped rates.

- [ ] **Step 3: Implement authoritative queries**

- Aggregate task points from `task_completions.pointsEarned` and `point_records`.
- Exclude `REWARD_EXCHANGE` from lifetime earned income.
- Use `daily_task_snapshots` for new completion-rate denominators; for legacy dates return an explicitly labelled fallback and clamp 0..100.
- Store the weekly anchor in one state object and derive both header and summary from it.
- Update all listed screens to collect repository Flows rather than recomputing from current task entities.

- [ ] **Step 4: Run green**

Run both classes, report/copy common tests, full unit suite, and iOS compile. Update P2-06/11/21/28.
