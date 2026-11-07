package com.hstudy.app.data.local

import androidx.sqlite.SQLiteConnection

/**
 * Room/androidx.sqlite 的 KMP 版本里，`SQLiteConnection.execSQL` 只声明在 androidx.sqlite 的
 * nativeMain/jvmMain 共享源集（commonMain 元数据不可见，见 compileCommonMainKotlinMetadata），
 * 且原 Android 版 `SupportSQLiteDatabase.execSQL` 在 KMP 中整个类型都不再提供。
 *
 * 这里用官方推荐的 `prepare + step + close` 实现完全等价的语义：
 * 立即执行一条无结果集的 SQL（DDL / PRAGMA / VACUUM INTO），出错时同样抛 SQLiteException，
 * 迁移 SQL 文本本身与原 app 逐字节一致。
 */
internal fun SQLiteConnection.execSQL(sql: String) {
    val statement = prepare(sql)
    try {
        statement.step()
    } finally {
        statement.close()
    }
}
