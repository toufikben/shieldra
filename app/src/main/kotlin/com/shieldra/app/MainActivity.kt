package com.shieldra.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.navigation.ShieldraNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShieldraTheme {
                ShieldraNavHost()
            }
        }
    }
}
