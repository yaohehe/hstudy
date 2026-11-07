package com.hstudy.app.ui.navigation

/**
 * 路由常量：route 字符串与原 Android 版（androidx.navigation）保持完全一致，
 * 带参路由使用 {arg} 占位，由 AppNavGraph 做前缀匹配与参数解析。
 *
 * 原实现用 java.net.URLEncoder 编码自由文本参数（任务标题、照片路径），
 * 跨平台版改用 UTF-8 百分号编码（[encodeRouteArg]），语义等价。
 */
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
            val encoded = encodeRouteArg(taskTitle)
            return "child_proof_capture/$taskId/$encoded"
        }
    }
    data object PhotoViewer : Screen("photo_viewer/{photoPath}") {
        fun createRoute(photoPath: String): String {
            val encoded = encodeRouteArg(photoPath)
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

/** 路由参数的百分号编码（等价原 URLEncoder.encode(title, "UTF-8")，保留 URL 安全字符） */
fun encodeRouteArg(value: String): String = buildString {
    for (ch in value) {
        when {
            ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.' || ch == '~' -> append(ch)
            else -> {
                for (b in ch.toString().encodeToByteArray()) {
                    append('%').append(b.toInt().and(0xFF).toString(16).uppercase().padStart(2, '0'))
                }
            }
        }
    }
}

/** 解码路由参数（等价原 URLDecoder.decode(x, "UTF-8")）；解码失败时返回原文 */
fun decodeRouteArg(value: String): String {
    if (!value.contains('%')) return value
    return try {
        val bytes = buildList {
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '%' && i + 2 < value.length) {
                    val hex = value.substring(i + 1, i + 3)
                    add(hex.toIntOrNull(16)?.toByte() ?: error("bad escape"))
                    i += 3
                } else {
                    // 未转义字符：非 ASCII 时按其 UTF-8 字节加入，保证多字节字符不被截断
                    if (c.code <= 0x7F) add(c.code.toByte()) else addAll(c.toString().encodeToByteArray().toList())
                    i++
                }
            }
        }
        bytes.toByteArray().decodeToString()
    } catch (_: Exception) {
        value
    }
}
