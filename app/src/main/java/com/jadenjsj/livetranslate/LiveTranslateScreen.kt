package com.jadenjsj.livetranslate

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

@Composable
fun LiveTranslateScreen(
    state: TranslationUiState,
    onOpenSettings: () -> Unit,
    onSaveSettings: (AppSettings) -> Unit,
    onAutoSaveSettings: (AppSettings) -> Unit,
    onTestConnection: (AppSettings) -> Unit,
    onOpenHistory: () -> Unit,
    onNewConversation: () -> Unit,
    onCloseHistory: () -> Unit,
    onClearHistory: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onSelectHistory: (Long) -> Unit,
    onCloseHistorySession: () -> Unit,
    onTogglePlayback: (TranslationTurn) -> Unit,
    onSeekPlayback: (Long) -> Unit,
    onPlaybackSpeed: (Float) -> Unit,
    onRetry: () -> Unit,
    onCancelPending: () -> Unit,
    onTalkStart: () -> Unit,
    onTalkStop: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (state.historyOpen) {
            HistoryScreen(
                state, onCloseHistory, onClearHistory, onExportDiagnostics, onSelectHistory,
                onCloseHistorySession, onTogglePlayback, onSeekPlayback, onPlaybackSpeed,
            )
        } else {
            InterpreterScreen(
                state, onOpenHistory, onNewConversation, onOpenSettings, onRetry,
                onCancelPending, onTalkStart, onTalkStop,
            )
        }
    }

    if (state.settingsOpen) {
        SettingsSheet(
            initial = state.settings,
            testResult = state.connectionTestResult,
            isTesting = state.phase == SessionPhase.Testing,
            onSave = onSaveSettings,
            onAutoSave = onAutoSaveSettings,
            onTest = onTestConnection,
        )
    }
}

@Composable
private fun InterpreterScreen(
    state: TranslationUiState,
    onOpenHistory: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onCancelPending: () -> Unit,
    onTalkStart: () -> Unit,
    onTalkStop: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        CompactTopBar(state, onOpenHistory, onNewConversation, onOpenSettings)
        AttentionBanner(state, onRetry, onCancelPending)
        LiveTranscript(state, Modifier.weight(1f))
        PushToTalk(
            phase = state.phase,
            ready = state.settings.isComplete,
            triggerMode = state.settings.triggerMode,
            onStart = onTalkStart,
            onStop = onTalkStop,
        )
    }
}

@Composable
private fun CompactTopBar(
    state: TranslationUiState,
    onOpenHistory: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val dotColor = when {
            !state.isOnline || state.phase == SessionPhase.Error -> MaterialTheme.colorScheme.error
            state.phase in setOf(SessionPhase.Connecting, SessionPhase.Queued, SessionPhase.Sending, SessionPhase.Testing) -> MaterialTheme.colorScheme.secondary
            state.phase in setOf(SessionPhase.Listening, SessionPhase.Translating) -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.outline
        }
        Box(
            Modifier
                .size(12.dp)
                .background(dotColor, CircleShape)
                .semantics { contentDescription = connectionDescription(state) },
        )
        Spacer(Modifier.weight(1f))
        val canClear = !state.isActive && (state.liveTurns.isNotEmpty() || state.sourceText.isNotBlank() || state.translationText.isNotBlank())
        IconButton(onClick = onNewConversation, enabled = canClear) {
            Icon(
                painterResource(R.drawable.ic_add),
                "New conversation",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (canClear) 1f else 0.38f),
            )
        }
        IconButton(onClick = onOpenHistory, enabled = !state.isActive) {
            Icon(
                painterResource(R.drawable.ic_history),
                "History",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (state.isActive) 0.38f else 1f),
            )
        }
        IconButton(onClick = onOpenSettings, enabled = !state.isActive) {
            Icon(
                painterResource(R.drawable.ic_settings),
                "Settings",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (state.isActive) 0.38f else 1f),
            )
        }
    }
}

private fun connectionDescription(state: TranslationUiState): String = when {
    !state.isOnline -> "Offline"
    state.phase == SessionPhase.Error -> "Connection error"
    state.phase == SessionPhase.Connecting -> "Connecting"
    state.phase == SessionPhase.Queued -> "Recording queued locally"
    state.phase == SessionPhase.Listening -> "Connected"
    state.phase == SessionPhase.Sending -> "Sending"
    state.phase == SessionPhase.Translating -> "Connected and translating"
    else -> "Disconnected"
}

