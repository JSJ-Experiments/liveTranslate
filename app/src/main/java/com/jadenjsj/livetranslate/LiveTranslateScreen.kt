package com.jadenjsj.livetranslate

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.weight
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
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
    onDirectionChange: (TranslationDirection) -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onSaveSettings: (AppSettings) -> Unit,
    onTestConnection: (AppSettings) -> Unit,
    onClear: () -> Unit,
    onTalkStart: () -> Unit,
    onTalkStop: () -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
                        background,
                        background,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp),
        ) {
            Header(state, onOpenSettings, onClear)
            DirectionPicker(state.direction, !state.isActive, onDirectionChange)
            Conversation(
                state = state,
                modifier = Modifier.weight(1f),
            )
            PushToTalk(
                phase = state.phase,
                ready = state.settings.isComplete,
                onStart = onTalkStart,
                onStop = onTalkStop,
            )
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
private fun Header(state: TranslationUiState, onOpenSettings: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Live Translate",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            ConnectionStatus(state)
        }
        IconButton(enabled = !state.isActive && state.turns.isNotEmpty(), onClick = onClear) {
            Icon(painterResource(R.drawable.ic_delete), "Clear conversation")
        }
        IconButton(enabled = state.phase != SessionPhase.Listening, onClick = onOpenSettings) {
            Icon(painterResource(R.drawable.ic_settings), "Settings")
        }
    }
}

@Composable
private fun ConnectionStatus(state: TranslationUiState) {
    val label = when (state.phase) {
        SessionPhase.Idle -> if (state.settings.isComplete) "Ready · disconnected" else "Setup required"
        SessionPhase.Testing -> "Testing connection"
        SessionPhase.Connecting -> "Connecting · audio stays local"
        SessionPhase.Listening -> "Connected · sending audio"
        SessionPhase.Sending -> "Sending final audio"
        SessionPhase.Translating -> "Connected · translating"
        SessionPhase.Error -> state.error ?: "Disconnected"
    }
    val dot = when (state.phase) {
        SessionPhase.Listening, SessionPhase.Translating -> Color(0xFF2DBE72)
        SessionPhase.Connecting, SessionPhase.Sending, SessionPhase.Testing -> Color(0xFFF4A62A)
        SessionPhase.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).background(dot, CircleShape))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (state.phase == SessionPhase.Error) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (state.isActive && state.phase != SessionPhase.Listening) {
            CircularProgressIndicator(Modifier.size(11.dp), strokeWidth = 1.5.dp)
        }
    }
}

