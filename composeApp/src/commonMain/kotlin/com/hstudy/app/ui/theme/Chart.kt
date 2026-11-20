package com.hstudy.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * M3 图表色板（指令 §3.4-报表1「图表配色接入 M3 Chart Palette」）。
 *
 * 所有取色均来自 `MaterialTheme.colorScheme` 语义角色，报表/仪表盘绘制代码
 * 一律经 [chartPalette] 取色，禁止在图表里出现裸十六进制 `Color` 常量：
 * - [line]     已结算趋势线（primary，账本已入账的确定值）
 * - [dot]      数据点（tertiary）
 * - [grid]     网格线（outlineVariant，低对比背景线）
 * - [pending]  待审预估线（outline，虚线弱化：指令 §3.4-报表2 的"已结算 vs 待审"区分）
 * - [series]   分类分布系列色（primary/tertiary/secondary 三角色循环）
 */
data class ChartPalette(
    val line: Color,
    val dot: Color,
    val grid: Color,
    val pending: Color,
    val series: List<Color>,
)

@Composable
fun chartPalette(): ChartPalette {
    val scheme = MaterialTheme.colorScheme
    return ChartPalette(
        line = scheme.primary,
        dot = scheme.tertiary,
        grid = scheme.outlineVariant,
        pending = scheme.outline,
        series = listOf(scheme.primary, scheme.tertiary, scheme.secondary),
    )
}
