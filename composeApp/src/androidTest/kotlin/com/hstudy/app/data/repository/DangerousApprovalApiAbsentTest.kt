package com.hstudy.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hstudy.app.data.local.dao.PointApprovalDao
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DangerousApprovalApiAbsentTest {
    @Test
    fun duplicateApprovalRepositoryIsAbsentFromRuntimeClasspath() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.hstudy.app.data.repository.PointApprovalRepository")
        }
    }

    @Test
    fun pointRepositoryExposesOnlyLedgerAwareApprovalTransitions() {
        val methodNames = PointRepository::class.java.declaredMethods.map { it.name }.toSet()
        assertTrue("approveAndAward" in methodNames)
        assertTrue("rejectApproval" in methodNames)
        assertFalse("approve" in methodNames)
        assertFalse("approveAll" in methodNames)
    }

    @Test
    fun approvalDaoHasNoBulkOrGenericLedgerBypassingTransition() {
        val methodNames = PointApprovalDao::class.java.declaredMethods.map { it.name }.toSet()
        assertFalse("approveAll" in methodNames)
        assertFalse("updateStatus" in methodNames)
        assertTrue("cancelPending" in methodNames)
    }
}
