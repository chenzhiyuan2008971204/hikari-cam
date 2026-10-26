package com.hikari.cam

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hikari.cam.data.FilterStore
import com.hikari.cam.ui.Accent
import com.hikari.cam.ui.CameraScreen
import com.hikari.cam.ui.EditorScreen
import com.hikari.cam.ui.HikariTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = FilterStore(applicationContext)
        setContent {
            HikariTheme {
                var granted by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    )
                }
                val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
                LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

                var editing by remember { mutableStateOf<Uri?>(null) }
                val e = editing
                when {
                    e != null -> EditorScreen(e, store) { editing = null }
                    granted -> CameraScreen(store) { editing = it }
                    else -> Column(
                        Modifier.fillMaxSize().background(Color.Black).padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("光 Hikari", color = Color.White, fontSize = 28.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("需要相機權限才能拍照", color = Color.Gray)
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { ask.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        ) { Text("允許相機") }
                    }
                }
            }
        }
    }
}
