package com.example.smileproc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smileproc.ui.HelloScreen
import com.example.smileproc.ui.RecoveryScreen
import com.example.smileproc.ui.theme.SmileProcTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = SmileProcApplication.container(this)
        setContent {
            val storageState by container.storageState.collectAsStateWithLifecycle()
            SmileProcTheme {
                when (storageState) {
                    StorageState.Loading -> {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background,
                        ) {}
                    }
                    StorageState.NeedsRecovery -> {
                        RecoveryScreen(
                            onImportExport = {
                                // .smileproc import flow is wired in M9.
                            },
                            onStartFresh = {
                                container.applicationScope.launch {
                                    container.startFresh()
                                }
                            },
                        )
                    }
                    is StorageState.Ready -> {
                        HelloScreen()
                    }
                }
            }
        }
    }
}
