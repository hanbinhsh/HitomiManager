package com.ice.hitomimanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ice.hitomimanager.ui.screen.AppRoot
import com.ice.hitomimanager.ui.theme.HitomiManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HitomiManagerTheme {
                AppRoot()
            }
        }
    }
}