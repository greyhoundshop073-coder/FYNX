package com.fynx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.fynx.app.ui.FynxApp
import com.fynx.app.ui.FynxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FynxTheme { FynxApp() } }
    }
}
