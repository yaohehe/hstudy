package com.hstudy.app.data.local

/**
 * iOS actual：**直通实现（不做事务）**——降级披露（验收文档 F-7 关联）。
 *
 * Room 2.7 KMP 的原生（common/native）klib 未提供 `withTransaction` 挂起事务 API
 * （该扩展仅 JVM/Android klib 可用，CI run 45045438877 实证），故 iOS 上批量审批/超期清理/
 * 撤回三入口的"中途异常整体回滚"原子性降级为直通：行为等同 v3.3.1（单条写各自落库，
 * 批量中途异常不回滚已写部分）。业务判定逻辑与写库顺序不变（红线），只失去事务壳。
 *
 * 回切路径：待 Room 在 common klib 提供原生事务 API 后，只改本文件的 actual 即可回切实事务，
 * commonMain 抽象与仓库层零改动。
 */
actual fun roomTransactionRunner(database: AppDatabase): DbTransactionRunner =
    DirectTransactionRunner
