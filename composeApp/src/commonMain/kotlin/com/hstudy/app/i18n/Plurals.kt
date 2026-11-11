package com.hstudy.app.i18n

/**
 * 英文 one/other 词形的唯一出处（i18n 共享层，不属于任何一页）。
 *
 * 为什么单独一个文件：带数值的英文文案必须做单复数分支（spec §3 末句"英文复数在函数内用
 * one/other 分支"、§8.3"覆盖英文 one/other"），而这类文案在后续任务里还会继续出现——
 * 仅 Task 9 就有 `已自动扣减 N 分`（ChildHomeScreen.kt:95）和 `再攒 N 分就能兑换…`（:822）两条，
 * Task 10 的 `${childScore.total}分`（ParentDashboardScreen.kt:142）同形。
 * 若每张表各写一遍 `if (n == 1)`，同一个术语会在五六处各自漂移；把 helper 收在一处，
 * 改词形只需动一行，且所有表共用它。函数保持 `internal`：只对 commonMain 的文案表可见，
 * 不构成对外 API。
 *
 * 约定：
 * - **只有英文表调用**——中文没有单复数（`+1分` 与 `+5分` 同形），中文表把数值直接插进句子；
 *   这点由 `StringsCopyTest.taskListEnglishPluralsCoverOneAndOther` 的中文断言钉住，
 *   防止有人把英文的 `if` 复制回中文表。
 * - 不做通用复数引擎（spec §1 非目标），所以这里只有"积分"一个词。后续页面遇到别的名词
 *   （分钟/天/个任务）时在本文件再加一个同样窄的函数，不要写成 `plural(n, word)` 泛化模板。
 *
 * 术语表 §11：积分 = Points（依据：代码 `points` / `adjustPoints` / `ParentPointsScreen`）。
 */
internal fun pointsWord(points: Int): String = if (points == 1) "point" else "points"

/**
 * 英文 one/other 窄函数（同 [pointsWord] 模式，spec §1：「后续页面遇到别的名词时在本文件再加
 * 一个同样窄的函数，不要写成泛化模板」）：
 * - [timesWord]：复习次数（Reviewed 1 time / 2 times）；
 * - [daysWord]：天（1 day / 2 days）；
 * - [mistakesWord]：错题（1 mistake / 2 mistakes）。
 */
internal fun timesWord(times: Int): String = if (times == 1) "time" else "times"

internal fun daysWord(days: Int): String = if (days == 1) "day" else "days"

internal fun mistakesWord(mistakes: Int): String = if (mistakes == 1) "mistake" else "mistakes"

/** 英文 one/other 窄函数：任务个数（1 task / 2 tasks）。任务计数文案专用，不泛化。 */
internal fun tasksWord(tasks: Int): String = if (tasks == 1) "task" else "tasks"
