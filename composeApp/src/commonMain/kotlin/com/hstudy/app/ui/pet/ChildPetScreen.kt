package com.hstudy.app.ui.pet

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.localizedPetStageName
import com.hstudy.app.i18n.petSceneCopy
import com.hstudy.app.ui.common.AmbientBackground
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// 宠物成长阶段（petStages）、getStageIndex 已移至 PetState.kt（同包共享）

@Composable
fun ChildPetScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = AppGraph
    val strings = LocalStrings.current.pet
    val displayLanguage = LocalDisplayLanguage.current
    val lifetimePoints by app.pointRepository.getLifetimeEarnedPoints().collectAsState(initial = 0)
    val totalPoints by app.pointRepository.getTotalPoints().collectAsState(initial = 0)
    val tasks by app.taskRepository.getAllActiveTasks().collectAsState(initial = emptyList())
    val today = com.hstudy.app.data.repository.PointRepository.getTodayString()
    val completedIds by app.pointRepository.getCompletedTaskIdsForDate(today).collectAsState(initial = emptyList())

    // 宠物照料状态（饱食度/心情）
    var fullness by remember { mutableIntStateOf(80) }
    var mood by remember { mutableIntStateOf(80) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 宠物场景装饰（已购买/已装扮）
    val ownedScenes by app.userPreferences.ownedPetScenes.collectAsState(initial = emptySet())
    val equippedSceneId by app.userPreferences.equippedPetScene.collectAsState(initial = "home")
    val equippedScene = getPetScene(equippedSceneId)

    // 加载照料状态并应用每日衰减（隔了一天没照顾，饱食度/心情会下降，可降到0，鼓励每天照顾宠物）
    LaunchedEffect(Unit) {
        val storedFullness = app.userPreferences.petFullness.first()
        val storedMood = app.userPreferences.petMood.first()
        val lastCare = app.userPreferences.petLastCareDate.first()
        if (lastCare != null && lastCare != today) {
            // 计算间隔天数，每天衰减 15，可降到 0
            val daysMissed = try {
                val fmt = com.hstudy.app.ui.dt.SimpleDateFormat("yyyy-MM-dd", com.hstudy.app.ui.dt.Locale.getDefault())
                val last = fmt.parse(lastCare)?.time ?: com.hstudy.app.ui.dt.nowMillis()
                val now = fmt.parse(today)?.time ?: com.hstudy.app.ui.dt.nowMillis()
                ((now - last) / (24L * 60 * 60 * 1000)).toInt().coerceAtLeast(1)
            } catch (_: Exception) { 1 }
            val decay = daysMissed * 15
            fullness = (storedFullness - decay).coerceAtLeast(0)
            mood = (storedMood - decay).coerceAtLeast(0)
            app.userPreferences.setPetCare(fullness, mood, today)
        } else {
            fullness = storedFullness
            mood = storedMood
        }
    }

    val stageIndex = getStageIndex(lifetimePoints)
    val currentStage = petStages[stageIndex]
    val nextStage = petStages.getOrNull(stageIndex + 1)
    val isMaxLevel = nextStage == null

    // 下一阶段进度
    val progress = if (isMaxLevel) 1f else {
        val span = nextStage!!.requiredPoints - currentStage.requiredPoints
        ((lifetimePoints - currentStage.requiredPoints).toFloat() / span).coerceIn(0f, 1f)
    }
    val pointsToNext = if (isMaxLevel) 0 else nextStage!!.requiredPoints - lifetimePoints

    // 今日完成度 + 照料状态 共同决定宠物心情（危急状态优先提示）
    val todayCompleted = completedIds.size
    val todayTotal = tasks.size
    val condition = petCondition(fullness, mood)
    val moodMessage = when {
        condition == PetCondition.CRITICAL -> strings.moodCritical
        condition == PetCondition.HUNGRY && fullness < 30 -> strings.moodHungry
        condition == PetCondition.HUNGRY -> strings.moodBored
        todayTotal > 0 && todayCompleted == todayTotal -> strings.moodAllDone
        todayCompleted > 0 -> strings.moodProgress(todayCompleted)
        else -> strings.moodNewDay
    }

    // 宠物浮动 + 呼吸动画
    val floatTransition = rememberInfiniteTransition(label = "petFloat")
    val floatOffset by floatTransition.animateFloat(
        initialValue = 0f, targetValue = 12f,
        animationSpec = infiniteRepeatable(animation = tween(1600, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "floatOffset"
    )
    val breathScale by floatTransition.animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "breathScale"
    )

    Scaffold(
        topBar = { HStudyTopBar(title = strings.title, onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            AmbientBackground()
            ResponsiveContent(
                windowSizeClass = windowSizeClass,
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 宠物展示区（背景为已装扮的场景）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.linearGradient(equippedScene.bgColors),
                                shape = MaterialTheme.shapes.extraLarge
                            )
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 场景图标点缀
                        Text(equippedScene.emoji, fontSize = 40.sp,
                            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))
                        Box(
                            modifier = Modifier
                                .offset(y = (-floatOffset).dp)
                                .scale(breathScale),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(currentStage.emoji, fontSize = 110.sp)
                        }
                    }
                    Text("${equippedScene.emoji} ${petSceneCopy[displayLanguage]?.get(equippedScene.id) ?: equippedScene.name}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(8.dp))

                    // 阶段名称
                    Text(localizedPetStageName(stageIndex, displayLanguage), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(strings.levelLine(stageIndex + 1, lifetimePoints), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(16.dp))

                    // 危急状态警示（宠物饥饿/昏迷，需花积分照料）
                    if (condition == PetCondition.CRITICAL) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("⚠️", style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    if (fullness <= 0 && mood <= 0) strings.criticalAll
                                    else if (fullness <= 0) strings.criticalHungry
                                    else strings.criticalBored,
                                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // 心情气泡
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text(moodMessage, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 照料区：饱食度/心情状态 + 喂养/陪玩按钮
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(strings.careTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                            // 饱食度
                            CareStatBar(label = strings.fullnessLabel, value = fullness, color = MaterialTheme.colorScheme.primary)
                            // 心情
                            CareStatBar(label = strings.moodLabel, value = mood, color = MaterialTheme.colorScheme.tertiary)

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        if (totalPoints < 10) {
                                            scope.launch { snackbarHostState.showSnackbar(strings.notEnoughPoints(10)) }
                                        } else {
                                            scope.launch {
                                                app.pointRepository.spendPoints(10, "喂养宠物")
                                                fullness = (fullness + 25).coerceAtMost(100)
                                                app.userPreferences.setPetCare(fullness, mood, today)
                                                snackbarHostState.showSnackbar(strings.fedMessage)
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text(strings.feedButton(10)) }
                                Button(
                                    onClick = {
                                        if (totalPoints < 5) {
                                            scope.launch { snackbarHostState.showSnackbar(strings.notEnoughPoints(5)) }
                                        } else {
                                            scope.launch {
                                                app.pointRepository.spendPoints(5, "陪宠物玩耍")
                                                mood = (mood + 25).coerceAtMost(100)
                                                app.userPreferences.setPetCare(fullness, mood, today)
                                                snackbarHostState.showSnackbar(strings.playedMessage)
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text(strings.playButton(5)) }
                            }
                            Text(strings.careTip,
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 进化进度
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isMaxLevel) {
                                Text(strings.maxLevel, style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(strings.progressTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                LinearProgressIndicator(
                                    progress = progress,
                                    modifier = Modifier.fillMaxWidth().height(12.dp),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Text(strings.nextStage(pointsToNext, nextStage!!.emoji, localizedPetStageName(stageIndex + 1, displayLanguage)),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 成长之路：所有阶段一览
                    Text(strings.growthPathTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    petStages.forEachIndexed { i, stage ->
                        val unlocked = lifetimePoints >= stage.requiredPoints
                        val isCurrent = i == stageIndex
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isCurrent -> MaterialTheme.colorScheme.secondaryContainer
                                    unlocked -> MaterialTheme.colorScheme.surfaceVariant
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (unlocked) stage.emoji else "🔒",
                                    fontSize = 26.sp,
                                    modifier = Modifier.width(40.dp),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(localizedPetStageName(i, displayLanguage), style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(strings.stageUnlock(stage.requiredPoints), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (isCurrent) {
                                    Text(strings.currentBadge, style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                } else if (unlocked) {
                                    Text("✓", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 装饰商店：用积分给宠物购买/装扮生活场景
                    Text(strings.shopTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(strings.shopHint, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    petScenes.forEach { scene ->
                        val owned = scene.id in ownedScenes || scene.price == 0
                        val equipped = equippedSceneId == scene.id
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (equipped) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(scene.emoji, fontSize = 26.sp, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(petSceneCopy[displayLanguage]?.get(scene.id) ?: scene.name, style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (equipped) FontWeight.Bold else FontWeight.Normal)
                                    Text(
                                        if (scene.price == 0) strings.defaultScene else strings.scenePrice(scene.price),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                when {
                                    equipped -> Text(strings.equippedBadge, style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    owned -> FilledTonalButton(
                                        onClick = { scope.launch { app.userPreferences.setEquippedScene(scene.id) } },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) { Text(strings.equipButton, style = MaterialTheme.typography.labelMedium) }
                                    else -> Button(
                                        onClick = {
                                            if (totalPoints < scene.price) {
                                                scope.launch { snackbarHostState.showSnackbar(strings.notEnoughPoints(scene.price)) }
                                            } else {
                                                scope.launch {
                                                    app.pointRepository.spendPoints(scene.price, "购买宠物场景：${scene.name}")
                                                    app.userPreferences.addOwnedScene(scene.id)
                                                    app.userPreferences.setEquippedScene(scene.id)
                                                    snackbarHostState.showSnackbar(strings.purchaseSuccess(petSceneCopy[displayLanguage]?.get(scene.id) ?: scene.name))
                                                }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) { Text(strings.buyButton, style = MaterialTheme.typography.labelMedium) }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/** 照料状态条：标签 + 进度条 + 数值 */
@Composable
private fun CareStatBar(label: String, value: Int, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(80.dp))
        LinearProgressIndicator(
            progress = value / 100f,
            modifier = Modifier.weight(1f).height(10.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("$value", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(30.dp))
    }
}
