package com.jadenjsj.livetranslate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiveTranslateTheme {
                val viewModel: TranslationViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                val context = LocalContext.current
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (!granted) viewModel.microphonePermissionDenied()
                }

                LiveTranslateScreen(
                    state = state,
                    onOpenSettings = viewModel::openSettings,
                    onCloseSettings = viewModel::closeSettings,
                    onSaveSettings = viewModel::saveSettings,
                    onTestConnection = viewModel::testConnection,
                    onOpenHistory = viewModel::openHistory,
                    onCloseHistory = viewModel::closeHistory,
                    onClearHistory = viewModel::clearHistory,
                    onPlayTurn = viewModel::playRecording,
                    onTalkStart = {
                        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            viewModel.startTalking()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onTalkStop = viewModel::stopTalking,
                )
            }
        }
    }
}
