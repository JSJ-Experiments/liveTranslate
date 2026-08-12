package dev.jsj.livetranslate

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp),
        ) {
            Header(
                state = state,
                onOpenSettings = onOpenSettings,
                onClear = onClear,
            )
            ConnectionBanner(state)
            Spacer(Modifier.height(14.dp))
            DirectionPicker(
                selected = state.direction,
                enabled = !state.isActive,
                onSelected = onDirectionChange,
            )
            Spacer(Modifier.height(14.dp))
            TranscriptCard(
                modifier = Modifier.weight(0.72f),
                label = state.direction.sourceLabel,
                text = state.sourceText,
                placeholder = "What you say appears here",
                emphasized = false,
            )
            Spacer(Modifier.height(10.dp))
            TranscriptCard(
                modifier = Modifier.weight(1f),
                label = state.direction.targetLabel,
                text = state.translationText,
                placeholder = "Translation appears here",
                emphasized = true,
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
            .padding(top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Live Translate",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Qwen 3.5 realtime",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(enabled = !state.isActive, onClick = onClear) {
            Icon(painterResource(R.drawable.ic_delete), "Clear transcripts")
        }
        IconButton(enabled = state.phase != SessionPhase.Listening, onClick = onOpenSettings) {
            Icon(painterResource(R.drawable.ic_settings), "Settings")
        }
    }
}

@Composable
private fun ConnectionBanner(state: TranslationUiState) {
    val (label, detail) = when (state.phase) {
        SessionPhase.Idle -> "Not connected" to if (state.settings.isComplete) "Hold the button to connect" else "Finish setup to begin"
        SessionPhase.Testing -> "Testing connection" to "Authenticating with Qwen"
        SessionPhase.Connecting -> "Connecting" to "Audio is buffered on this device"
        SessionPhase.Listening -> "Connected · live" to "Audio is streaming to Qwen"
        SessionPhase.Sending -> "Sending" to "Uploading the final audio buffer"
        SessionPhase.Translating -> "Connected · translating" to "Waiting for the final response"
        SessionPhase.Error -> "Disconnected" to (state.error ?: "Connection failed")
    }
    val dot = when (state.phase) {
        SessionPhase.Listening, SessionPhase.Translating -> Color(0xFF45D483)
        SessionPhase.Connecting, SessionPhase.Sending, SessionPhase.Testing -> Color(0xFFFFC857)
        SessionPhase.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(Modifier.size(9.dp).background(dot, CircleShape))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.isActive && state.phase != SessionPhase.Listening) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
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
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(18.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TranslationDirection.entries.forEach { direction ->
            val active = direction == selected
            Surface(
                modifier = Modifier.weight(1f),
                onClick = { onSelected(direction) },
                enabled = enabled,
                color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text = direction.shortLabel,
                    modifier = Modifier.padding(vertical = 11.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TranscriptCard(
    modifier: Modifier,
    label: String,
    text: String,
    placeholder: String,
    emphasized: Boolean,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(19.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = text.ifBlank { placeholder },
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                style = if (emphasized) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                fontWeight = if (text.isBlank()) FontWeight.Normal else FontWeight.Medium,
                color = if (text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PushToTalk(
    phase: SessionPhase,
    ready: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val pressed = phase == SessionPhase.Connecting || phase == SessionPhase.Listening
    val scale by animateFloatAsState(if (pressed) 1.08f else 1f, label = "mic scale")
    val color by animateColorAsState(
        if (pressed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        label = "mic color",
    )
    val text = when (phase) {
        SessionPhase.Connecting -> "KEEP HOLDING · CONNECTING"
        SessionPhase.Listening -> "LISTENING · RELEASE TO SEND"
        SessionPhase.Sending -> "SENDING AUDIO…"
        SessionPhase.Translating -> "TRANSLATING…"
        else -> if (ready) "HOLD TO TALK" else "SET UP CONNECTION"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 15.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .scale(scale)
                .size(78.dp)
                .background(color, CircleShape)
                .semantics {
                    role = Role.Button
                    contentDescription = "Push to talk. Hold while speaking."
                }
                .pointerInput(phase, ready) {
                    detectTapGestures(
                        onPress = {
                            if (phase == SessionPhase.Idle || phase == SessionPhase.Error) {
                                onStart()
                                tryAwaitRelease()
                                onStop()
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (pressed) color else MaterialTheme.colorScheme.onSurfaceVariant,
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
                "Credentials stay in this app's private storage and are sent only to Alibaba Cloud. A client-side key is fine for a personal MVP, not a public release.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionLabel("MODEL STUDIO")
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
                "The official reference client uses PCM. Opus is smaller, but Alibaba does not document the required packet container, so it is not exposed as an unsafe option.",
            )
            Text("Quality / sample rate", style = MaterialTheme.typography.labelLarge)
            ChoiceRow(
                choices = listOf("8000" to "8 kHz · data saver", "16000" to "16 kHz · best"),
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
                "Shorter packets can feel more responsive; longer packets use slightly less overhead.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()
            SectionLabel("TRANSLATION")
            OutlinedTextField(
                value = hotwords,
                onValueChange = { hotwords = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Hotwords (optional)") },
                placeholder = { Text("Qwen=千问\nVPN=虚拟专用网络") },
                minLines = 2,
                supportingText = { Text("One source=translation pair per line") },
            )
            ReadOnlySetting("Output", "Text only", "Speech output is intentionally deferred from this first reliable build.")

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
                    color = if (it.startsWith("Connected")) Color(0xFF3BBF76) else MaterialTheme.colorScheme.onSurfaceVariant,
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
