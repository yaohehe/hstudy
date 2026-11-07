package com.hstudy.app.ui.role

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import com.hstudy.app.ui.theme.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.ui.common.AmbientBackground
import com.hstudy.app.ui.common.ResponsiveContent
import com.hstudy.app.ui.common.bouncyClickable

@Composable
fun RoleSelectScreen(
    onParentSelected: () -> Unit,
    onChildSelected: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val strings = LocalStrings.current.role
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
                    text = strings.brandTitle,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Text(
                    text = strings.tagline,
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
                            title = strings.parentEntry,
                            subtitle = strings.parentDesc,
                            gradient = listOf(Color(0xFF90CAF9), Color(0xFF42A5F5)),
                            onClick = onParentSelected,
                            modifier = Modifier.weight(1f)
                        )
                        RoleCard(
                            emoji = "🧒",
                            title = strings.childEntry,
                            subtitle = strings.childDesc,
                            gradient = listOf(Color(0xFFFFCC80), Color(0xFFFF9800)),
                            onClick = onChildSelected,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        RoleCard(
                            emoji = "👨‍👩‍👧",
                            title = strings.parentEntry,
                            subtitle = strings.parentDesc,
                            gradient = listOf(Color(0xFF90CAF9), Color(0xFF42A5F5)),
                            onClick = onParentSelected
                        )
                        RoleCard(
                            emoji = "🧒",
                            title = strings.childEntry,
                            subtitle = strings.childDesc,
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
