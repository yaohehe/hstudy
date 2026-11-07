package com.hstudy.app.ui.child

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hstudy.app.HStudyApp
import com.hstudy.app.ui.common.AchievementManager
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent

/**
 * 成就中心：集中展示全部成就勋章，已解锁高亮、未解锁显示达成进度，
 * 让成就系统更有"收集感"，激励孩子持续达成各项科学学习行为。
 */
@Composable
fun ChildAchievementsScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = HStudyApp.instance
    val unlocked by app.habitRepository.getAllAchievements().collectAsState(initial = emptyList())
    val allDefs = AchievementManager.allAchievements
    val unlockedTypes = unlocked.map { it.type }.toSet()

    // 收集每个成就的达成进度
    var progressMap by remember { mutableStateOf<Map<String, Pair<Int, Int>>>(emptyMap()) }
    LaunchedEffect(Unit) {
        progressMap = AchievementManager.getProgress(app)
    }

    val unlockedCount = allDefs.count { it.type in unlockedTypes }

    Scaffold(
        topBar = { HStudyTopBar(title = "🏅 成就中心", onNavigateBack = onNavigateBack) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            ResponsiveContent(
                windowSizeClass = windowSizeClass,
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 解锁进度汇总
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🏅 我的成就", style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("$unlockedCount/${allDefs.size}", style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            LinearProgressIndicator(
                                progress = if (allDefs.isNotEmpty()) unlockedCount.toFloat() / allDefs.size else 0f,
                                modifier = Modifier.fillMaxWidth().height(10.dp),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                if (unlockedCount == allDefs.size) "🎉 太厉害了！已解锁全部成就！"
                                else "继续加油，解锁更多成就勋章！",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // 全部成就列表
                    allDefs.forEach { def ->
                        val isUnlocked = def.type in unlockedTypes
                        val (cur, target) = progressMap[def.type] ?: (0 to 1)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isUnlocked) MaterialTheme.colorScheme.tertiaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(def.emoji, style = MaterialTheme.typography.headlineSmall)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(def.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(def.description, style = MaterialTheme.typography.bodySmall,
                                        color = if (isUnlocked) MaterialTheme.colorScheme.onTertiaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (!isUnlocked) {
                                        LinearProgressIndicator(
                                            progress = if (target > 0) (cur.toFloat() / target).coerceIn(0f, 1f) else 0f,
                                            modifier = Modifier.fillMaxWidth().height(6.dp),
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        Text("$cur/$target", style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (isUnlocked) {
                                    Text("✅", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
