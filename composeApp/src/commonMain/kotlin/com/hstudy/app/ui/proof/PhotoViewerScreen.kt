package com.hstudy.app.ui.proof

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hstudy.app.ui.dt.fileExists
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.ui.common.HStudyTopBar

@Composable
fun PhotoViewerScreen(
    photoPath: String,
    onNavigateBack: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    val strings = LocalStrings.current.photoViewer

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                }
            }
    ) {
        if (photoPath.isNotEmpty() && fileExists(photoPath)) {
            AsyncImage(
                model = photoPath,
                contentDescription = strings.photoContentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }

        // 关闭按钮
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = strings.closeContentDescription, tint = Color.White, modifier = Modifier.size(32.dp))
        }

        // 缩放提示
        Text(
            strings.zoomHint,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}
