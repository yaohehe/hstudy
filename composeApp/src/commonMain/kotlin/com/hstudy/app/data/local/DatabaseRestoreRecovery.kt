package com.hstudy.app.data.local

import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath

/**
 * Filesystem journal for crash-safe database restore.
 *
 * PREPARED is atomically installed before any live file is moved. Startup recovery restores every
 * `.bak` after deleting partial live/WAL/SHM files; the operation is repeatable if recovery itself
 * crashes. COMMITTED is written only after the candidate has been reopened and validated, so later
 * cleanup retries preserve the candidate instead of rolling it back.
 */
internal object DatabaseRestoreRecovery {
    const val PREPARED = "PREPARED"
    const val COMMITTED = "COMMITTED"

    fun journalPath(liveDbPath: Path): Path = "$liveDbPath.restore.journal".toPath()

    fun writeJournal(fs: FileSystem, liveDbPath: Path, state: String) {
        require(state == PREPARED || state == COMMITTED) { "unknown restore journal state: $state" }
        val journal = journalPath(liveDbPath)
        val staged = "$journal.tmp".toPath()
        fs.write(staged) { writeUtf8(state) }
        if (fs.exists(journal)) fs.delete(journal)
        fs.atomicMove(staged, journal)
    }

    fun clearJournal(fs: FileSystem, liveDbPath: Path) {
        val journal = journalPath(liveDbPath)
        if (fs.exists(journal)) fs.delete(journal)
    }

    /**
     * Recover before Room constructs/opens the database. A journal with no `.bak` and no live file is
     * fail-closed: throwing here prevents Room from silently creating an empty replacement database.
     */
    fun recover(fs: FileSystem, liveDbPath: Path) {
        val journal = journalPath(liveDbPath)
        val state = if (fs.exists(journal)) fs.read(journal) { readUtf8() } else null
        val originals = listOf(
            liveDbPath,
            "$liveDbPath-wal".toPath(),
            "$liveDbPath-shm".toPath(),
        )
        val backups = originals.map { it to "$it.bak".toPath() }
        val presentBackups = backups.filter { (_, backup) -> fs.exists(backup) }

        if (state == COMMITTED) {
            if (!fs.exists(liveDbPath)) {
                throw IOException("恢复已提交但实时数据库缺失，拒绝创建新库：$liveDbPath")
            }
            presentBackups.forEach { (_, backup) -> fs.delete(backup) }
            clearJournal(fs, liveDbPath)
            return
        }

        if (presentBackups.isEmpty()) {
            if (fs.exists(liveDbPath)) {
                // Crash before the first move, or after all recovery moves completed.
                clearJournal(fs, liveDbPath)
                return
            }
            if (state != null || originals.drop(1).any { fs.exists(it) }) {
                throw IOException("恢复日志存在但实时数据库与 .bak 均缺失，拒绝创建新库：$liveDbPath")
            }
            return // Fresh install: no journal, no live file, no sidecars.
        }

        val mainBackup = backups.first().second
        if (!fs.exists(mainBackup)) {
            if (!fs.exists(liveDbPath)) {
                throw IOException("恢复备份缺少主数据库 .bak，拒绝创建新库：$liveDbPath")
            }
            // Recovery already moved the main .bak home before an interrupted sidecar restore.
            // Keep that restored main file and finish only the remaining sidecars.
            originals.drop(1).forEach { if (fs.exists(it)) fs.delete(it) }
            backups.drop(1).forEach { (original, backup) ->
                if (fs.exists(backup)) fs.atomicMove(backup, original)
            }
            clearJournal(fs, liveDbPath)
            return
        }

        // Pending restore: discard every partial candidate file before putting originals back.
        originals.forEach { if (fs.exists(it)) fs.delete(it) }
        backups.forEach { (original, backup) ->
            if (fs.exists(backup)) fs.atomicMove(backup, original)
        }
        clearJournal(fs, liveDbPath)
    }
}
