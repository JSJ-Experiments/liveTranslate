package com.jadenjsj.livetranslate

import android.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private var webSocket: WebSocket? = null
    @Volatile private var inputCommitted = false
    @Volatile private var finishSent = false

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

    fun commit() {
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
                QwenServerEvent.SessionUpdated -> connected.complete(Unit)
                QwenServerEvent.ResponseDone -> if (inputCommitted && !finishSent) {
                    finishSent = true
                    webSocket.send(simpleEvent("session.finish"))
                }
                QwenServerEvent.SessionFinished -> finished.complete(Unit)
                is QwenServerEvent.Error -> fail(IllegalStateException(event.message))
                else -> onEvent(event)
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
