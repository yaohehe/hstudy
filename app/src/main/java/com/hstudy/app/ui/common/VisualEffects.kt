package com.hstudy.app.ui.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.sin

/**
 * 按压弹性微交互 - 按下时轻微缩放，松开弹回，提供触觉反馈感
 * 用法：Modifier.bouncyClickable { ... }
 */
@Composable
fun Modifier.bouncyClickable(
    onClick: () -> Unit,
    pressedScale: Float = 0.95f
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pressScale"
    )
    return this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

/**
 * 氛围背景 - 缓慢漂浮的柔和圆形与星点，营造层次感的"活"背景
 * @param baseHueColors 漂浮物颜色（建议用低透明度）
 */
@Composable
fun AmbientBackground(
    modifier: Modifier = Modifier,
    bubbleColors: List<Color> = listOf(
        Color(0x22FFB74D),  // 暖橙
        Color(0x1C81C784),  // 清新绿
        Color(0x1C64B5F6),  // 天空蓝
        Color(0x18FFD54F),  // 金黄
    )
) {
    // 用一个长时间循环的进度值驱动所有漂浮物
    val transition = rememberInfiniteTransition(label = "ambient")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing)
        ),
        label = "ambientT"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val phase = t * 2f * PI.toFloat()

        // 柔和漂浮圆（各自不同频率/振幅，错落有致）
        val bubbles = listOf(
            floatArrayOf(0.12f, 0.20f, 70f, 1.0f, 0.0f),
            floatArrayOf(0.85f, 0.15f, 50f, 1.4f, 1.2f),
            floatArrayOf(0.75f, 0.70f, 90f, 0.8f, 2.1f),
            floatArrayOf(0.18f, 0.78f, 55f, 1.2f, 3.0f),
            floatArrayOf(0.50f, 0.45f, 40f, 1.6f, 4.2f),
            floatArrayOf(0.92f, 0.50f, 35f, 1.1f, 5.0f),
        )
        bubbles.forEachIndexed { i, b ->
            val cx = b[0] * w + sin(phase * b[3] + b[4]) * 18f
            val cy = b[1] * h + sin(phase * b[3] * 0.7f + b[4] + 1.5f) * 24f
            drawCircle(
                color = bubbleColors[i % bubbleColors.size],
                radius = b[2],
                center = Offset(cx, cy)
            )
        }

        // 点缀小星点（闪烁）
        val stars = listOf(
            floatArrayOf(0.30f, 0.12f, 1.0f),
            floatArrayOf(0.62f, 0.25f, 1.5f),
            floatArrayOf(0.40f, 0.65f, 2.2f),
            floatArrayOf(0.88f, 0.85f, 0.8f),
            floatArrayOf(0.08f, 0.50f, 1.8f),
        )
        stars.forEach { s ->
            val twinkle = (sin(phase * 2f + s[2] * 3f) + 1f) / 2f // 0~1 闪烁
            val alpha = 0.15f + twinkle * 0.35f
            drawCircle(
                color = Color(0xFFFFB300).copy(alpha = alpha),
                radius = 4f + twinkle * 3f,
                center = Offset(s[0] * w, s[1] * h)
            )
        }
    }
}
