package com.hikari.cam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.hikari.cam.lab.FilterLabScreen

/** Filter Lab build: the app opens straight into the filter test bench. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFFB86B), onPrimary = Color.Black,
                    surface = Color(0xFF161616), background = Color.Black,
                )
            ) {
                FilterLabScreen()
            }
        }
    }
}
