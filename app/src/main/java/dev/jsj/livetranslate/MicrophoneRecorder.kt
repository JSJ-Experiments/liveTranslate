package dev.jsj.livetranslate

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MicrophoneRecorder(private val context: Context) {
    private var recorder: AudioRecord? = null
    private var readJob: Job? = null

    @SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        sampleRate: Int,
        chunkMilliseconds: Int,
        onAudio: (ByteArray) -> Unit,
    ) {
        check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "Microphone permission is required"
        }
        check(recorder == null) { "Microphone is already active" }

        val minimum = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimum > 0) { "This device cannot record 16 kHz audio" }
        val bytesPerChunk = sampleRate * 2 * chunkMilliseconds / 1_000
        val bufferSize = maxOf(minimum * 2, bytesPerChunk * 4)
        val audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize)
            .build()
        check(audioRecord.state == AudioRecord.STATE_INITIALIZED) { "Microphone failed to initialize" }

        recorder = audioRecord
        audioRecord.startRecording()
        readJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(bytesPerChunk)
            while (isActive) {
                val count = audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (count > 0) onAudio(buffer.copyOf(count))
                if (count == AudioRecord.ERROR_DEAD_OBJECT || count == AudioRecord.ERROR_INVALID_OPERATION) break
            }
        }
    }

    fun stop() {
        val audioRecord = recorder ?: return
        recorder = null
        runCatching { audioRecord.stop() }
        readJob?.cancel()
        readJob = null
        audioRecord.release()
    }

}
