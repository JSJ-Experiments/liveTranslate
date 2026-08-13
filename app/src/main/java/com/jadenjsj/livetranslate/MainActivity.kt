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
                    onSaveSettings = viewModel::saveSettings,
                    onAutoSaveSettings = viewModel::autoSaveSettings,
                    onTestConnection = viewModel::testConnection,
                    onOpenHistory = viewModel::openHistory,
                    onNewConversation = viewModel::newLiveConversation,
                    onCloseHistory = viewModel::closeHistory,
                    onClearHistory = viewModel::clearHistory,
                    onExportDiagnostics = viewModel::exportDiagnostics,
                    onSelectHistory = viewModel::selectHistorySession,
                    onCloseHistorySession = viewModel::closeHistorySession,
                    onTogglePlayback = viewModel::togglePlayback,
                    onSeekPlayback = viewModel::seekPlayback,
                    onPlaybackSpeed = viewModel::setPlaybackSpeed,
                    onRetry = viewModel::forceRetry,
                    onCancelPending = viewModel::cancelPendingTranslation,
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
