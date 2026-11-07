package com.hstudy.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

/** 轻量跨平台导航器：替代 androidx.navigation（Android-only），保持 route 字符串语义 */
class AppNavigator(startRoute: String) {
    private val _backStack = mutableStateListOf(startRoute)
    val backStack: List<String> get() = _backStack
    val currentRoute: String get() = _backStack.last()

    fun navigate(route: String) {
        _backStack.add(route)
    }

    fun navigateSingleTop(route: String) {
        if (_backStack.lastOrNull() != route) _backStack.add(route)
    }

    fun popBackStack(): Boolean {
        if (_backStack.size <= 1) return false
        _backStack.removeAt(_backStack.lastIndex)
        return true
    }

    fun popBackStack(route: String, inclusive: Boolean): Boolean {
        val index = _backStack.indexOfLast { it == route }
        if (index < 0) return false
        val removeFrom = if (inclusive) index else index + 1
        while (_backStack.size > removeFrom) _backStack.removeAt(_backStack.lastIndex)
        return _backStack.isNotEmpty()
    }

    fun navigateAndClearStack(route: String) {
        _backStack.clear()
        _backStack.add(route)
    }
}

val LocalNavigator = compositionLocalOf<AppNavigator> { error("No AppNavigator provided") }

@Composable
fun rememberAppNavigator(startRoute: String): AppNavigator = remember { AppNavigator(startRoute) }