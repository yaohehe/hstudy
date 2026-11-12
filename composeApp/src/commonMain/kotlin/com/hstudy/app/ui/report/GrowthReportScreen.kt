package com.hstudy.app.ui.report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.DisplayLanguage
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.localizedPetStageName
import com.hstudy.app.platform.Platform
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent
import com.hstudy.app.ui.pet.getStageIndex
import com.hstudy.app.ui.pet.petStages

/** 宠物阶段名：与学习宠物页共用 [petStages] 目录与 [localizedPetStageName] 注册表 */
private fun petStageName(lifetime: Int, lang: DisplayLanguage): String {
    val index = getStageIndex(lifetime)
    return "${petStages[index].emoji} ${localizedPetStageName(index, lang)}"
}

/**
 * 成长报告：汇总孩子的成长数据，家长可一键分享到家族群
 */
@Composable
fun GrowthReportScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val strings = LocalStrings.current.growthReport
    val displayLanguage = LocalDisplayLanguage.current
    val app = AppGraph
    val snackbarHostState = remember { SnackbarHostState() }

    val lifetimePoints by app.pointRepository.getLifetimeEarnedPoints().collectAsState(initial = 0)
    val totalPoints by app.pointRepository.getTotalPoints().collectAsState(initial = 0)
    val goals by app.wishGoalRepository.getAllGoals().collectAsState(initial = emptyList())
    val exchanges by app.rewardRepository.getAllExchanges().collectAsState(initial = emptyList())
    val tasks by app.taskRepository.getAllActiveTasks().collectAsState(initial = emptyList())
    val today = com.hstudy.app.data.repository.PointRepository.getTodayString()
    val completedIds by app.pointRepository.getCompletedTaskIdsForDate(today).collectAsState(initial = emptyList())

    var streak by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { streak = app.pointRepository.getCurrentStreak() }

    val achievedWishes = goals.count { !it.isActive }
    val petStage = petStageName(lifetimePoints, displayLanguage)

    Scaffold(
        topBar = { HStudyTopBar(title = strings.title, onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            ResponsiveContent(
                windowSizeClass = windowSizeClass,
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 标题
                    Text(strings.heading, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(strings.subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(20.dp))

                    // 核心数据卡片
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            ReportStat(
                                "🌟", strings.lifetimeLabel,
                                strings.lifetimeValue(lifetimePoints), strings.balanceSub(totalPoints)
                            )
                            ReportStat(
                                "🔥", strings.streakLabel, strings.streakValue(streak),
                                if (streak > 0) strings.streakSubPositive else strings.streakSubZero
                            )
                            ReportStat("🐾", strings.petLabel, petStage, strings.petSub)
                            ReportStat(
                                "✅", strings.todayTasksLabel, strings.todayValue(completedIds.size, tasks.size),
                                if (completedIds.size == tasks.size && tasks.isNotEmpty()) strings.todaySubAll else strings.todaySubMore
                            )
                            ReportStat(
                                "🎁", strings.exchangesLabel,
                                strings.exchangesValue(exchanges.size), strings.exchangesSub
                            )
                            ReportStat(
                                "🐷", strings.wishesLabel,
                                strings.wishesValue(achievedWishes), strings.wishesSub
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 分享按钮
                    Button(
                        onClick = {
                            // 跨平台分享：Intent.ACTION_SEND -> Platform.fileSharer 文本分享面板
                            Platform.fileSharer.shareTextFile(
                                fileName = "hstudy_growth_report.txt",
                                content = strings.reportText(
                                    lifetimePoints,
                                    streak,
                                    petStage,
                                    completedIds.size,
                                    tasks.size,
                                    exchanges.size,
                                    achievedWishes,
                                ),
                                mimeType = "text/plain"
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.shareButton, modifier = Modifier.padding(vertical = 6.dp))
                    }
                    Text(strings.shareHint, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ReportStat(emoji: String, label: String, value: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 28.sp, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
