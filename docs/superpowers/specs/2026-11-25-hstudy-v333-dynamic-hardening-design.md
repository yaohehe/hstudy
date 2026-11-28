# HStudy v3.3.3 Dynamic Hardening Design

**Date:** 2026-11-25
**Baseline branch:** `feature/v332-hardening`
**Baseline commit:** `42f757d595e86883b3aea168315c576bfca0f63a`
**Target branch:** `feature/v333-dynamic-hardening`
**Target version:** `3.3.3` / `versionCode 30303`
**Status:** Approved to continue on 2026-11-25

## 1. Purpose

Dynamically and intrusively reproduce the 8 P1 and 29 P2 findings in `docs/antigravity-post-v332-review.md`, fix every confirmed defect or risk, regress the fixes, and publish v3.3.3.

Static source analysis is a hypothesis source, not confirmation. Every finding must receive runtime evidence and one final adjudication:

- `CONFIRMED`: a deterministic test or measurement reproduces the claimed harmful behavior.
- `NOT_REPRODUCED`: a valid targeted test runs and the harmful behavior does not occur.
- `ENVIRONMENT_BLOCKED`: the required runtime or hardware is unavailable; the finding remains unconfirmed by that environment.
- `NOT_A_DEFECT`: runtime behavior matches the intended contract, or the report's premise is invalid.

`ENVIRONMENT_BLOCKED` and `NOT_REPRODUCED` must never be reported as fixed. Fake DAO tests must never be presented as real SQLite, Android platform, or UI evidence.

## 2. Baseline Evidence

Firebase Test Lab project `hstudy-ad3f2`, matrix `matrix-24obb1afqhp4o`, ran the signed v3.3.2 APK on Pixel 5 / API 30 with Robo.

- Console: success, 1 passed, 0 failed, 5 minutes 12 seconds, 108 actions, 5 activities, 46 screens.
- `actions.json`: 104 events, all `SUCCESS`; this is not the same metric as the console's 108 actions.
- Full `logcat.txt`: 12,757,594 bytes, 111,557 lines, SHA-256 `edd52dbf5c12e8bdde2b1dc05f132716e8aee8d716138b03f28f0afac1de0bb1`.
- TestRunner: `run finished: 1 tests, 0 failed, 0 ignored`.
- HStudy PID 31288: no FATAL exception, ANR, SIGSEGV, SIGABRT, force-finish, or process-kill marker.
- Room clean-install verification: database version 13, all 16 tables present with zero rows, v12 `point_approvals` columns present.
- Six resource-release warnings have no stack or HStudy resource identifier and also occur in system processes; they do not confirm a HStudy leak.
- The crawl ended at its five-minute timeout and does not prove 43-route coverage or any precise concurrency invariant.

Evidence: `.verify-v332/testlab-matrix-24obb1afqhp4o/baseline-evidence.md`.

## 3. Non-Goals

- Do not rebuild real-time device discovery or transport in v3.3.3. Correct the product copy and capability boundary to describe manual JSON file sync.
- Do not introduce Paging 3 unless the P2-08 benchmark confirms a measurable problem.
- Do not use destructive database migration to remove historical audit rows.
- Do not claim iOS runtime verification on this Windows host. iOS must at least compile; unavailable runtime evidence is `ENVIRONMENT_BLOCKED`.
- Do not treat a successful Robo crawl as proof of transaction, migration, DataStore, backup, or notification correctness.
- Do not push, create a GitHub Release, or deploy the website until the release evidence is complete and the exact shared-state action is confirmed.

## 4. Test Architecture

### 4.1 Layers

1. **Common/JVM deterministic tests**
   - Coroutine interleavings with `runTest`.
   - Fault-injecting DAOs, preferences, file systems, clocks, and platform services.
   - Fast red/green proof for repository state machines and validation.
2. **Android local instrumentation with real dependencies**
   - Real Room, `BundledSQLiteDriver`, SQLite files, DataStore, and filesystem.
   - Required for transaction rollback, constraints, populated migrations, backup recovery, and password migration.