@Composable
private fun DirectionPicker(
    selected: TranslationDirection,
    enabled: Boolean,
    onSelected: (TranslationDirection) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f), RoundedCornerShape(15.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        TranslationDirection.entries.forEach { direction ->
            val active = direction == selected
            Surface(
                modifier = Modifier.weight(1f),
                onClick = { onSelected(direction) },
                enabled = enabled,
                color = if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
                tonalElevation = if (active) 2.dp else 0.dp,
            ) {
                Text(
                    text = direction.shortLabel,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Conversation(state: TranslationUiState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val hasLiveTurn = state.sourceText.isNotBlank() || state.translationText.isNotBlank() || state.isActive
    val itemCount = state.turns.size + if (hasLiveTurn) 1 else 0

    LaunchedEffect(itemCount, state.sourceText, state.translationText) {
        if (itemCount > 0) listState.scrollToItem(itemCount - 1)
    }

    if (itemCount == 0) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Hold the mic and speak",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    if (state.direction == TranslationDirection.Auto) {
                        "Chinese and English are detected automatically"
                    } else {
                        state.direction.shortLabel
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(top = 22.dp, bottom = 12.dp),
    ) {
        items(state.turns, key = TranslationTurn::id) { turn ->
            TranscriptTurn(
                sourceText = turn.sourceText,
                translationText = turn.translationText,
                sourceLanguage = turn.sourceLanguage,
                targetLanguage = turn.targetLanguage,
                live = false,
            )
        }
        if (hasLiveTurn) {
            item(key = "live") {
                TranscriptTurn(
                    sourceText = state.sourceText,
                    translationText = state.translationText,
                    sourceLanguage = state.detectedSourceLanguage ?: state.direction.sourceLanguage,
                    targetLanguage = state.activeTargetLanguage,
                    live = state.isActive,
                )
            }
        }
    }
}

@Composable
private fun TranscriptTurn(
    sourceText: String,
    translationText: String,
    sourceLanguage: String?,
    targetLanguage: String,
    live: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${languageLabel(sourceLanguage)} · ORIGINAL",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (live) {
                Spacer(Modifier.size(7.dp))
                Box(Modifier.size(6.dp).background(Color(0xFF2DBE72), CircleShape))
            }
        }
        Text(
            text = sourceText.ifBlank { if (live) "Listening…" else "—" },
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            fontStyle = if (sourceText.isBlank()) FontStyle.Italic else FontStyle.Normal,
            color = if (sourceText.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(11.dp))
        Text(
            text = "${languageLabel(targetLanguage)} · TRANSLATION",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (translationText.isNotBlank()) {
            Text(
                text = translationText,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        )
    }
}

private fun languageLabel(code: String?): String = when {
    code == null -> "AUTO"
    code == "zh" || code.startsWith("zh-") -> "中文"
    code == "en" || code.startsWith("en-") -> "ENGLISH"
    else -> code.uppercase()
}

@Composable
private fun PushToTalk(
    phase: SessionPhase,
    ready: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val active = phase == SessionPhase.Connecting || phase == SessionPhase.Listening
    val scale by animateFloatAsState(if (active) 1.07f else 1f, label = "mic scale")
    val color by animateColorAsState(
        if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        label = "mic color",
    )
    val infiniteTransition = rememberInfiniteTransition(label = "mic glow")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "mic pulse",
    )
    val currentStart by rememberUpdatedState(onStart)
    val currentStop by rememberUpdatedState(onStop)
    val canStart by rememberUpdatedState(ready && (phase == SessionPhase.Idle || phase == SessionPhase.Error))

    val text = when (phase) {
        SessionPhase.Connecting -> "CONNECTING · KEEP HOLDING"
        SessionPhase.Listening -> "LISTENING · RELEASE TO SEND"
        SessionPhase.Sending -> "SENDING…"
        SessionPhase.Translating -> "TRANSLATING…"
        SessionPhase.Testing -> "TESTING CONNECTION…"
        else -> if (ready) "HOLD TO TALK" else "SET UP CONNECTION"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 5.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Push to talk. Hold while speaking, then release to send."
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        if (canStart) {
                            currentStart()
                            try {
                                waitForUpOrCancellation()
                            } finally {
                                currentStop()
                            }
                        } else {
                            waitForUpOrCancellation()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (active) {
                Box(
                    Modifier
                        .size(76.dp)
                        .graphicsLayer {
                            scaleX = pulse
                            scaleY = pulse
                            alpha = 0.2f
                        }
                        .background(color, CircleShape),
                )
            }
            Box(
                modifier = Modifier
                    .scale(scale)
                    .size(66.dp)
                    .background(color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_mic),
                    contentDescription = null,
                    modifier = Modifier.size(29.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Text(
            text = text,
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
    var showKey by rememberSaveable { mutableStateOf(false) }

    fun draft() = AppSettings(
        apiKey = apiKey,
        workspaceId = workspaceId,
        region = Region.valueOf(regionName),
        sampleRate = sampleRate,
        chunkMilliseconds = chunkMilliseconds,
        hotwords = hotwords,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "The API key is AES-GCM encrypted with Android Keystore and sent only to Alibaba Cloud. A client-side key is suitable for a personal build, not broad distribution.",
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
                trailingIcon = {
                    TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") }
                },
            )
            OutlinedTextField(
                value = workspaceId,
                onValueChange = { workspaceId = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Workspace ID") },
                singleLine = true,
            )
            Text("Region", style = MaterialTheme.typography.labelLarge)
            ChoiceRow(
                choices = Region.entries.map { it.name to it.label },
                selected = regionName,
                onSelect = { regionName = it },
            )
            ReadOnlySetting("Model", "qwen3.5-livetranslate-flash-realtime")

            HorizontalDivider()
            SectionLabel("AUDIO")
            ReadOnlySetting(
                "Format",
                "PCM 16-bit mono",
                "Qwen also accepts Opus. This build keeps PCM because Android's raw Opus packet framing needs a separate interoperability pass before it is safe to expose.",
            )
            Text("Quality / sample rate", style = MaterialTheme.typography.labelLarge)
            ChoiceRow(
                choices = listOf("8000" to "8 kHz · saver", "16000" to "16 kHz · best"),
                selected = sampleRate.toString(),
                onSelect = { sampleRate = it.toInt() },
            )
            Text("Packet interval", style = MaterialTheme.typography.labelLarge)
            ChoiceRow(
                choices = listOf("40" to "40 ms", "100" to "100 ms", "200" to "200 ms"),
                selected = chunkMilliseconds.toString(),
                onSelect = { chunkMilliseconds = it.toInt() },
            )
            Text(
                "Shorter packets reduce streaming latency; longer packets use slightly less overhead.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()
            SectionLabel("TRANSLATION")
            ReadOnlySetting(
                "Automatic direction",
                "Source language auto-detection",
                "In Auto mode, Qwen detects the source and the app switches the target between Chinese and English before the push-to-talk turn is committed.",
            )
            OutlinedTextField(
                value = hotwords,
                onValueChange = { hotwords = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Hotwords (optional)") },
                placeholder = { Text("Qwen=千问\nVPN=虚拟专用网络") },
                minLines = 2,
                supportingText = { Text("One source=translation pair per line") },
            )
            ReadOnlySetting("Output", "Text only", "Spoken output is deferred until the translation path is reliable.")

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
                    color = if (it.startsWith("Connected")) Color(0xFF2DBE72)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = apiKey.isNotBlank() && workspaceId.isNotBlank() && !isTesting,
                onClick = { onSave(draft()) },
            ) {
                Text("Save settings")
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ChoiceRow(
    choices: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
