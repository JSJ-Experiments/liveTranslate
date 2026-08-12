package com.jadenjsj.livetranslate

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.matchParentSize
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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

@Composable
fun LiveTranslateScreen(
    state: TranslationUiState,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onSaveSettings: (AppSettings) -> Unit,
    onTestConnection: (AppSettings) -> Unit,
    onOpenHistory: () -> Unit,
    onCloseHistory: () -> Unit,
    onClearHistory: () -> Unit,
    onPlayTurn: (TranslationTurn) -> Unit,
    onTalkStart: () -> Unit,
    onTalkStop: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (state.historyOpen) {
            HistoryScreen(state, onCloseHistory, onClearHistory, onPlayTurn)
        } else {
            InterpreterScreen(state, onOpenHistory, onOpenSettings, onTalkStart, onTalkStop)
        }
    }

    if (state.settingsOpen) {
        SettingsSheet(
            initial = state.settings,
            testResult = state.connectionTestResult,
            isTesting = state.phase == SessionPhase.Testing,
            onDismiss = onCloseSettings,
            onSave = onSaveSettings,
            onTest = onTestConnection,
        )
    }
}

@Composable
private fun InterpreterScreen(
    state: TranslationUiState,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
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
        CompactTopBar(state, onOpenHistory, onOpenSettings)
        AttentionBanner(state)
        LiveTranscript(state, Modifier.weight(1f))
        PushToTalk(
            phase = state.phase,
            ready = state.settings.isComplete && state.isOnline,
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
    onOpenSettings: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val dotColor = when {
            !state.isOnline || state.phase == SessionPhase.Error -> Color(0xFFE5484D)
            state.phase in setOf(SessionPhase.Connecting, SessionPhase.Sending, SessionPhase.Testing) -> Color(0xFFF4A62A)
            state.phase in setOf(SessionPhase.Listening, SessionPhase.Translating) -> Color(0xFF20B86A)
            else -> MaterialTheme.colorScheme.outline
        }
        Box(
            Modifier
                .size(12.dp)
                .background(dotColor, CircleShape)
                .semantics { contentDescription = connectionDescription(state) },
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onOpenHistory, enabled = !state.isActive) {
            Icon(
                painterResource(R.drawable.ic_history),
                "History",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onOpenSettings, enabled = !state.isActive) {
            Icon(
                painterResource(R.drawable.ic_settings),
                "Settings",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun connectionDescription(state: TranslationUiState): String = when {
    !state.isOnline -> "Offline"
    state.phase == SessionPhase.Error -> "Connection error"
    state.phase == SessionPhase.Connecting -> "Connecting"
    state.phase == SessionPhase.Listening -> "Connected"
    state.phase == SessionPhase.Sending -> "Sending"
    state.phase == SessionPhase.Translating -> "Connected and translating"
    else -> "Disconnected"
}

@Composable
private fun AttentionBanner(state: TranslationUiState) {
    val message = when {
        !state.isOnline -> "Offline · reconnect to the internet before starting"
        state.phase == SessionPhase.Error -> state.error ?: "Translation connection failed"
        state.phase == SessionPhase.Connecting -> "Connecting to Qwen…"
        else -> null
    } ?: return
    val warning = state.phase == SessionPhase.Connecting
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        color = if (warning) Color(0xFFFFE9BF) else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (warning) Color(0xFF5F4200) else MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
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
        "zh" -> Color(0xFFEA6A8E)
        "en" -> Color(0xFF2D8CFF)
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
    onPlayTurn: (TranslationTurn) -> Unit,
) {
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
            IconButton(onClick = onClear, enabled = state.turns.isNotEmpty()) {
                Icon(painterResource(R.drawable.ic_delete), "Clear history", tint = MaterialTheme.colorScheme.onSurface)
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
                    HistoryItem(turn, onPlayTurn)
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(turn: TranslationTurn, onPlay: (TranslationTurn) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                turn.sourceText.ifBlank { "Source transcript unavailable" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                turn.translationText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (turn.audioPath != null) {
                TextButton(onClick = { onPlay(turn) }, contentPadding = PaddingValues(0.dp)) {
                    Icon(painterResource(R.drawable.ic_play), null, Modifier.size(17.dp))
                    Spacer(Modifier.size(5.dp))
                    Text("Play full recording")
                }
            }
        }
    }
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
    val micColor = if (active) Color(0xFFE5484D) else Color(0xFF1976ED)
    val color by animateColorAsState(micColor, label = "mic color")
    val scale by animateFloatAsState(if (active) 1.07f else 1f, label = "mic scale")
    val pulse by rememberInfiniteTransition(label = "mic glow").animateFloat(
        initialValue = 0.88f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "mic pulse",
    )
    val currentStart by rememberUpdatedState(onStart)
    val currentStop by rememberUpdatedState(onStop)
    val canStart by rememberUpdatedState(ready && (phase == SessionPhase.Idle || phase == SessionPhase.Error))
    val currentPhase by rememberUpdatedState(phase)
    val currentMode by rememberUpdatedState(triggerMode)
    val label = when (phase) {
        SessionPhase.Listening -> if (triggerMode == TriggerMode.Hold) "RELEASE TO STOP" else "TAP TO STOP"
        SessionPhase.Connecting -> "CONNECTING…"
        SessionPhase.Sending, SessionPhase.Translating -> "FINISHING…"
        else -> if (!ready) "OFFLINE / SETUP REQUIRED"
        else if (triggerMode == TriggerMode.Hold) "HOLD TO TALK" else "TAP TO START"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(82.dp)
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
            contentAlignment = Alignment.Center,
        ) {
            if (active) {
                Box(
                    Modifier
                        .size(72.dp)
                        .graphicsLayer {
                            scaleX = pulse
                            scaleY = pulse
                            alpha = 0.2f
                        }
                        .background(color, CircleShape),
                )
            }
            Box(
                Modifier
                    .scale(scale)
                    .size(64.dp)
                    .background(color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_mic),
                    null,
                    Modifier.size(29.dp),
                    tint = Color.White,
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (active) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    initial: AppSettings,
    testResult: String?,
    isTesting: Boolean,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit,
    onTest: (AppSettings) -> Unit,
) {
    var apiKey by rememberSaveable(initial.apiKey) { mutableStateOf(initial.apiKey) }
    var workspaceId by rememberSaveable(initial.workspaceId) { mutableStateOf(initial.workspaceId) }
    var regionName by rememberSaveable(initial.region.name) { mutableStateOf(initial.region.name) }
    var sampleRate by rememberSaveable(initial.sampleRate) { mutableStateOf(initial.sampleRate) }
    var chunkMilliseconds by rememberSaveable(initial.chunkMilliseconds) { mutableStateOf(initial.chunkMilliseconds) }
    var hotwords by rememberSaveable(initial.hotwords) { mutableStateOf(initial.hotwords) }
    var primaryLanguage by rememberSaveable(initial.primaryLanguage) { mutableStateOf(initial.primaryLanguage) }
    var secondaryLanguage by rememberSaveable(initial.secondaryLanguage) { mutableStateOf(initial.secondaryLanguage) }
    var triggerMode by rememberSaveable(initial.triggerMode.name) { mutableStateOf(initial.triggerMode.name) }
    var translationMode by rememberSaveable(initial.translationMode.name) { mutableStateOf(initial.translationMode.name) }
    var saveHistory by rememberSaveable(initial.saveHistory) { mutableStateOf(initial.saveHistory) }
    var showKey by rememberSaveable { mutableStateOf(false) }

    fun draft() = AppSettings(
        apiKey = apiKey,
        workspaceId = workspaceId,
        region = Region.valueOf(regionName),
        sampleRate = sampleRate,
        chunkMilliseconds = chunkMilliseconds,
        hotwords = hotwords,
        primaryLanguage = primaryLanguage,
        secondaryLanguage = secondaryLanguage,
        triggerMode = TriggerMode.valueOf(triggerMode),
        saveHistory = saveHistory,
        translationMode = TranslationMode.valueOf(translationMode),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                "The API key is encrypted with Android Keystore and sent only to Alibaba Cloud.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionLabel("MODEL STUDIO · DASHSCOPE")
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") } },
            )
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

            HorizontalDivider()
            SectionLabel("TRANSLATION")
            Text("Mode", style = MaterialTheme.typography.labelLarge)
            TranslationMode.entries.forEach { mode ->
                val selected = translationMode == mode.name
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { translationMode = mode.name },
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(Modifier.padding(13.dp)) {
                        Text(mode.label, fontWeight = FontWeight.SemiBold)
                        Text(
                            mode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (translationMode != TranslationMode.DualEnglishChinese.name) {
                LanguagePicker("Language A", primaryLanguage) { if (it != secondaryLanguage) primaryLanguage = it }
                LanguagePicker("Language B", secondaryLanguage) { if (it != primaryLanguage) secondaryLanguage = it }
            } else {
                ReadOnlySetting("Language pair", "English ↔ Chinese", "This reliable mode uses two simultaneous target streams.")
            }
            OutlinedTextField(
                value = hotwords,
                onValueChange = { hotwords = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Hotwords (optional)") },
                placeholder = { Text("Qwen=千问\nVPN=虚拟专用网络") },
                minLines = 2,
                supportingText = { Text("One source=translation pair per line") },
            )

            HorizontalDivider()
            SectionLabel("AUDIO")
            Text("Microphone trigger", style = MaterialTheme.typography.labelLarge)
            ChoiceRow(TriggerMode.entries.map { it.name to it.label }, triggerMode) { triggerMode = it }
            ReadOnlySetting("Format", "PCM 16-bit mono", "Opus remains deferred until packet interoperability is tested.")
            Text("Quality / sample rate", style = MaterialTheme.typography.labelLarge)
            ChoiceRow(listOf("8000" to "8 kHz · saver", "16000" to "16 kHz · best"), sampleRate.toString()) {
                sampleRate = it.toInt()
            }
            Text("Packet interval", style = MaterialTheme.typography.labelLarge)
            ChoiceRow(listOf("40" to "40 ms", "100" to "100 ms", "200" to "200 ms"), chunkMilliseconds.toString()) {
                chunkMilliseconds = it.toInt()
            }

            HorizontalDivider()
            SectionLabel("HISTORY & DEBUG")
            ChoiceRow(listOf("true" to "Save text + audio + events", "false" to "Don't save"), saveHistory.toString()) {
                saveHistory = it.toBoolean()
            }
            Text(
                "Each start/stop session is saved as one full WAV plus tagged raw Qwen JSONL events. Clear History deletes all of it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReadOnlySetting(
                "Debug log",
                "files/debug_logs/livetranslate.log",
                "Readable with Android Studio Device Explorer or: adb exec-out run-as com.jadenjsj.livetranslate cat files/debug_logs/livetranslate.log",
            )

            HorizontalDivider()
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTesting && apiKey.isNotBlank() && workspaceId.isNotBlank(),
                onClick = { onTest(draft()) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
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
                    color = if (it.startsWith("Connected")) Color(0xFF20B86A) else MaterialTheme.colorScheme.error,
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = apiKey.isNotBlank() && workspaceId.isNotBlank() && !isTesting,
                onClick = { onSave(draft()) },
            ) { Text("Save settings") }
        }
    }
}

@Composable
private fun LanguagePicker(label: String, selectedCode: String, onSelected: (String) -> Unit) {
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
        Box(Modifier.matchParentSize().clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            supportedLanguages.forEach { language ->
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
