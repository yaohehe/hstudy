package com.hstudy.app.data.local

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 复现 iOS 全新安装时的误报：dbVersion=12、point_approvals 行数为 0，
 * 却报 "missing columns [taskId, bizKey, awardedRecordId, date]"。
 *
 * 证明范围（诚实说明）：commonTest 跑在 Android 本地 JVM 单测上，sqlite-bundled 只有
 * android(.so)/iOS 变体，桌面 JVM 加载不了原生库，所以这里用 Fake 连接建模两条真实
 * SQLite 语义：① prepare 阶段就校验列名，未知列抛错；② 空结果集 step() 返回 false。
 * 被测函数只依赖这两条语义，Fake 不模拟其他 SQL 行为。
 */
class MigrationVerifyTest {

    @Test
    fun emptyTableWithV12ColumnsReportsNothingMissing() {
        val conn = FakeConnection(columns = v11Columns + v12Columns, rowCount = 0)
        assertEquals(emptyList(), pointApprovalMissingColumns(conn))
    }

    @Test
    fun preV12TableReportsAllFourMissingColumns() {
        val conn = FakeConnection(columns = v11Columns, rowCount = 0)
        assertEquals(v12Columns, pointApprovalMissingColumns(conn))
    }

    @Test
    fun populatedTableWithV12ColumnsReportsNothingMissing() {
        val conn = FakeConnection(columns = v11Columns + v12Columns, rowCount = 3)
        assertEquals(emptyList(), pointApprovalMissingColumns(conn))
    }

    @Test
    fun partiallyMigratedTableReportsOnlyAbsentColumns() {
        val conn = FakeConnection(columns = v11Columns + listOf("taskId", "bizKey"), rowCount = 0)
        assertEquals(listOf("awardedRecordId", "date"), pointApprovalMissingColumns(conn))
    }

    /** Stock Android API 26 ships SQLite 3.18, which rejects aliases on UPDATE targets. */
    @Test
    fun v14MigrationDoesNotUseUnsupportedUpdateTargetAliases() {
        val updates = recordV14Updates()

        assertTrue(updates.isNotEmpty())
        val targetAlias = Regex("""(?i)\bUPDATE\s+(?:`[^`]+`|[A-Za-z_][A-Za-z0-9_]*)\s+AS\b""")
        assertFalse(
            updates.any { targetAlias.containsMatchIn(it) },
            "MIGRATION_13_14 must remain compatible with SQLite 3.18: ${updates.joinToString("\n")}"
        )
    }

    /** SQLite 3.18 supports correlated subqueries, but not UPDATE target aliases or UPDATE FROM. */
    @Test
    fun v14FrequencyBackfillUsesCompatibleCorrelatedSubqueries() {
        val updates = recordV14Updates()
        val frequencyUpdates = updates.filter { it.contains("SET frequencyPeriod") }
        assertEquals(2, frequencyUpdates.size)
        val updateFrom = Regex("""(?is)\bSET\s+frequencyPeriod\s*=\s*CASE\b.*?\bEND\s+FROM\b""")
        frequencyUpdates.forEach { sql ->
            assertFalse(updateFrom.containsMatchIn(sql), "SQLite 3.18 rejects UPDATE FROM: $sql")
            assertTrue(sql.contains("SELECT frequency FROM tasks"), "Backfill must read task frequency: $sql")
            assertTrue(sql.contains("task_completions.taskId") || sql.contains("point_approvals.taskId"))
        }
    }

    /** A same-name custom reward must not be keyed merely because its name is a preset name. */
    @Test
    fun v14RewardCanonicalizationUsesTheCompletePresetSignature() {
        val rewardUpdate = recordV14Updates().single { it.trimStart().startsWith("UPDATE rewards") }

        listOf("name", "description", "pointsCost", "iconEmoji", "category", "sortOrder").forEach { field ->
            assertTrue(field in rewardUpdate, "Reward canonicalization must compare $field: $rewardUpdate")
        }
    }

