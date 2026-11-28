@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.hstudy.app.security

import com.hstudy.app.iosbridge.HStudyHashParentPassword
import com.hstudy.app.iosbridge.HStudyVerifyParentPassword

actual object ParentPasswordHasher {
    actual fun hash(password: String): String =
        HStudyHashParentPassword(password) ?: error("Could not hash parent password")

    actual fun verify(password: String, encoded: String): Boolean =
        HStudyVerifyParentPassword(password, encoded) != 0
}
