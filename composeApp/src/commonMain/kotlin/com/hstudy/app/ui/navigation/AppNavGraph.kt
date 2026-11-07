package com.hstudy.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hstudy.app.AppGraph
import com.hstudy.app.ui.child.*
import com.hstudy.app.ui.focus.*
import com.hstudy.app.ui.habit.*
import com.hstudy.app.ui.mistake.*
import com.hstudy.app.ui.parent.*
import com.hstudy.app.ui.pet.ChildPetScreen
import com.hstudy.app.ui.proof.*
import com.hstudy.app.ui.report.GrowthReportScreen
import com.hstudy.app.ui.role.RoleSelectScreen
import com.hstudy.app.ui.schedule.ChildScheduleTimelineScreen
import com.hstudy.app.ui.schedule.ParentScheduleManageScreen
import com.hstudy.app.ui.target.ChildDailyTargetScreen
import com.hstudy.app.ui.theme.LocalWindowSizeClass
import com.hstudy.app.ui.theme.WindowSizeClass
import com.hstudy.app.ui.weekly.WeeklyTrackerScreen
import com.hstudy.app.ui.wish.ChildWishGoalScreen
import com.hstudy.app.ui.wish.ParentWishGoalManageScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 跨平台导航图：替代原 androidx.navigation NavHost。
 * 路由字符串与参数编码规则与原 AppNavGraph 完全一致（见 [Screen]），
 * 通过 [AppNavigator] 的 currentRoute + 前缀匹配解析带参路由。
 */
