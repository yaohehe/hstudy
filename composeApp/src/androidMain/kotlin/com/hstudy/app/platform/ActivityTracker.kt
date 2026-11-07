package com.hstudy.app.platform

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * 跟踪当前前台 Activity：FileSharer/CameraLauncher 需要 Activity 上下文弹出系统界面。
 * 由 HStudyApplication.registerActivityLifecycleCallbacks 注册。
 */
object ActivityTracker : Application.ActivityLifecycleCallbacks {

    var currentActivity: Activity? = null
        private set

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityPaused(activity: Activity) {
        if (currentActivity === activity) currentActivity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) currentActivity = null
    }
}