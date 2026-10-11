package com.example.smileproc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.smileproc.ui.HelloScreen
import com.example.smileproc.ui.theme.SmileProcTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmileProcTheme {
                HelloScreen()
            }
        }
    }
}
