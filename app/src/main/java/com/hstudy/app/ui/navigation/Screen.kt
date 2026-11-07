package com.hstudy.app.ui.navigation

import java.net.URLEncoder

sealed class Screen(val route: String) {
    // 角色选择
    data object RoleSelect : Screen("role_select")

    // 父母端
    data object ParentHome : Screen("parent_home")
    data object ParentTaskManage : Screen("parent_task_manage")
    data object ParentTaskEdit : Screen("parent_task_edit/{taskId}") {
        fun createRoute(taskId: Long = 0L) = "parent_task_edit/$taskId"
    }
    data object ParentPoints : Screen("parent_points")
    data object ParentRewardManage : Screen("parent_reward_manage")
    data object ParentRewardEdit : Screen("parent_reward_edit/{rewardId}") {
        fun createRoute(rewardId: Long = 0L) = "parent_reward_edit/$rewardId"
    }
    data object ParentDashboard : Screen("parent_dashboard")
    data object ParentWeeklyReport : Screen("parent_weekly_report")
    data object ParentGrowthReport : Screen("parent_growth_report")
    data object ParentProofReview : Screen("parent_proof_review")
    data object ParentMistakeReview : Screen("parent_mistake_review")
    data object ParentSettings : Screen("parent_settings")
    data object ParentApproval : Screen("parent_approval")
    data object ParentAssignTask : Screen("parent_assign_task")
    data object ParentTaskTemplate : Screen("parent_task_template")
    data object ParentWishGoalManage : Screen("parent_wish_goal_manage")
    data object ParentScheduleManage : Screen("parent_schedule_manage")
    data object ParentTutoringAdvice : Screen("parent_tutoring_advice")

    // 孩子端
    data object ChildHome : Screen("child_home")
    data object ChildTaskList : Screen("child_task_list")
    data object ChildPoints : Screen("child_points")
    data object ChildRewardStore : Screen("child_reward_store")
    data object ChildWeeklyView : Screen("child_weekly_view")
    data object ChildLuckyDraw : Screen("child_lucky_draw")

    // 错题本
    data object ChildMistakeList : Screen("child_mistake_list")
    data object ChildMistakeAdd : Screen("child_mistake_add")
    data object ChildMistakeDetail : Screen("child_mistake_detail/{mistakeId}") {
        fun createRoute(mistakeId: Long) = "child_mistake_detail/$mistakeId"
    }

    // 时间规划
    data object ChildPomodoro : Screen("child_pomodoro")
    data object ChildFocusTraining : Screen("child_focus_training")
    data object ChildMemoryTraining : Screen("child_memory_training")
    data object ChildDailyPlan : Screen("child_daily_plan")
    data object ChildFocusStats : Screen("child_focus_stats")

    // 完成证明
    data object ChildProofCapture : Screen("child_proof_capture/{taskId}/{taskTitle}") {
        fun createRoute(taskId: Long, taskTitle: String): String {
            val encoded = URLEncoder.encode(taskTitle, "UTF-8")
            return "child_proof_capture/$taskId/$encoded"
        }
    }
    data object PhotoViewer : Screen("photo_viewer/{photoPath}") {
        fun createRoute(photoPath: String): String {
            val encoded = URLEncoder.encode(photoPath, "UTF-8")
            return "photo_viewer/$encoded"
        }
    }

    // 习惯养成
    data object ChildHabitChallenge : Screen("child_habit_challenge")
    data object ChildGoalTracker : Screen("child_goal_tracker")
    data object ChildEfficiencyReport : Screen("child_efficiency_report")

    // 心愿存钱罐 + 每日日程
    data object ChildWishGoal : Screen("child_wish_goal")
    data object ChildScheduleTimeline : Screen("child_schedule_timeline")

    // 学习宠物
    data object ChildPet : Screen("child_pet")

    // 每日学习目标
    data object ChildDailyTarget : Screen("child_daily_target")

    // 成就中心
    data object ChildAchievements : Screen("child_achievements")
}
