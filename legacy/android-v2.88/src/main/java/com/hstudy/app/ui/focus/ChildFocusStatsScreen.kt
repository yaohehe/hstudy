package com.hstudy.app.ui.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hstudy.app.HStudyApp
import com.hstudy.app.ui.common.*
import java.util.*

@Composable
fun ChildFocusStatsScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = HStudyApp.instance
    val totalMinutes by app.focusRepository.getTotalFocusMinutes().collectAsState(initial = 0)

    // 最近7天数据
    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
    val dailyData by app.focusRepository.getDailyFocusMinutesSince(cal.timeInMillis)
        .collectAsState(initial = emptyList())

    val todayMinutes by app.focusRepository.getTotalFocusMinutesForDate(
        com.hstudy.app.data.repository.FocusRepository.getTodayString()
    ).collectAsState(initial = 0)

    // 专注力训练数据（舒尔特方格）
    val trainingCount by app.userPreferences.focusTrainingCount.collectAsState(initial = 0)
    val trainingBestMs by app.userPreferences.focusTrainingBestMs.collectAsState(initial = 0L)

    Scaffold(
        topBar = { HStudyTopBar(title = "📊 专注统计", onNavigateBack = onNavigateBack) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 总览
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatCard("今日专注", "${todayMinutes}分钟", "⏱️")
                    StatCard("累计专注", "${totalMinutes}分钟", "📚")
                    StatCard("约", "${totalMinutes / 60}小时", "🕐")
                }
            }

            // 专注力训练统计（舒尔特方格训练数据闭环）
            if (trainingCount > 0) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🎯 专注力训练", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                            StatCard("累计训练", "${trainingCount}次", "🎯")
                            if (trainingBestMs > 0L) {
                                StatCard("方格最佳", "${"%.1f".format(trainingBestMs / 1000f)}秒", "🏆")
                            }
                        }
                        Text("舒尔特方格练选择性注意，数字记忆练工作记忆，坚持训练专注力更强！",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // 7日柱状图
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("最近7天专注时长", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    val maxMinutes = dailyData.maxOfOrNull { it.totalMinutes }?.toFloat()?.coerceAtLeast(1f) ?: 1f
                    val barColor = MaterialTheme.colorScheme.primary
                    val trackColor = MaterialTheme.colorScheme.surfaceVariant
                    val textColor = MaterialTheme.colorScheme.onSurface

                    Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                        val barWidth = size.width / 10f
                        val gap = barWidth * 0.3f
                        val chartHeight = size.height - 40f

                        dailyData.forEachIndexed { index, data ->
                            val x = gap + index * (barWidth + gap)
                            val barHeight = (data.totalMinutes / maxMinutes) * chartHeight

                            // 背景柱
                            drawRect(
                                color = trackColor,
                                topLeft = Offset(x, 0f),
                                size = Size(barWidth, chartHeight)
                            )
                            // 数据柱
                            drawRect(
                                color = barColor,
                                topLeft = Offset(x, chartHeight - barHeight),
                                size = Size(barWidth, barHeight)
                            )
                        }
                    }

                    // X轴标签
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        dailyData.forEach { data ->
                            val dayLabel = data.date.takeLast(5) // MM-dd
                            Text(dayLabel, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // 详细记录
            Text("专注记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            if (dailyData.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.padding(40.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("暂无专注记录，开始你的第一个番茄钟吧！🍅",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                dailyData.takeLast(7).reversed().forEach { data ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📅", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(data.date, style = MaterialTheme.typography.bodyMedium)
                                Text("${data.totalMinutes} 分钟",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            Text("${data.totalMinutes}m", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, emoji: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, style = MaterialTheme.typography.headlineSmall)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
