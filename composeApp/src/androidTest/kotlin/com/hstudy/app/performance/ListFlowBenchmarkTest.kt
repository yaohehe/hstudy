package com.hstudy.app.performance

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.data.local.AppDatabase
import com.hstudy.app.data.local.roomTransactionRunner
import com.hstudy.app.data.local.entity.FocusSessionEntity
import com.hstudy.app.data.local.entity.MistakeEntity
import com.hstudy.app.data.local.entity.MistakeSubject
import com.hstudy.app.data.local.entity.PointRecordEntity
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.testutil.testDatabase
import com.hstudy.app.ui.child.summarizePointRecords
import com.hstudy.app.ui.mistake.deriveMistakeListStats
import com.hstudy.app.ui.mistake.SpacedRepetition
import com.hstudy.app.ui.dt.Calendar
import com.hstudy.app.ui.dt.Date
import com.hstudy.app.ui.dt.Locale
import com.hstudy.app.ui.dt.SimpleDateFormat
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P2-08: real Room + BundledSQLite measurement of the unbounded list flows.
 *
 * Production screens use the Room entity lists as their state model (there is no separate
 * ViewState). The local immutable models retain those same entity lists and call the production
 * `summarizePointRecords` / `deriveMistakeListStats` helpers, so the measured path is the shipped path.
 *
 * The reference verdict is valid only on Pixel 5 / API 30. Each case uses its own file-backed
 * database, seeds the specified baseline, then measures 20 sequential insert-to-mapped-emission
 * intervals after one initial emission and two warm-up insert cycles. Warm-up emissions are never
 * counted among the 20 samples. Every measured emission must carry the next insert sequence and expected row count.
 */
