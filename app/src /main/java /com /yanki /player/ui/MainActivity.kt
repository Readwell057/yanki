package com.yanki.player.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.yanki.player.service.PlayerConnection

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        PlayerConnection.connect(this)
        vm.onPermissionChanged(this)
        setContent {
            YankiTheme {
                val wsc = calculateWindowSizeClass(this)
                YankiRoot(vm, wsc.widthSizeClass)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onPermissionChanged(this)
    }
}
