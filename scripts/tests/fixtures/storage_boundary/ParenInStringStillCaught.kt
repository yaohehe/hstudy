// 缺陷 6 的另一半：**不带插值**的字符串内容被空白化之后不会造成漏抓。
// 真实形态见 ChildHomeScreen.kt 的 `"已完成(点击撤销)"` 与 i18n_template_diff.py 的同源教训：
// 英文文案里本来就写着括号。这里把带括号/URL 的文案串和真正的 sink 调用混在同一行、
// 同一个实参里，闸门仍必须报红（被空白化的只是字面量的文字，`${…}` 之外的文案引用本来就是裸代码）。
// 注意这条证明的范围：旧脚本注释曾经由此推广出"空白化不会造成漏抓"这个一般性结论，
// 9b 审查 Critical 1 推翻的正是那个推广 —— 插值里的文案确实在字符串内部，会漏。
// 插值那一半由 InterpolationCopy.kt（必红）与 InterpolationNoFp.kt（必绿）单独钉住。
package com.hstudy.app.fixture

import androidx.compose.runtime.Composable
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.LocalStrings

@Composable
fun ParenInStringStillCaughtScreen() {
    val app = AppGraph
    val strings = LocalStrings.current.childHome
    // H1 同一实参里：先一段带括号的字面量，再拼文案
    app.pointRepository.adjustPoints(5, "save(注意括号) " + strings.milestoneBonusNote)
    // H2 跨行调用 + 字面量里带 URL（含 // 与括号）
    app.pointApprovalRepository.submitApproval(
        "LINK",
        "https://x.example.com/checkIn(points) 说明 " + strings.checkInDescription,
        2,
    )
    // H3 三引号原始串里写 sink 名，紧随其后的实参仍是文案
    val banner = """
        save( ... ) 与 insert( 只是说明文字 (a(b))
    """.trimIndent()
    app.pointRepository.checkIn("BIZ_H3", banner + strings.weeklyChallengeDescription, 1)
}
