package com.jadenjsj.livetranslate

import android.app.Application
import android.media.MediaPlayer
import android.media.PlaybackParams
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class TranslationViewModel(application: Application) : AndroidViewModel(application) {
    private val debugLog = DebugLog(application)
    private val repository = SettingsRepository(application)
    private val microphone = MicrophoneRecorder(application)
    private val history = HistoryRepository(application)
    private val debugExporter = DebugExporter(application)
    private val networkMonitor = NetworkMonitor(application) { online ->
        debugLog.write(if (online) "INFO" else "WARN", if (online) "Network restored" else "Network lost")
    }
    private val mutableState = MutableStateFlow(TranslationUiState())
    val state: StateFlow<TranslationUiState> = mutableState.asStateFlow()

    private var audioChannel: Channel<ByteArray>? = null
    private var sessionJob: kotlinx.coroutines.Job? = null
    private var testJob: kotlinx.coroutines.Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: kotlinx.coroutines.Job? = null
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
            networkMonitor.online.collect { online ->
                val before = state.value
                mutableState.update { it.copy(isOnline = online) }
                if (online && !before.isOnline && before.phase == SessionPhase.Error && before.settings.isComplete) {
                    testConnection()
                }
            }
        }
    }

    fun setDirection(direction: TranslationDirection) {
        if (!state.value.isActive) mutableState.update { it.copy(direction = direction) }
    }

    fun openSettings() = mutableState.update { it.copy(settingsOpen = true) }
    fun closeSettings() = mutableState.update { it.copy(settingsOpen = false) }
    fun openHistory() = mutableState.update { it.copy(historyOpen = true, selectedHistoryId = null) }
    fun closeHistory() {
        stopPlayback()
        mutableState.update { it.copy(historyOpen = false, selectedHistoryId = null) }
    }

    fun selectHistorySession(id: Long) {
        stopPlayback()
        val turn = state.value.turns.firstOrNull { it.id == id } ?: return
        mutableState.update {
            it.copy(
                selectedHistoryId = id,
                playback = PlaybackState(sessionId = id, durationMillis = turn.durationMillis),
            )
        }
    }

    fun closeHistorySession() {
        stopPlayback()
        mutableState.update { it.copy(selectedHistoryId = null) }
    }

    fun saveSettings(settings: AppSettings) {
        viewModelScope.launch {
            repository.save(settings)
            mutableState.update { it.copy(settingsOpen = false, connectionTestResult = null) }
        }
    }

    fun clearHistory() {
        if (!state.value.isActive) {
            stopPlayback()
            mutableState.update { it.copy(turns = emptyList(), selectedHistoryId = null) }
            viewModelScope.launch(Dispatchers.IO) { history.clear() }
        }
    }

    fun exportDiagnostics() {
        runCatching { debugExporter.share() }
            .onSuccess { debugLog.write("INFO", "Diagnostics export opened") }
            .onFailure { debugLog.write("ERROR", "Could not export diagnostics", it) }
    }

    fun microphonePermissionDenied() {
        mutableState.update {
            it.copy(phase = SessionPhase.Error, error = "Microphone permission is required")
        }
    }

    fun togglePlayback(turn: TranslationTurn) {
        val path = turn.audioPath ?: return
        val current = mediaPlayer
        if (current != null && state.value.playback.sessionId == turn.id) {
            if (current.isPlaying) current.pause() else current.start()
            mutableState.update { it.copy(playback = it.playback.copy(isPlaying = current.isPlaying)) }
            if (current.isPlaying) startPlaybackProgress()
            return
        }
        stopPlayback()
        runCatching {
            MediaPlayer().apply {
                setDataSource(path)
                prepare()
                playbackParams = PlaybackParams().setSpeed(state.value.playback.speed)
                setOnCompletionListener {
                    mutableState.update { currentState ->
                        currentState.copy(
                            playback = currentState.playback.copy(
                                isPlaying = false,
                                positionMillis = currentState.playback.durationMillis,
                            ),
                        )
                    }
                }
                start()
            }
        }.onSuccess { player ->
            mediaPlayer = player
            mutableState.update {
                it.copy(
                    playback = PlaybackState(
                        sessionId = turn.id,
                        isPlaying = true,
                        durationMillis = player.duration.toLong(),
                        speed = it.playback.speed,
                    ),
                )
            }
            startPlaybackProgress()
        }.onFailure { debugLog.write("ERROR", "Could not play saved session", it) }
    }

    fun seekPlayback(positionMillis: Long) {
        val duration = state.value.playback.durationMillis
        val position = positionMillis.coerceIn(0, duration)
        mediaPlayer?.seekTo(position.toInt())
        mutableState.update { it.copy(playback = it.playback.copy(positionMillis = position)) }
    }

    fun setPlaybackSpeed(speed: Float) {
        mediaPlayer?.let { player ->
            runCatching { player.playbackParams = player.playbackParams.setSpeed(speed) }
        }
        mutableState.update { it.copy(playback = it.playback.copy(speed = speed)) }
    }

    private fun startPlaybackProgress() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (mediaPlayer?.isPlaying == true) {
                val position = mediaPlayer?.currentPosition?.toLong() ?: break
                mutableState.update { it.copy(playback = it.playback.copy(positionMillis = position, isPlaying = true)) }
                delay(200)
            }
            mutableState.update { it.copy(playback = it.playback.copy(isPlaying = mediaPlayer?.isPlaying == true)) }
        }
    }

    private fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        mediaPlayer?.release()
        mediaPlayer = null
        mutableState.update { it.copy(playback = PlaybackState()) }
    }

    fun startTalking() {
        val snapshot = state.value
        if (snapshot.isActive) return
        if (!snapshot.settings.isComplete) {
            mutableState.update { it.copy(settingsOpen = true) }
            return
        }
        // ConnectivityManager can report a VPN as unvalidated even while Qwen is reachable.
        // The WebSocket connection is the source of truth, so always allow a real attempt.
        networkMonitor.refreshNow()

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
                mode = snapshot.settings.microphoneMode,
                onDiagnostic = { debugLog.write("INFO", it) },
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
            var sessions = emptyList<QwenRealtimeSession>()
            try {
                sessions = connectSessionsWithRetry(snapshot, capture)
                mutableState.update {
                    if (it.phase == SessionPhase.Connecting) it.copy(phase = SessionPhase.Listening, error = null) else it
                }

                var bytesSent = 0L
                for (chunk in channel) {
                    try {
                        sessions.forEach { it.append(chunk) }
                    } catch (error: Throwable) {
                        debugLog.write("WARN", "Audio WebSocket interrupted; reconnecting", error)
                        sessions.forEach(QwenRealtimeSession::close)
                        finishPendingSegment()
                        mutableState.update {
                            it.copy(phase = SessionPhase.Connecting, error = "Connection interrupted · reconnecting…")
                        }
                        sessions = connectSessionsWithRetry(snapshot, capture)
                        mutableState.update { it.copy(phase = SessionPhase.Listening, error = null, isOnline = true) }
                        sessions.forEach { it.append(chunk) }
                    }
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

    private suspend fun connectSessionsWithRetry(
        snapshot: TranslationUiState,
        capture: TurnCapture?,
    ): List<QwenRealtimeSession> {
        var lastError: Throwable? = null
        repeat(3) { index ->
            val attempt = index + 1
            val sessions = createSessions(snapshot, capture)
            try {
                debugLog.write(
                    "INFO",
                    "Connecting ${snapshot.settings.translationMode.name} with ${sessions.size} stream(s), attempt $attempt/3",
                )
                coroutineScope { sessions.map { async { it.connect() } }.awaitAll() }
                debugLog.write("INFO", "Qwen session(s) connected")
                return sessions
            } catch (error: Throwable) {
                lastError = error
                sessions.forEach(QwenRealtimeSession::close)
                if (attempt < 3) {
                    mutableState.update {
                        it.copy(error = "Could not connect · retrying ${attempt + 1}/3…")
                    }
                    delay(400L * attempt)
                }
            }
        }
        throw lastError ?: IOException("Could not connect")
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
            segments = segments.map {
                TranslationSegment(
                    id = it.id,
                    sourceText = it.sourceText,
                    translationText = it.translationText,
                    sourceLanguage = it.sourceLanguage,
                    targetLanguage = it.targetLanguage,
                )
            },
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
                    it.copy(phase = SessionPhase.Idle, connectionTestResult = "Connected · ${latency} ms", isOnline = true, error = null)
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

    fun forceRetry() {
        debugLog.write("INFO", "User forced connection retry")
        networkMonitor.refreshNow()
        testConnection()
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
        playbackJob?.cancel()
        networkMonitor.close()
    }
}
