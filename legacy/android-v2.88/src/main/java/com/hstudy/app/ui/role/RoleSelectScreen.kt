package com.hstudy.app.ui.role

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hstudy.app.ui.common.AmbientBackground
import com.hstudy.app.ui.common.ResponsiveContent
import com.hstudy.app.ui.common.bouncyClickable

@Composable
fun RoleSelectScreen(
    onParentSelected: () -> Unit,
    onChildSelected: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val isTablet = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    // 徽章浮动动画
    val floatTransition = rememberInfiniteTransition(label = "emblem")
    val emblemOffset by floatTransition.animateFloat(
        initialValue = 0f, targetValue = 10f,
        animationSpec = infiniteRepeatable(animation = tween(1800, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "emblemFloat"
    )
    val starScale by floatTransition.animateFloat(
        initialValue = 1f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "starPulse"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // 氛围漂浮背景
        AmbientBackground()

        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            ResponsiveContent(windowSizeClass = windowSizeClass) {
                // ===== 动态徽章：书本 + 星星 =====
                Box(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "📚",
                            fontSize = 80.sp,
                            modifier = Modifier.offset(y = emblemOffset.dp)
                        )
                        Text(
                            "⭐",
                            fontSize = 34.sp,
                            modifier = Modifier
                                .offset(x = 52.dp, y = (-34).dp + emblemOffset.dp)
                                .scale(starScale)
                        )
                    }
                }

                // 标题（强字重对比）
                Text(
                    text = "学习积分奖励",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Text(
                    text = "好习惯，看得见 · 选择你的身份",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                // ===== 角色选择卡片（双色渐变 + 按压弹性）=====
                if (isTablet) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RoleCard(
                            emoji = "👨‍👩‍👧",
                            title = "我是家长",
                            subtitle = "发布任务 · 管理积分 · 设置奖励",
                            gradient = listOf(Color(0xFF90CAF9), Color(0xFF42A5F5)),
                            onClick = onParentSelected,
                            modifier = Modifier.weight(1f)
                        )
                        RoleCard(
                            emoji = "🧒",
                            title = "我是孩子",
                            subtitle = "完成任务 · 获取积分 · 兑换奖励",
                            gradient = listOf(Color(0xFFFFCC80), Color(0xFFFF9800)),
                            onClick = onChildSelected,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        RoleCard(
                            emoji = "👨‍👩‍👧",
                            title = "我是家长",
                            subtitle = "发布任务 · 管理积分 · 设置奖励",
                            gradient = listOf(Color(0xFF90CAF9), Color(0xFF42A5F5)),
                            onClick = onParentSelected
                        )
                        RoleCard(
                            emoji = "🧒",
                            title = "我是孩子",
                            subtitle = "完成任务 · 获取积分 · 兑换奖励",
                            gradient = listOf(Color(0xFFFFCC80), Color(0xFFFF9800)),
                            onClick = onChildSelected
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleCard(
    emoji: String,
    title: String,
    subtitle: String,
    gradient: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .bouncyClickable(onClick = onClick, pressedScale = 0.96f),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = Brush.linearGradient(gradient))
                .padding(vertical = 28.dp, horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(emoji, fontSize = 56.sp)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF2B2320)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF4A3F35)
                )
            }
        }
    }
}
