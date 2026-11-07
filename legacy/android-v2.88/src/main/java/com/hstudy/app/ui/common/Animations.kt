package com.hstudy.app.ui.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// ============================================================
// 🎊 撒花 / Confetti 动画
// ============================================================
private data class Confetti(
    val x: Float, val y: Float,
    val vx: Float, val vy: Float,
    val color: Color, val size: Float,
    val rotation: Float, val rotSpeed: Float
)

@Composable
fun ConfettiOverlay(
    trigger: Boolean,
    modifier: Modifier = Modifier,
    particleCount: Int = 60,
    durationMs: Int = 1500,
    colors: List<Color> = listOf(
        Color(0xFFFF6F00), Color(0xFFFFAB00), Color(0xFF00C853),
        Color(0xFF2979FF), Color(0xFFD500F9), Color(0xFFFF1744)
    )
) {
    if (!trigger) return

    var particles by remember { mutableStateOf(emptyList<Confetti>()) }
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        particles = List(particleCount) {
            Confetti(
                x = Random.nextFloat(), y = -0.1f,
                vx = (Random.nextFloat() - 0.5f) * 0.8f,
                vy = Random.nextFloat() * 0.3f + 0.2f,
                color = colors.random(),
                size = Random.nextFloat() * 8f + 4f,
                rotation = Random.nextFloat() * 360f,
                rotSpeed = (Random.nextFloat() - 0.5f) * 720f
            )
        }
        animProgress.snapTo(0f)
        animProgress.animateTo(1f, tween(durationMs, easing = LinearEasing))
    }

    val progress = animProgress.value

    Canvas(modifier = modifier.fillMaxSize()) {
        if (particles.isNotEmpty()) {
            val w = size.width
            val h = size.height
            particles.forEach { p ->
                val cx = (p.x + p.vx * progress) * w
                val cy = (p.y + p.vy * progress + 0.5f * progress * progress * 0.5f) * h
                val alpha = (1f - progress).coerceIn(0f, 1f)
                val rot = p.rotation + p.rotSpeed * progress
                drawCircle(
                    color = p.color.copy(alpha = alpha),
                    radius = p.size * (1f - progress * 0.3f),
                    center = Offset(cx, cy)
                )
            }
        }
    }
}

// ============================================================
// ⭐ 积分数字滚动动画
// ============================================================
@Composable
fun AnimatedPointsDisplay(
    targetPoints: Int,
    modifier: Modifier = Modifier
) {
    val animatedPoints by animateIntAsState(
        targetValue = targetPoints,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "pointsAnim"
    )

    Text(
        text = "⭐ $animatedPoints",
        style = MaterialTheme.typography.displayLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

// ============================================================
// 🔥 Streak 火焰徽章
// ============================================================
@Composable
fun StreakBadge(
    streakDays: Int,
    modifier: Modifier = Modifier
) {
    if (streakDays <= 0) return

    val infiniteTransition = rememberInfiniteTransition(label = "fire")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fireScale"
    )

    val flameEmoji = when {
        streakDays >= 30 -> "🔥🔥🔥"
        streakDays >= 14 -> "🔥🔥"
        streakDays >= 7 -> "🔥"
        streakDays >= 3 -> "🔥"
        else -> "✨"
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$flameEmoji ${streakDays}天",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.scale(scale)
        )
    }
}

// ============================================================
// 🎉 庆祝弹出（奖励兑换成功时）
// ============================================================
@Composable
fun CelebrationPopup(
    show: Boolean,
    message: String,
    onDismiss: () -> Unit
) {
    if (!show) return

    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(2000)
        visible = false
        onDismiss()
    }

    if (visible) {
        val transition = rememberInfiniteTransition(label = "celebrate")
        val rotation by transition.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(
                animation = tween(300),
                repeatMode = RepeatMode.Reverse
            ),
            label = "rotate"
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.rotate(rotation)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ============================================================
// ✅ 打卡成功 +积分 飞入动画
// ============================================================
@Composable
fun PointsFlyUp(
    show: Boolean,
    points: Int,
    modifier: Modifier = Modifier
) {
    if (!show) return

    val offsetY by animateFloatAsState(
        targetValue = -120f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "flyUp"
    )
    val alpha by animateFloatAsState(
        targetValue = 0f,
        animationSpec = tween(800),
        label = "flyAlpha"
    )

    Text(
        text = "+$points ⭐",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = Color(0xFFFFA000).copy(alpha = alpha),
        modifier = modifier.offset { IntOffset(0, offsetY.roundToInt()) }
    )
}
