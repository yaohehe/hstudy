package com.hstudy.app.i18n

import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManualSyncCopyTest {
    @Test
    fun forbiddenClaimPredicateRejectsMisleadingSamplesButAllowsNeutralReferences() {
        assertTrue(containsForbiddenSyncClaim("Once paired, your devices synchronize automatically."))
        assertTrue(containsForbiddenSyncClaim("Share settings between two paired devices."))
        assertTrue(containsForbiddenSyncClaim("配对后可实时同步设备状态。"))
        assertFalse(containsForbiddenSyncClaim("Manual JSON file sync; no pairing or live connection required."))
        assertFalse(containsForbiddenSyncClaim("Troubleshoot unpaired devices with manual JSON export."))
        assertFalse(containsForbiddenSyncClaim("手动 JSON 文件同步，无需配对或实时连接。"))
        assertFalse(containsForbiddenSyncClaim("未配对设备可改用手动 JSON 文件同步。"))
    }

    @Test
    fun settingsDescribeManualJsonFileSyncWithoutPairingOrDiscoveryClaims() {
        val zh = AppStringsZh.settings
        val en = AppStringsEn.settings
        val zhCopy = zh.manualSyncCopy()
        val enCopy = en.manualSyncCopy()

        assertTrue(zh.remoteControlNote.contains("JSON"), "ZH settings must name the JSON file workflow")
        assertTrue(zh.remoteControlNote.contains("手动"), "ZH settings must say the workflow is manual")
        assertTrue(zhCopy.any { "导出" in it }, "ZH settings must describe JSON export")
        assertTrue(zhCopy.any { "分享" in it }, "ZH settings must describe JSON sharing")
        assertTrue(zhCopy.any { "导入" in it }, "ZH settings must describe JSON import")

        assertTrue(en.remoteControlNote.contains("JSON"), "EN settings must name the JSON file workflow")
        assertTrue(en.remoteControlNote.contains("manual", ignoreCase = true), "EN settings must say the workflow is manual")
        assertTrue(enCopy.any { "export" in it.lowercase() }, "EN settings must describe JSON export")
        assertTrue(enCopy.any { "share" in it.lowercase() }, "EN settings must describe JSON sharing")
        assertTrue(enCopy.any { "import" in it.lowercase() }, "EN settings must describe JSON import")

        (zhCopy + enCopy).forEach { copy ->
            assertFalse(
                containsForbiddenSyncClaim(copy),
                "Manual-sync copy must not advertise pairing or live discovery: $copy",
            )
        }
    }

    @Test
    fun parentHomePointsToManualJsonSync() {
        val zh = AppStringsZh.parentHome
        val en = AppStringsEn.parentHome

        assertTrue(zh.menuSettingsSubtitle.contains("JSON"))
        assertTrue(zh.syncOverdueNote.contains("JSON"))
        assertTrue(zh.syncOverdueNote.contains("手动"))
        assertTrue(en.menuSettingsSubtitle.contains("JSON"))
        assertTrue(en.syncOverdueNote.contains("JSON"))
        assertTrue(en.syncOverdueNote.contains("manual", ignoreCase = true))

        listOf(
            zh.menuSettingsSubtitle,
            zh.syncOverdueTitle(2),
            zh.syncOverdueNote,
            en.menuSettingsSubtitle,
            en.syncOverdueTitle(2),
            en.syncOverdueNote,
        ).forEach { copy ->
            assertFalse(
                containsForbiddenSyncClaim(copy),
                "Parent-home copy must not advertise pairing or live discovery: $copy",
            )
        }
    }

    @Test
    fun readmeDescribesManualJsonFileSyncAndImportOnlyAnalyticsWithoutPairingClaims() {
        val readme = readReadme()

        assertTrue("Manual JSON file sync" in readme, "README must document the shipped manual JSON workflow")
        assertTrue("手动 JSON 文件同步" in readme, "README must document the shipped manual JSON workflow in Chinese")
        assertFalse("two paired devices" in readme, "README must not ship a two-device pairing claim")
        assertFalse("两台配对设备" in readme, "README must not ship a two-device pairing claim")
        FORBIDDEN_SYNC_CLAIMS.forEach { claim ->
            assertFalse(
                readme.contains(claim, ignoreCase = true),
                "README must not advertise pairing or live discovery: $claim",
            )
        }

        val englishAnalytics = readme.substringAfter("### Analytics & privacy").substringBefore("### Support / donate")
        val chineseAnalytics = readme.substringAfter("### 统计与隐私").substringBefore("### 支持 / 捐赠")
        val englishSent = englishAnalytics.substringAfter("- Sent:").substringBefore("\n- ")
        val chineseSent = chineseAnalytics.substringAfter("- 发送：").substringBefore("\n- ")

        assertFalse("JSON" in englishSent, "README must not claim JSON export/share is sent to analytics")
        assertFalse("JSON" in chineseSent, "README must not claim JSON export/share is sent to analytics")
        listOf(
            "sync_config_imported",
            "successful parent-settings JSON import",
            "no analytics event",
            "does not upload file contents",
        ).forEach { phrase ->
            assertTrue(phrase in englishAnalytics, "English analytics copy must disclose import-only event behavior: $phrase")
        }
        listOf(
            "sync_config_imported",
            "成功导入家长设置 JSON",
            "不会触发统计事件",
            "不会上传文件内容",
        ).forEach { phrase ->
            assertTrue(phrase in chineseAnalytics, "Chinese analytics copy must disclose import-only event behavior: $phrase")
        }
    }

    private fun containsForbiddenSyncClaim(copy: String): Boolean =
        FORBIDDEN_SYNC_CLAIMS.any { copy.contains(it, ignoreCase = true) }

    private fun SettingsStrings.manualSyncCopy(): List<String> = listOf(
        remoteControlTitle,
        remoteControlNote,
        howToTitle,
        pushStepNote,
        viewStepNote,
        transportNote,
        pushSettingsButton,
        viewProgressButton,
        exportChooserTitle,
        settingsExportedMessage,
        progressImportedMessage,
        childProgressImportFailedMessage("detail"),
    )

    private fun readReadme(): String {
        val candidates = listOf("README.md", "../README.md")
        return candidates.firstNotNullOfOrNull { path ->
            runCatching { FileSystem.SYSTEM.read(path.toPath()) { readUtf8() } }.getOrNull()
        } ?: error("README.md not found from Gradle test working directory")
    }

    private companion object {
        val FORBIDDEN_SYNC_CLAIMS = listOf(
            "two paired devices",
            "device pairing",
            "once paired",
            "pairing code",
            "pairing completed",
            "real-time discovery",
            "real-time device discovery",
            "live discovery",
            "两台配对设备",
            "设备配对",
            "配对后",
            "配对码",
            "实时设备发现",
            "实时发现",
        )
    }
}
