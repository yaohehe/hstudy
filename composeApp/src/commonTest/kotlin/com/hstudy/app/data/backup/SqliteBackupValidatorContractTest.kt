package com.hstudy.app.data.backup

import com.hstudy.app.data.local.APP_SCHEMA_VERSION
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SqliteBackupValidatorContractTest {
    @Test
    fun validatorContractMatchesExportedUnreleasedV14Schema() {
        val relative = "schemas/com.hstudy.app.data.local.AppDatabase/$APP_SCHEMA_VERSION.json"
        val path = listOf(relative, "../composeApp/$relative")
            .map { it.toPath() }
            .first { FileSystem.SYSTEM.exists(it) }
        val schema = Json.parseToJsonElement(FileSystem.SYSTEM.read(path) { readUtf8() })
            .jsonObject["database"]!!.jsonObject
        assertEquals(APP_SCHEMA_VERSION, schema["version"]!!.jsonPrimitive.content.toInt())

        val columns = schema["entities"]!!.jsonArray.associate { entity ->
            val obj = entity.jsonObject
            obj["tableName"]!!.jsonPrimitive.content to obj["fields"]!!.jsonArray.map {
                val field = it.jsonObject
                BackupColumnContract(
                    name = field["columnName"]!!.jsonPrimitive.content,
                    affinity = field["affinity"]!!.jsonPrimitive.content,
                    notNull = field["notNull"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                    defaultValue = field["defaultValue"]?.jsonPrimitive?.content,
                )
            }
        }
        assertEquals(columns, BACKUP_EXPECTED_COLUMNS)

        val primaryKeys = schema["entities"]!!.jsonArray.associate { entity ->
            val obj = entity.jsonObject
            obj["tableName"]!!.jsonPrimitive.content to
                obj["primaryKey"]!!.jsonObject["columnNames"]!!.jsonArray.map { it.jsonPrimitive.content }
        }
        assertEquals(primaryKeys, BACKUP_EXPECTED_PRIMARY_KEYS)

        val foreignKeys = schema["entities"]!!.jsonArray.associate { entity ->
            val obj = entity.jsonObject
            obj["tableName"]!!.jsonPrimitive.content to (obj["foreignKeys"]?.jsonArray.orEmpty()).map { raw ->
                val foreignKey = raw.jsonObject
                BackupForeignKeyContract(
                    columns = foreignKey["columns"]!!.jsonArray.map { it.jsonPrimitive.content },
                    referencedTable = foreignKey["table"]!!.jsonPrimitive.content,
                    referencedColumns = foreignKey["referencedColumns"]!!.jsonArray.map { it.jsonPrimitive.content },
                    onUpdate = foreignKey["onUpdate"]!!.jsonPrimitive.content,
                    onDelete = foreignKey["onDelete"]!!.jsonPrimitive.content,
                )
            }
        }
        assertEquals(foreignKeys, BACKUP_EXPECTED_FOREIGN_KEYS)

        val indexes = schema["entities"]!!.jsonArray.flatMap { entity ->
            val entityObject = entity.jsonObject
            val rawIndices = entityObject["indices"]?.jsonArray ?: return@flatMap emptyList()
            rawIndices.map { rawIndex ->
                val index = rawIndex.jsonObject
                Triple(
                    index["name"]!!.jsonPrimitive.content,
                    entityObject["tableName"]!!.jsonPrimitive.content,
                    index["columnNames"]!!.jsonArray.map { it.jsonPrimitive.content },
                )
            }
        }.associate { (name, table, indexColumns) -> name to (table to indexColumns) }
        assertEquals(indexes, BACKUP_REQUIRED_UNIQUE_INDEXES)
    }

    @Test
    fun schemaObjectPolicyAllowsOnlyExactTablesIndexesAndMetadataTables() {
        val allowed = listOf(
            SchemaObject("table", "tasks", "tasks", "CREATE TABLE tasks (id INTEGER PRIMARY KEY)"),
            SchemaObject("table", "room_master_table", "room_master_table", "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY)"),
            SchemaObject("table", "android_metadata", "android_metadata", "CREATE TABLE android_metadata (key TEXT PRIMARY KEY)"),
            SchemaObject("table", "sqlite_sequence", "sqlite_sequence", "CREATE TABLE sqlite_sequence(name,seq)"),
            SchemaObject(
                "index",
                "index_point_approvals_activeBizKey",
                "point_approvals",
                "CREATE UNIQUE INDEX index_point_approvals_activeBizKey ON point_approvals (activeBizKey)",
            ),
            SchemaObject("index", "sqlite_autoindex_tasks_1", "tasks", null),
        )
        allowed.forEach { candidate ->
            assertTrue(candidate.isAllowed(), "must allow $candidate")
        }

        val rejected = listOf(
            SchemaObject("trigger", "sqliteXsteal_points", "point_records", "CREATE TRIGGER sqliteXsteal_points AFTER INSERT ON point_records BEGIN SELECT 1; END"),
            SchemaObject("trigger", "steal_points", "point_records", "CREATE TRIGGER steal_points AFTER INSERT ON point_records BEGIN SELECT 1; END"),
            SchemaObject("view", "sqliteXview", "tasks", "CREATE VIEW sqliteXview AS SELECT * FROM tasks"),
            SchemaObject("view", "room_master_table", "tasks", "CREATE VIEW room_master_table AS SELECT * FROM tasks"),
            SchemaObject("view", "android_metadata", "tasks", "CREATE VIEW android_metadata AS SELECT * FROM tasks"),
            SchemaObject("view", "sqlite_sequence", "tasks", "CREATE VIEW sqlite_sequence AS SELECT * FROM tasks"),
            SchemaObject("table", "sqliteXhidden", "sqliteXhidden", "CREATE TABLE sqliteXhidden (payload TEXT)"),
            SchemaObject("table", "sqlite_sequence_extra", "sqlite_sequence_extra", "CREATE TABLE sqlite_sequence_extra (name TEXT, seq INTEGER)"),
            SchemaObject("table", "attacker_data", "attacker_data", "CREATE TABLE attacker_data (payload TEXT)"),
            SchemaObject("table", "index_point_approvals_activeBizKey", "index_point_approvals_activeBizKey", "CREATE TABLE index_point_approvals_activeBizKey (payload TEXT)"),
            SchemaObject("index", "room_master_table", "tasks", "CREATE INDEX room_master_table ON tasks (title)"),
            SchemaObject("index", "android_metadata", "tasks", "CREATE INDEX android_metadata ON tasks (title)"),
            SchemaObject("index", "index_point_approvals_activeBizKey", "tasks", "CREATE UNIQUE INDEX index_point_approvals_activeBizKey ON tasks (title)"),
            SchemaObject("index", "index_point_approvals_activeBizKey", "point_approvals", null),
            SchemaObject("index", "sqliteXindex", "tasks", "CREATE INDEX sqliteXindex ON tasks (title)"),
            SchemaObject("index", "sqlite_autoindex_", "tasks", null),
            SchemaObject("index", "sqlite_autoindex_tasks_1", "tasks", "CREATE INDEX sqlite_autoindex_tasks_1 ON tasks (title)"),
            SchemaObject("index", "sqlite_autoindex_attacker_1", "attacker_data", null),
            SchemaObject("unknown", "tasks", "tasks", null),
        )
        rejected.forEach { candidate ->
            assertFalse(candidate.isAllowed(), "must reject $candidate")
        }
    }

    private data class SchemaObject(
        val type: String,
        val name: String,
        val tableName: String,
        val sql: String?,
    ) {
        fun isAllowed(): Boolean = isAllowedBackupSchemaObject(type, name, tableName, sql)
    }
}
