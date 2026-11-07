package com.hstudy.app.i18n

/**
 * 角色选择页（ui/role/RoleSelectScreen）文案表。
 *
 * ZH 六条逐字来自 RoleSelectScreen.kt 第 75/83/98/99/106/107 行；
 * EN 按设计文档 §11 术语表（Study Points & Rewards / Build visible habits /
 * I'm the parent / I'm the learner / Points）。
 */
data class RoleStrings(
    val brandTitle: String,
    val tagline: String,
    val parentEntry: String,
    val parentDesc: String,
    val childEntry: String,
    val childDesc: String,
)

val RoleStringsZh = RoleStrings(
    brandTitle = "学习积分奖励",
    tagline = "好习惯，看得见 · 选择你的身份",
    parentEntry = "我是家长",
    parentDesc = "发布任务 · 管理积分 · 设置奖励",
    childEntry = "我是孩子",
    childDesc = "完成任务 · 获取积分 · 兑换奖励",
)

val RoleStringsEn = RoleStrings(
    brandTitle = "Study Points & Rewards",
    tagline = "Build visible habits · Choose who you are",
    parentEntry = "I'm the parent",
    parentDesc = "Assign tasks · Manage points · Set rewards",
    childEntry = "I'm the learner",
    childDesc = "Finish tasks · Earn points · Redeem rewards",
)
