package com.hstudy.app.data.backup

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.hstudy.app.data.local.APP_SCHEMA_VERSION
import com.hstudy.app.platform.systemFileSystem
import okio.FileSystem
import okio.Path

/** Exact v14 column contract, mirrored by SqliteBackupValidatorContractTest against 14.json. */
internal data class BackupColumnContract(
    val name: String,
    val affinity: String,
    val notNull: Boolean = true,
    val defaultValue: String? = null,
)

internal data class BackupForeignKeyContract(
    val columns: List<String>,
    val referencedTable: String,
    val referencedColumns: List<String>,
    val onUpdate: String,
    val onDelete: String,
)

private fun column(
    name: String,
    affinity: String,
    notNull: Boolean = true,
    defaultValue: String? = null,
): BackupColumnContract = BackupColumnContract(name, affinity, notNull, defaultValue)

internal val BACKUP_EXPECTED_COLUMNS: Map<String, List<BackupColumnContract>> = mapOf(
    "tasks" to listOf(
        column("id", "INTEGER"), column("title", "TEXT"), column("description", "TEXT"),
        column("category", "TEXT"), column("points", "INTEGER"), column("bonusPoints", "INTEGER"),
        column("frequency", "TEXT"), column("difficulty", "INTEGER"), column("isMandatory", "INTEGER"),
        column("isActive", "INTEGER"), column("sortOrder", "INTEGER"), column("createdAt", "INTEGER"),
    ),
    "point_records" to listOf(
        column("id", "INTEGER"), column("taskId", "INTEGER", notNull = false),
        column("points", "INTEGER"), column("type", "TEXT"), column("note", "TEXT"),
        column("date", "INTEGER"), column("weekStart", "INTEGER"),
        column("approvalId", "INTEGER", notNull = false),
    ),
    "task_completions" to listOf(
        column("id", "INTEGER"), column("taskId", "INTEGER"), column("completedAt", "INTEGER"),
        column("pointsEarned", "INTEGER"), column("note", "TEXT"), column("date", "TEXT"),
        column("frequencyPeriod", "TEXT", notNull = false),
    ),
    "rewards" to listOf(
        column("id", "INTEGER"), column("name", "TEXT"), column("description", "TEXT"),
        column("pointsCost", "INTEGER"), column("iconEmoji", "TEXT"),
        column("category", "TEXT", defaultValue = "'其他'"), column("isActive", "INTEGER"),
        column("sortOrder", "INTEGER"), column("createdAt", "INTEGER"),
        column("presetKey", "TEXT", notNull = false),
    ),
    "reward_exchanges" to listOf(
        column("id", "INTEGER"), column("rewardId", "INTEGER"), column("pointsSpent", "INTEGER"),
        column("exchangedAt", "INTEGER"), column("note", "TEXT"),
    ),
    "mistakes" to listOf(
        column("id", "INTEGER"), column("subject", "TEXT"), column("title", "TEXT"),
        column("content", "TEXT"), column("wrongReason", "TEXT"), column("correctMethod", "TEXT"),
        column("isMastered", "INTEGER"), column("photoPath", "TEXT"), column("createdAt", "INTEGER"),
        column("masteredAt", "INTEGER", notNull = false), column("lastReviewAt", "INTEGER"),
        column("reviewCount", "INTEGER"), column("variationNote", "TEXT"),
        column("hasVariation", "INTEGER"), column("errorType", "TEXT"),
    ),
    "mistake_reviews" to listOf(
        column("id", "INTEGER"), column("mistakeId", "INTEGER"), column("reviewedAt", "INTEGER"),
        column("earnedPoints", "INTEGER"), column("note", "TEXT"),
    ),
    "focus_sessions" to listOf(
        column("id", "INTEGER"), column("durationMinutes", "INTEGER"), column("taskTitle", "TEXT"),
        column("completedAt", "INTEGER"), column("earnedPoints", "INTEGER"), column("date", "TEXT"),
        column("sessionKey", "TEXT", notNull = false),
    ),
    "daily_plans" to listOf(
        column("id", "INTEGER"), column("date", "TEXT"), column("startHour", "INTEGER"),
        column("startMinute", "INTEGER"), column("endHour", "INTEGER"), column("endMinute", "INTEGER"),
        column("taskTitle", "TEXT"), column("isCompleted", "INTEGER"), column("createdAt", "INTEGER"),
    ),
    "completion_proofs" to listOf(
        column("id", "INTEGER"), column("taskId", "INTEGER"), column("photoPath", "TEXT"),
        column("note", "TEXT"), column("status", "TEXT"), column("submittedAt", "INTEGER"),
        column("reviewedAt", "INTEGER", notNull = false), column("pointsAwarded", "INTEGER"),
        column("date", "TEXT"),
    ),
    "habit_challenges" to listOf(
        column("id", "INTEGER"), column("title", "TEXT"), column("description", "TEXT"),
        column("startDate", "TEXT"), column("totalDays", "INTEGER"), column("completedDays", "INTEGER"),
        column("isActive", "INTEGER"), column("isCompleted", "INTEGER"), column("emoji", "TEXT"),
        column("dailyPoints", "INTEGER"), column("lastCheckInDate", "TEXT", defaultValue = "''"),
        column("createdAt", "INTEGER"),
    ),
    "achievements" to listOf(
        column("id", "INTEGER"), column("type", "TEXT"), column("title", "TEXT"),
        column("description", "TEXT"), column("emoji", "TEXT"), column("unlockedAt", "INTEGER"),
        column("canonicalType", "TEXT", notNull = false),
    ),
    "daily_assignments" to listOf(
        column("id", "INTEGER"), column("title", "TEXT"), column("description", "TEXT"),
        column("points", "INTEGER"), column("assignedDate", "TEXT"), column("isCompleted", "INTEGER"),
        column("completedAt", "INTEGER", notNull = false), column("parentNote", "TEXT"),
        column("createdAt", "INTEGER"),
    ),
    "wish_goals" to listOf(
        column("id", "INTEGER"), column("name", "TEXT"), column("description", "TEXT"),
        column("targetPoints", "INTEGER"), column("savedPoints", "INTEGER"), column("iconEmoji", "TEXT"),
        column("isActive", "INTEGER"), column("createdAt", "INTEGER"),
        column("completedAt", "INTEGER", notNull = false),
    ),
    "daily_schedules" to listOf(
        column("id", "INTEGER"), column("time", "TEXT"), column("title", "TEXT"), column("emoji", "TEXT"),
        column("linkedTaskId", "INTEGER", notNull = false), column("isEnabled", "INTEGER"),
        column("repeatDays", "TEXT"), column("sortOrder", "INTEGER"),
    ),
    "point_approvals" to listOf(
        column("id", "INTEGER"), column("actionType", "TEXT"), column("description", "TEXT"),
        column("requestedPoints", "INTEGER"), column("status", "TEXT"), column("requestedAt", "INTEGER"),
        column("reviewedAt", "INTEGER"), column("taskId", "INTEGER", notNull = false),
        column("bizKey", "TEXT"), column("awardedRecordId", "INTEGER", notNull = false),
        column("date", "TEXT"), column("activeBizKey", "TEXT", notNull = false),
        column("frequencyPeriod", "TEXT", notNull = false),
    ),
    "daily_task_snapshots" to listOf(
        column("date", "TEXT"), column("taskIdsJson", "TEXT"), column("totalCount", "INTEGER"),
        column("createdAt", "INTEGER"),
    ),
    "pet_scene_purchases" to listOf(
        column("sceneKey", "TEXT"), column("purchasedAt", "INTEGER"),
    ),
    "mandatory_penalty_runs" to listOf(
        column("periodKey", "TEXT"), column("processedAt", "INTEGER"), column("deductedPoints", "INTEGER"),
    ),
)

