package com.jadenjsj.livetranslate

import java.util.UUID
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
import okio.ByteString

internal class VolcRealtimeSession(
    private val settings: AppSettings,
    private val mode: String,
    private val sourceLanguage: String,
    private val targetLanguage: String,
    private val onRawEvent: (String) -> Unit,
    private val onSource: (text: String, done: Boolean) -> Unit,
    private val onTranslation: (text: String, done: Boolean) -> Unit,
    private val onSegment: (source: String, translation: String) -> Unit,
) : RealtimeTranslationSession {
    private val sessionId = UUID.randomUUID().toString()
    private val connected = CompletableDeferred<Unit>()
    private val finished = CompletableDeferred<Unit>()
    private val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    private val player = if (mode == "s2s" && settings.playTranslatedAudio) StreamingAudioPlayer(16_000) else null
    private var webSocket: WebSocket? = null
    private var source = ""
    private var translation = ""

    override suspend fun connect() = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("wss://openspeech.bytedance.com/api/v4/ast/v2/translate")
            .header("X-Api-Key", settings.volcApiKey)
            .header("X-Api-Resource-Id", settings.volcResourceId)
            .header("X-Api-Connect-Id", UUID.randomUUID().toString())
            .build()
        webSocket = client.newWebSocket(request, Listener())
        try {
            withTimeout(10_000) { connected.await() }
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    override fun append(pcm: ByteArray) {
        check(!finished.isCompleted) { "Volcengine audio connection was lost" }
        check(webSocket?.send(ByteString.of(*VolcProtobuf.audio(sessionId, pcm))) == true) {
            "Volcengine audio connection was lost"
        }
    }

    override fun finish() {
        check(webSocket?.send(ByteString.of(*VolcProtobuf.finishSession(sessionId))) == true) {
            "Could not finish Volcengine translation session"
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
            val start = VolcProtobuf.startSession(sessionId, mode, sourceLanguage, targetLanguage)
            if (!webSocket.send(ByteString.of(*start))) fail(IllegalStateException("Could not configure Volcengine AST"))
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            val event = runCatching { VolcProtobuf.parseResponse(bytes.toByteArray()) }.getOrElse {
                fail(IllegalStateException("Volcengine returned invalid protobuf data", it))
                return
            }
            onRawEvent(
                "event=${event.event} status=${event.statusCode} start=${event.startTime} end=${event.endTime} " +
                    "speakerChanged=${event.speakerChanged} text=${event.text}",
            )
            when (event.event) {
                150 -> if (!connected.isCompleted) connected.complete(Unit)
                651 -> {
                    source = event.text
                    onSource(source, false)
                }
                652 -> {
                    source = event.text.ifBlank { source }
                    onSource(source, true)
                }
                654 -> {
                    translation = event.text
                    onTranslation(translation, false)
                }
                655 -> {
                    translation = event.text.ifBlank { translation }
                    onTranslation(translation, true)
                    if (translation.isNotBlank()) onSegment(source, translation)
                    source = ""
                    translation = ""
                }
                352 -> if (event.data.isNotEmpty()) player?.append(event.data)
                152 -> if (!finished.isCompleted) finished.complete(Unit)
                153 -> fail(IllegalStateException("Volcengine ${event.statusCode}: ${event.message.ifBlank { "session failed" }}"))
            }
            if (event.statusCode != 0 && event.statusCode != 20_000_000 && event.event != 154) {
                fail(IllegalStateException("Volcengine ${event.statusCode}: ${event.message.ifBlank { "request failed" }}"))
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            onRawEvent(text)
            fail(IllegalStateException("Volcengine returned an unexpected text frame: $text"))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = fail(t)

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!finished.isCompleted) fail(IllegalStateException("Volcengine connection closed before translation finished ($code)"))
        }

        private fun fail(error: Throwable) {
            if (!connected.isCompleted) connected.completeExceptionally(error)
            if (!finished.isCompleted) finished.completeExceptionally(error)
        }
    }
}

