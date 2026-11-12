package com.hstudy.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * 阶段 2 W7 宠物文案表守卫（沿用 StringsCopyTest/MistakeCopyTest/WishCopyTest 约定）：
 * 静态字段 ZH≠EN；函数型字段逐字双语钉死（Ruling 20-b）。
 *
 * 本文件只覆盖 [PetStrings] 一张表（孩子端宠物页 + 首页预览卡共用）。场景名/阶段名不在表内：
 * 场景名走 [petSceneCopy]（W0）、阶段名走 [localizedPetStageName]（W7 T1），覆盖性分别由
 * PresetRegistryTest 的 `petSceneCopy*` / `petStageCopy*` 守卫钉住；本文件的
 * `crossTableVocabularyPinned` 只抽样钉住两处注册表的取值（宠物页与成长报告同一译名），
 * 不重复全量覆盖。
 *
 * `petStatusMessage` 的 5 参签名与六个 when 分支没有单测：本文件只做文案表守卫与注册表抽样 pin；
 * 函数体是 `strings.status*` 的直取/直调（无阈值、无拼接），文案本身已由下面的
 * status* 静态断言与 `statusProgress` pin 逐字钉死，分支映射由 PetState 的 diff 复核。
 * 阈值判断仍在 `petCondition` 的原逻辑里，本任务未动。
 */
class PetCopyTest {

    @Test
    fun petStringsCopyDiffers() {
        val zh = AppStringsZh.pet
        val en = AppStringsEn.pet
        assertDiffers(
            zh.title to en.title,
            zh.careTitle to en.careTitle,
            zh.fullnessLabel to en.fullnessLabel,
            zh.moodLabel to en.moodLabel,
            zh.feedLabel to en.feedLabel,
            zh.playLabel to en.playLabel,
            zh.fedMessage to en.fedMessage,
            zh.playedMessage to en.playedMessage,
            zh.careTip to en.careTip,
            zh.maxLevel to en.maxLevel,
            zh.progressTitle to en.progressTitle,
            zh.currentBadge to en.currentBadge,
            zh.growthPathTitle to en.growthPathTitle,
            zh.criticalAll to en.criticalAll,
            zh.criticalHungry to en.criticalHungry,
            zh.criticalBored to en.criticalBored,
            zh.shopTitle to en.shopTitle,
            zh.shopHint to en.shopHint,
            zh.defaultScene to en.defaultScene,
            zh.equippedBadge to en.equippedBadge,
            zh.equipButton to en.equipButton,
            zh.buyButton to en.buyButton,
            zh.previewTip to en.previewTip,
            zh.statusCritical to en.statusCritical,
            zh.statusHungry to en.statusHungry,
            zh.statusBored to en.statusBored,
            zh.statusAllDone to en.statusAllDone,
            zh.statusNewDay to en.statusNewDay,
            zh.moodCritical to en.moodCritical,
            zh.moodHungry to en.moodHungry,
            zh.moodBored to en.moodBored,
            zh.moodAllDone to en.moodAllDone,
            zh.moodNewDay to en.moodNewDay,
        )
    }

