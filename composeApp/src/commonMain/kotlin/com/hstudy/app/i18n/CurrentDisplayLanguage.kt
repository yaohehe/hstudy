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

/**
 * [currentDisplayLanguage] 的**同步**版本：给没有 suspend 上下文的平台回调用
 * （`AndroidFileSharer` 的分享面板标题、`ScheduleAlarmReceiver` 的兜底标题）。
 *
 * 来源是 [AppGraph.startupLanguage]（bootstrap 起跟随存储持续刷新的进程内快照，终审 Ruling 46）
 * 加 [systemLanguageTag]——解析式与 suspend 版**完全相同**（`appLanguageFromStorage(…).resolveWith(…)`），
 * 只是用快照代替 `appLanguage.first()`。所以它不阻塞调用线程（主线程安全；BroadcastReceiver 的
 * onReceive 因此不必 runBlocking、也不必改成 goAsync + 协程——那会是逻辑变更）。
 *
 * 代价（诚实说明，两处窗口都只影响瞬时文案）：
 * 1. 进程刚起、快照尚未播种时值为 null → SYSTEM → 系统语言（与 App 首帧的退化口径一致）；
 * 2. 刚切完语言、订阅还没收到新值时最多滞后一次 emission。
 * 通知正文/排程语言不走这里：那些发出点有 suspend 上下文，一律用 [currentDisplayLanguage] 的存储值。
 *
 * 参数可注入只为测试确定性（与 [currentDisplayLanguage] 同型）；生产调用一律无参。
 */
fun currentDisplayLanguageSnapshot(
    storedPreference: String? = AppGraph.startupLanguage.value,
    systemTag: String = systemLanguageTag(),
): DisplayLanguage = appLanguageFromStorage(storedPreference).resolveWith(systemTag)