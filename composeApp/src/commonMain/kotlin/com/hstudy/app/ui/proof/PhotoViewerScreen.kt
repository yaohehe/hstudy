package com.hstudy.app.ui.proof
package com.hstudy.app.ui.proof


import androidx.compose.foundation.Image
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImage
import com.hstudy.app.ui.dt.fileExists
import com.hstudy.app.ui.dt.fileExists
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.HStudyTopBar
import java.io.File
import java.io.File


@Composable
@Composable
fun PhotoViewerScreen(
fun PhotoViewerScreen(
    photoPath: String,
    photoPath: String,
    onNavigateBack: () -> Unit
    onNavigateBack: () -> Unit
) {
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var scale by remember { mutableFloatStateOf(1f) }


    Box(
    Box(
        modifier = Modifier
        modifier = Modifier
            .fillMaxSize()
            .fillMaxSize()
            .background(Color.Black)
            .background(Color.Black)
            .pointerInput(Unit) {
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                detectTransformGestures { _, _, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                }
                }
            }
            }
    ) {
    ) {
        if (photoPath.isNotEmpty() && fileExists(photoPath)) {
        if (photoPath.isNotEmpty() && fileExists(photoPath)) {
            AsyncImage(
            AsyncImage(
                model = photoPath,
                model = photoPath,
                contentDescription = "查看照片",
                contentDescription = "查看照片",
                modifier = Modifier
                modifier = Modifier
                    .fillMaxSize()
                    .fillMaxSize()
                    .graphicsLayer {
                    .graphicsLayer {
                        scaleX = scale
                        scaleX = scale
                        scaleY = scale
                        scaleY = scale
                    }
                    }
            )
            )
        }
        }


        // 关闭按钮
        // 关闭按钮
        IconButton(
        IconButton(
            onClick = onNavigateBack,
            onClick = onNavigateBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
        ) {
            Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White, modifier = Modifier.size(32.dp))
            Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White, modifier = Modifier.size(32.dp))
        }
        }


        // 缩放提示
        // 缩放提示
        Text(
        Text(
            "双指缩放查看",
            "双指缩放查看",
            style = MaterialTheme.typography.bodySmall,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
        )
    }
    }
}
}