    /**
     * 13 个函数型字段的双语整串钉死（brief 规定实参），外加 mood* 与 status* 两组独立取值的
     * 区分度断言：页面版 critical/progress/newDay 的措辞必须与预览卡版不同（同值会让
     * "两组都进表"退化成一份可被顺手合并的重复槽）；moodHungry/moodBored/moodAllDone 的源串
     * 两组逐字节相同，故不断言不等（见 PetStrings KDoc）。
     */
    @Test
    fun petStringsInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.pet
        val en = AppStringsEn.pet
        assertEquals("🍎 喂养 (10分)", zh.feedButton(10))
        assertEquals("🍎 Feed (10 points)", en.feedButton(10))
        assertEquals("🎾 陪玩 (5分)", zh.playButton(5))
        assertEquals("🎾 Play (5 points)", en.playButton(5))
        assertEquals("积分不足，需要 5 分", zh.notEnoughPoints(5))
        assertEquals("Not enough points — 5 needed", en.notEnoughPoints(5))
        // 进化目标名是已本地化的阶段名（调用方传 localizedPetStageName 的结果），表只负责句式
        assertEquals("再获得 100 分，就能进化成 🐣 Just Hatched 啦！", zh.nextStage(100, "🐣", "Just Hatched"))
        assertEquals("Earn 100 more points to evolve into 🐣 Just Hatched!", en.nextStage(100, "🐣", "Just Hatched"))
        assertEquals("300 分解锁", zh.stageUnlock(300))
        assertEquals("Unlocks at 300 points", en.stageUnlock(300))
        assertEquals("Lv.3 · 累计获得 600 分", zh.levelLine(3, 600))
        assertEquals("Lv.3 · 600 points earned in total", en.levelLine(3, 600))
        assertEquals("200 积分", zh.scenePrice(200))
        assertEquals("200 points", en.scenePrice(200))
        // 场景名由调用方传入已本地化的名字（petSceneCopy），表只负责句式
        assertEquals("🎉 购买成功，已装扮阳光海滩！", zh.purchaseSuccess("阳光海滩"))
        assertEquals("🎉 Purchased and equipped: Sunny Beach!", en.purchaseSuccess("Sunny Beach"))
        assertEquals("我的学习宠物 · 宠物蛋", zh.previewTitle("宠物蛋"))
        assertEquals("My study pet · Pet Egg", en.previewTitle("Pet Egg"))
        assertEquals("🍖 饱食 80", zh.fullnessShort(80))
        assertEquals("🍖 Fullness 80", en.fullnessShort(80))
        assertEquals("😊 心情 80", zh.moodShort(80))
        assertEquals("😊 Mood 80", en.moodShort(80))
        assertEquals("💪 已经完成 3 个任务了，继续加油，我陪着你！", zh.moodProgress(3))
        assertEquals("💪 3 tasks done — keep going, I'm with you!", en.moodProgress(3))
        assertEquals("💪 已经完成 2 个任务了，继续加油！", zh.statusProgress(2))
        assertEquals("💪 2 tasks done — keep going!", en.statusProgress(2))
        // 两组独立取值：页面版比预览卡版更"黏人"，合并槽位会让这些断言先红
        assertNotEquals(zh.moodCritical, zh.statusCritical)
        assertNotEquals(en.moodCritical, en.statusCritical)
        assertNotEquals(zh.moodProgress(3), zh.statusProgress(3))
        assertNotEquals(en.moodProgress(3), en.statusProgress(3))
        assertNotEquals(zh.moodNewDay, zh.statusNewDay)
        assertNotEquals(en.moodNewDay, en.statusNewDay)
    }

    @Test
    fun growthReportUsesSharedPetStageName() {
        // W7：成长报告阶段名与宠物页共用注册表（W6 披露时约定归 W7）
        assertEquals("🥚 Pet Egg", "🥚 ${localizedPetStageName(0, DisplayLanguage.EN)}")
        assertEquals("🥚 宠物蛋", "🥚 ${localizedPetStageName(0, DisplayLanguage.ZH)}")
        assertEquals("🐉 Little Dragon", "🐉 ${localizedPetStageName(7, DisplayLanguage.EN)}")
    }

    @Test
    fun crossTableVocabularyPinned() {
        // 宠物页与成长报告共用阶段名注册表（同一译名）：首尾两个阶段在两语下各取到同一串
        assertEquals("Pet Egg", localizedPetStageName(0, DisplayLanguage.EN))
        assertEquals("Little Dragon", localizedPetStageName(7, DisplayLanguage.EN))
        // 场景名走 petSceneCopy（键 = PetScene.id，落库只存 id），抽样钉住中英文两端
        assertEquals("我的小窝", petSceneCopy[DisplayLanguage.ZH]?.get("home"))
        assertEquals("Sunny Beach", petSceneCopy[DisplayLanguage.EN]?.get("beach"))
    }
}