3. **Compose UI instrumentation**
   - Double-click, stale-state, Back, cross-midnight, form validation, progress refresh, and result messaging.
4. **Firebase Test Lab**
   - Targeted instrumentation/Compose matrices on a small device set.
   - Real AlarmManager, PendingIntent, notification clicks, process recreation, and Robo compatibility smoke.
5. **iOS**
   - Kotlin/Native compile for all three iOS targets.
   - Runtime tests only when a macOS/Xcode artifact is available.

### 4.2 Infrastructure

- Enable Room schema export and commit the generated v13 schema.
- Add Android instrumentation dependencies, `AndroidJUnitRunner`, Room testing, AndroidX Test, and Compose UI test artifacts.
- Add test-only fault-injection seams; no production debug backdoors.
- Inject `DbTransactionRunner` into every repository/helper that needs a multi-write transaction.
- Add a testable clock/date source for frequency, reports, schedules, and midnight behavior.
- Preserve the existing 325-test baseline.
- Generate historical schema fixtures from repository tags in isolated temporary worktrees; never switch the active implementation branch.

### 4.3 Required Commands

The final verification must include fresh runs of:

```bash
./gradlew :composeApp:testDebugUnitTest --rerun-tasks
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:assembleDebugAndroidTest
./gradlew :composeApp:connectedDebugAndroidTest
./gradlew :composeApp:lintDebug
./gradlew :composeApp:compileKotlinIosSimulatorArm64
python scripts/analytics_call_sites.py
python scripts/i18n_storage_boundary.py
python scripts/i18n_template_diff.py
python scripts/i18n_template_diff.py --branch
python scripts/tests/i18n_storage_boundary_selftest.py
python scripts/tests/i18n_template_diff_selftest.py
```

The exact connected-device command may target a named instrumentation class during development, but the release gate runs the full instrumentation suite.

## 5. Evidence Ledger

The ledger is `.verify-v333/evidence-ledger.md`. It contains one row per finding with:

- ID and severity.
- Claim under test.
- Environment layer and test class/method.
- Pre-fix command, commit, result, and artifact path.
- Final state and rationale.
- Root cause.
- Fix commit and changed files.
- Post-fix command, result, and artifact path.
- Residual risk or environment limitation.

Rules:

- A P1 database finding cannot be `CONFIRMED` solely by Fake DAO evidence.
- A platform finding cannot be `CONFIRMED` solely by common/JVM evidence.
- A red test must fail for the claimed reason, not because of compilation or setup.
- A fix is green only when the same targeted test passes and relevant regression suites pass.
- Any changed verdict requires a written reason and fresh evidence.

## 6. Finding Matrix

### 6.1 Data Integrity Batch

