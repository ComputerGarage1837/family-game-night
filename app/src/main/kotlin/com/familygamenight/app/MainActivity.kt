package com.familygamenight.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.familygamenight.app.ui.FamilyGameNightApp
import com.familygamenight.app.ui.theme.FamilyGameNightTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the status/navigation bars ourselves (dark castle colours), and keep all
        // content inside the safe area – see FamilyGameNightApp.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            FamilyGameNightTheme {
                FamilyGameNightApp()
            }
        }
    }
}
