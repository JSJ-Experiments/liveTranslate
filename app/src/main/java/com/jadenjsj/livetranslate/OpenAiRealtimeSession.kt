package com.jadenjsj.livetranslate

import android.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

internal class OpenAiRealtimeSession(
    private val settings: AppSettings,
    private val targetLanguage: String,
    private val onRawEvent: (String) -> Unit,
    private val onSource: (String) -> Unit,
    private val onTranslation: (String) -> Unit,
    private val onSegment: (source: String, translation: String) -> Unit,
) : RealtimeTranslationSession {
    private val connected = CompletableDeferred<Unit>()
    private val finished = CompletableDeferred<Unit>()
    private val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    private val player = if (settings.playTranslatedAudio) StreamingAudioPlayer(24_000) else null
    private var webSocket: WebSocket? = null
    private var source = ""
    private var translation = ""
    private var emittedSource = ""
    private var emittedTranslation = ""

    suspend fun connectInternal() = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("wss://api.openai.com/v1/realtime/translations?model=gpt-realtime-translate")
            .header("Authorization", "Bearer ${settings.openAiApiKey}")
            .header("OpenAI-Safety-Identifier", settings.openAiSafetyIdentifier)
            .build()
        webSocket = client.newWebSocket(request, Listener())
        try {
            withTimeout(10_000) { connected.await() }
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    override suspend fun connect() = connectInternal()

    override fun append(pcm: ByteArray) {
        check(!finished.isCompleted) { "OpenAI audio connection was lost" }
        val message = JSONObject()
            .put("type", "session.input_audio_buffer.append")
            .put("audio", Base64.encodeToString(pcm, Base64.NO_WRAP))
            .toString()
        check(webSocket?.send(message) == true) { "OpenAI audio connection was lost" }
    }

    override fun finish() {
        check(webSocket?.send(JSONObject().put("type", "session.close").toString()) == true) {
            "Could not close OpenAI translation session"
        }
    }

    override suspend fun awaitFinished() {
        withTimeout(45_000) { finished.await() }
    }

    override fun close() {
        webSocket?.close(1000, null)
        webSocket = null
        player?.close()
        client.dispatcher.executorService.shutdown()
        if (!connected.isCompleted) connected.cancel()
        if (!finished.isCompleted) finished.cancel()
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val update = JSONObject()
                .put("type", "session.update")
                .put(
                    "session",
                    JSONObject().put(
                        "audio",
                        JSONObject()
                            .put(
                                "input",
                                JSONObject()
                                    .put("transcription", JSONObject().put("model", "gpt-realtime-whisper"))
                                    .put("noise_reduction", JSONObject.NULL),
                            )
                            .put("output", JSONObject().put("language", targetLanguage)),
                    ),
                )
            if (!webSocket.send(update.toString())) fail(IllegalStateException("Could not configure OpenAI translation"))
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            onRawEvent(text)
            val json = runCatching { JSONObject(text) }.getOrElse {
                fail(IllegalStateException("OpenAI returned invalid realtime data"))
                return
            }
            when (json.optString("type")) {
                "session.updated" -> if (!connected.isCompleted) connected.complete(Unit)
                "session.input_transcript.delta" -> {
                    source += json.optString("delta")
                    onSource(source.removePrefix(emittedSource))
                }
                "session.output_transcript.delta" -> {
                    translation += json.optString("delta")
                    onTranslation(translation.removePrefix(emittedTranslation))
                    maybeCompleteSentence()
                }
                "session.output_audio.delta" -> {
                    val bytes = runCatching { Base64.decode(json.optString("delta"), Base64.DEFAULT) }.getOrNull()
                    if (bytes != null && bytes.isNotEmpty()) player?.append(bytes)
                }
                "session.closed" -> {
                    completeRemaining()
                    if (!finished.isCompleted) finished.complete(Unit)
                }
                "error" -> {
                    val error = json.optJSONObject("error") ?: json
                    fail(IllegalStateException(listOf(error.optString("code"), error.optString("message")).filter(String::isNotBlank).joinToString(": ")))
                }
            }
        }

        private fun maybeCompleteSentence() {
            val pending = translation.removePrefix(emittedTranslation)
            if (pending.length < 8 || pending.lastOrNull() !in ".!?。！？；;\n") return
            val sourcePending = source.removePrefix(emittedSource)
            onSegment(sourcePending.trim(), pending.trim())
            emittedSource = source
            emittedTranslation = translation
        }

        private fun completeRemaining() {
            val pending = translation.removePrefix(emittedTranslation).trim()
            if (pending.isNotBlank()) onSegment(source.removePrefix(emittedSource).trim(), pending)
            emittedSource = source
            emittedTranslation = translation
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = fail(t)

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!finished.isCompleted) fail(IllegalStateException("OpenAI connection closed before translation finished ($code)"))
        }

        private fun fail(error: Throwable) {
            if (!connected.isCompleted) connected.completeExceptionally(error)
            if (!finished.isCompleted) finished.completeExceptionally(error)
        }
    }
}