@Composable
fun AppNavGraph(navigator: AppNavigator) {
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val windowSizeClass = LocalWindowSizeClass.current
    val route = navigator.currentRoute

    val back: () -> Unit = { navigator.popBackStack() }

    when {
        // ====== 角色选择 ======
        route == Screen.RoleSelect.route -> RoleSelectDestination(
            windowSizeClass = windowSizeClass,
            onEnterParent = {
                scope.launch { app.userPreferences.setRole("parent") }
                navigator.navigateAndClearStack(Screen.ParentHome.route)
            },
            onEnterChild = {
                scope.launch { app.userPreferences.setRole("child") }
                navigator.navigateAndClearStack(Screen.ChildHome.route)
            }
        )

        // ====== 父母端 ======
        route == Screen.ParentHome.route -> ParentHomeScreen(
            onNavigate = { target -> navigator.navigate(target) },
            onSwitchRole = {
                scope.launch { app.userPreferences.clearRole() }
                navigator.navigateAndClearStack(Screen.RoleSelect.route)
            },
            windowSizeClass = windowSizeClass
        )
        route == Screen.ParentTaskManage.route -> ParentTaskManageScreen(
            onNavigateBack = back,
            onEditTask = { taskId -> navigator.navigate(Screen.ParentTaskEdit.createRoute(taskId)) },
            windowSizeClass = windowSizeClass
        )
        route.startsWith(argPrefix(Screen.ParentTaskEdit.route)) -> {
            val taskId = pathArg(route, Screen.ParentTaskEdit.route).toLongOrNull() ?: 0L
            ParentTaskEditScreen(taskId = taskId, onNavigateBack = back, windowSizeClass = windowSizeClass)
        }
        route == Screen.ParentPoints.route ->
            ParentPointsScreen(onNavigateBack = back, windowSizeClass = windowSizeClass)
        route == Screen.ParentRewardManage.route -> ParentRewardManageScreen(
            onNavigateBack = back,
            onEditReward = { rewardId -> navigator.navigate(Screen.ParentRewardEdit.createRoute(rewardId)) },
            windowSizeClass = windowSizeClass
        )
        route.startsWith(argPrefix(Screen.ParentRewardEdit.route)) -> {
            val rewardId = pathArg(route, Screen.ParentRewardEdit.route).toLongOrNull() ?: 0L
            ParentRewardEditScreen(rewardId = rewardId, onNavigateBack = back, windowSizeClass = windowSizeClass)
        }
        route == Screen.ParentDashboard.route ->
            ParentDashboardScreen(onNavigateBack = back, windowSizeClass = windowSizeClass)
        route == Screen.ParentWeeklyReport.route ->
            WeeklyTrackerScreen(onNavigateBack = back, isParentView = true, windowSizeClass = windowSizeClass)
        route == Screen.ParentProofReview.route -> ParentProofReviewScreen(
            onNavigateBack = back,
            onViewPhoto = { path -> navigator.navigate(Screen.PhotoViewer.createRoute(path)) },
            windowSizeClass = windowSizeClass
        )
        route == Screen.ParentMistakeReview.route ->
            ParentMistakeReviewScreen(onNavigateBack = back, windowSizeClass = windowSizeClass)
        route == Screen.ParentSettings.route ->
            ParentSettingsScreen(onNavigateBack = back, windowSizeClass = windowSizeClass)
        route == Screen.ParentApproval.route ->
            ParentApprovalScreen(onNavigateBack = back, windowSizeClass = windowSizeClass)
        route == Screen.ParentAssignTask.route ->
            ParentAssignTaskScreen(onNavigateBack = back, windowSizeClass = windowSizeClass)
        route == Screen.ParentTaskTemplate.route ->
            ParentTaskTemplateScreen(onNavigateBack = back, windowSizeClass = windowSizeClass)
        route == Screen.ParentWishGoalManage.route ->
            ParentWishGoalManageScreen(onNavigateBack = back, windowSizeClass = windowSizeClass)
        route == Screen.ParentGrowthReport.route ->
            GrowthReportScreen(onNavigateBack = back, windowSizeClass = windowSizeClass)
        route == Screen.ParentScheduleManage.route ->
            ParentScheduleManageScreen(onNavigateBack = back, windowSizeClass = windowSizeClass)
        route == Screen.ParentTutoringAdvice.route ->
            ParentTutoringAdviceScreen(onNavigateBack = back, windowSizeClass = windowSizeClass)

        // ====== 孩子端 ======
        route == Screen.ChildHome.route -> ChildHomeScreen(
            onNavigate = { target -> navigator.navigate(target) },
            onSwitchRole = {
                scope.launch { app.userPreferences.clearRole() }
                navigator.navigateAndClearStack(Screen.RoleSelect.route)
            },
            windowSizeClass = windowSizeClass
        )
        route == Screen.ChildTaskList.route -> ChildTaskListScreen(
            onNavigateBack = back,
            onUploadProof = { taskId, taskTitle ->
                navigator.navigate(Screen.ChildProofCapture.createRoute(taskId, taskTitle))
            },
            windowSizeClass = windowSizeClass
        )
        route == Screen.ChildPoints.route ->
            ChildPointsScreen(onNavigateBack = back, windowSizeClass = windowSizeClass)
        route == Screen.ChildRewardStore.route ->
            ChildRewardStoreScreen(onNavigateBack = back, windowSizeClass = windowSizeClass)
        route == Screen.ChildWeeklyView.route ->
            WeeklyTrackerScreen(onNavigateBack = back, isParentView = false, windowSizeClass = windowSizeClass)
        route == Screen.ChildLuckyDraw.route ->
            ChildLuckyDrawScreen(onNavigateBack = back, windowSizeClass = windowSizeClass)

        // ====== 错题本 ======
        route == Screen.ChildMistakeList.route -> ChildMistakeListScreen(
            onNavigateBack = back,
            onAddMistake = { navigator.navigate(Screen.ChildMistakeAdd.route) },
            onViewDetail = { id -> navigator.navigate(Screen.ChildMistakeDetail.createRoute(id)) },
            windowSizeClass = windowSizeClass
        )
        route == Screen.ChildMistakeAdd.route ->
            ChildMistakeAddScreen(onNavigateBack = back)
        route.startsWith(argPrefix(Screen.ChildMistakeDetail.route)) -> {
            val mistakeId = pathArg(route, Screen.ChildMistakeDetail.route).toLongOrNull() ?: 0L
            ChildMistakeDetailScreen(mistakeId = mistakeId, onNavigateBack = back)
        }

        // ====== 时间规划 ======
        route == Screen.ChildPomodoro.route -> ChildPomodoroScreen(
            onNavigateBack = back,
            windowSizeClass = windowSizeClass,
            onNavigateToTraining = { navigator.navigate(Screen.ChildFocusTraining.route) },
            onNavigateToMemory = { navigator.navigate(Screen.ChildMemoryTraining.route) }
        )
        route == Screen.ChildFocusTraining.route -> ChildFocusTrainingScreen(onNavigateBack = back)
        route == Screen.ChildMemoryTraining.route -> ChildMemoryTrainingScreen(onNavigateBack = back)
        route == Screen.ChildDailyPlan.route ->
            ChildDailyPlanScreen(onNavigateBack = back, windowSizeClass = windowSizeClass)
        route == Screen.ChildFocusStats.route ->
            ChildFocusStatsScreen(onNavigateBack = back, windowSizeClass = windowSizeClass)

        // ====== 完成证明 ======
        route.startsWith(argPrefix(Screen.ChildProofCapture.route)) -> {
            val args = pathArgs(route, Screen.ChildProofCapture.route)
            val taskId = args.getOrNull(0)?.toLongOrNull() ?: 0L
            val taskTitle = decodeRouteArg(args.getOrNull(1) ?: "")
            ChildProofCaptureScreen(taskId = taskId, taskTitle = taskTitle, onNavigateBack = back)
        }
        route.startsWith(argPrefix(Screen.PhotoViewer.route)) -> {
            val photoPath = decodeRouteArg(pathArg(route, Screen.PhotoViewer.route))
            PhotoViewerScreen(photoPath = photoPath, onNavigateBack = back)
        }

        // ====== 习惯养成 ======
        route == Screen.ChildHabitChallenge.route ->
            ChildHabitChallengeScreen(onNavigateBack = back, windowSizeClass = windowSizeClass)
        route == Screen.ChildGoalTracker.route ->
            ChildGoalTrackerScreen(onNavigateBack = back, windowSizeClass = windowSizeClass)
        route == Screen.ChildEfficiencyReport.route ->
            ChildEfficiencyReportScreen(onNavigateBack = back, windowSizeClass = windowSizeClass)

        // ====== 心愿存钱罐 + 每日日程 ======
        route == Screen.ChildWishGoal.route ->
            ChildWishGoalScreen(onNavigateBack = back, windowSizeClass = windowSizeClass)
        route == Screen.ChildScheduleTimeline.route ->
            ChildScheduleTimelineScreen(onNavigateBack = back, windowSizeClass = windowSizeClass)

        // ====== 学习宠物 ======
        route == Screen.ChildPet.route ->
            ChildPetScreen(onNavigateBack = back, windowSizeClass = windowSizeClass)

        // ====== 每日学习目标 ======
        route == Screen.ChildDailyTarget.route ->
            ChildDailyTargetScreen(onNavigateBack = back, windowSizeClass = windowSizeClass)

        // ====== 成就中心 ======
        route == Screen.ChildAchievements.route ->
            ChildAchievementsScreen(onNavigateBack = back, windowSizeClass = windowSizeClass)
    }
}

