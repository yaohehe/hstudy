package com.hstudy.app.ui.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.localizedLevelTitle

/**
 * 等级系统 - 基于累计获得积分
 */
data class LevelInfo(
    val level: Int,
    val title: String,
    val emoji: String,
    val requiredPoints: Int,
    val nextLevelPoints: Int,
    val progress: Float // 0.0 ~ 1.0
)

object LevelSystem {
    private val levels = listOf(
        Triple(1, "学习新手", "🌱") to 0,
        Triple(2, "勤奋小学徒", "🌿") to 100,
        Triple(3, "知识探索者", "🌳") to 300,
        Triple(4, "学习达人", "🌲") to 600,
        Triple(5, "学霸少年", "🎋") to 1000,
        Triple(6, "知识王者", "🌸") to 1500,
        Triple(7, "学习之星", "🍎") to 2500,
        Triple(8, "超级学霸", "🌴") to 4000,
        Triple(9, "传奇学者", "🏔️") to 6000,
        Triple(10, "学神", "🌟") to 10000
    )

    fun getLevelInfo(totalEarnedPoints: Int): LevelInfo {
        var currentIdx = 0
        for (i in levels.indices) {
            if (totalEarnedPoints >= levels[i].second) {
                currentIdx = i
            } else break
        }

        val current = levels[currentIdx]
        val next = if (currentIdx < levels.lastIndex) levels[currentIdx + 1] else null

        val progress = if (next != null) {
            val range = next.second - current.second
            val earned = totalEarnedPoints - current.second
            earned.toFloat() / range
        } else 1f

        return LevelInfo(
            level = current.first.first,
            title = current.first.second,
            emoji = current.first.third,
            requiredPoints = current.second,
            nextLevelPoints = next?.second ?: current.second,
            progress = progress.coerceIn(0f, 1f)
        )
    }
}

@Composable
fun LevelBadge(
    totalEarnedPoints: Int,
    modifier: Modifier = Modifier
) {
    val info = LevelSystem.getLevelInfo(totalEarnedPoints)
    // 等级称号走键控注册表（levelTitleCopy），升级提示句式走公共文案表（W8）
    val strings = LocalStrings.current
    val displayLanguage = LocalDisplayLanguage.current

    val infiniteTransition = rememberInfiniteTransition(label = "levelPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = info.emoji,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.scale(pulseScale)
        )
        Column {
            Text(
                "Lv.${info.level} ${localizedLevelTitle(info.level, displayLanguage)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (info.level < 10) {
                val remaining = info.nextLevelPoints - totalEarnedPoints
                Text(
                    strings.common.levelRemaining(remaining),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