    @Test
    fun v14CanonicalApprovalBackfillExcludesTerminalOnlyGroups() {
        val update = recordV14Updates().single { it.trimStart().startsWith("UPDATE point_approvals") && it.contains("SET activeBizKey") }
        val eligible = Regex("(?is)status\\s+IN\\s*\\(\\s*'PENDING'\\s*,\\s*'APPROVED'\\s*\\)")
        assertEquals(2, eligible.findAll(update).count(), "both candidate and canonical rows must be active-status only: $update")
    }

    @Test
    fun v14AddsNullableUniqueFocusSessionKeyWithoutBackfillingHistory() {
        val statements = recordV14Statements()

        assertTrue("ALTER TABLE `focus_sessions` ADD COLUMN `sessionKey` TEXT" in statements)
        assertTrue(
            statements.any {
                it.startsWith("CREATE UNIQUE INDEX `index_focus_sessions_sessionKey`") &&
                    it.endsWith("ON `focus_sessions` (`sessionKey`)")
            },
            "Focus session keys must be unique: ${statements.joinToString("\n")}",
        )
        assertFalse(
            statements.any { it.trimStart().startsWith("UPDATE focus_sessions") },
            "Historical focus rows must remain unkeyed: ${statements.joinToString("\n")}",
        )
    }

    private fun recordV14Updates(): List<String> =
        recordV14Statements().filter { it.trimStart().startsWith("UPDATE ") }

    private fun recordV14Statements(): List<String> {
        val connection = RecordingConnection()
        Migrations.MIGRATION_13_14.migrate(connection)
        return connection.statements
    }

    private class RecordingConnection : SQLiteConnection {
        val statements = mutableListOf<String>()

        override fun prepare(sql: String): SQLiteStatement {
            statements += sql
            return FakeStatement(emptyList())
        }

        override fun close() {}
    }

    private val v11Columns = listOf("id", "actionType", "description", "requestedPoints", "status", "requestedAt", "reviewedAt")
    private val v12Columns = listOf("taskId", "bizKey", "awardedRecordId", "date")

    private class FakeConnection(
        private val columns: List<String>,
        private val rowCount: Int,
    ) : SQLiteConnection {
        override fun prepare(sql: String): SQLiteStatement {
            val select = Regex("SELECT `(\\w+)` FROM `point_approvals` LIMIT 1").matchEntire(sql)
            if (select != null) {
                val col = select.groupValues[1]
                check(col in columns) { "no such column: $col" }
                return FakeStatement(List(rowCount) { listOf(col) })
            }
            if (sql == "PRAGMA table_info(`point_approvals`)") {
                return FakeStatement(columns.mapIndexed { i, name -> listOf(i.toString(), name) })
            }
            throw IllegalArgumentException("unexpected sql: $sql")
        }

        override fun close() {}
    }

    private class FakeStatement(private val rows: List<List<String>>) : SQLiteStatement {
        private var index = -1

        override fun step(): Boolean {
            if (index + 1 >= rows.size) return false
            index++
            return true
        }

        override fun getText(index: Int): String = rows[this.index][index]
        override fun getColumnName(index: Int): String = ""
        override fun getColumnCount(): Int = rows.firstOrNull()?.size ?: 0
        override fun reset() { index = -1 }
        override fun close() {}

        override fun bindBlob(index: Int, value: ByteArray) = unsupported()
        override fun bindDouble(index: Int, value: Double) = unsupported()
        override fun bindLong(index: Int, value: Long) = unsupported()
        override fun bindText(index: Int, value: String) = unsupported()
        override fun bindNull(index: Int) = unsupported()
        override fun getBlob(index: Int): ByteArray = unsupported()
        override fun getDouble(index: Int): Double = unsupported()
        override fun getLong(index: Int): Long = unsupported()
        override fun isNull(index: Int): Boolean = unsupported()
        override fun getColumnType(index: Int): Int = unsupported()
        override fun clearBindings() = unsupported()

        private fun unsupported(): Nothing = throw UnsupportedOperationException()
    }
}