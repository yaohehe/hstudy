package com.hstudy.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 学习树 Canvas 绘制 - 6个成长阶段
 */
object LearningTree {

    fun getStage(level: Int): Int = when {
        level <= 1 -> 0  // 种子
        level <= 2 -> 1  // 发芽
        level <= 3 -> 2  // 幼苗
        level <= 5 -> 3  // 大树
        level <= 7 -> 4  // 开花
        else -> 5         // 结果
    }

    fun getStageName(stage: Int): String = when (stage) {
        0 -> "种子"
        1 -> "发芽"
        2 -> "幼苗"
        3 -> "大树"
        4 -> "开花"
        5 -> "结果"
        else -> "种子"
    }
}

@Composable
fun LearningTreeCanvas(
    level: Int,
    modifier: Modifier = Modifier
) {
    val stage = LearningTree.getStage(level)

    val trunkColor = Color(0xFF6D4C41)
    val leafColor = Color(0xFF4CAF50)
    val flowerColor = Color(0xFFFF9800)
    val fruitColor = Color(0xFFF44336)
    val groundColor = Color(0xFF795548)
    val seedColor = Color(0xFF8D6E63)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier.size(120.dp)
        ) {
            val w = size.width
            val h = size.height
            val centerX = w / 2
            val groundY = h * 0.85f

            // 地面
            drawRect(
                color = groundColor.copy(alpha = 0.3f),
                topLeft = Offset(0f, groundY),
                size = Size(w, h - groundY)
            )

            when (stage) {
                0 -> { // 种子
                    drawCircle(color = seedColor, radius = 12f, center = Offset(centerX, groundY - 12f))
                    drawCircle(color = seedColor.copy(alpha = 0.5f), radius = 6f, center = Offset(centerX + 10f, groundY - 6f))
                }
                1 -> { // 发芽
                    drawLine(color = trunkColor, start = Offset(centerX, groundY), end = Offset(centerX, groundY - 40f), strokeWidth = 4f, cap = StrokeCap.Round)
                    drawCircle(color = leafColor, radius = 10f, center = Offset(centerX - 8f, groundY - 40f))
                    drawCircle(color = leafColor, radius = 10f, center = Offset(centerX + 8f, groundY - 40f))
                }
                2 -> { // 幼苗
                    drawLine(color = trunkColor, start = Offset(centerX, groundY), end = Offset(centerX, groundY - 60f), strokeWidth = 6f, cap = StrokeCap.Round)
                    drawCircle(color = leafColor, radius = 18f, center = Offset(centerX, groundY - 65f))
                    drawCircle(color = leafColor.copy(alpha = 0.7f), radius = 14f, center = Offset(centerX - 15f, groundY - 50f))
                    drawCircle(color = leafColor.copy(alpha = 0.7f), radius = 14f, center = Offset(centerX + 15f, groundY - 50f))
                }
                3 -> { // 大树
                    drawLine(color = trunkColor, start = Offset(centerX, groundY), end = Offset(centerX, groundY - 80f), strokeWidth = 10f, cap = StrokeCap.Round)
                    drawLine(color = trunkColor, start = Offset(centerX, groundY - 60f), end = Offset(centerX - 25f, groundY - 75f), strokeWidth = 5f, cap = StrokeCap.Round)
                    drawLine(color = trunkColor, start = Offset(centerX, groundY - 60f), end = Offset(centerX + 25f, groundY - 75f), strokeWidth = 5f, cap = StrokeCap.Round)
                    drawCircle(color = leafColor, radius = 30f, center = Offset(centerX, groundY - 85f))
                    drawCircle(color = leafColor.copy(alpha = 0.8f), radius = 22f, center = Offset(centerX - 22f, groundY - 70f))
                    drawCircle(color = leafColor.copy(alpha = 0.8f), radius = 22f, center = Offset(centerX + 22f, groundY - 70f))
                }
                4 -> { // 开花
                    drawLine(color = trunkColor, start = Offset(centerX, groundY), end = Offset(centerX, groundY - 80f), strokeWidth = 10f, cap = StrokeCap.Round)
                    drawLine(color = trunkColor, start = Offset(centerX, groundY - 55f), end = Offset(centerX - 30f, groundY - 70f), strokeWidth = 5f, cap = StrokeCap.Round)
                    drawLine(color = trunkColor, start = Offset(centerX, groundY - 55f), end = Offset(centerX + 30f, groundY - 70f), strokeWidth = 5f, cap = StrokeCap.Round)
                    drawCircle(color = leafColor, radius = 30f, center = Offset(centerX, groundY - 85f))
                    drawCircle(color = leafColor.copy(alpha = 0.8f), radius = 22f, center = Offset(centerX - 22f, groundY - 70f))
                    drawCircle(color = leafColor.copy(alpha = 0.8f), radius = 22f, center = Offset(centerX + 22f, groundY - 70f))
                    // 花朵
                    listOf(
                        Offset(centerX - 15f, groundY - 90f),
                        Offset(centerX + 15f, groundY - 90f),
                        Offset(centerX, groundY - 100f),
                        Offset(centerX - 25f, groundY - 75f),
                        Offset(centerX + 25f, groundY - 75f)
                    ).forEach { pos ->
                        drawCircle(color = flowerColor, radius = 6f, center = pos)
                    }
                }
                5 -> { // 结果
                    drawLine(color = trunkColor, start = Offset(centerX, groundY), end = Offset(centerX, groundY - 80f), strokeWidth = 12f, cap = StrokeCap.Round)
                    drawLine(color = trunkColor, start = Offset(centerX, groundY - 50f), end = Offset(centerX - 35f, groundY - 65f), strokeWidth = 6f, cap = StrokeCap.Round)
                    drawLine(color = trunkColor, start = Offset(centerX, groundY - 50f), end = Offset(centerX + 35f, groundY - 65f), strokeWidth = 6f, cap = StrokeCap.Round)
                    drawCircle(color = leafColor, radius = 35f, center = Offset(centerX, groundY - 85f))
                    drawCircle(color = leafColor.copy(alpha = 0.8f), radius = 25f, center = Offset(centerX - 25f, groundY - 70f))
                    drawCircle(color = leafColor.copy(alpha = 0.8f), radius = 25f, center = Offset(centerX + 25f, groundY - 70f))
                    // 果实
                    listOf(
                        Offset(centerX - 10f, groundY - 85f),
                        Offset(centerX + 10f, groundY - 80f),
                        Offset(centerX - 20f, groundY - 70f),
                        Offset(centerX + 20f, groundY - 70f)
                    ).forEach { pos ->
                        drawCircle(color = fruitColor, radius = 7f, center = pos)
                    }
                }
            }
        }
        Text(
            LearningTree.getStageName(stage),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
