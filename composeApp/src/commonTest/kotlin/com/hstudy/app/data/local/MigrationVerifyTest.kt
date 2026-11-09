package com.hstudy.app.data.local

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import kotlin.test.Test
import kotlin.test.assertEquals

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