package com.hstudy.app.platform

/**
 * 第一层拦截（spec §5）：关闭时连 Platform.analytics 都不碰。
 * 属性是"当前状态"不是事件：重新开启时重发缓存值，不构成回溯补发。
 * 标志位与缓存的读写来自 bootstrap 协程与 UI 主线程，竞态最坏后果是切换瞬间
 * 多/少一条属性，不影响事件拦截语义，故不加锁（commonMain 无 @Synchronized）。
 */
object AnalyticsReporter {
    private var enabled = true
    private var channel = "release"
    private var language: String? = null
    private var role: String? = null

    fun setChannel(value: String) { channel = value }

    fun setLanguage(value: String) {
        language = value
        if (enabled) Platform.analytics.setUserProperty(UserProperty.APP_LANGUAGE, value)
    }

    fun setRole(value: String) {
        role = value
        if (enabled) Platform.analytics.setUserProperty(UserProperty.USER_ROLE, value)
    }

    fun setEnabled(on: Boolean) {
        enabled = on
        Platform.analytics.setEnabled(on)
        if (on) {
            Platform.analytics.setUserProperty(UserProperty.APP_CHANNEL, channel)
            language?.let { Platform.analytics.setUserProperty(UserProperty.APP_LANGUAGE, it) }
            role?.let { Platform.analytics.setUserProperty(UserProperty.USER_ROLE, it) }
        }
    }

    fun log(event: AnalyticsEvent) {
        if (enabled) Platform.analytics.logEvent(event)
    }
}
