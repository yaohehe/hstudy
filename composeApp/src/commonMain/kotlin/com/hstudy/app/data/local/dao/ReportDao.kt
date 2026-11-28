package com.hstudy.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.hstudy.app.data.local.entity.DailyTaskSnapshotEntity
import kotlinx.coroutines.flow.Flow

/** Room aggregate rows; names intentionally describe the persisted source, not a UI estimate. */
data class HistoricalTaskStatsRow(
    val taskId: Long,
    val points: Int,
    val awards: Int
)

data class DailyAwardedPointsRow(
    val date: String,
    val points: Int
)

data class ApprovedTaskCompletionRow(
    val date: String,
    val taskId: Long
)

@Dao
interface ReportDao {

    /**
     * Task award history comes from append-only ledger rows. A task edit/delete cannot
     * affect it. APPROVED and REVOKED original awards remain eligible because the latter
     * are canceled by their audit reversal; PENDING/REJECTED/CANCELLED awards do not.
     * Only a REVOKED TASK_COMPLETE approval may reverse task history, so task-linked
     * BONUS/OTHER reversals still net in daily income but never rewrite a task's history.
     * A linked reversal uses its original awarded record's date for range filtering; an
     * unresolved awardedRecordId falls back to the reversal row's own date.
     */
    @Query(
        "WITH report_records AS (" +
            "SELECT pr.id AS id, pr.taskId AS taskId, pr.type AS type, pr.points AS points, " +
            "pr.approvalId AS approvalId, " +
            "CASE WHEN pr.type = 'REVERSAL' THEN COALESCE(" +
            "  (SELECT original.date FROM point_approvals reversal_approval " +
            "   JOIN point_records original ON original.id = reversal_approval.awardedRecordId " +
            "   WHERE reversal_approval.id = pr.approvalId), pr.date) " +
            "ELSE pr.date END AS effectiveDate " +
            "FROM point_records pr" +
            ") " +
            "SELECT rr.taskId AS taskId, SUM(rr.points) AS points, " +
            "COUNT(DISTINCT CASE WHEN rr.type = 'TASK_COMPLETE' THEN rr.id END) AS awards " +
            "FROM report_records rr " +
            "WHERE rr.taskId IS NOT NULL AND rr.effectiveDate >= :start AND rr.effectiveDate < :end AND (" +
            "  (rr.type = 'TASK_COMPLETE' AND rr.points > 0 AND (" +
            "    (rr.approvalId IS NOT NULL AND EXISTS(SELECT 1 FROM point_approvals pa " +
            "      WHERE pa.id = rr.approvalId AND pa.status IN ('APPROVED', 'REVOKED') AND pa.actionType = 'TASK_COMPLETE')) OR " +
            "    EXISTS(SELECT 1 FROM point_approvals pa WHERE pa.awardedRecordId = rr.id " +
            "      AND pa.status IN ('APPROVED', 'REVOKED') AND pa.actionType = 'TASK_COMPLETE') OR " +
            "    (rr.approvalId IS NULL AND NOT EXISTS(SELECT 1 FROM point_approvals pa " +
            "      WHERE pa.awardedRecordId = rr.id))" +
            "  )) OR " +
            "  (rr.type = 'REVERSAL' AND rr.points < 0 AND EXISTS(SELECT 1 FROM point_approvals pa " +
            "    WHERE pa.id = rr.approvalId AND pa.status = 'REVOKED' AND pa.actionType = 'TASK_COMPLETE'))" +
            ") GROUP BY rr.taskId"
    )
    fun getHistoricalTaskStats(start: Long, end: Long): Flow<List<HistoricalTaskStatsRow>>

