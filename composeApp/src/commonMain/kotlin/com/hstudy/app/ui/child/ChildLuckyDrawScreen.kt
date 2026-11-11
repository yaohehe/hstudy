package com.hstudy.app.ui.child

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 每日幸运抽奖 - 可变比率强化（同类app核心留存机制）
 * 每天可抽奖一次，随机获得奖励积分，增强趣味与期待感
 */
@Composable
fun ChildLuckyDrawScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val strings = LocalStrings.current.childLuckyDraw
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val today = com.hstudy.app.data.repository.PointRepository.getTodayString()

    var canDraw by remember { mutableStateOf(true) }
    var isDrawing by remember { mutableStateOf(false) }
    var resultPoints by remember { mutableIntStateOf(-1) }

    // 抽奖动画
    val spinTransition = rememberInfiniteTransition(label = "spin")
    val rotation by spinTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(800, easing = LinearEasing)),
        label = "rotation"
    )
    val pulse by spinTransition.animateFloat(
        initialValue = 1f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(animation = tween(600), repeatMode = RepeatMode.Reverse),
        label = "pulse"
    )

    LaunchedEffect(Unit) {
        val lastDraw = app.userPreferences.lastLuckyDraw.first()
        canDraw = lastDraw != today
    }

    // 奖励池（可变比率：小奖励概率高，大奖励概率低）
    val rewardPool = listOf(1, 1, 1, 1, 2, 2, 2, 3, 3, 5)

    Scaffold(
        topBar = { HStudyTopBar(title = strings.title, onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            ResponsiveContent(windowSizeClass = windowSizeClass) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(strings.headline, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(strings.subtitle, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    // 抽奖转盘/礼盒
                    Box(
                        modifier = Modifier.size(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDrawing) {
                            Text("🎁", fontSize = 100.sp, modifier = Modifier.rotate(rotation))
                        } else if (resultPoints >= 0) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(pulse)) {
                                Text("🎉", fontSize = 72.sp)
                                Text(strings.pointsWonValue(resultPoints), style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            Text("🎁", fontSize = 100.sp, modifier = Modifier.scale(if (canDraw) pulse else 1f))
                        }
                    }

                    // 结果提示
                    when {
                        resultPoints >= 0 -> Text(strings.winMessage(resultPoints),
                            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold)
                        !canDraw -> Text(strings.alreadyDrawn,
                            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // 抽奖按钮
                    Button(
                        onClick = {
                            if (!canDraw || isDrawing) return@Button
                            isDrawing = true
                            resultPoints = -1
                            scope.launch {
                                delay(1500) // 动画时长
                                val won = rewardPool.random()
                                resultPoints = won
                                isDrawing = false
                                canDraw = false
                                app.pointRepository.adjustPoints(won, "每日幸运抽奖")
                                app.userPreferences.setLastLuckyDraw(today)
                                snackbarHostState.showSnackbar(strings.drawSnackbar(won))
                            }
                        },
                        enabled = canDraw && !isDrawing,
                        modifier = Modifier.fillMaxWidth(0.7f).height(56.dp)
                    ) {
                        Text(
                            if (isDrawing) strings.drawing else if (canDraw) strings.drawButton else strings.comeTomorrow,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(strings.infoTitle, style = MaterialTheme.typography.titleSmall)
                            Text(strings.infoPool,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(strings.infoStreak,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
