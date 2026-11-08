// 通道 E（污点链，长度 ≥2）：Task 9b 缺陷 2 的正面样本。
// 旧实现只认"同一行含文案"这一级，`val b = a` 这种二跳完全 uncovered（而且这个限制只写在
// 注释里、没有任何东西能证明它），所以 `checkIn(description = secondHop)` 当时是绿的。
// 现在赋值形态迭代到不动点，链深不设上限：下面 2 跳与 4 跳两条都要报。
package com.hstudy.app.fixture

import androidx.compose.runtime.Composable
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.LocalStrings

@Composable
fun ChainTaintScreen() {
    val app = AppGraph
    val strings = LocalStrings.current.childHome

    // E1 链长 2
    val firstHop = strings.checkInDescription
    val secondHop = firstHop
    app.pointRepository.checkIn(bizKey = "CHAIN_2", description = secondHop, points = 1)

    // E2 链长 4，且中间一跳是带类型注解的赋值
    val hop1 = strings.milestoneRewardNote
    val hop2 = hop1
    val hop3: String = hop2
    val hop4 = hop3.trim()
    app.pointApprovalRepository.submitApproval("CHAIN_4", hop4, 2)

    // E3 经由调用实参传播（同文件、无跨文件数据流；这里 hop1 已在污点集内）
    val wrapped = listOf(hop1).joinToString()
    app.pointRepository.adjustPoints(3, wrapped)
}
