// 通道 G（插值文案）：Task 9b 审查 Critical 1 的正面样本 —— 字符串字面量**里面**的 `${…}` /
// `$name` 带的就是文案，旧实现把它连同整串一起空白化，于是双语迁移最常产出的"复合说明"
// 在闸门里完全隐身。审查的两条探针（I1/I2）当时实测 exit 0，现在必须报红。
// 与 InterpolationNoFp.kt 成对：那条证明"保留插值不会把纯字面量部分变成代码"。
package com.hstudy.app.fixture

import androidx.compose.runtime.Composable
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.AppStringsZh
import com.hstudy.app.i18n.LocalStrings

@Composable
fun InterpolationCopyScreen() {
    val app = AppGraph
    val strings = LocalStrings.current.childHome
    val common = LocalStrings.current.common

    // I1 探针原样：文案进插值，赋给变量，再进 sink（旧版 [经由变量 composed] 完全不成立 → 0 命中）
    val composed = "review: ${strings.checkInDescription}"
    app.pointRepository.adjustPoints(1, composed)

    // I2 探针原样：插值直接写在 sink 实参里
    app.pointApprovalRepository.submitApproval("R7", "${strings.milestoneRewardNote} ok", 1)

    // I3 简单 `$name` 插值（Kotlin 里 `$name` 带的就是变量本身）→ 污点链的第二跳
    val wrappedNote = "今日 ${strings.rewardStoreNote}"
    val outerNote = "备注 $wrappedNote 结束"
    app.pointRepository.spendPoints(10, outerNote)

    // I4 形状句柄 + `=` 之后换行（TAINT_ASSIGN 的 `\s*` 桥接一个换行是有意保留的行为）
    val multilineNote =
        "${common.milestoneAchievedLabel} 请家长确认"
    app.focusRepository.recordFocusSession(20, multilineNote, 3)

    // I5 三引号原始串里的插值同样算文案
    val rawNote = """
        审批说明 ${AppStringsZh.weeklyChallengeDescription}
        第二行只是文案
    """.trimIndent()
    app.pointApprovalRepository.submitApproval("RAW_INTERP", rawNote, 2)
}
