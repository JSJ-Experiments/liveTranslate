package com.jadenjsj.livetranslate

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Non-blocking PCM16 player for translated audio arriving on an OkHttp callback. */
internal class StreamingAudioPlayer(sampleRate: Int) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val chunks = Channel<ByteArray>(Channel.UNLIMITED)
    private val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(
            maxOf(
                AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
                sampleRate * 2,
            ),
        )
        .build()

    init {
        check(track.state == AudioTrack.STATE_INITIALIZED) { "Could not initialize translated audio output" }
        track.play()
        scope.launch {
            for (chunk in chunks) {
                var offset = 0
                while (offset < chunk.size) {
                    val written = track.write(chunk, offset, chunk.size - offset, AudioTrack.WRITE_BLOCKING)
                    if (written <= 0) break
                    offset += written
                }
            }
        }
    }

    fun append(pcm: ByteArray) {
        chunks.trySend(pcm)
    }

    override fun close() {
        chunks.close()
        scope.cancel()
        runCatching { track.pause() }
        runCatching { track.flush() }
        track.release()
    }
}

