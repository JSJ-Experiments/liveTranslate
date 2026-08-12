package dev.jsj.livetranslate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.time.TimeSource

class TranslationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val microphone = MicrophoneRecorder(application)
    private val mutableState = MutableStateFlow(TranslationUiState())
    val state: StateFlow<TranslationUiState> = mutableState.asStateFlow()

    private var audioChannel: Channel<ByteArray>? = null
    private var sessionJob: kotlinx.coroutines.Job? = null
    private var testJob: kotlinx.coroutines.Job? = null

    init {
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
    }

    fun setDirection(direction: TranslationDirection) {
        if (!state.value.isActive) mutableState.update { it.copy(direction = direction) }
    }

    fun openSettings() = mutableState.update { it.copy(settingsOpen = true) }
    fun closeSettings() = mutableState.update { it.copy(settingsOpen = false) }

    fun saveSettings(settings: AppSettings) {
        viewModelScope.launch {
            repository.save(settings)
            mutableState.update { it.copy(settingsOpen = false, connectionTestResult = null) }
        }
    }

    fun clearTranscript() {
        if (!state.value.isActive) {
            mutableState.update { it.copy(sourceText = "", translationText = "", error = null) }
        }
    }

    fun microphonePermissionDenied() {
        mutableState.update {
            it.copy(phase = SessionPhase.Error, error = "Microphone permission is required for push-to-talk")
        }
    }

    fun startTalking() {
        val snapshot = state.value
        if (snapshot.isActive) return
        if (!snapshot.settings.isComplete) {
            mutableState.update { it.copy(settingsOpen = true) }
            return
        }

        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        audioChannel = channel
        mutableState.update {
            it.copy(
                phase = SessionPhase.Connecting,
                sourceText = "",
                translationText = "",
                error = null,
            )
        }

        try {
            microphone.start(
                scope = viewModelScope,
                sampleRate = snapshot.settings.sampleRate,
                chunkMilliseconds = snapshot.settings.chunkMilliseconds,
                onAudio = { channel.trySend(it) },
            )
        } catch (error: Throwable) {
            channel.close()
            showError(error)
            return
        }

        sessionJob = viewModelScope.launch {
            var session: QwenRealtimeSession? = null
            try {
                session = QwenRealtimeSession(snapshot.settings, snapshot.direction, ::handleEvent)
                session.connect()
                mutableState.update {
                    if (it.phase == SessionPhase.Connecting) it.copy(phase = SessionPhase.Listening) else it
                }

                var bytesSent = 0L
                for (chunk in channel) {
                    session.append(chunk)
                    bytesSent += chunk.size
                }
                if (bytesSent == 0L) {
                    session.finish()
                } else {
                    mutableState.update { it.copy(phase = SessionPhase.Translating) }
                    session.commit()
                }
                session.awaitFinished()
                mutableState.update { it.copy(phase = SessionPhase.Idle, error = null) }
            } catch (error: Throwable) {
                showError(error)
            } finally {
                microphone.stop()
                audioChannel = null
                session?.close()
            }
        }
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
                session = QwenRealtimeSession(settings, state.value.direction) {}
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

    private fun handleEvent(event: QwenServerEvent) {
        mutableState.update { current ->
            when (event) {
                is QwenServerEvent.SourcePreview -> current.copy(sourceText = event.text)
                is QwenServerEvent.SourceDone -> current.copy(sourceText = event.text)
                is QwenServerEvent.TranslationPreview -> current.copy(translationText = event.text)
                is QwenServerEvent.TranslationDone -> current.copy(translationText = event.text)
                else -> current
            }
        }
    }

    private fun showError(error: Throwable) {
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
    }
}
