// 缺陷 6 的另一半：字符串内容空白化**不会**造成漏抓。
// 真实形态见 ChildHomeScreen.kt 的 `"已完成(点击撤销)"` 与 i18n_template_diff.py 的同源教训：
// 英文文案里本来就写着括号。这里把带括号/URL 的文案串和真正的 sink 调用混在同一行、
// 同一个实参里，闸门仍必须报红（文案引用是代码，不是字符串内容）。
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