@Composable
private fun AttentionBanner(state: TranslationUiState, onRetry: () -> Unit, onCancelPending: () -> Unit) {
    val message = when {
        state.phase == SessionPhase.Queued -> state.error ?: "Recording saved locally · waiting to send…"
        state.phase == SessionPhase.Connecting -> state.error ?: "Recording locally · connecting to ${state.settings.provider.shortLabel}…"
        state.phase == SessionPhase.Error -> state.error ?: "Translation connection failed"
        state.phase == SessionPhase.Testing -> "Checking the ${state.settings.provider.shortLabel} connection…"
        !state.isOnline -> "Offline · you can still record and it will send after reconnection"
        else -> null
    } ?: return
    val warning = state.phase in setOf(SessionPhase.Connecting, SessionPhase.Queued, SessionPhase.Testing)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        color = if (warning) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (warning) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.phase != SessionPhase.Testing) {
                TextButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.textButtonColors(contentColor = if (warning) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer),
                ) { Text("Retry") }
            }
            if (state.phase == SessionPhase.Queued) {
                TextButton(
                    onClick = onCancelPending,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                ) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun LiveTranscript(state: TranslationUiState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    var followLatest by rememberSaveable { mutableStateOf(true) }
    var autoScrolling by remember { mutableStateOf(false) }
    val hasCurrent = state.sourceText.isNotBlank() || state.translationText.isNotBlank() || state.isActive
    val count = state.liveTurns.size + if (hasCurrent) 1 else 0

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }.collect { (scrolling, forward) ->
            if (!forward) followLatest = true
            else if (scrolling && !autoScrolling) followLatest = false
        }
    }
    LaunchedEffect(count, state.sourceText, state.translationText) {
        if (count > 0 && followLatest) {
            autoScrolling = true
            listState.scrollToItem(count - 1)
            autoScrolling = false
        }
    }

    if (count == 0) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (state.settings.triggerMode == TriggerMode.Hold) "Hold the mic and speak" else "Tap the mic and speak",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(top = 10.dp, bottom = 8.dp),
    ) {
        items(state.liveTurns, key = TranslationTurn::id) { segment ->
            LiveSegment(segment, active = false)
        }
        if (hasCurrent) {
            item(key = "active") {
                LiveSegment(
                    TranslationTurn(
                        id = Long.MIN_VALUE,
                        sourceText = state.sourceText,
                        translationText = state.translationText,
                        sourceLanguage = state.detectedSourceLanguage,
                        targetLanguage = state.activeTargetLanguage,
                    ),
                    active = state.isActive,
                )
            }
        }
    }
}

