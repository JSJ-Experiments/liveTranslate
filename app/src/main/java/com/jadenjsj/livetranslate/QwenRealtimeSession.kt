package com.jadenjsj.livetranslate

import android.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.Closeable
import java.util.concurrent.TimeUnit

internal class QwenRealtimeSession(
    private val settings: AppSettings,
    private val direction: TranslationDirection,
    private val onEvent: (QwenServerEvent) -> Unit,
) : Closeable {
    private val connected = CompletableDeferred<Unit>()
    private val finished = CompletableDeferred<Unit>()
    private val detectedLanguage = CompletableDeferred<String>()
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private var webSocket: WebSocket? = null
    @Volatile private var inputCommitted = false
    @Volatile private var finishSent = false
    @Volatile private var currentTargetLanguage = direction.targetLanguage
    @Volatile private var pendingTranslationUpdate: CompletableDeferred<Unit>? = null

    suspend fun connect() = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(buildQwenUrl(settings.workspaceId, settings.region))
            .header("Authorization", "Bearer ${settings.apiKey}")
            .build()
        webSocket = client.newWebSocket(request, Listener())
        try {
            withTimeout(15_000) { connected.await() }
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    fun append(pcm: ByteArray) {
        val audio = Base64.encodeToString(pcm, Base64.NO_WRAP)
        check(webSocket?.send(appendAudio(audio)) == true) { "Audio connection was lost" }
    }

    suspend fun commit() {
        if (direction == TranslationDirection.Auto) {
            withTimeoutOrNull(700) { detectedLanguage.await() }
            pendingTranslationUpdate?.let { update ->
                withTimeoutOrNull(1_000) { update.await() }
            }
        }
        inputCommitted = true
        check(webSocket?.send(simpleEvent("input_audio_buffer.commit")) == true) {
            "Could not submit audio"
        }
    }

    fun finish() {
        finishSent = true
        check(webSocket?.send(simpleEvent("session.finish")) == true) { "Could not finish session" }
    }

    suspend fun awaitFinished() {
        withTimeout(45_000) { finished.await() }
    }

    override fun close() {
        webSocket?.close(1000, null)
        webSocket = null
        client.dispatcher.executorService.shutdown()
        if (!connected.isCompleted) connected.cancel()
        if (!finished.isCompleted) finished.cancel()
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!webSocket.send(sessionUpdate(direction, settings))) {
                fail(IllegalStateException("Could not configure the Qwen session"))
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            when (val event = parseServerEvent(text)) {
                QwenServerEvent.SessionUpdated -> {
                    if (!connected.isCompleted) connected.complete(Unit)
                    else pendingTranslationUpdate?.complete(Unit)
                }
                QwenServerEvent.ResponseDone -> if (inputCommitted && !finishSent) {
                    finishSent = true
                    webSocket.send(simpleEvent("session.finish"))
                }
                QwenServerEvent.SessionFinished -> finished.complete(Unit)
                is QwenServerEvent.Error -> fail(IllegalStateException(event.message))
                else -> {
                    adaptAutoDirection(event, webSocket)
                    onEvent(event)
                }
            }
        }

        private fun adaptAutoDirection(event: QwenServerEvent, webSocket: WebSocket) {
            if (direction != TranslationDirection.Auto || inputCommitted) return
            val language = when (event) {
                is QwenServerEvent.SourcePreview -> event.language
                is QwenServerEvent.SourceDone -> event.language
                else -> null
            }?.lowercase()?.takeIf(String::isNotBlank) ?: return

            detectedLanguage.complete(language)
            val target = if (language == "zh" || language.startsWith("zh-")) "en" else "zh"
            if (target == currentTargetLanguage) return

            currentTargetLanguage = target
            val update = CompletableDeferred<Unit>()
            pendingTranslationUpdate = update
            if (!webSocket.send(sessionUpdate(direction, settings, target))) {
                update.completeExceptionally(IllegalStateException("Could not update automatic translation direction"))
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            fail(t)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!finished.isCompleted) {
                fail(IllegalStateException("Connection closed before translation finished ($code)"))
            }
        }

        private fun fail(error: Throwable) {
            if (!connected.isCompleted) connected.completeExceptionally(error)
            if (!finished.isCompleted) finished.completeExceptionally(error)
        }
    }
}
