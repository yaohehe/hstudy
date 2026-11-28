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
import com.hstudy.app.data.repository.CompletionRateReport
import com.hstudy.app.data.repository.CompletionRateSource
import com.hstudy.app.i18n.DisplayLanguage
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.localizedPetStageName
import com.hstudy.app.platform.Platform
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent
import com.hstudy.app.ui.pet.getStageIndex
import com.hstudy.app.ui.pet.petStages
import kotlinx.datetime.LocalDate

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
    // §3.4-报表2：已结算余额之外，展示待家长审批的积分预估（只读流，与审批页同源查询）
    val allApprovals by app.pointRepository.getAllApprovals().collectAsState(initial = emptyList())
    val pendingPoints = allApprovals
        .filter { it.status == com.hstudy.app.data.local.entity.ApprovalStatus.PENDING }
        .sumOf { it.requestedPoints }
    val goals by app.wishGoalRepository.getAllGoals().collectAsState(initial = emptyList())
    val exchanges by app.rewardRepository.getAllExchanges().collectAsState(initial = emptyList())
    val today = LocalDate.parse(com.hstudy.app.data.repository.PointRepository.getTodayString())
    val completion by app.reportRepository.completionRateReport(today)
        .collectAsState(initial = CompletionRateReport(0, 0, 0, CompletionRateSource.LEGACY_ACTIVE_TASK_FALLBACK))
    val completionSourceLabel = LocalStrings.current.childGoalTracker.completionRateSourceLabel(
        completion.source == CompletionRateSource.DAILY_SNAPSHOT
    )

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
                                strings.lifetimeValue(lifetimePoints),
                                if (pendingPoints > 0) strings.balanceSubPending(totalPoints, pendingPoints)
                                else strings.balanceSub(totalPoints)
                            )
                            ReportStat(
                                "🔥", strings.streakLabel, strings.streakValue(streak),
                                if (streak > 0) strings.streakSubPositive else strings.streakSubZero
                            )
                            ReportStat("🐾", strings.petLabel, petStage, strings.petSub)
                            ReportStat(
                                "✅", strings.todayTasksLabel, strings.todayValue(completion.completed, completion.total),
                                if (completion.rate == 100 && completion.total > 0) strings.todaySubAll else strings.todaySubMore
                            )
                            Text(
                                completionSourceLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    completion.completed,
                                    completion.total,
                                    exchanges.size,
                                    achievedWishes,
                                    completionSourceLabel,
                                ),
                                mimeType = "text/plain"
                            )
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(strings.shareButton)
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