| ID | Dynamic reproduction | Required acceptance after confirmed fix |
|---|---|---|
| P1-01 | Real Room concurrency: two approvals of one PENDING row; concurrent check-in/undo; historical duplicate `bizKey` migration fixture | Exactly one award; terminal CAS rejects the loser; historical rows preserved; one active key per business operation |
| P1-02 | Real Room transaction with injected insert failure and process-death simulation | Old active tasks remain after failure; template replacement is all-or-nothing |
| P1-03 | UI/repository deposit above remaining; injected failure between debit and wish update | Deposit is rejected above `min(balance, remaining)`; debit and progress roll back together |
| P1-05 | Real filesystem/SQLite: disk-full-like copy failure, corrupt import, invalid schema, failed reopen | Old backup/live DB survives every failed step; corrupt input never replaces live DB; successful restore passes integrity check |
| P1-06 | Import `{"type":"parent_control"}` and malformed/partial DTO; inject failure mid-replacement | Empty or invalid import is rejected before deletion; valid replacement is atomic |
| P1-08 | Compose double-click and concurrent feed/play/scene purchase with balance 10 | At most one operation spends; balance never becomes negative; scene unlock and debit agree |
| P2-02 | Real Room concurrent approve/reject of one proof | Only the first PENDING transition succeeds; terminal state is not overwritten |
| P2-03 | Complete daily targets, reset targets, and race checkbox completion | Old approvals/bonus cannot be reused; all-complete decision is atomic and idempotent |
| P2-04 | Settle a Pomodoro session, then pause/resume from a stale snapshot | Stale write is rejected; settled state and focus statistics remain unchanged |
| P2-12 | First Schulte result, concurrent better result, and memory-training process recreation | First result is a record; best score cannot regress; memory state follows the approved persistence contract |
| P2-14 | Interleaved stale habit update and concurrent achievement unlock | Progress cannot regress; one canonical achievement per type |
| P2-15 | Inject failure at each step of mistake review | Progress, history, and approval roll back together; retry writes each exactly once |
| P2-16 | Inject failure after exchange insert and test insufficient balance | No exchange without debit; no debit without exchange; balance guard is explicit |
| P2-17 | Balance 10 with penalty 25 | Return value, ledger, and UI all report actual deducted amount 10 |
| P2-19 | Soft-delete a task linked to a schedule | Link is cleared atomically and registered reminder is cancelled; unlinked deletion is unchanged |
| P2-23 | Populated physical SQLite fixtures for v1 through v13 upgraded to v14 | Schema, row counts, representative values, indexes, and relationships are preserved at every step |
| P2-24 | Concurrent and repeated preset initialization with custom rewards present | Exactly one canonical preset set; custom rewards are not overwritten |
| P2-25 | Inject a write between export reads | Export returns one consistent DB snapshot or fails explicitly; DataStore sample time is disclosed |
| P2-26 | Import negative, impossible, inconsistent, wrong-version progress DTOs | Invalid input is rejected before state/UI changes |

### 6.2 Business Semantics Batch

| ID | Dynamic reproduction | Required acceptance after confirmed fix |
|---|---|---|
| P1-07 | Check in DAILY/WEEKLY/MONTHLY/ONCE tasks across controlled date boundaries | Only the allowed completion per frequency period succeeds |
| P2-01 | Complete a task with nonzero `bonusPoints` | Approval/ledger includes the configured bonus exactly once |
| P2-05 | Satisfy perfect-day and habit-complete conditions; remain on achievement screen while data changes | Progress is calculated from real data and refreshes reactively |
| P2-06 | Deposit into a wish and delete it to refund points | Lifetime earned income does not increase from the refund |
| P2-10 | Schedule a weekend-only imported schedule and advance a controlled clock | No weekday alarm is registered; next matching day is selected |
| P2-11 | Change task points and soft-delete a task after historical completions | Historical points remain tied to completion/ledger snapshots; denominator semantics are explicit |
| P2-13 | Fail the first points request after marking an assignment complete, then retry | Retry succeeds without duplicate completion or approval |
| P2-21 | Complete three tasks, then soft-delete one | Completion rate uses a consistent snapshot and remains within 0..100 |
| P2-28 | Change points/delete task, add pending/rejected records and bonus/penalty, then cross a week boundary | History matches authoritative records and the week header/summary share one anchor |

### 6.3 Platform and Security Batch

| ID | Dynamic reproduction | Required acceptance after confirmed fix |
|---|---|---|
| P1-04 | Send weekly-report broadcast with `repeatDaily=false`; inspect next alarm | Next weekly alarm is registered; nonweekly one-shot behavior is unchanged |
| P2-07 | Compose navigation depth >1 and dispatch Android Back | App stack pops one destination; root Back exits normally |
| P2-09 | Invoke the duplicate approval API against a real DB and run a call-site guard | Dangerous API is removed or cannot bypass ledger/award invariants |
| P2-18 | Click schedule/review/weekly notifications from cold and warm starts | Stable route extras/delegate response opens the correct destination |
| P2-20 | Keep daily plan/timeline composed while clock moves 23:59→00:01 | Date, query, and highlight switch together |
| P2-22 | Set password, inspect DataStore, verify login, migrate a legacy plaintext value | Stored value is salted PBKDF2, verification succeeds, plaintext key is cleared |
| P2-27 | Inject share/read failure after file export | UI distinguishes exported, shared, cancelled, and failed outcomes |
| P2-29 | Exercise settings sync flow and spy on discovery calls | UI/docs state manual file sync only; no false real-time discovery claim remains |

