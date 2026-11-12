# i18n 阶段 2 W8——生成器/引擎文案 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** W8 九源双语化：效率报表页（85 条）、三个文案引擎（GoalRecommendation 20 / TutoringAdvice 38 / LearningScore 10，均加 `lang` 参数）、成就管理器渲染区（16）、等级系统（11）、学习树（7）、首页动画条（1）、家长辅导建议页（2）。新增 2 个键控注册表（`levelTitleCopy` 10×2、`treeStageCopy` 6×2）、2 个 CommonStrings 字段、2 张新表（EfficiencyReportStrings ~85、ParentTutoringAdviceStrings 2）+ 成就区文案并入一张小组件表；测试 209→~219，唯一 W8 commit。

**Architecture:** 沿用方案 A。文案引擎（纯函数）采用 W1 `SpacedRepetition` 先例：**加 `lang: DisplayLanguage` 参数**，内部按语言取文案（引擎文案不进 strings 表——它们是生成器输出，用 lang-keyed copy 常量）；titles/subjects 等 canonical 值走 `localizedPresetText`（注册表已覆盖日常目标标题与学科名）；UI 页照常进 strings 表；等级名/树阶段名走键控注册表（并行 `petStageCopy`）。

**Tech Stack:** KMP + CMP 1.7.3、Kotlin 2.1.20；commonTest；Python 闸门。

**Spec:** `docs/superpowers/specs/2026-11-09-hstudy-bilingual-stage2-design.md`（§12 W8 行、§4/§6 注册表、§10、§11、§14 残留）

## Global Constraints

- **Ruling 25**：引擎/组件不得把文案字符串送入写库或 bizKey；`AchievementManager.tryUnlockAchievement(type, title, description, emoji)` 与 `checkAndUnlock` 的写库参数保持 canonical（unchanged）。
- **不动**：`data/**`、schema、`DateTimeCompat.kt`、`Plurals.kt` 机制、平台源集、`AppGraph`、题库/统计口径、`AchievementManager.allAchievements` 定义、`LevelSystem.levels`/`LearningTree.getStage` 阈值。
- **跨波授权**：W5 的 V-52 明示「智能推荐标题/理由属 W8」→ W8 允许改 `ChildDailyTargetScreen.kt` 的引擎调用（传 `displayLanguage`）；W6 的 V-61 已回接宠物阶段名（本波不重复）。
- 唯一改动形态：`"文本"` → `strings.xxx` / 注册表渲染 / 引擎 `lang` 取词；逻辑零变化。**签名形变**：三个引擎的 `generate`/`calculate` 加 `lang` 参数（调用点全部在 Composable/协程内可读 `LocalDisplayLanguage`，逐一改）。
- 中文态字节等价；复用值逐字节：`CommonStrings.cancelButton` 等既有；本波新增 `levelRemaining`/`streakDays` 入 CommonStrings。
- 注册表守卫双向相等：`levelTitleCopy`/`treeStageCopy` 为独立键控表（不并入 `presetTextRegistry`，不扩 expected 集；由新测试钉覆盖）。
- 闭集逐任务加；每波四闸；T5 唯一 commit；不推送。
- 环境：Gradle 内联 JAVA_HOME/GRADLE_USER_HOME；Python `PYTHONIOENCODING=utf-8`。

---

## File Structure

| 文件 | 职责 |
|---|---|
| `.../i18n/PresetRegistry.kt`（改） | +`levelTitleCopy`（10×2）+`localizedLevelTitle`；+`treeStageCopy`（6×2）+`localizedTreeStageName` |
| `.../i18n/CommonStrings.kt`（改） | +`levelRemaining(points)`、+`streakDays(days)` |
| `.../i18n/EfficiencyReportStrings.kt`（新建） | 效率报表页 ~85 字段 |
| `.../i18n/ParentTutoringAdviceStrings.kt`（新建） | 辅导建议页 2 字段 |
| `.../i18n/AchievementSectionStrings.kt`（新建） | 成就区组件 5 字段（标签）+ chips 走 achievementCopy |
| `.../i18n/Strings.kt`（改） | +3 表 |
| `.../i18n/TutoringCopy.kt` / `GoalRecommendationCopy.kt` / `LearningScoreCopy.kt`（新建） | 引擎文案 lang-keyed 常量（见任务） |
| `.../ui/target/GoalRecommendation.kt`（改） | `generate(…, lang)`；titles/subjects 走注册表 |
| `.../ui/target/ChildDailyTargetScreen.kt`（改，跨波授权） | 调用处传 `displayLanguage`；推荐卡标题/理由渲染原样（引擎已本地化） |
| `.../ui/parent/TutoringAdviceEngine.kt`（改） | `generate(…, lang)` + copy |
| `.../ui/parent/ParentTutoringAdviceScreen.kt`（改） | 2 条文案入表 + 调用传 lang |
| `.../ui/common/LearningScore.kt`（改） | `calculate(…, lang)` + copy；调用点（grep：ChildHomeScreen 等）传 lang |
| `.../ui/common/LevelSystem.kt`（改） | 等级名走 `localizedLevelTitle`；升级提示走 `common.levelRemaining` |
| `.../ui/common/LearningTree.kt`（改） | 阶段名走 `localizedTreeStageName` |
| `.../ui/common/AchievementManager.kt`（改） | AchievementSection 5 条文案入表 + chips 走 `achievementCopy[lang][type]` |
| `.../ui/common/Animations.kt`（改） | `"${streakDays}天"` → `common.streakDays(streakDays)`（组件内读 LocalStrings） |
| `.../ui/habit/ChildEfficiencyReportScreen.kt`（改） | 全部 85 条文案入表；科目名/错因名等 canonical 渲染包注册表 |
| `.../commonTest/i18n/W8CopyTest.kt`（新建） | 表守卫 + 引擎两语 pin + 注册表覆盖 pin + 跨表 pin |
| `scripts/i18n_template_diff.py`、`docs/i18n-stage1-verification.md`（改） | 闸门登记 + V-68…V-72 + 读数 |