@RunWith(AndroidJUnit4::class)
class ListFlowBenchmarkTest {
    @Test
    fun benchmarkUnboundedListFlows() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val referenceDevice = Build.MODEL.equals("Pixel 5", ignoreCase = true) && Build.VERSION.SDK_INT == 30
        val startedAt = System.currentTimeMillis()
        if (!referenceDevice) {
            val blocked = buildReport(emptyList(), startedAt).apply {
                put(
                    "reason",
                    "Reference Pixel 5 / API 30 is unavailable; found ${Build.MANUFACTURER} ${Build.MODEL} / API ${Build.VERSION.SDK_INT}"
                )
            }
            writeReport(context, blocked)
            assumeTrue(
                "P2-08 verdict requires the reference Pixel 5 / API 30; found ${Build.MANUFACTURER} ${Build.MODEL} / API ${Build.VERSION.SDK_INT}",
                false
            )
        }
        val report = try {
            val cases = listOf(
                BenchmarkCase(
                    id = "points_500",
                    table = "point_records",
                    flowName = "PointDao.getAllPointRecords",
                    screenModel = "ChildPointsScreen: entity list + totalEarned + summarizePointRecords groups",
                    seedRows = 500,
                    flow = { db -> db.pointDao().getAllPointRecords() },
                    seed = { db, count -> seedPoints(db, count) },
                    insert = { db, index -> insertPoint(db, index) },
                    mapToUiModel = { rows -> PointScreenModel.from(rows) },
                    modelRowCount = { (it as PointScreenModel).records.size }
                ),
                BenchmarkCase(
                    id = "points_5000",
                    table = "point_records",
                    flowName = "PointDao.getAllPointRecords",
                    screenModel = "ChildPointsScreen: entity list + totalEarned + summarizePointRecords groups",
                    seedRows = 5_000,
                    flow = { db -> db.pointDao().getAllPointRecords() },
                    seed = { db, count -> seedPoints(db, count) },
                    insert = { db, index -> insertPoint(db, index) },
                    mapToUiModel = { rows -> PointScreenModel.from(rows) },
                    modelRowCount = { (it as PointScreenModel).records.size }
                ),
                BenchmarkCase(
                    id = "mistakes_500",
                    table = "mistakes",
                    flowName = "MistakeDao.getAllMistakes",
                    screenModel = "ChildMistakeListScreen: entity list + default full-list filter pass + due list + weakest subject + 7-day forecast",
                    seedRows = 500,
                    flow = { db -> db.mistakeDao().getAllMistakes() },
                    seed = { db, count -> seedMistakes(db, count) },
                    insert = { db, index -> insertMistake(db, index) },
                    mapToUiModel = { rows -> MistakeScreenModel.from(rows) },
                    modelRowCount = { (it as MistakeScreenModel).records.size }
                ),
                BenchmarkCase(
                    id = "mistakes_1000",
                    table = "mistakes",
                    flowName = "MistakeDao.getAllMistakes",
                    screenModel = "ChildMistakeListScreen: entity list + default full-list filter pass + due list + weakest subject + 7-day forecast",
                    seedRows = 1_000,
                    flow = { db -> db.mistakeDao().getAllMistakes() },
                    seed = { db, count -> seedMistakes(db, count) },
                    insert = { db, index -> insertMistake(db, index) },
                    mapToUiModel = { rows -> MistakeScreenModel.from(rows) },
                    modelRowCount = { (it as MistakeScreenModel).records.size }
                ),
                BenchmarkCase(
                    id = "focus_500",
                    table = "focus_sessions",
                    flowName = "FocusDao.getAllSessions",
                    screenModel = "ChildPomodoroScreen: entity list + golden-hour aggregation",
                    seedRows = 500,
                    flow = { db -> db.focusDao().getAllSessions() },
                    seed = { db, count -> seedFocus(db, count) },
                    insert = { db, index -> insertFocus(db, index) },
                    mapToUiModel = { rows -> FocusScreenModel.from(rows) },
                    modelRowCount = { (it as FocusScreenModel).records.size }
                ),
                BenchmarkCase(
                    id = "focus_1000",
                    table = "focus_sessions",
                    flowName = "FocusDao.getAllSessions",
                    screenModel = "ChildPomodoroScreen: entity list + golden-hour aggregation",
                    seedRows = 1_000,
                    flow = { db -> db.focusDao().getAllSessions() },
                    seed = { db, count -> seedFocus(db, count) },
                    insert = { db, index -> insertFocus(db, index) },
                    mapToUiModel = { rows -> FocusScreenModel.from(rows) },
                    modelRowCount = { (it as FocusScreenModel).records.size }
                )
            )
            val results = cases.map { measureCase(context, it) }
            buildReport(results, startedAt)
        } catch (error: Throwable) {
            val failure = buildReport(emptyList(), startedAt).apply {
                put("status", "ERROR")
                put("verdict", "ERROR")
                getJSONObject("overall").put("verdict", "ERROR")
                put("error", error.stackTraceToString())
            }
            writeReport(context, failure)
            throw error
        }

