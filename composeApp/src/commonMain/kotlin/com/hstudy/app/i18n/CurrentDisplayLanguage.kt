package com.hstudy.app.i18n

import com.hstudy.app.AppGraph
import com.hstudy.app.data.preferences.UserPreferences
import kotlinx.coroutines.flow.first

/**
 * 无 CompositionLocal 场景的生效语言解析：通知发出点（AppGraph bootstrap、各 Receiver、
 * ScheduleAlarmHelper）与分享标题调用点用；界面内仍读 LocalDisplayLanguage，不走这里。
 *
 * 两个参数都可注入只为测试确定性（见 CurrentDisplayLanguageTest）：生产调用一律无参，
 * 默认值在**调用期**求值——bootstrap 之前调用会 UninitializedPropertyAccessException，
 * fail-fast 与仓库其余 lateinit 约定一致。通知文案在发出时冻结（spec §8），不追更。
 */
suspend fun currentDisplayLanguage(
    prefs: UserPreferences = AppGraph.userPreferences,
    systemTag: String = systemLanguageTag(),
): DisplayLanguage = appLanguageFromStorage(prefs.appLanguage.first()).resolveWith(systemTag)