---

### Task 1: 注册表 2 键控表 + CommonStrings 2 字段

- [ ] 测试（红）→ 实现 → 绿（**211/0**）：
  - `PresetRegistry.kt` 追加：
```kotlin
/** 等级称号（键控表，索引对齐 LevelSystem 的 1..10 级） */
val levelTitleCopy: Map<DisplayLanguage, List<String>> = mapOf(
    DisplayLanguage.ZH to listOf("学习新手","勤奋小学徒","知识探索者","学习达人","学霸少年","知识王者","学习之星","超级学霸","传奇学者","学神"),
    DisplayLanguage.EN to listOf("Learning Novice","Diligent Apprentice","Knowledge Explorer","Study Expert","Star Student","Knowledge King","Study Star","Super Scholar","Legendary Scholar","Study Legend"),
)
fun localizedLevelTitle(level: Int, lang: DisplayLanguage): String =
    levelTitleCopy[lang]?.getOrNull(level - 1) ?: levelTitleCopy[lang]?.firstOrNull() ?: ""

/** 学习树成长阶段名（键控表，6 阶段） */
val treeStageCopy: Map<DisplayLanguage, List<String>> = mapOf(
    DisplayLanguage.ZH to listOf("种子","发芽","幼苗","大树","开花","结果"),
    DisplayLanguage.EN to listOf("Seed","Sprout","Sapling","Big Tree","Blooming","Fruiting"),
)
fun localizedTreeStageName(stage: Int, lang: DisplayLanguage): String =
    treeStageCopy[lang]?.getOrNull(stage) ?: treeStageCopy[lang]?.firstOrNull() ?: ""
```
  - `CommonStrings` 加字段 `levelRemaining: (points: Int) -> String`（ZH "还需 ${points}分 升级" / EN "${points} ${pointsWord(points)} to level up"）；`streakDays: (days: Int) -> String`（ZH "${days}天" / EN "$days ${if (days == 1) "day" else "days"}"）。
  - 测试（W8CopyTest）：`levelAndTreeRegistryCoveragePinned`（10/6 覆盖、ZH==LevelSystem 标题、EN 无汉字、越界回落）；`commonStringsLevelAndStreakPinned`（两语 exact，含 1/3）。
  - 调用点接线：LevelSystem 的 `"Lv.${info.level} ${info.title}"` → `LocalStrings + localizedLevelTitle`；`"还需 ${remaining}分 升级"` → `common.levelRemaining(remaining)`；LearningTreeCanvas 的 `getStageName` → `LocalizedTreeStageName`；Animations 的 `"${streakDays}天"` → `common.streakDays`。均读 `LocalDisplayLanguage`/`LocalStrings`（Composable 内）。
  - 预期 **211/0**。

---

### Task 2: 三引擎加 lang + 消费页

- [ ] `GoalRecommendationEngine.generate(mistakes, recentFocusMinutes, lang: DisplayLanguage)`：
  - `subjectName` 删除，改 `localizedPresetText(subjectCanonical, lang)`（canonical 学科串：数学/语文/英语/科学/其他——注册表已覆盖）。
  - titles 走 `localizedPresetText(title, lang)`（5 个 canonical 目标标题注册表已覆盖）。
  - reasons：新增 `GoalRecommendationCopy`（新建 `.../i18n/GoalRecommendationCopy.kt`）lang-keyed 函数集（7 条模板：薄弱科目/到期复习/未掌握多/专注不足（0 与非 0 两支）/作业/阅读/锻炼）。
  - 调用点：`ChildDailyTargetScreen.kt` 的 `GoalRecommendationEngine.generate(allMistakes, recentFocus)` → 加 `displayLanguage`（Composable 内已有）。
