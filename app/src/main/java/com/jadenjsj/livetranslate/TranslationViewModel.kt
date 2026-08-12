package com.jadenjsj.livetranslate

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TranslationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val microphone = MicrophoneRecorder(application)
    private val history = HistoryRepository(application)
    private val debugLog = DebugLog(application)
    private val networkMonitor = NetworkMonitor(application) { online ->
        debugLog.write(if (online) "INFO" else "WARN", if (online) "Network restored" else "Network lost")
    }
    private val mutableState = MutableStateFlow(TranslationUiState())
    val state: StateFlow<TranslationUiState> = mutableState.asStateFlow()

    private var audioChannel: Channel<ByteArray>? = null
    private var sessionJob: kotlinx.coroutines.Job? = null
    private var testJob: kotlinx.coroutines.Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private val segmentIds = AtomicLong(System.currentTimeMillis())

    init {
        viewModelScope.launch {
            val savedTurns = withContext(Dispatchers.IO) { history.load() }
            mutableState.update { it.copy(turns = savedTurns) }
        }
        viewModelScope.launch {
            repository.settings.collect { settings ->
                mutableState.update {
                    it.copy(
                        settings = settings,
                        settingsLoaded = true,
                        settingsOpen = it.settingsOpen || (!it.settingsLoaded && !settings.isComplete),
                    )
                }
            }
        }
        viewModelScope.launch {
            networkMonitor.online.collect { online -> mutableState.update { it.copy(isOnline = online) } }
        }
    }

    fun setDirection(direction: TranslationDirection) {
        if (!state.value.isActive) mutableState.update { it.copy(direction = direction) }
    }

    fun openSettings() = mutableState.update { it.copy(settingsOpen = true) }
    fun closeSettings() = mutableState.update { it.copy(settingsOpen = false) }
    fun openHistory() = mutableState.update { it.copy(historyOpen = true) }
    fun closeHistory() = mutableState.update { it.copy(historyOpen = false) }

    fun saveSettings(settings: AppSettings) {
        viewModelScope.launch {
            repository.save(settings)
            mutableState.update { it.copy(settingsOpen = false, connectionTestResult = null) }
        }
    }

    fun clearHistory() {
        if (!state.value.isActive) {
            mutableState.update { it.copy(turns = emptyList()) }
            viewModelScope.launch(Dispatchers.IO) { history.clear() }
        }
    }

    fun microphonePermissionDenied() {
        mutableState.update {
            it.copy(phase = SessionPhase.Error, error = "Microphone permission is required")
        }
    }

    fun playRecording(turn: TranslationTurn) {
        val path = turn.audioPath ?: return
        mediaPlayer?.release()
        mediaPlayer = runCatching {
            MediaPlayer().apply {
                setDataSource(path)
                prepare()
                setOnCompletionListener {
                    it.release()
                    if (mediaPlayer === it) mediaPlayer = null
                }
                start()
            }
        }.getOrNull()
    }

    fun startTalking() {
        val snapshot = state.value
        if (snapshot.isActive) return
        if (!snapshot.settings.isComplete) {
            mutableState.update { it.copy(settingsOpen = true) }
            return
        }
        if (!snapshot.isOnline) {
            debugLog.write("WARN", "Translation start rejected while offline")
            mutableState.update { it.copy(phase = SessionPhase.Error, error = "Offline · check your network connection") }
            return
        }

        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        val conversationId = System.currentTimeMillis()
        val capture = if (snapshot.settings.saveHistory) {
            runCatching { history.begin(conversationId, snapshot.settings.sampleRate) }.getOrNull()
        } else null

        audioChannel = channel
        mutableState.update {
            it.copy(
                phase = SessionPhase.Connecting,
                liveTurns = emptyList(),
                sourceText = "",
                translationText = "",
                detectedSourceLanguage = null,
                error = null,
            )
        }

        try {
            microphone.start(
                scope = viewModelScope,
                sampleRate = snapshot.settings.sampleRate,
                chunkMilliseconds = snapshot.settings.chunkMilliseconds,
                onAudio = {
                    capture?.appendAudio(it)
                    channel.trySend(it)
                },
            )
        } catch (error: Throwable) {
            channel.close()
            capture?.discard()
            showError(error)
            return
        }

        sessionJob = viewModelScope.launch {
            val sessions = createSessions(snapshot, capture)
            try {
                debugLog.write("INFO", "Starting ${snapshot.settings.translationMode.name} with ${sessions.size} session(s)")
                coroutineScope { sessions.map { async { it.connect() } }.awaitAll() }
                debugLog.write("INFO", "Qwen session(s) connected")
                mutableState.update {
                    if (it.phase == SessionPhase.Connecting) it.copy(phase = SessionPhase.Listening) else it
                }

                var bytesSent = 0L
                for (chunk in channel) {
                    sessions.forEach { it.append(chunk) }
                    bytesSent += chunk.size
                }

                mutableState.update { it.copy(phase = SessionPhase.Translating) }
                sessions.forEach(QwenRealtimeSession::finish)
                coroutineScope { sessions.map { async { it.awaitFinished() } }.awaitAll() }
                finishPendingSegment()
                archiveConversation(conversationId, snapshot, capture)
                mutableState.update { it.copy(phase = SessionPhase.Idle, error = null) }
                debugLog.write("INFO", "Translation session finished with ${state.value.liveTurns.size} segment(s)")
            } catch (error: Throwable) {
                debugLog.write("ERROR", "Translation session failed", error)
                finishPendingSegment()
                archiveConversation(conversationId, snapshot, capture)
                showError(error)
            } finally {
                microphone.stop()
                audioChannel = null
                sessions.forEach(QwenRealtimeSession::close)
            }
        }
    }

    private fun createSessions(
        snapshot: TranslationUiState,
        capture: TurnCapture?,
    ): List<QwenRealtimeSession> {
        val settings = snapshot.settings
        return when (settings.translationMode) {
            TranslationMode.DualEnglishChinese -> listOf(
                createSession(settings, "zh", transcribeSource = true, skipSame = true, capture = capture),
                createSession(settings, "en", transcribeSource = false, skipSame = true, capture = capture),
            )
            TranslationMode.DetectedPair -> listOf(
                createSession(
                    settings,
                    settings.secondaryLanguage,
                    transcribeSource = true,
                    skipSame = false,
                    capture = capture,
                ),
            )
            TranslationMode.ManualForward -> listOf(
                createSession(
                    settings,
                    settings.secondaryLanguage,
                    sourceLanguage = settings.primaryLanguage,
                    transcribeSource = true,
                    skipSame = false,
                    capture = capture,
                ),
            )
            TranslationMode.ManualReverse -> listOf(
                createSession(
                    settings,
                    settings.primaryLanguage,
                    sourceLanguage = settings.secondaryLanguage,
                    transcribeSource = true,
                    skipSame = false,
                    capture = capture,
                ),
            )
        }
    }

    private fun createSession(
        settings: AppSettings,
        targetLanguage: String,
        sourceLanguage: String? = null,
        transcribeSource: Boolean,
        skipSame: Boolean,
        capture: TurnCapture?,
    ): QwenRealtimeSession {
        var streamTranslation = ""
        return QwenRealtimeSession(
            settings = settings,
            direction = TranslationDirection.Auto,
            options = QwenSessionOptions(
                targetLanguage = targetLanguage,
                sourceLanguage = sourceLanguage,
                serverVad = true,
                skipSameLanguage = skipSame,
                transcribeSource = transcribeSource,
            ),
            onRawEvent = { raw -> capture?.appendServerEvent(targetLanguage, raw) },
            onEvent = { event ->
                when (event) {
                    is QwenServerEvent.SourcePreview -> updateSource(event.text, event.language)
                    is QwenServerEvent.SourceDone -> updateSource(event.text, event.language)
                    is QwenServerEvent.TranslationPreview -> {
                        streamTranslation = event.text
                        mutableState.update {
                            it.copy(translationText = event.text, activeTargetLanguage = targetLanguage)
                        }
                    }
                    is QwenServerEvent.TranslationDone -> {
                        streamTranslation = event.text
                        mutableState.update {
                            it.copy(translationText = event.text, activeTargetLanguage = targetLanguage)
                        }
                    }
                    QwenServerEvent.ResponseDone -> {
                        if (streamTranslation.isNotBlank()) {
                            completeSegment(streamTranslation, targetLanguage)
                            streamTranslation = ""
                        }
                    }
                    else -> Unit
                }
            },
        )
    }

    private fun updateSource(text: String, language: String?) {
        if (text.isBlank()) return
        mutableState.update {
            it.copy(
                sourceText = text,
                detectedSourceLanguage = language?.lowercase() ?: it.detectedSourceLanguage,
            )
        }
    }

    private fun completeSegment(translation: String, targetLanguage: String) {
        mutableState.update { current ->
            if (translation.isBlank()) return@update current
            val segment = TranslationTurn(
                id = segmentIds.updateAndGet { previous -> maxOf(previous + 1, System.currentTimeMillis()) },
                sourceText = current.sourceText,
                translationText = translation,
                sourceLanguage = current.detectedSourceLanguage,
                targetLanguage = targetLanguage,
            )
            current.copy(
                liveTurns = current.liveTurns + segment,
                sourceText = "",
                translationText = "",
                detectedSourceLanguage = null,
            )
        }
    }

    private fun finishPendingSegment() {
        val current = state.value
        if (current.translationText.isNotBlank()) {
            completeSegment(current.translationText, current.activeTargetLanguage)
        }
    }

    private suspend fun archiveConversation(
        id: Long,
        snapshot: TranslationUiState,
        capture: TurnCapture?,
    ) {
        val segments = state.value.liveTurns
        if (segments.isEmpty()) {
            capture?.discard()
            return
        }
        val archived = TranslationTurn(
            id = id,
            sourceText = segments.map(TranslationTurn::sourceText).filter(String::isNotBlank).joinToString("\n"),
            translationText = segments.joinToString("\n") { it.translationText },
            sourceLanguage = segments.mapNotNull(TranslationTurn::sourceLanguage).distinct().joinToString("/").ifBlank { null },
            targetLanguage = segments.map(TranslationTurn::targetLanguage).distinct().joinToString("/"),
            createdAtMillis = id,
        )
        val saved = if (capture == null) archived else withContext(Dispatchers.IO) { capture.complete(archived) }
        mutableState.update { it.copy(turns = it.turns + saved) }
    }

    fun stopTalking() {
        val phase = state.value.phase
        if (phase != SessionPhase.Connecting && phase != SessionPhase.Listening) return
        microphone.stop()
        audioChannel?.close()
        mutableState.update { it.copy(phase = SessionPhase.Sending) }
    }

    fun testConnection(settings: AppSettings = state.value.settings) {
        if (testJob?.isActive == true || state.value.isActive) return
        if (!settings.isComplete) {
            mutableState.update { it.copy(connectionTestResult = "Add an API key and workspace ID first") }
            return
        }
        testJob = viewModelScope.launch {
            mutableState.update { it.copy(phase = SessionPhase.Testing, connectionTestResult = "Connecting…") }
            val started = TimeSource.Monotonic.markNow()
            var session: QwenRealtimeSession? = null
            try {
                session = QwenRealtimeSession(settings, TranslationDirection.Auto, onEvent = {})
                session.connect()
                val latency = started.elapsedNow().inWholeMilliseconds
                session.finish()
                session.awaitFinished()
                mutableState.update {
                    it.copy(phase = SessionPhase.Idle, connectionTestResult = "Connected · ${latency} ms")
                }
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(phase = SessionPhase.Idle, connectionTestResult = friendlyMessage(error))
                }
            } finally {
                session?.close()
            }
        }
    }

    private fun showError(error: Throwable) {
        debugLog.write("ERROR", "UI error: ${friendlyMessage(error)}", error)
        microphone.stop()
        audioChannel?.close()
        mutableState.update { it.copy(phase = SessionPhase.Error, error = friendlyMessage(error)) }
    }

    private fun friendlyMessage(error: Throwable): String = when (error) {
        is kotlinx.coroutines.TimeoutCancellationException -> "Connection timed out"
        is IOException -> "Network error · ${error.message ?: "check your connection"}"
        else -> error.message ?: "Something went wrong"
    }

    override fun onCleared() {
        microphone.stop()
        audioChannel?.close()
        sessionJob?.cancel()
        testJob?.cancel()
        mediaPlayer?.release()
        networkMonitor.close()
    }
}
