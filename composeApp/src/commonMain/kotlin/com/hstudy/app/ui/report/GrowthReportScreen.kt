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
import com.hstudy.app.platform.Platform
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent

/** 宠物阶段（与学习宠物页保持一致） */
private val reportPetStages = listOf(
    "🥚" to 0, "🐣" to 100, "🐤" to 300, "🐱" to 600,
    "🦊" to 1000, "🦁" to 2000, "🦄" to 4000, "🐉" to 8000
)

private fun petStageName(lifetime: Int): String {
    val names = listOf("宠物蛋", "刚破壳", "小黄鸡", "小猫咪", "小狐狸", "小狮子", "独角兽", "小神龙")
    var idx = 0
    reportPetStages.forEachIndexed { i, (_, req) -> if (lifetime >= req) idx = i }
    return "${reportPetStages[idx].first} ${names[idx]}"
}

/**
 * 成长报告：汇总孩子的成长数据，家长可一键分享到家族群
 */
@Composable
fun GrowthReportScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
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
    val petStage = petStageName(lifetimePoints)

    // 生成分享文案
    val reportText = buildString {
        appendLine("📚 【HStudy 学习成长报告】")
        appendLine("🌟 累计获得积分：$lifetimePoints 分")
        appendLine("🔥 当前连续打卡：$streak 天")
        appendLine("🐾 学习宠物：$petStage")
        appendLine("✅ 今日完成任务：${completedIds.size}/${tasks.size} 个")
        appendLine("🎁 已兑换奖励：${exchanges.size} 次")
        appendLine("🐷 已达成心愿：$achievedWishes 个")
        appendLine("—— 每一份坚持都值得被看见 ❤️")
    }

    Scaffold(
        topBar = { HStudyTopBar(title = "📊 成长报告", onNavigateBack = onNavigateBack) },
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
                    Text("🌱 孩子的成长足迹", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("每一份坚持，都值得被看见", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(20.dp))

                    // 核心数据卡片
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            ReportStat("🌟", "累计获得积分", "$lifetimePoints 分", "当前余额 $totalPoints 分")
                            ReportStat("🔥", "当前连续打卡", "$streak 天", if (streak > 0) "坚持就是胜利！" else "今天开始打卡吧")
                            ReportStat("🐾", "学习宠物", petStage, "累计积分越多，宠物越强大")
                            ReportStat("✅", "今日完成任务", "${completedIds.size}/${tasks.size} 个", if (completedIds.size == tasks.size && tasks.isNotEmpty()) "今天全部完成，太棒了！" else "继续加油")
                            ReportStat("🎁", "已兑换奖励", "${exchanges.size} 次", "用努力换来的奖励最甜")
                            ReportStat("🐷", "已达成心愿", "$achievedWishes 个", "攒钱实现大梦想")
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 分享按钮
                    Button(
                        onClick = {
                            // 跨平台分享：Intent.ACTION_SEND -> Platform.fileSharer 文本分享面板
                            Platform.fileSharer.shareTextFile(
                                fileName = "hstudy_growth_report.txt",
                                content = reportText,
                                mimeType = "text/plain"
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📤 分享成长报告到家族群", modifier = Modifier.padding(vertical = 6.dp))
                    }
                    Text("把孩子的进步分享给家人，一起见证成长～", style = MaterialTheme.typography.labelSmall,
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
