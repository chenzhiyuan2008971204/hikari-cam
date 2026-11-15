package com.hikari.cam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.height
import androidx.compose.material3.NavigationBar
import androidx.compose.ui.unit.dp
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hikari.cam.lab.FilterLabScreen
import com.hikari.cam.ui.AppViewModel
import com.hikari.cam.ui.CameraScreen
import com.hikari.cam.ui.EditorScreen
import com.hikari.cam.ui.FiltersScreen
import com.hikari.cam.ui.GalleryScreen
import com.hikari.cam.ui.HikariColors
import com.hikari.cam.ui.HikariTheme
import com.hikari.cam.ui.Route
import com.hikari.cam.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HikariTheme { HikariAppUi() } }
    }
}

@Composable
fun HikariAppUi(app: AppViewModel = viewModel()) {
    // pages cross-fade; full-screen pages (editor, lab) fade over the tabs
    Crossfade(targetState = app.route, animationSpec = tween(220), label = "route") { r -> RouteContent(app, r) }
}

@Composable
private fun RouteContent(app: AppViewModel, r: Route) {
    when (r) {
        is Route.Editor -> EditorScreen(app, r.uri, r.lookId)
        Route.Lab -> {
            BackHandler { app.back() }
            FilterLabScreen()
        }
        else -> Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (r) {
                    Route.Camera -> CameraScreen(app)
                    Route.Gallery -> GalleryScreen(app)
                    Route.Filters -> FiltersScreen(app)
                    else -> SettingsScreen(app, app.rawSupported)
                }
            }
            NavigationBar(containerColor = HikariColors.Panel, windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
                modifier = Modifier.navigationBarsPadding().height(62.dp)) {
                for ((route, label, icon) in listOf(
                    Triple(Route.Camera, "Camera", Icons.Outlined.PhotoCamera),
                    Triple(Route.Gallery, "Gallery", Icons.Outlined.PhotoLibrary),
                    Triple(Route.Filters, "Filters", Icons.Outlined.AutoAwesome),
                    Triple(Route.Settings, "Settings", Icons.Outlined.Settings),
                )) {
                    NavigationBarItem(
                        selected = r == route, onClick = { app.go(route) },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp) },
                    )
                }
            }
        }
    }
}