    /**
     * Daily awarded income, net of legitimate award reversals. Spending, wish refunds
     * and penalties are not income. APPROVED/REVOKED original awards remain eligible;
     * a null approvalId is legacy income only when no approval backlinks through
     * awardedRecordId, and linked rows require APPROVED or REVOKED status. A reversal
     * must link to a REVOKED approval or be an unbacklinked legacy null-approval row.
     * A linked reversal uses its original awarded record's date for both range filtering
     * and daily grouping; an unresolved awardedRecordId falls back to the reversal date.
     * ADJUSTMENT remains unconditional parent-awarded income.
     */
    @Query(
        "WITH report_records AS (" +
            "SELECT pr.id AS id, pr.taskId AS taskId, pr.type AS type, pr.points AS points, " +
            "pr.approvalId AS approvalId, " +
            "CASE WHEN pr.type = 'REVERSAL' THEN COALESCE(" +
            "  (SELECT original.date FROM point_approvals reversal_approval " +
            "   JOIN point_records original ON original.id = reversal_approval.awardedRecordId " +
            "   WHERE reversal_approval.id = pr.approvalId), pr.date) " +
            "ELSE pr.date END AS effectiveDate " +
            "FROM point_records pr" +
            ") " +
            "SELECT date(rr.effectiveDate / 1000, 'unixepoch', 'localtime') AS date, SUM(rr.points) AS points " +
            "FROM report_records rr " +
            "WHERE rr.effectiveDate >= :start AND rr.effectiveDate < :end AND (" +
            "  (rr.points > 0 AND rr.type IN ('TASK_COMPLETE', 'BONUS', 'ADJUSTMENT') AND (" +
            "    rr.type = 'ADJUSTMENT' OR " +
            "    (rr.approvalId IS NOT NULL AND EXISTS(SELECT 1 FROM point_approvals pa " +
            "      WHERE pa.id = rr.approvalId AND pa.status IN ('APPROVED', 'REVOKED'))) OR " +
            "    EXISTS(SELECT 1 FROM point_approvals pa WHERE pa.awardedRecordId = rr.id " +
            "      AND pa.status IN ('APPROVED', 'REVOKED')) OR " +
            "    (rr.approvalId IS NULL AND NOT EXISTS(SELECT 1 FROM point_approvals pa " +
            "      WHERE pa.awardedRecordId = rr.id))" +
            "  )) OR " +
            "  (rr.type = 'REVERSAL' AND rr.points < 0 AND (" +
            "    (rr.approvalId IS NOT NULL AND EXISTS(SELECT 1 FROM point_approvals pa " +
            "      WHERE pa.id = rr.approvalId AND pa.status = 'REVOKED')) OR " +
            "    (rr.approvalId IS NULL AND NOT EXISTS(SELECT 1 FROM point_approvals pa " +
            "      WHERE pa.awardedRecordId = rr.id))" +
            "  ))" +
            ") GROUP BY date(rr.effectiveDate / 1000, 'unixepoch', 'localtime')"
    )
    fun getDailyAwardedPoints(start: Long, end: Long): Flow<List<DailyAwardedPointsRow>>

    /** Approved and actually awarded task completions only; pending/rejected rows are invisible. */
    @Query(
        "SELECT tc.date AS date, tc.taskId AS taskId FROM task_completions tc " +
            "JOIN point_approvals pa ON pa.taskId = tc.taskId AND pa.date = tc.date " +
            "AND pa.actionType = 'TASK_COMPLETE' AND pa.status = 'APPROVED' " +
            "AND (tc.frequencyPeriod = pa.frequencyPeriod OR " +
            "     (tc.frequencyPeriod IS NULL AND pa.frequencyPeriod IS NULL)) " +
            "WHERE tc.date >= :startDate AND tc.date <= :endDate " +
            "AND EXISTS(SELECT 1 FROM point_records pr WHERE pr.type = 'TASK_COMPLETE' AND " +
            "  (pr.approvalId = pa.id OR pr.id = pa.awardedRecordId)) " +
            "GROUP BY tc.date, tc.taskId"
    )
    fun getApprovedTaskCompletions(startDate: String, endDate: String): Flow<List<ApprovedTaskCompletionRow>>

    @Query("SELECT * FROM daily_task_snapshots WHERE date >= :startDate AND date <= :endDate ORDER BY date")
    fun getDailySnapshots(startDate: String, endDate: String): Flow<List<DailyTaskSnapshotEntity>>
}