internal val BACKUP_EXPECTED_PRIMARY_KEYS: Map<String, List<String>> = mapOf(
    "tasks" to listOf("id"),
    "point_records" to listOf("id"),
    "task_completions" to listOf("id"),
    "rewards" to listOf("id"),
    "reward_exchanges" to listOf("id"),
    "mistakes" to listOf("id"),
    "mistake_reviews" to listOf("id"),
    "focus_sessions" to listOf("id"),
    "daily_plans" to listOf("id"),
    "completion_proofs" to listOf("id"),
    "habit_challenges" to listOf("id"),
    "achievements" to listOf("id"),
    "daily_assignments" to listOf("id"),
    "wish_goals" to listOf("id"),
    "daily_schedules" to listOf("id"),
    "point_approvals" to listOf("id"),
    "daily_task_snapshots" to listOf("date"),
    "pet_scene_purchases" to listOf("sceneKey"),
    "mandatory_penalty_runs" to listOf("periodKey"),
)

/** v14 has no foreign keys; any foreign_key_list row is therefore unexpected. */
internal val BACKUP_EXPECTED_FOREIGN_KEYS: Map<String, List<BackupForeignKeyContract>> =
    BACKUP_EXPECTED_COLUMNS.keys.associateWith { emptyList() }

/** index name -> (owning table, exact ordered indexed columns). */
internal val BACKUP_REQUIRED_UNIQUE_INDEXES: Map<String, Pair<String, List<String>>> = mapOf(
    "index_point_approvals_activeBizKey" to ("point_approvals" to listOf("activeBizKey")),
    "index_achievements_canonicalType" to ("achievements" to listOf("canonicalType")),
    "index_rewards_presetKey" to ("rewards" to listOf("presetKey")),
    "index_focus_sessions_sessionKey" to ("focus_sessions" to listOf("sessionKey")),
)

