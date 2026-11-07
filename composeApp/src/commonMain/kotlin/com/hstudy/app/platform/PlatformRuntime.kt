package com.hstudy.app.platform

import kotlinx.coroutines.CoroutineDispatcher
import okio.FileSystem

/**
 * 平台运行时补充能力（expect/actual）。
 *
 * 存在原因（不是设计偏好，是依赖库的源集限制）：
 * - okio 的 `FileSystem.SYSTEM` 声明在 okio 的 jvm/native 共享源集（systemFileSystemMain），
 *   不在其 commonMain 元数据里，commonMain 直接写 `FileSystem.SYSTEM` 过不了
 *   `compileCommonMainKotlinMetadata`；
 * - kotlinx-coroutines 的 `Dispatchers.IO` 同样只在 JVM/native 源集提供。
 * 因此统一由平台 actual 暴露，behavior 与原 Android 版一致（Android 仍是 Dispatchers.IO / SYSTEM）。
 */

/** 跨平台文件系统（Android/iOS 均为 okio 的 SYSTEM 实现） */
expect val systemFileSystem: FileSystem

/** 阻塞式 IO（数据库文件、备份文件）调度器：Android 为 Dispatchers.IO，iOS 为 Dispatchers.Default */
expect val ioDispatcher: CoroutineDispatcher

/**
 * 重启应用（对应原 Android 版 `BackupHelper.restartApp(context)`：launch intent + 结束进程）。
 * 备份恢复后必须重启，否则 [com.hstudy.app.AppGraph] 里缓存的仍是已关闭的旧数据库连接。
 *
 * @return true 表示已触发平台重启；iOS 无应用自重启 API，返回 false，调用方需提示用户手动重启。
 */
expect fun restartApp(): Boolean