@Composable
private fun LiveSegment(turn: TranslationTurn, active: Boolean) {
    val accent = when (turn.sourceLanguage?.substringBefore('-')) {
        "zh" -> MaterialTheme.colorScheme.tertiary
        "en" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = if (active) 54.dp else 38.dp)
                .background(accent, RoundedCornerShape(4.dp)),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    languageLabel(turn.sourceLanguage),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                if (active) {
                    Spacer(Modifier.size(7.dp))
                    Box(Modifier.size(6.dp).background(accent, CircleShape))
                }
            }
            if (turn.sourceText.isNotBlank()) {
                Text(
                    turn.sourceText,
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = turn.translationText.ifBlank { if (active) "Listening…" else "" },
                modifier = Modifier.padding(top = 5.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                fontStyle = if (turn.translationText.isBlank()) FontStyle.Italic else FontStyle.Normal,
                color = if (turn.translationText.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun HistoryScreen(
    state: TranslationUiState,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
    onSelect: (Long) -> Unit,
    onCloseSession: () -> Unit,
    onTogglePlayback: (TranslationTurn) -> Unit,
    onSeekPlayback: (Long) -> Unit,
    onPlaybackSpeed: (Float) -> Unit,
) {
    val selected = state.selectedHistoryId?.let { id -> state.turns.firstOrNull { it.id == id } }
    if (selected != null) {
        HistoryDetailScreen(
            turn = selected,
            playback = state.playback,
            onBack = onCloseSession,
            onTogglePlayback = { onTogglePlayback(selected) },
            onSeekPlayback = onSeekPlayback,
            onPlaybackSpeed = onPlaybackSpeed,
        )
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onExport) {
                Icon(painterResource(R.drawable.ic_share), "Export diagnostics", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onClear, enabled = state.turns.isNotEmpty()) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    "Clear history",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (state.turns.isEmpty()) 0.38f else 1f),
                )
            }
        }
        if (state.turns.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No saved conversations", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.turns.asReversed(), key = TranslationTurn::id) { turn ->
                    HistoryItem(turn) { onSelect(turn.id) }
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(turn: TranslationTurn, onOpen: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(turn.createdAtMillis)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${turn.segments.size.takeIf { it > 0 } ?: 1} segments · ${formatTime(turn.durationMillis)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                turn.sourceText.ifBlank { "Source transcript unavailable" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                turn.translationText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HistoryDetailScreen(
    turn: TranslationTurn,
    playback: PlaybackState,
    onBack: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekPlayback: (Long) -> Unit,
    onPlaybackSpeed: (Float) -> Unit,
) {
    val segments = turn.segments.ifEmpty {
        listOf(
            TranslationSegment(
                id = turn.id,
                sourceText = turn.sourceText,
                translationText = turn.translationText,
                sourceLanguage = turn.sourceLanguage,
                targetLanguage = turn.targetLanguage,
            ),
        )
    }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), "Back to sessions", tint = MaterialTheme.colorScheme.onSurface)
            }
            Column {
                Text("Saved session", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(turn.createdAtMillis)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (turn.audioPath != null) {
            AudioPlayer(
                playback = playback.copy(
                    sessionId = turn.id,
                    durationMillis = playback.durationMillis.takeIf { it > 0 } ?: turn.durationMillis,
                ),
                onToggle = onTogglePlayback,
                onSeek = onSeekPlayback,
                onSpeed = onPlaybackSpeed,
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            items(segments, key = TranslationSegment::id) { segment ->
                LiveSegment(
                    TranslationTurn(
                        id = segment.id,
                        sourceText = segment.sourceText,
                        translationText = segment.translationText,
                        sourceLanguage = segment.sourceLanguage,
                        targetLanguage = segment.targetLanguage,
                    ),
                    active = false,
                )
            }
        }
    }
}

@Composable
private fun AudioPlayer(
    playback: PlaybackState,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeed: (Float) -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggle) {
                    Icon(
                        painterResource(if (playback.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        if (playback.isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Slider(
                    value = playback.positionMillis.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..playback.durationMillis.coerceAtLeast(1).toFloat(),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatTime(playback.positionMillis)} / ${formatTime(playback.durationMillis)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                    FilterChip(
                        selected = playback.speed == speed,
                        onClick = { onSpeed(speed) },
                        label = { Text("${speed}×") },
                        modifier = Modifier.padding(start = 3.dp),
                    )
                }
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1_000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun languageLabel(code: String?): String = when {
    code == null -> "AUTO"
    code == "zh" || code.startsWith("zh-") -> "中文"
    code == "en" || code.startsWith("en-") -> "ENGLISH"
    else -> supportedLanguages.firstOrNull { it.code == code }?.label?.uppercase() ?: code.uppercase()
}

@Composable
private fun PushToTalk(
    phase: SessionPhase,
    ready: Boolean,
    triggerMode: TriggerMode,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val active = phase == SessionPhase.Connecting || phase == SessionPhase.Listening
    val micColor = if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val micContentColor = if (active) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
    val color by animateColorAsState(micColor, label = "mic color")
    val pulse by rememberInfiniteTransition(label = "mic glow").animateFloat(
        initialValue = 0.10f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "mic pulse",
    )
    val currentStart by rememberUpdatedState(onStart)
    val currentStop by rememberUpdatedState(onStop)
    val canStart by rememberUpdatedState(ready && (phase == SessionPhase.Idle || phase == SessionPhase.Error))
    val currentPhase by rememberUpdatedState(phase)
    val currentMode by rememberUpdatedState(triggerMode)
    val label = when (phase) {
        SessionPhase.Listening -> if (triggerMode == TriggerMode.Hold) "Listening · release to send" else "Listening · tap to send"
        SessionPhase.Connecting -> "Recording locally · connecting…"
        SessionPhase.Queued -> "Recorded locally · waiting to send"
        SessionPhase.Sending, SessionPhase.Translating -> "Sending · finishing translation"
        else -> if (!ready) "Open Settings to connect"
        else if (triggerMode == TriggerMode.Hold) "Hold to speak" else "Tap to start listening"
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(66.dp)
                    .graphicsLayer { alpha = pulse }
                    .background(color, RoundedCornerShape(24.dp)),
            )
        }
        Surface(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = label
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        if (currentMode == TriggerMode.Tap) {
                            waitForUpOrCancellation()
                            when (currentPhase) {
                                SessionPhase.Idle, SessionPhase.Error -> if (canStart) currentStart()
                                SessionPhase.Connecting, SessionPhase.Listening -> currentStop()
                                else -> Unit
                            }
                        } else if (canStart) {
                            currentStart()
                            try {
                                waitForUpOrCancellation()
                            } finally {
                                currentStop()
                            }
                        } else waitForUpOrCancellation()
                    }
                },
            color = color,
            contentColor = micContentColor,
            shape = RoundedCornerShape(20.dp),
            tonalElevation = if (active) 5.dp else 1.dp,
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_mic),
                    null,
                    Modifier.size(25.dp),
                    tint = micContentColor,
                )
                Spacer(Modifier.size(10.dp))
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = micContentColor)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    initial: AppSettings,
    testResult: String?,
    isTesting: Boolean,
    onSave: (AppSettings) -> Unit,
    onAutoSave: (AppSettings) -> Unit,
    onTest: (AppSettings) -> Unit,
) {
    var apiKey by rememberSaveable { mutableStateOf(initial.apiKey) }
    var workspaceId by rememberSaveable { mutableStateOf(initial.workspaceId) }
    var providerName by rememberSaveable { mutableStateOf(initial.provider.name) }
    var openAiApiKey by rememberSaveable { mutableStateOf(initial.openAiApiKey) }
    var openAiSafetyIdentifier by rememberSaveable { mutableStateOf(initial.openAiSafetyIdentifier) }
    var volcApiKey by rememberSaveable { mutableStateOf(initial.volcApiKey) }
    var volcResourceId by rememberSaveable { mutableStateOf(initial.volcResourceId) }
    var regionName by rememberSaveable { mutableStateOf(initial.region.name) }
    var sampleRate by rememberSaveable { mutableStateOf(initial.sampleRate) }
    var chunkMilliseconds by rememberSaveable { mutableStateOf(initial.chunkMilliseconds) }
    var hotwords by rememberSaveable { mutableStateOf(initial.hotwords) }
    var primaryLanguage by rememberSaveable { mutableStateOf(initial.primaryLanguage) }
    var secondaryLanguage by rememberSaveable { mutableStateOf(initial.secondaryLanguage) }
    var triggerMode by rememberSaveable { mutableStateOf(initial.triggerMode.name) }
    var translationMode by rememberSaveable {
        mutableStateOf(
            initial.translationMode.takeIf { it.provider == initial.provider }?.name
                ?: defaultMode(initial.provider).name,
        )
    }
    var microphoneMode by rememberSaveable { mutableStateOf(initial.microphoneMode.name) }
    var vadSilenceMilliseconds by rememberSaveable { mutableStateOf(initial.vadSilenceMilliseconds) }
    var saveHistory by rememberSaveable { mutableStateOf(initial.saveHistory) }
    var playTranslatedAudio by rememberSaveable { mutableStateOf(initial.playTranslatedAudio) }
    var showKey by rememberSaveable { mutableStateOf(false) }

    fun draft() = AppSettings(
        provider = RealtimeProvider.valueOf(providerName),
        apiKey = apiKey,
        workspaceId = workspaceId,
        openAiApiKey = openAiApiKey,
        openAiSafetyIdentifier = openAiSafetyIdentifier,
        volcApiKey = volcApiKey,
        volcResourceId = volcResourceId,
        region = Region.valueOf(regionName),
        sampleRate = sampleRate,
        chunkMilliseconds = chunkMilliseconds,
        hotwords = hotwords,
        primaryLanguage = primaryLanguage,
        secondaryLanguage = secondaryLanguage,
        triggerMode = TriggerMode.valueOf(triggerMode),
        saveHistory = saveHistory,
        translationMode = TranslationMode.valueOf(translationMode),
        microphoneMode = MicrophoneMode.valueOf(microphoneMode),
        vadSilenceMilliseconds = vadSilenceMilliseconds,
        playTranslatedAudio = playTranslatedAudio,
    )

    LaunchedEffect(
        providerName, apiKey, workspaceId, openAiApiKey, openAiSafetyIdentifier,
        volcApiKey, volcResourceId, regionName, sampleRate, chunkMilliseconds, hotwords,
        primaryLanguage, secondaryLanguage, triggerMode, translationMode,
        microphoneMode, vadSilenceMilliseconds, saveHistory, playTranslatedAudio,
    ) {
        delay(350)
        onAutoSave(draft())
    }

    ModalBottomSheet(
        onDismissRequest = { onSave(draft()) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Changes save automatically. Provider keys are separately encrypted with Android Keystore and sent only to the selected provider.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionLabel("PROVIDER")
            RealtimeProvider.entries.forEach { provider ->
                val selected = providerName == provider.name
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        providerName = provider.name
                        translationMode = defaultMode(provider).name
                    },
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(provider.label, Modifier.padding(14.dp), fontWeight = FontWeight.SemiBold)
                }
            }
            when (RealtimeProvider.valueOf(providerName)) {
                RealtimeProvider.Qwen -> {
                    SectionLabel("MODEL STUDIO · DASHSCOPE")
                    SecretField("API key", apiKey, showKey, { apiKey = it }, { showKey = !showKey })
                    OutlinedTextField(
                        value = workspaceId,
                        onValueChange = { workspaceId = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Workspace ID") },
                        singleLine = true,
                    )
                    Text("Region", style = MaterialTheme.typography.labelLarge)
                    ChoiceRow(Region.entries.map { it.name to it.label }, regionName) { regionName = it }
                    ReadOnlySetting("Model", "qwen3.5-livetranslate-flash-realtime")
                }
                RealtimeProvider.OpenAI -> {
                    SectionLabel("OPENAI")
                    SecretField("OpenAI API key", openAiApiKey, showKey, { openAiApiKey = it }, { showKey = !showKey })
                    OutlinedTextField(
                        value = openAiSafetyIdentifier,
                        onValueChange = { openAiSafetyIdentifier = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Safety identifier") },
                        supportingText = { Text("Stable pseudonymous SHA-256 ID required by the Translation API") },
                        singleLine = true,
                    )
                    ReadOnlySetting("Model", "gpt-realtime-translate", "Native simultaneous source transcription plus translated text and PCM speech.")
                }
                RealtimeProvider.Volcengine -> {
                    SectionLabel("VOLCENGINE · 豆包语音")
                    SecretField("Volcengine API key", volcApiKey, showKey, { volcApiKey = it }, { showKey = !showKey })
                    OutlinedTextField(
                        value = volcResourceId,
                        onValueChange = { volcResourceId = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Resource ID") },
                        supportingText = { Text("Official AST 2.0 value: volc.service_type.10053") },
                        singleLine = true,
                    )
                    ReadOnlySetting("Model", "Seed LiveInterpret 2.0", "Direct AST WebSocket; supports native zhen bidirectional mode.")
                }
            }

            HorizontalDivider()
            SectionLabel("TRANSLATION")
            Text("Mode", style = MaterialTheme.typography.labelLarge)
            val selectedProvider = RealtimeProvider.valueOf(providerName)
            TranslationMode.entries.filter { it.provider == selectedProvider }.forEach { mode ->
                val selected = translationMode == mode.name
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        translationMode = mode.name
                        if (mode in setOf(TranslationMode.VolcForwardSpeech, TranslationMode.VolcReverseSpeech)) {
                            if (primaryLanguage !in volcS2sLanguages.map { it.code }) primaryLanguage = "en"
                            if (secondaryLanguage !in volcS2sLanguages.map { it.code }) secondaryLanguage = "zh"
                        }
                    },
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(Modifier.padding(13.dp)) {
                        Text(mode.label, fontWeight = FontWeight.SemiBold)
                        Text(
                            mode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            when (TranslationMode.valueOf(translationMode)) {
                TranslationMode.DualEnglishChinese -> {
                    ReadOnlySetting(
                        "Locked source languages",
                        "English + Chinese",
                        "English recognition feeds the Chinese target; Chinese recognition feeds the English target. This prevents detections such as Uyghur.",
                    )
                }
                TranslationMode.DetectedPair -> {
                    LanguagePicker("Translation target", secondaryLanguage) { secondaryLanguage = it }
                    Text(
                        "Qwen automatically detects the spoken source language. It does not automatically reverse the fixed target.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TranslationMode.ManualForward, TranslationMode.ManualReverse -> {
                    LanguagePicker("Language A", primaryLanguage) { if (it != secondaryLanguage) primaryLanguage = it }
                    LanguagePicker("Language B", secondaryLanguage) { if (it != primaryLanguage) secondaryLanguage = it }
                }
                TranslationMode.OpenAiForward, TranslationMode.OpenAiReverse -> {
                    LanguagePicker("Language A", primaryLanguage) { if (it != secondaryLanguage) primaryLanguage = it }
                    LanguagePicker("Language B", secondaryLanguage) { if (it != primaryLanguage) secondaryLanguage = it }
                    Text(
                        "GPT uses one full-duplex stream with a fixed output target. Pick → B or → A above; source transcription remains automatic.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TranslationMode.VolcBidirectionalText, TranslationMode.VolcBidirectionalSpeech -> {
                    ReadOnlySetting(
                        "Automatic pair",
                        "English ↔ Chinese · zhen",
                        "One native stream detects English/Chinese and reverses the translation direction without reconnecting.",
                    )
                }
                TranslationMode.VolcForwardText, TranslationMode.VolcReverseText,
                TranslationMode.VolcForwardSpeech, TranslationMode.VolcReverseSpeech -> {
                    val languageOptions = if (TranslationMode.valueOf(translationMode).usesSpeechOutput) volcS2sLanguages else volcS2tLanguages
                    val bridgeLanguages = setOf("en", "zh")
                    val languageAOptions = if (secondaryLanguage in bridgeLanguages) languageOptions
                        else languageOptions.filter { it.code in bridgeLanguages }
                    val languageBOptions = if (primaryLanguage in bridgeLanguages) languageOptions
                        else languageOptions.filter { it.code in bridgeLanguages }
                    LanguagePicker("Language A", primaryLanguage, languageAOptions) { if (it != secondaryLanguage) primaryLanguage = it }
                    LanguagePicker("Language B", secondaryLanguage, languageBOptions) { if (it != primaryLanguage) secondaryLanguage = it }
                    Text(
                        "Volcengine requires one side of a fixed pair to be English or Chinese.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selectedProvider == RealtimeProvider.Qwen) {
                OutlinedTextField(
                    value = hotwords,
                    onValueChange = { hotwords = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Hotwords (optional)") },
                    placeholder = { Text("Qwen=千问\nVPN=虚拟专用网络") },
                    minLines = 2,
                    supportingText = { Text("One source=translation pair per line") },
                )
            }

            HorizontalDivider()
            SectionLabel("AUDIO")
            Text("Microphone trigger", style = MaterialTheme.typography.labelLarge)
            ChoiceRow(TriggerMode.entries.map { it.name to it.label }, triggerMode) { triggerMode = it }
            Text("Microphone processing", style = MaterialTheme.typography.labelLarge)
            MicrophoneMode.entries.forEach { mode ->
                val selected = microphoneMode == mode.name
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { microphoneMode = mode.name },
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(Modifier.padding(13.dp)) {
                        Text(mode.label, fontWeight = FontWeight.SemiBold)
                        Text(
                            mode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                "Xiaomi exposes bottom/back logical inputs, but its audio HAL—not normal Android apps—chooses the individual capsules in the quad-mic array. The speech preset is the best default.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (TranslationMode.valueOf(translationMode).usesSpeechOutput) {
                Text("Translated speech", style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    listOf("true" to "Play live", "false" to "Text only / muted"),
                    playTranslatedAudio.toString(),
                ) { playTranslatedAudio = it.toBoolean() }
                Text(
                    "Use headphones or the Communication microphone preset to reduce speaker echo during full-duplex playback.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (selectedProvider) {
                RealtimeProvider.Qwen -> {
                    Text("End-of-speech silence", style = MaterialTheme.typography.labelLarge)
                    ChoiceRow(
                        listOf("400" to "400 ms · fast", "700" to "700 ms", "1200" to "1.2 s · long"),
                        vadSilenceMilliseconds.toString(),
                    ) { vadSilenceMilliseconds = it.toInt() }
                    Text(
                        "Qwen splits speech automatically with server VAD. 700 ms is the balanced default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ReadOnlySetting("Format", "PCM 16-bit mono")
                    Text("Quality / sample rate", style = MaterialTheme.typography.labelLarge)
                    ChoiceRow(listOf("8000" to "8 kHz · saver", "16000" to "16 kHz · best"), sampleRate.toString()) {
                        sampleRate = it.toInt()
                    }
                    Text("Packet interval", style = MaterialTheme.typography.labelLarge)
                    ChoiceRow(listOf("40" to "40 ms", "100" to "100 ms", "200" to "200 ms"), chunkMilliseconds.toString()) {
                        chunkMilliseconds = it.toInt()
                    }
                }
                RealtimeProvider.OpenAI -> ReadOnlySetting(
                    "Wire audio",
                    "PCM16 mono · 24 kHz · 100 ms",
                    "The Translation endpoint requires raw 24 kHz PCM16 input and streams 24 kHz PCM16 output.",
                )
                RealtimeProvider.Volcengine -> ReadOnlySetting(
                    "Wire audio",
                    "PCM16 mono · 16 kHz · 80 ms",
                    "AST 2.0 requires 16 kHz source audio and recommends 80 ms packets. S2S playback uses 16 kHz PCM16.",
                )
            }

            HorizontalDivider()
            SectionLabel("HISTORY & DEBUG")
            ChoiceRow(listOf("true" to "Save text + audio + events", "false" to "Don't save"), saveHistory.toString()) {
                saveHistory = it.toBoolean()
            }
            Text(
                "Each start/stop session is saved as one full source WAV plus provider-tagged raw events. Clear History deletes all of it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReadOnlySetting(
                "Debug log",
                "files/debug_logs/livetranslate.log",
                "Open History and tap Export to share a ZIP containing this log, raw provider events, transcripts, and WAV recordings.",
            )

            HorizontalDivider()
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTesting && draft().isComplete,
                onClick = { onTest(draft()) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                if (isTesting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Test connection")
            }
            testResult?.let {
                Text(
                    it,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (it.contains("connected", ignoreCase = true)) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTesting,
                onClick = { onSave(draft()) },
            ) { Text("Done") }
        }
    }
}

@Composable
private fun LanguagePicker(
    label: String,
    selectedCode: String,
    options: List<LanguageOption> = supportedLanguages,
    onSelected: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selected = supportedLanguages.firstOrNull { it.code == selectedCode }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = "${selected?.label ?: selectedCode} · ${selectedCode.uppercase()}",
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            readOnly = true,
        )
        Box(Modifier.fillMaxSize().clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { language ->
                DropdownMenuItem(
                    text = { Text("${language.label} · ${language.code}") },
                    onClick = {
                        onSelected(language.code)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SecretField(
    label: String,
    value: String,
    visible: Boolean,
    onValueChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { TextButton(onClick = onToggleVisibility) { Text(if (visible) "Hide" else "Show") } },
    )
}

private fun defaultMode(provider: RealtimeProvider): TranslationMode = when (provider) {
    RealtimeProvider.Qwen -> TranslationMode.DualEnglishChinese
    RealtimeProvider.OpenAI -> TranslationMode.OpenAiForward
    RealtimeProvider.Volcengine -> TranslationMode.VolcBidirectionalText
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ChoiceRow(choices: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { (value, label) ->
            FilterChip(
                modifier = Modifier.weight(1f),
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
private fun ReadOnlySetting(title: String, value: String, description: String? = null) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