private val BACKUP_METADATA_TABLES = setOf("room_master_table", "android_metadata", "sqlite_sequence")
private val BACKUP_EXPECTED_SQLITE_SEQUENCE_COLUMNS = listOf(
    BackupColumnContract(name = "name", affinity = "", notNull = false),
    BackupColumnContract(name = "seq", affinity = "", notNull = false),
)
private const val SQLITE_AUTOINDEX_PREFIX = "sqlite_autoindex_"

/** Type-aware exact allowlist for every sqlite_master row; names alone never grant an exemption. */
internal fun isAllowedBackupSchemaObject(
    type: String,
    name: String,
    tableName: String,
    sql: String?,
): Boolean = when (type) {
    "table" -> name in BACKUP_EXPECTED_COLUMNS || name in BACKUP_METADATA_TABLES
    "index" -> {
        val requiredOwner = BACKUP_REQUIRED_UNIQUE_INDEXES[name]?.first
        when {
            requiredOwner != null -> sql != null && tableName == requiredOwner
            sql == null && name.startsWith(SQLITE_AUTOINDEX_PREFIX) &&
                name.length > SQLITE_AUTOINDEX_PREFIX.length ->
                tableName in BACKUP_EXPECTED_COLUMNS
            else -> false
        }
    }
    else -> false
}