        writeReport(context, report)
        val datasets = report.getJSONArray("datasets")
        assertEquals(6, datasets.length())
        for (index in 0 until datasets.length()) {
            val dataset = datasets.getJSONObject(index)
            assertEquals(20, dataset.getInt("emissionCount"))
            assertEquals(20, dataset.getJSONArray("emissionTimingsMs").length())
            assertEquals(
                dataset.getInt("seedRows") + WARMUP_INSERT_COUNT + 20,
                dataset.getInt("finalRows")
            )
        }
        val overall = report.getJSONObject("overall")
        assertTrue(
            "Every completed dataset must have a threshold verdict",
            (0 until datasets.length()).all { index ->
                datasets.getJSONObject(index).getString("verdict") in setOf("CONFIRMED", "NOT_REPRODUCED")
            }
        )
        assertEquals(
            if ((0 until datasets.length()).any { datasets.getJSONObject(it).getString("verdict") == "CONFIRMED" })
                "CONFIRMED" else "NOT_REPRODUCED",
            overall.getString("verdict")
        )
        assertTrue(
            "P2-08 release gate: every reference-device dataset must stay below p95 100ms and retained heap 64MiB",
            (0 until datasets.length()).all { datasets.getJSONObject(it).getString("verdict") == "NOT_REPRODUCED" }
        )
    }

    private data class BenchmarkCase<T : Any>(
        val id: String,
        val table: String,
        val flowName: String,
        val screenModel: String,
        val seedRows: Int,
        val flow: (AppDatabase) -> Flow<List<T>>,
        val seed: suspend (AppDatabase, Int) -> Unit,
        val insert: suspend (AppDatabase, Int) -> Unit,
        val mapToUiModel: (List<T>) -> Any,
        val modelRowCount: (Any) -> Int
    )

    private data class DatasetResult(
        val benchmarkCase: BenchmarkCase<*>,
        val finalRows: Int,
        val timingsMs: List<Double>,
        val p95Ms: Double,
        val heapBeforeBytes: Long,
        val heapAfterBytes: Long,
        val retainedHeapDeltaBytes: Long,
        val verdict: String
    )

    private data class MappedEmission(
        val insertSequence: Int,
        val expectedRowCount: Int,
        val actualRowCount: Int,
        val initialEmission: Boolean
    )

    private suspend fun <T : Any> measureCase(
        context: Context,
        benchmarkCase: BenchmarkCase<T>
    ): DatasetResult = withContext(Dispatchers.Default) {
        val databaseName = "list-flow-benchmark-${benchmarkCase.id}-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)
        val db = testDatabase(context, databaseName)
        try {
            benchmarkCase.seed(db, benchmarkCase.seedRows)
            val emissionSequence = AtomicInteger(0)
            val emissions = Channel<MappedEmission>(Channel.UNLIMITED)
            val retainedModel = AtomicReference<Any>()
            val collector = launch(Dispatchers.Main.immediate) {
                benchmarkCase.flow(db)
                    .map { rows -> benchmarkCase.mapToUiModel(rows) }
                    .collect { model ->
                        retainedModel.set(model)
                        val insertSequence = emissionSequence.incrementAndGet() - 1
                        emissions.trySend(
                            MappedEmission(
                                insertSequence = insertSequence,
                                expectedRowCount = benchmarkCase.seedRows + insertSequence,
                                actualRowCount = benchmarkCase.modelRowCount(model),
                                initialEmission = insertSequence == 0
                            )
                        )
                    }
            }

            val warmup = withTimeout(EMISSION_TIMEOUT_MS) { emissions.receive() }
            assertTrue("The first mapped emission must be the explicit warm-up", warmup.initialEmission)
            assertEquals(0, warmup.insertSequence)
            assertEquals(benchmarkCase.seedRows, warmup.expectedRowCount)
            assertEquals(benchmarkCase.seedRows, warmup.actualRowCount)
            assertEquals(benchmarkCase.seedRows, benchmarkCase.modelRowCount(retainedModel.get()))
            assertTrue("No extra mapped emission may be queued before warm-up inserts", emissions.tryReceive().isFailure)

            repeat(WARMUP_INSERT_COUNT) { warmupIndex ->
                val insertSequence = warmupIndex + 1
                val expectedRowCount = benchmarkCase.seedRows + insertSequence
                benchmarkCase.insert(db, warmupIndex)
                val emission = withTimeout(EMISSION_TIMEOUT_MS) { emissions.receive() }
                assertTrue("Warm-up inserts must not be measured", !emission.initialEmission)
                assertEquals(insertSequence, emission.insertSequence)
                assertEquals(expectedRowCount, emission.expectedRowCount)
                assertEquals(expectedRowCount, emission.actualRowCount)
            }

            val timings = ArrayList<Double>(20)
            val measuredSequences = ArrayList<Int>(20)
            forceGc()
            val heapBefore = retainedHeapBytes()

            repeat(20) { insertIndex ->
                val insertSequence = WARMUP_INSERT_COUNT + insertIndex + 1
                val expectedRowCount = benchmarkCase.seedRows + insertSequence
                val startedNanos = System.nanoTime()
                benchmarkCase.insert(db, WARMUP_INSERT_COUNT + insertIndex)
                val emission = withTimeout(EMISSION_TIMEOUT_MS) { emissions.receive() }
                val elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000.0
                assertTrue("Warm-up must never be counted as an insert sample", !emission.initialEmission)
                assertEquals(insertSequence, emission.insertSequence)
                assertEquals(expectedRowCount, emission.expectedRowCount)
                assertEquals(expectedRowCount, emission.actualRowCount)
                measuredSequences += emission.insertSequence
                timings += elapsedMs
            }
            assertEquals((WARMUP_INSERT_COUNT + 1..WARMUP_INSERT_COUNT + 20).toList(), measuredSequences)

            assertTrue("The collector must remain active for the after-GC sample", collector.isActive)
            forceGc()
            val heapAfter = retainedHeapBytes()
            val finalRows = benchmarkCase.modelRowCount(retainedModel.get())
            assertEquals(benchmarkCase.seedRows + WARMUP_INSERT_COUNT + 20, finalRows)
            assertEquals(20, timings.size)

            collector.cancelAndJoin()
            assertTrue("No extra mapped emission may follow the measured sequence", emissions.tryReceive().isFailure)
            emissions.close()

            val sorted = timings.sorted()
            val p95 = sorted[ceil(0.95 * sorted.size).toInt() - 1]
            val heapDelta = heapAfter - heapBefore
            val confirmed = p95 > P95_THRESHOLD_MS || heapDelta > RETAINED_HEAP_THRESHOLD_BYTES
            DatasetResult(
                benchmarkCase = benchmarkCase,
                finalRows = finalRows,
                timingsMs = timings,
                p95Ms = p95,
                heapBeforeBytes = heapBefore,
                heapAfterBytes = heapAfter,
                retainedHeapDeltaBytes = heapDelta,
                verdict = if (confirmed) "CONFIRMED" else "NOT_REPRODUCED"
            )
        } finally {
            db.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun buildReport(results: List<DatasetResult>, startedAt: Long): JSONObject {
        val datasets = JSONArray()
        results.forEach { result ->
            val timings = JSONArray()
            result.timingsMs.forEach { timings.put(it) }
            datasets.put(
                JSONObject()
                    .put("id", result.benchmarkCase.id)
                    .put("table", result.benchmarkCase.table)
                    .put("flow", result.benchmarkCase.flowName)
                    .put("screenModel", result.benchmarkCase.screenModel)
                    .put("seedRows", result.benchmarkCase.seedRows)
                    .put("finalRows", result.finalRows)
                    .put("warmupEmissions", WARMUP_INSERT_COUNT + 1)
                    .put("emissionCount", result.timingsMs.size)
                    .put("emissionTimingsMs", timings)
                    .put("p95Ms", result.p95Ms)
                    .put("retainedHeapBeforeBytes", result.heapBeforeBytes)
                    .put("retainedHeapAfterBytes", result.heapAfterBytes)
                    .put("retainedHeapDeltaBytes", result.retainedHeapDeltaBytes)
                    .put("retainedHeapDeltaMiB", result.retainedHeapDeltaBytes / (1024.0 * 1024.0))
                    .put("verdict", result.verdict)
            )
        }
        val maxP95 = results.maxOfOrNull { it.p95Ms }
        val maxHeapDelta = results.maxOfOrNull { it.retainedHeapDeltaBytes }
        val overallVerdict = when {
            results.isEmpty() -> "ENVIRONMENT_BLOCKED"
            results.any { it.verdict == "CONFIRMED" } -> "CONFIRMED"
            else -> "NOT_REPRODUCED"
        }
        return JSONObject()
            .put("schemaVersion", 1)
            .put("task", "Task 17 / P2-08")
            .put("status", if (results.isEmpty()) "ENVIRONMENT_BLOCKED" else "COMPLETED")
            .put("verdict", overallVerdict)
            .put("startedAtEpochMs", startedAt)
            .put("completedAtEpochMs", System.currentTimeMillis())
            .put(
                "device",
                JSONObject()
                    .put("manufacturer", Build.MANUFACTURER)
                    .put("model", Build.MODEL)
                    .put("device", Build.DEVICE)
                    .put("apiLevel", Build.VERSION.SDK_INT)
                    .put("androidRelease", Build.VERSION.RELEASE)
                    .put("referencePixel5Api30", Build.MODEL.equals("Pixel 5", true) && Build.VERSION.SDK_INT == 30)
            )
            .put(
                "method",
                JSONObject()
                    .put("database", "file-backed Room AppDatabase schema v14")
                    .put("driver", "androidx.sqlite.driver.bundled.BundledSQLiteDriver")
                    .put("queryDispatcher", "Dispatchers.IO")
                    .put("mappingDispatcher", "Dispatchers.Main.immediate")
                    .put("timing", "System.nanoTime immediately before each DAO insert through receipt of the corresponding mapped emission payload; the warm-up is fully received before any insert timestamp is read")
                    .put(
                        "emissionBinding",
                        "Channel payloads carry insertSequence, expectedRowCount, actualRowCount, and initialEmission; sequence 0 is the sole warm-up, and measured sequences 1..20 must occur exactly once with row counts seedRows+1..seedRows+20"
                    )
                    .put("p95", "nearest rank: sorted[ceil(0.95 * n) - 1]")
                    .put(
                        "heap",
                        "Runtime.totalMemory - Runtime.freeMemory after forced GC; baseline is taken after the initial model is retained with Room and collector alive, and the after sample is taken with the final model retained while Room and collector are still alive; only then are collector/database closed"
                    )
                    .put(
                        "mistakeProjection",
                        "ChildMistakeListScreen defaults mirrored benchmark-locally: selectedSubject=null, showOnlyUnmastered=false, showOnlyDue=false, searchQuery=\"\"; the full filter pass runs and its filtered list is retained"
                    )
                    .put("isolation", "fresh file-backed database per dataset; deleted after measurement")
            )
            .put(
                "thresholds",
                JSONObject()
                    .put("p95Ms", P95_THRESHOLD_MS)
                    .put("retainedHeapDeltaBytes", RETAINED_HEAP_THRESHOLD_BYTES)
                    .put("retainedHeapDeltaMiB", RETAINED_HEAP_THRESHOLD_BYTES / (1024.0 * 1024.0))
                    .put("rule", "CONFIRMED if p95 > 100 ms OR retained heap delta > 64 MiB on Pixel 5 / API 30; otherwise NOT_REPRODUCED")
            )
            .put(
                "verdictPolicy",
                JSONObject()
                    .put("CONFIRMED", "p95 > 100 ms OR retained heap delta > 64 MiB on the reference device")
                    .put("NOT_REPRODUCED", "both thresholds are not exceeded on the reference device")
                    .put("ENVIRONMENT_BLOCKED", "reference-device assumption or device unavailability only")
                    .put("ERROR", "connected-device benchmark execution or validation failure")
            )
            .put("datasets", datasets)
            .put(
                "overall",
                JSONObject()
                    .put("datasetCount", results.size)
                    .put("maxP95Ms", maxP95 ?: JSONObject.NULL)
                    .put("maxRetainedHeapDeltaBytes", maxHeapDelta ?: JSONObject.NULL)
                    .put("verdict", overallVerdict)
            )
    }

    private fun writeReport(context: Context, report: JSONObject) {
        val json = report.toString(2)
        val external = context.getExternalFilesDir(null)
        val directory = external ?: context.cacheDir
        val output = File(directory, "list-flow-benchmark.json")
        output.parentFile?.mkdirs()
        output.writeText(json, Charsets.UTF_8)
        Log.i(TAG, "BENCHMARK_JSON_PATH=${output.absolutePath}")
        json.chunked(3_000).forEach { Log.i(TAG, it) }
        println("BENCHMARK_JSON_PATH=${output.absolutePath}")
        println(json)
    }

    private fun forceGc() {
        repeat(3) {
            Runtime.getRuntime().gc()
            @Suppress("DEPRECATION")
            System.runFinalization()
            Thread.sleep(100L)
        }
    }

    private fun retainedHeapBytes(): Long =
        Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

    private suspend fun seedPoints(db: AppDatabase, count: Int) {
        roomTransactionRunner(db).runInTransaction {
            repeat(count) { index -> insertPoint(db, index) }
        }
    }

    private suspend fun insertPoint(db: AppDatabase, index: Int) {
        val timestamp = BASE_TIME_MS + index * 60_000L
        db.pointDao().insertPointRecord(
            PointRecordEntity(
                points = if (index % 5 == 0) -10 else 10,
                type = if (index % 5 == 0) PointRecordType.REWARD_EXCHANGE else PointRecordType.ADJUSTMENT,
                note = "benchmark-point-$index",
                date = timestamp,
                weekStart = timestamp
            )
        )
    }

    private suspend fun seedMistakes(db: AppDatabase, count: Int) {
        roomTransactionRunner(db).runInTransaction {
            repeat(count) { index -> insertMistake(db, index) }
        }
    }

    private suspend fun insertMistake(db: AppDatabase, index: Int) {
        db.mistakeDao().insert(
            MistakeEntity(
                subject = MistakeSubject.entries[index % MistakeSubject.entries.size],
                title = "Benchmark mistake $index",
                content = "Question content $index",
                wrongReason = "Reason $index",
                correctMethod = "Method $index",
                isMastered = index % 4 == 0,
                createdAt = BASE_TIME_MS + index * 60_000L
            )
        )
    }

    private suspend fun seedFocus(db: AppDatabase, count: Int) {
        roomTransactionRunner(db).runInTransaction {
            repeat(count) { index -> insertFocus(db, index) }
        }
    }

    private suspend fun insertFocus(db: AppDatabase, index: Int) {
        val timestamp = BASE_TIME_MS + index * 60_000L
        db.focusDao().insertSession(
            FocusSessionEntity(
                durationMinutes = 15 + index % 4 * 5,
                taskTitle = "Benchmark focus $index",
                completedAt = timestamp,
                date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
            )
        )
    }

    private data class PointScreenModel(
        val records: List<PointRecordEntity>,
        val totalEarned: Int,
        val grouped: Map<Int, List<PointRecordEntity>>
    ) {
        companion object {
            fun from(records: List<PointRecordEntity>): PointScreenModel {
                val summary = summarizePointRecords(records)
                return PointScreenModel(records, summary.totalEarned, summary.grouped)
            }
        }
    }

    private data class MistakeScreenModel(
        val records: List<MistakeEntity>,
        val filtered: List<MistakeEntity>,
        val dueForReview: List<MistakeEntity>,
        val weakestSubject: MistakeSubject?,
        val reviewForecast: List<Int>
    ) {
        companion object {
            fun from(records: List<MistakeEntity>): MistakeScreenModel {
                val stats = deriveMistakeListStats(records)
                return MistakeScreenModel(records, records, stats.dueForReview, stats.weakestSubject, stats.reviewForecast)
            }
        }
    }

    private data class FocusScreenModel(
        val records: List<FocusSessionEntity>,
        val goldenHour: Int?
    ) {
        companion object {
            fun from(records: List<FocusSessionEntity>): FocusScreenModel {
                val goldenHour = if (records.isEmpty()) null else {
                    records.groupBy { session ->
                        Calendar.getInstance().apply { timeInMillis = session.completedAt }
                            .get(Calendar.HOUR_OF_DAY)
                    }.mapValues { entry -> entry.value.sumOf { it.durationMinutes } }
                        .maxByOrNull { it.value }?.key
                }
                return FocusScreenModel(records, goldenHour)
            }
        }
    }

    private companion object {
        const val TAG = "ListFlowBenchmark"
        const val EMISSION_TIMEOUT_MS = 30_000L
        const val WARMUP_INSERT_COUNT = 2
        const val P95_THRESHOLD_MS = 100.0
        const val RETAINED_HEAP_THRESHOLD_BYTES = 64L * 1024L * 1024L
        const val BASE_TIME_MS = 1_700_000_000_000L
    }
}
