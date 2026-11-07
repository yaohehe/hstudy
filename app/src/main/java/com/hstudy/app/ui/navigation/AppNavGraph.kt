package com.hstudy.app.ui.navigation

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hstudy.app.HStudyApp
import com.hstudy.app.ui.role.RoleSelectScreen
import com.hstudy.app.ui.parent.*
import com.hstudy.app.ui.child.*
import com.hstudy.app.ui.mistake.*
import com.hstudy.app.ui.focus.*
import com.hstudy.app.ui.proof.*
import com.hstudy.app.ui.habit.*
import com.hstudy.app.ui.weekly.WeeklyTrackerScreen
import com.hstudy.app.ui.wish.ChildWishGoalScreen
import com.hstudy.app.ui.wish.ParentWishGoalManageScreen
import com.hstudy.app.ui.schedule.ChildScheduleTimelineScreen
import com.hstudy.app.ui.schedule.ParentScheduleManageScreen
import com.hstudy.app.ui.pet.ChildPetScreen
import com.hstudy.app.ui.target.ChildDailyTargetScreen
import com.hstudy.app.ui.report.GrowthReportScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URLDecoder

@Composable
fun AppNavGraph(windowSizeClass: WindowSizeClass) {
    val navController = rememberNavController()
    val app = HStudyApp.instance
    val scope = rememberCoroutineScope()

    var startDestination by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val role = app.userPreferences.currentRole.first()
        startDestination = when (role) {
            "parent" -> Screen.ParentHome.route
            "child" -> Screen.ChildHome.route
            else -> Screen.RoleSelect.route
        }
    }

    val start = startDestination ?: return

    NavHost(navController = navController, startDestination = start) {

        // ====== 角色选择 ======
        composable(Screen.RoleSelect.route) {
            // 家长密码门禁状态
            var showPasswordGate by remember { mutableStateOf(false) }
            var pwdInput by remember { mutableStateOf("") }
            var pwdError by remember { mutableStateOf<String?>(null) }

            RoleSelectScreen(
                onParentSelected = {
                    scope.launch {
                        val needPassword = app.userPreferences.hasParentPassword.first()
                        if (needPassword) {
                            pwdInput = ""
                            pwdError = null
                            showPasswordGate = true
                        } else {
                            app.userPreferences.setRole("parent")
                            navController.navigate(Screen.ParentHome.route) {
                                popUpTo(Screen.RoleSelect.route) { inclusive = true }
                            }
                        }
                    }
                },
                onChildSelected = {
                    scope.launch { app.userPreferences.setRole("child") }
                    navController.navigate(Screen.ChildHome.route) {
                        popUpTo(Screen.RoleSelect.route) { inclusive = true }
                    }
                },
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
                                Text(pwdError!!, color = androidx.compose.ui.graphics.Color.Red,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            scope.launch {
                                if (app.userPreferences.verifyParentPassword(pwdInput)) {
                                    showPasswordGate = false
                                    app.userPreferences.setRole("parent")
                                    navController.navigate(Screen.ParentHome.route) {
                                        popUpTo(Screen.RoleSelect.route) { inclusive = true }
                                    }
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

        // ====== 父母端 ======
        composable(Screen.ParentHome.route) {
            ParentHomeScreen(
                onNavigate = { route -> navController.navigate(route) },
                onSwitchRole = {
                    scope.launch { app.userPreferences.clearRole() }
                    navController.navigate(Screen.RoleSelect.route) { popUpTo(0) { inclusive = true } }
                },
                windowSizeClass = windowSizeClass
            )
        }
        composable(Screen.ParentTaskManage.route) {
            ParentTaskManageScreen(
                onNavigateBack = { navController.popBackStack() },
                onEditTask = { taskId -> navController.navigate(Screen.ParentTaskEdit.createRoute(taskId)) },
                windowSizeClass = windowSizeClass
            )
        }
        composable(
            Screen.ParentTaskEdit.route,
            arguments = listOf(navArgument("taskId") { type = NavType.LongType; defaultValue = 0L })
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getLong("taskId") ?: 0L
            ParentTaskEditScreen(taskId = taskId, onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }
        composable(Screen.ParentPoints.route) {
            ParentPointsScreen(onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }
        composable(Screen.ParentRewardManage.route) {
            ParentRewardManageScreen(
                onNavigateBack = { navController.popBackStack() },
                onEditReward = { rewardId -> navController.navigate(Screen.ParentRewardEdit.createRoute(rewardId)) },
                windowSizeClass = windowSizeClass
            )
        }
        composable(
            Screen.ParentRewardEdit.route,
            arguments = listOf(navArgument("rewardId") { type = NavType.LongType; defaultValue = 0L })
        ) { backStackEntry ->
            val rewardId = backStackEntry.arguments?.getLong("rewardId") ?: 0L
            ParentRewardEditScreen(rewardId = rewardId, onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }
        composable(Screen.ParentDashboard.route) {
            ParentDashboardScreen(onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }
        composable(Screen.ParentWeeklyReport.route) {
            WeeklyTrackerScreen(onNavigateBack = { navController.popBackStack() }, isParentView = true, windowSizeClass = windowSizeClass)
        }
        composable(Screen.ParentProofReview.route) {
            ParentProofReviewScreen(
                onNavigateBack = { navController.popBackStack() },
                onViewPhoto = { path -> navController.navigate(Screen.PhotoViewer.createRoute(path)) },
                windowSizeClass = windowSizeClass
            )
        }
        composable(Screen.ParentMistakeReview.route) {
            ParentMistakeReviewScreen(onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }
        composable(Screen.ParentSettings.route) {
            ParentSettingsScreen(onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }
        composable(Screen.ParentApproval.route) {
            ParentApprovalScreen(onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }
        composable(Screen.ParentAssignTask.route) {
            ParentAssignTaskScreen(onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }
        composable(Screen.ParentTaskTemplate.route) {
            ParentTaskTemplateScreen(onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }
        composable(Screen.ParentWishGoalManage.route) {
            ParentWishGoalManageScreen(onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }
        composable(Screen.ParentGrowthReport.route) {
            GrowthReportScreen(onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }
        composable(Screen.ParentScheduleManage.route) {
            ParentScheduleManageScreen(onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }
        composable(Screen.ParentTutoringAdvice.route) {
            ParentTutoringAdviceScreen(onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }

        // ====== 孩子端 ======
        composable(Screen.ChildHome.route) {
            ChildHomeScreen(
                onNavigate = { route -> navController.navigate(route) },
                onSwitchRole = {
                    scope.launch { app.userPreferences.clearRole() }
                    navController.navigate(Screen.RoleSelect.route) { popUpTo(0) { inclusive = true } }
                },
                windowSizeClass = windowSizeClass
            )
        }
        composable(Screen.ChildTaskList.route) {
            ChildTaskListScreen(
                onNavigateBack = { navController.popBackStack() },
                onUploadProof = { taskId, taskTitle ->
                    navController.navigate(Screen.ChildProofCapture.createRoute(taskId, taskTitle))
                },
                windowSizeClass = windowSizeClass
            )
        }
        composable(Screen.ChildPoints.route) {
            ChildPointsScreen(onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }
        composable(Screen.ChildRewardStore.route) {
            ChildRewardStoreScreen(onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }
        composable(Screen.ChildWeeklyView.route) {
            WeeklyTrackerScreen(onNavigateBack = { navController.popBackStack() }, isParentView = false, windowSizeClass = windowSizeClass)
        }
        composable(Screen.ChildLuckyDraw.route) {
            ChildLuckyDrawScreen(onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }

        // ====== 错题本 ======
        composable(Screen.ChildMistakeList.route) {
            ChildMistakeListScreen(
                onNavigateBack = { navController.popBackStack() },
                onAddMistake = { navController.navigate(Screen.ChildMistakeAdd.route) },
                onViewDetail = { id -> navController.navigate(Screen.ChildMistakeDetail.createRoute(id)) },
                windowSizeClass = windowSizeClass
            )
        }
        composable(Screen.ChildMistakeAdd.route) {
            ChildMistakeAddScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(
            Screen.ChildMistakeDetail.route,
            arguments = listOf(navArgument("mistakeId") { type = NavType.LongType })
        ) { backStackEntry ->
            val mistakeId = backStackEntry.arguments?.getLong("mistakeId") ?: 0L
            ChildMistakeDetailScreen(mistakeId = mistakeId, onNavigateBack = { navController.popBackStack() })
        }

        // ====== 时间规划 ======
        composable(Screen.ChildPomodoro.route) {
            ChildPomodoroScreen(
                onNavigateBack = { navController.popBackStack() },
                windowSizeClass = windowSizeClass,
                onNavigateToTraining = { navController.navigate(Screen.ChildFocusTraining.route) },
                onNavigateToMemory = { navController.navigate(Screen.ChildMemoryTraining.route) }
            )
        }
        composable(Screen.ChildFocusTraining.route) {
            ChildFocusTrainingScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.ChildMemoryTraining.route) {
            ChildMemoryTrainingScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.ChildDailyPlan.route) {
            ChildDailyPlanScreen(onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }
        composable(Screen.ChildFocusStats.route) {
            ChildFocusStatsScreen(onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }

        // ====== 完成证明 ======
        composable(
            Screen.ChildProofCapture.route,
            arguments = listOf(
                navArgument("taskId") { type = NavType.LongType },
                navArgument("taskTitle") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getLong("taskId") ?: 0L
            val taskTitle = URLDecoder.decode(backStackEntry.arguments?.getString("taskTitle") ?: "", "UTF-8")
            ChildProofCaptureScreen(taskId = taskId, taskTitle = taskTitle, onNavigateBack = { navController.popBackStack() })
        }
        composable(
            Screen.PhotoViewer.route,
            arguments = listOf(navArgument("photoPath") { type = NavType.StringType })
        ) { backStackEntry ->
            val photoPath = URLDecoder.decode(backStackEntry.arguments?.getString("photoPath") ?: "", "UTF-8")
            PhotoViewerScreen(photoPath = photoPath, onNavigateBack = { navController.popBackStack() })
        }

        // ====== 习惯养成 ======
        composable(Screen.ChildHabitChallenge.route) {
            ChildHabitChallengeScreen(onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }
        composable(Screen.ChildGoalTracker.route) {
            ChildGoalTrackerScreen(onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }
        composable(Screen.ChildEfficiencyReport.route) {
            ChildEfficiencyReportScreen(onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }
        composable(Screen.ChildWishGoal.route) {
            ChildWishGoalScreen(onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }
        composable(Screen.ChildScheduleTimeline.route) {
            ChildScheduleTimelineScreen(onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }
        composable(Screen.ChildPet.route) {
            ChildPetScreen(onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }
        composable(Screen.ChildDailyTarget.route) {
            ChildDailyTargetScreen(onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }
        composable(Screen.ChildAchievements.route) {
            ChildAchievementsScreen(onNavigateBack = { navController.popBackStack() }, windowSizeClass = windowSizeClass)
        }
    }
}
