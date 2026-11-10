package com.hstudy.app.ui.mistake

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.MistakeSubject
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.ui.common.*

@Composable
fun ParentMistakeReviewScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val strings = LocalStrings.current.parentMistakeReview
    val shared = LocalStrings.current.mistake
    val app = AppGraph
    val allMistakes by app.mistakeRepository.getAllMistakes().collectAsState(initial = emptyList())
    val masteredCount by app.mistakeRepository.getMasteredCount().collectAsState(initial = 0)

    // 统计
    val totalCount = allMistakes.size
    val unmasteredCount = totalCount - masteredCount

    // 按科目统计
    val subjectStats = MistakeSubject.entries.associateWith { subject ->
        allMistakes.count { it.subject == subject }
    }

    Scaffold(
        topBar = {
            HStudyTopBar(title = strings.title, onNavigateBack = onNavigateBack)
        }
    ) { padding ->
        ResponsiveContent(
            windowSizeClass = windowSizeClass,
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    // 概览卡片
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(strings.overviewTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                StatItem(strings.statTotal, "$totalCount", MaterialTheme.colorScheme.primary)
                                StatItem(strings.statUnmastered, "$unmasteredCount", MaterialTheme.colorScheme.error)
                                StatItem(strings.statMastered, "$masteredCount", MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }

                // 科目分布
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(strings.subjectDistribution, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            subjectStats.forEach { (subject, count) ->
                                if (count > 0) {
                                    val subjectEmoji = when (subject) {
                                        MistakeSubject.MATH -> "🔢"
                                        MistakeSubject.CHINESE -> "📝"
                                        MistakeSubject.ENGLISH -> "🔤"
                                        MistakeSubject.SCIENCE -> "🔬"
                                        MistakeSubject.OTHER -> "📋"
                                    }
                                    val subjectName = when (subject) {
                                        MistakeSubject.MATH -> shared.subjectMath
                                        MistakeSubject.CHINESE -> shared.subjectChinese
                                        MistakeSubject.ENGLISH -> shared.subjectEnglish
                                        MistakeSubject.SCIENCE -> shared.subjectScience
                                        MistakeSubject.OTHER -> shared.subjectOther
                                    }
                                    val ratio = if (totalCount > 0) count.toFloat() / totalCount else 0f
                                    Row(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("$subjectEmoji $subjectName", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(80.dp))
                                        LinearProgressIndicator(
                                            progress = ratio,
                                            modifier = Modifier.weight(1f).height(8.dp),
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("$count", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(30.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // 最近错题
                item {
                    Text(strings.recentMistakes, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp))
                }

                items(allMistakes.take(10), key = { it.id }) { mistake ->
                    val subjectEmoji = when (mistake.subject) {
                        MistakeSubject.MATH -> "🔢"
                        MistakeSubject.CHINESE -> "📝"
                        MistakeSubject.ENGLISH -> "🔤"
                        MistakeSubject.SCIENCE -> "🔬"
                        MistakeSubject.OTHER -> "📋"
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(subjectEmoji, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mistake.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(if (mistake.isMastered) shared.masteredStatus else shared.unmasteredStatus,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (mistake.isMastered) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
