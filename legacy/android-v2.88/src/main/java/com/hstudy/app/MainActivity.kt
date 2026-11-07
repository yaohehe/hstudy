package com.hstudy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.ui.Modifier
import com.hstudy.app.ui.navigation.AppNavGraph
import com.hstudy.app.ui.theme.HStudyTheme

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HStudyTheme {
                val windowSizeClass = calculateWindowSizeClass(this)
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavGraph(windowSizeClass = windowSizeClass)
                }
            }
        }
    }
}