### 6.4 Performance Batch

| ID | Dynamic reproduction | Decision rule |
|---|---|---|
| P2-08 | Real SQLite with 5,000 point records, 1,000 mistakes, and 1,000 focus sessions; measure 20 insert-triggered full-list emissions against a 500-row baseline | `CONFIRMED` if p95 emission/mapping exceeds 100 ms or retained heap growth exceeds 64 MiB on the reference Pixel 5; otherwise `NOT_REPRODUCED` and no paging change in v3.3.3 |

## 7. Core Implementation Decisions

### 7.1 Transactions

`DbTransactionRunner` remains the common abstraction.

- Android continues to use `RoomDatabase.withTransaction`.
- iOS uses `database.useWriterConnection { it.immediateTransaction { block() } }`.
- `DirectTransactionRunner` is test-only and must not be returned by production platform factories.
- Nested repository calls join the outer transaction.

### 7.2 Schema v14

Schema v14 is non-destructive:

- `point_approvals.activeBizKey`: nullable normalized active business key with a unique index. Historical duplicate `bizKey` rows remain intact; only the canonical active row receives the active key.
- `achievements`: add a canonical unique key/upsert path without deleting historical rows.
- `rewards`: add nullable stable `presetKey` with a unique index; custom rewards remain unkeyed and are never overwritten.
- Add a daily task snapshot table for new historical completion-rate data. Legacy dates without snapshots use an explicitly labelled fallback rather than pretending exact historical denominators exist.
- Add migration tests from every historical schema version, including duplicate and partially populated data.

### 7.3 Backup and Restore

- Backup writes to a unique temporary file and replaces the previous backup only after `VACUUM INTO` and integrity validation succeed.
- Import validates envelope, Base64, SQLite magic, expected schema version, and `PRAGMA integrity_check` before touching live data.
- Restore renames live files to `.bak`, installs the validated candidate, reopens and validates it, and restores `.bak` on failure.
- `.bak` is removed only after successful reopen and validation.

### 7.4 Reports

- Historical task points come from `TaskCompletionEntity.pointsEarned` and authoritative `point_records`, never current task points.
- Pending/rejected completions do not count as awarded history.
- New daily snapshots provide consistent denominators; legacy fallback is labelled.
- Lifetime earned points exclude wish refunds and other non-income positive adjustments.

### 7.5 Parent Password

- PBKDF2-HMAC-SHA256, 600,000 iterations, 128-bit random salt, 256-bit derived key.
- Store algorithm version, iterations, salt, and hash; never store plaintext.
- Use platform-native cryptographic implementations and constant-time comparison.
- On successful legacy plaintext verification, atomically write the hash and remove the plaintext key.

### 7.6 Product Scope

P2-29 is resolved by correcting the settings copy, help text, and capability exposure to manual JSON file sync. Real-time discovery remains unimplemented and must not be advertised as available.

## 8. Release Gates

v3.3.3 may be prepared only when:

1. All 37 findings have a final evidence state and artifact.
2. Every `CONFIRMED` P1 is fixed and green.
3. Every `CONFIRMED` P2 is fixed or has an explicit approved residual; no silent deferral.
4. Original 325 tests and all new tests pass.
5. Real SQLite migration, transaction, constraint, backup, and DataStore tests pass.
6. Android build, lint, instrumentation, and targeted Test Lab matrix pass.
7. All iOS targets compile; unavailable iOS runtime checks are disclosed.
8. Existing analytics, i18n, storage-boundary, template-diff, and secret-scan gates pass.
9. Version stamps, release notes, acceptance report, signed APK hash/signature, and website changelog are synchronized.

Push, GitHub Release, and website deployment are separate shared-state actions requiring confirmation with the completed evidence summary.
