package com.hstudy.app.i18n

/**
 * 同步/备份诊断消息的渲染侧映射（spec §9）：固定文案精确命中走注册表；
 * 唯一插值消息「已应用${deviceName}的设置：${n}个任务、${m}个奖励」
 * （FamilySyncHelper.kt:172）用钉死正则识别 canonical 格式并重组英文；
 * deviceName 经 localizedPresetText 回退映射（家长设备/孩子设备）；未命中原样。
 *
 * 只用于显示：FamilySyncHelper/BackupHelper（data 层）的 canonical 写入与业务判等不动，
 * 家长自建设备名、历史消息、序列化异常文本都按原样透传（未命中原样返回）。
 */
object SyncDiagnosticAdapter {
    private val APPLIED = Regex("^已应用(.+)的设置：(\\d+)个任务、(\\d+)个奖励$")

    fun localize(raw: String, lang: DisplayLanguage): String {
        if (lang == DisplayLanguage.ZH) return raw
        val m = APPLIED.find(raw) ?: return localizedPresetText(raw, lang)
        val device = localizedPresetText(m.groupValues[1], lang)
        val tasks = m.groupValues[2].toInt()
        val rewards = m.groupValues[3].toInt()
        return "Applied settings from $device: $tasks ${tasksWord(tasks)}, $rewards ${if (rewards == 1) "reward" else "rewards"}"
    }
}