- [ ] `TutoringAdviceEngine.generate(mistakes, recentFocusMinutes, streak, lang)`：全部 38 条文案 → `TutoringCopy`（新建）lang-keyed（subjectName→注册表；5 科方法、5 错因建议、类别名、标题模板、详情、兜底）。调用点 `ParentTutoringAdviceScreen` 传 `LocalDisplayLanguage.current`；该页 2 条文案（title "💡 辅导建议"/"💡 Tutoring advice"、intro 原文/EN）入 `ParentTutoringAdviceStrings` 表。
- [ ] `LearningScoreCalculator.calculate(checkinRate, focusMinutes, reviewCount, lang)`：grades/encouragements → `LearningScoreCopy`（新建）lang-keyed；调用点 grep 后逐一传 lang（`ChildHomeScreen`/效率报表等）。
- [ ] 测试（W8CopyTest +3）：`goalRecommendationCopyPinsBothLanguages`（engine 纯函数用 Fake 错题列表驱动？——引擎接收 `MistakeEntity` 列表，可用构造实例；至少 pin copy 函数的 7 条两语 + subject/title 解析）；`tutoringCopyPinsBothLanguages`（38 条中抽代表 12 条两语 exact + 覆盖全 key 集断言）；`learningScoreCopyPinsBothLanguages`（5 grade + 5 encouragement 两语 + calculate 两语）。
  - 预期 **214/0**。

---

### Task 3: 成就区 + 效率报表页

- [ ] `AchievementSectionStrings` 表（5 字段：sectionTitle "🏅 成就徽章"/"🏅 Achievement badges"；emptyLocked "还没有解锁任何成就，加油吧！"/"No achievements unlocked yet — keep going!"；lockedTitle "🔒 待解锁（加油达成）"/"🔒 Locked (keep working on them)"；chips 的 `def.emoji/def.title` 与 `achievement.emoji/achievement.title` 渲染 → `achievementCopy[lang]?.get(def.type/achievement.type)?.title ?: 原标题`；进度 `$cur/$target` 数字不动）。
- [ ] `ChildEfficiencyReportScreen`（85 条）：建 `EfficiencyReportStrings`（字段按报表分区：标题/四统计卡/周月对比四句×2/四个指标行单位/学科分析（科目标题走注册表）/错因分析（错因名+5 条建议+兜底）/学习建议 7 条/趋势 4 区/时段名 6+`$period${hour}点`/专注分布/薄弱科目句等；函数字段带插值）。科目名/错因名等 canonical 渲染包注册表（错因名与注册表错题类型同值）。调用 `LearningScoreCalculator.calculate(..., lang)`。
- [ ] 测试（W8CopyTest +2）：`achievementSectionAndReportCopyDiffers` + `efficiencyReportInterpolatedPinsBothLanguages`（代表函数 10 条两语）。
  - 预期 **216/0**。

---

### Task 4: 闸门 + 文档 + 四闸 + 唯一 W8 commit

- 闸门：`W8_REV = "7026a15"`；`BRANCH_PAGES` +6（ChildEfficiencyReportScreen、ParentTutoringAdviceScreen、AchievementManager.kt、LevelSystem.kt、LearningTree.kt、Animations.kt；引擎 `.kt` 文件（GoalRecommendation/TutoringAdviceEngine/LearningScore）也登记为文案源（+3）——共 **+9**）；`BRANCH_TABLES` +4（EfficiencyReportStrings/ParentTutoringAdviceStrings/AchievementSectionStrings + 引擎 copy 文件不是表（不登记）——实际 +3）；披露：按实跑（引擎/注册表 canonical 值若页面单边则登记；subject/错因 canonical 已有披露或自然配平——以实跑为准）。读数按实跑（47→56 源、43→46 表、registry 数字）。
- 文档：V-68 效率报表页；V-69 辅导建议页（引擎文案随语言，含薄弱科目/错因建议）；V-70 成就徽章区（标签英文；成就名/描述走 achievementCopy）；V-71 等级徽章/学习树/首页动画条（等级称号与树阶段名走键控注册表；升级提示与 "N 天" 随语言）；V-72 智能推荐（目标页推荐卡标题/理由随语言，W5 的 V-52 残留关闭）。§1/§6/L-19 读数按实跑；若 L-19 相关（"其他"等共享词）新增披露触发，按实跑登记。
- 四闸全绿；**~219/0**（以实跑为准）；唯一 commit：
  `feat(i18n): 阶段 2 W8——效率报表/辅导建议/成就徽章双语化、三引擎 lang 取词、等级与学习树键控表、等价闸门登记`
  **不推送。**

---

## 验证上限（诚实声明，spec §11）

- 可自动证明：编译、单测、字节等价闸门整枝组、存储边界、中文态逐字节不变、引擎 copy 两语 pin、注册表覆盖。
- 不可自动证明：英文态报表/建议视觉（V-68…V-72）；引擎生成文案在真实数据下的措辞（仅代表样例 pin）。
- 不以"CI 绿"表述"双语已验证"。