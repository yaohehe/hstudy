package com.hstudy.app.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import com.hstudy.app.ui.theme.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 响应式内容宽度约束
 * - Compact (手机/手机横屏): 全屏宽度 + 16dp padding
 * - Medium (小平板竖屏/手机大横屏): 90%宽度 + 24dp padding
 * - Expanded (大平板横屏): 80%宽度 + 32dp padding
 */
@Composable
fun ResponsiveContent(
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val widthModifier = when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> Modifier.fillMaxWidth()
        WindowWidthSizeClass.Medium -> Modifier.fillMaxWidth(0.90f)
        else -> Modifier.fillMaxWidth(0.80f)
    }
    val hPadding = when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> 16.dp
        WindowWidthSizeClass.Medium -> 24.dp
        else -> 32.dp
    }
    Column(
        modifier = modifier
            .then(widthModifier)
            .padding(horizontal = hPadding),
        content = content
    )
}

/**
 * 响应式双栏布局 - Expanded宽度下左右分栏，否则单栏
 */
@Composable
fun ResponsiveTwoColumn(
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    leftWeight: Float = 0.45f,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit
) {
    val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded

    if (isExpanded) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(leftWeight), content = left)
            Column(modifier = Modifier.weight(1f - leftWeight), content = right)
        }
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            left()
            Spacer(modifier = Modifier.height(12.dp))
            right()
        }
    }
}

/**
 * 通用TopBar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HStudyTopBar(
    title: String,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

/**
 * 积分徽章
 */
@Composable
fun PointsBadge(
    points: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("⭐", style = MaterialTheme.typography.labelLarge)
            Text(
                "$points",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text("分", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/**
 * 时分步进选择器：用 +/- 按钮调整小时和分钟，彻底避免自由文本输入的光标定位与解析校验问题。
 * 小时范围 0-23，分钟范围 0-59，循环调整（0 减为 23，23 加为 0）。
 */
@Composable
fun HourMinuteStepper(
    hour: Int,
    minute: Int,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 小时步进
        NumberStepper(
            value = com.hstudy.app.ui.dt.strFormat("%02d", hour),
            label = "时",
            onDecrement = { onTimeChange(if (hour == 0) 23 else hour - 1, minute) },
            onIncrement = { onTimeChange(if (hour == 23) 0 else hour + 1, minute) }
        )
        Text(":", style = MaterialTheme.typography.titleLarge)
        // 分钟步进
        NumberStepper(
            value = com.hstudy.app.ui.dt.strFormat("%02d", minute),
            label = "分",
            onDecrement = { onTimeChange(hour, if (minute == 0) 59 else minute - 1) },
            onIncrement = { onTimeChange(hour, if (minute == 59) 0 else minute + 1) }
        )
    }
}

/** 单个数字步进器：- 值 + */
@Composable
private fun NumberStepper(
    value: String,
    label: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = onDecrement) {
            Icon(Icons.Filled.Remove, contentDescription = "减少$label")
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(52.dp)
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(onClick = onIncrement) {
            Icon(Icons.Filled.Add, contentDescription = "增加$label")
        }
    }
}