/** Validates an untrusted SQLite backup before it can replace a known-good file. */
open class SqliteBackupValidator(
    private val fs: FileSystem = systemFileSystem,
    private val expectedSchemaVersion: Int = APP_SCHEMA_VERSION,
) {
    open fun validate(path: Path): Boolean = runCatching {
        if (!fs.exists(path)) return false
        val header = fs.read(path) {
            val bytes = readByteString(SQLITE_HEADER.size.toLong())
            bytes.toByteArray()
        }
        if (!header.contentEquals(SQLITE_HEADER)) return false

        BundledSQLiteDriver().open(path.toString()).use { connection ->
            val version = connection.prepare("PRAGMA user_version").use { statement ->
                check(statement.step()) { "missing user_version" }
                statement.getLong(0)
            }
            if (version != expectedSchemaVersion.toLong()) return false

            connection.prepare("SELECT type, name, tbl_name, sql FROM sqlite_master").use { statement ->
                val tables = mutableSetOf<String>()
                val metadataTables = mutableSetOf<String>()
                val indexes = mutableSetOf<String>()
                while (statement.step()) {
                    val type = statement.getText(0)
                    val name = statement.getText(1)
                    val tableName = statement.getText(2)
                    val sql = if (statement.isNull(3)) null else statement.getText(3)
                    if (!isAllowedBackupSchemaObject(type, name, tableName, sql)) return false
                    when (type) {
                        "table" -> if (name in BACKUP_METADATA_TABLES) metadataTables += name else tables += name
                        "index" -> if (name in BACKUP_REQUIRED_UNIQUE_INDEXES) indexes += name
                        else -> return false
                    }
                }
                if (tables != BACKUP_EXPECTED_COLUMNS.keys) return false
                if ("sqlite_sequence" !in metadataTables) return false
                if (indexes != BACKUP_REQUIRED_UNIQUE_INDEXES.keys) return false
            }

            val sequenceColumns = connection.prepare("PRAGMA table_xinfo(`sqlite_sequence`)").use { statement ->
                buildList {
                    while (statement.step()) {
                        if (statement.getLong(6) != 0L || statement.getLong(5) != 0L) return false
                        add(
                            BackupColumnContract(
                                name = statement.getText(1),
                                affinity = statement.getText(2).uppercase(),
                                notNull = statement.getLong(3) != 0L,
                                defaultValue = if (statement.isNull(4)) null else statement.getText(4),
                            )
                        )
                    }
                }
            }
            if (sequenceColumns != BACKUP_EXPECTED_SQLITE_SEQUENCE_COLUMNS) return false
            if (readForeignKeys(connection, "sqlite_sequence").isNotEmpty()) return false

            BACKUP_EXPECTED_COLUMNS.forEach { (table, expectedColumns) ->
                val primaryKeyPositions = mutableListOf<Pair<Int, String>>()
                val actualColumns = connection.prepare("PRAGMA table_xinfo(`$table`)").use { statement ->
                    buildList {
                        while (statement.step()) {
                            // table_xinfo hidden: 0=normal, 1=hidden, 2/3=generated virtual/stored.
                            if (statement.getLong(6) != 0L) return false
                            val name = statement.getText(1)
                            val primaryKeyPosition = statement.getLong(5).toInt()
                            if (primaryKeyPosition > 0) primaryKeyPositions += primaryKeyPosition to name
                            add(
                                BackupColumnContract(
                                    name = name,
                                    affinity = statement.getText(2).uppercase(),
                                    notNull = statement.getLong(3) != 0L,
                                    defaultValue = if (statement.isNull(4)) null else statement.getText(4),
                                )
                            )
                        }
                    }
                }
                if (actualColumns != expectedColumns) return false
                val actualPrimaryKey = primaryKeyPositions.sortedBy { it.first }.map { it.second }
                if (actualPrimaryKey != BACKUP_EXPECTED_PRIMARY_KEYS[table]) return false
                if (readForeignKeys(connection, table).canonicalized() !=
                    BACKUP_EXPECTED_FOREIGN_KEYS[table].orEmpty().canonicalized()
                ) return false
            }

            BACKUP_REQUIRED_UNIQUE_INDEXES.forEach { (indexName, requirement) ->
                val (table, expectedColumns) = requirement
                val unique = connection.prepare("PRAGMA index_list(`$table`)").use { statement ->
                    var found = false
                    while (statement.step()) {
                        if (statement.getText(1) == indexName) {
                            found = statement.getLong(2) != 0L &&
                                statement.getText(3) == "c" &&
                                statement.getLong(4) == 0L
                        }
                    }
                    found
                }
                if (!unique) return false
                val actualColumns = connection.prepare("PRAGMA index_info(`$indexName`)").use { statement ->
                    buildList {
                        while (statement.step()) add(statement.getText(2))
                    }
                }
                if (actualColumns != expectedColumns) return false
            }

            connection.prepare("PRAGMA integrity_check").use { statement ->
                if (!statement.step() || statement.getText(0) != "ok") return false
                // integrity_check returns exactly one row ("ok") for a healthy database.
                if (statement.step()) return false
            }
        }
        true
    }.getOrDefault(false)

    private data class ForeignKeyRow(
        val id: Long,
        val sequence: Long,
        val table: String,
        val from: String,
        val to: String,
        val onUpdate: String,
        val onDelete: String,
    )

    private fun readForeignKeys(
        connection: SQLiteConnection,
        table: String,
    ): List<BackupForeignKeyContract> {
        val rows = connection.prepare("PRAGMA foreign_key_list(`$table`)").use { statement ->
            buildList {
                while (statement.step()) {
                    add(
                        ForeignKeyRow(
                            id = statement.getLong(0),
                            sequence = statement.getLong(1),
                            table = statement.getText(2),
                            from = statement.getText(3),
                            to = statement.getText(4),
                            onUpdate = statement.getText(5),
                            onDelete = statement.getText(6),
                        )
                    )
                }
            }
        }
        return rows.groupBy { it.id }.values.map { group ->
            val ordered = group.sortedBy { it.sequence }
            BackupForeignKeyContract(
                columns = ordered.map { it.from },
                referencedTable = ordered.first().table,
                referencedColumns = ordered.map { it.to },
                onUpdate = ordered.first().onUpdate,
                onDelete = ordered.first().onDelete,
            )
        }
    }

    private fun List<BackupForeignKeyContract>.canonicalized(): List<BackupForeignKeyContract> =
        sortedWith(
            compareBy<BackupForeignKeyContract> { it.referencedTable }
                .thenBy { it.columns.joinToString(",") }
                .thenBy { it.referencedColumns.joinToString(",") }
                .thenBy { it.onUpdate }
                .thenBy { it.onDelete }
        )

    private companion object {
        val SQLITE_HEADER = "SQLite format 3\u0000".encodeToByteArray()
    }
}