/** 角色选择目的地：保留原 AppNavGraph 中的家长密码门禁逻辑 */
@Composable
private fun RoleSelectDestination(
    windowSizeClass: WindowSizeClass,
    onEnterParent: () -> Unit,
    onEnterChild: () -> Unit
) {
    val app = AppGraph
    val scope = rememberCoroutineScope()
    var showPasswordGate by remember { mutableStateOf(false) }
    var pwdInput by remember { mutableStateOf("") }
    var pwdError by remember { mutableStateOf<String?>(null) }

    RoleSelectScreen(
        onParentSelected = {
            scope.launch {
                if (app.userPreferences.hasParentPassword.first()) {
                    pwdInput = ""
                    pwdError = null
                    showPasswordGate = true
                } else {
                    onEnterParent()
                }
            }
        },
        onChildSelected = onEnterChild,
        windowSizeClass = windowSizeClass
    )

    // 家长密码验证对话框
    if (showPasswordGate) {
        AlertDialog(
            onDismissRequest = { showPasswordGate = false },
            title = { Text("🔒 请输入家长密码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pwdInput,
                        onValueChange = { pwdInput = it; pwdError = null },
                        label = { Text("家长密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pwdError != null) {
                        Text(pwdError!!, color = Color.Red,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        if (app.userPreferences.verifyParentPassword(pwdInput)) {
                            showPasswordGate = false
                            onEnterParent()
                        } else {
                            pwdError = "密码错误，请重试"
                        }
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showPasswordGate = false }) { Text("取消") } }
        )
    }
}

/** 带参路由前缀："parent_task_edit/{taskId}" -> "parent_task_edit/" */
private fun argPrefix(pattern: String): String = pattern.substringBefore("{")

/** 取出带参路由中占位部分对应的实参（单参数路由） */
private fun pathArg(route: String, pattern: String): String = route.removePrefix(argPrefix(pattern))

/** 取出多参数路由的参数段列表（按占位数量切分） */
private fun pathArgs(route: String, pattern: String): List<String> {
    val count = pattern.count { it == '{' }
    val tail = pathArg(route, pattern)
    return if (count <= 1) listOf(tail) else tail.split("/")
}
