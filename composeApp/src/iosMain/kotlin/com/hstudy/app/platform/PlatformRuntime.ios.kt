package com.hstudy.app.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okio.FileSystem

actual val systemFileSystem: FileSystem get() = FileSystem.SYSTEM

/**
 * iOS 没有 Dispatchers.IO：Room KMP 官方建议 native 侧用 [Dispatchers.Default]
 * （多线程 worker 池）承载数据库 IO；BundledSQLiteDriver 自身串行化写连接，
 * 因此与原 Android 版行为等价。
 */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default

/** iOS 无应用自重启 API（原 Android 版用 launch intent + Runtime.exit）；返回 false 由调用方提示手动重启 */
actual fun restartApp(): Boolean = false
