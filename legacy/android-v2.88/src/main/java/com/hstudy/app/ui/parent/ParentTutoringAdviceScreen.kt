package com.hstudy.app.ui.parent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hstudy.app.HStudyApp
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent

/**
 * 家长辅导建议页：基于孩子学习数据生成具体可执行的辅导方法建议，
 * 帮助家长从"不知道怎么辅导"到"科学辅导"。
 */
@Composable
fun ParentTutoringAdviceScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = HStudyApp.instance
    val allMistakes by app.mistakeRepository.getAllMistakes().collectAsState(initial = emptyList())
    val allSessions by app.focusRepository.getAllSessions().collectAsState(initial = emptyList())

    var streak by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { streak = app.pointRepository.getCurrentStreak() }

    val adviceList = remember(allMistakes, allSessions, streak) {
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val recentFocus = allSessions.filter { it.completedAt >= weekAgo }.sumOf { it.durationMinutes }
        TutoringAdviceEngine.generate(allMistakes, recentFocus, streak)
    }

    Scaffold(
        topBar = { HStudyTopBar(title = "💡 辅导建议", onNavigateBack = onNavigateBack) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            ResponsiveContent(
                windowSizeClass = windowSizeClass,
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 说明
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Text("以下建议根据孩子的学科掌握度、错题情况和专注表现自动生成，帮助您更有针对性地辅导孩子。",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp))
                    }

                    // 建议列表
                    adviceList.forEach { advice ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${advice.emoji} ${advice.category}",
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold)
                                Text(advice.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(advice.detail, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
