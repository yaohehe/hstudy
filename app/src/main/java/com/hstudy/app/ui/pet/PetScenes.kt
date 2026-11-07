package com.hstudy.app.ui.pet

import androidx.compose.ui.graphics.Color

/**
 * 宠物生活场景：孩子用积分购买并装扮，给宠物一个漂亮的"家"
 */
data class PetScene(
    val id: String,
    val name: String,
    val emoji: String,
    val price: Int,              // 0 = 默认免费
    val bgColors: List<Color>    // 宠物展示区渐变背景色
)

val petScenes = listOf(
    PetScene("home", "我的小窝", "🏠", 0, listOf(Color(0xFFFFF3E0), Color(0xFFFFE0B2))),
    PetScene("forest", "森林小屋", "🌳", 100, listOf(Color(0xFFC8E6C9), Color(0xFFA5D6A7))),
    PetScene("beach", "阳光海滩", "🏖️", 200, listOf(Color(0xFFB3E5FC), Color(0xFF81D4FA))),
    PetScene("candy", "糖果乐园", "🍭", 350, listOf(Color(0xFFF8BBD0), Color(0xFFF48FB1))),
    PetScene("castle", "梦幻城堡", "🏰", 600, listOf(Color(0xFFD1C4E9), Color(0xFFB39DDB))),
    PetScene("space", "浩瀚星空", "🌌", 1000, listOf(Color(0xFF90CAF9), Color(0xFF5C6BC0))),
    PetScene("rainbow", "彩虹天堂", "🌈", 2000, listOf(Color(0xFFFFCDD2), Color(0xFFBBDEFB)))
)

/** 根据场景ID获取场景（找不到返回默认小窝） */
fun getPetScene(id: String): PetScene = petScenes.find { it.id == id } ?: petScenes[0]
