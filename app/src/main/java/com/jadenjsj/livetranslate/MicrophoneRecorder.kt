package com.jadenjsj.livetranslate

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioDeviceInfo
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
        mode: MicrophoneMode,
        onDiagnostic: (String) -> Unit = {},
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
        val audioRecord = createAndStartRecorder(sampleRate, bufferSize, mode)
        val inputs = context.getSystemService(android.media.AudioManager::class.java)
            .getDevices(android.media.AudioManager.GET_DEVICES_INPUTS)
            .joinToString { "${it.productName}(${deviceType(it.type)})" }
        onDiagnostic(
            "Microphone started preset=${mode.name}, source=${sourceName(audioRecord.audioSource)}, " +
                "route=${audioRecord.routedDevice?.let { "${it.productName}(${deviceType(it.type)})" } ?: "pending"}, " +
                "available=[$inputs]",
        )
        recorder = audioRecord
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

    @SuppressLint("MissingPermission")
    private fun createAndStartRecorder(sampleRate: Int, bufferSize: Int, mode: MicrophoneMode): AudioRecord {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val sources = when (mode) {
            MicrophoneMode.Speech -> listOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.MIC,
            )
            MicrophoneMode.Communication -> listOf(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC,
            )
            MicrophoneMode.Unprocessed -> listOf(
                MediaRecorder.AudioSource.UNPROCESSED,
                MediaRecorder.AudioSource.MIC,
            )
        }
        val failures = mutableListOf<Throwable>()

        for (source in sources) {
            val candidate = try {
                AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSize)
                    .build()
            } catch (error: Throwable) {
                failures += error
                continue
            }
            try {
                check(candidate.state == AudioRecord.STATE_INITIALIZED)
                candidate.startRecording()
                check(candidate.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                return candidate
            } catch (error: Throwable) {
                failures += error
                runCatching { candidate.release() }
            }
        }

        val cause = failures.lastOrNull()
        throw IllegalStateException(
            "Microphone unavailable at ${sampleRate / 1_000} kHz. Close other apps using the microphone and try again.",
            cause,
        )
    }

}

private fun sourceName(source: Int): String = when (source) {
    MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
    MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
    MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED"
    MediaRecorder.AudioSource.MIC -> "MIC"
    else -> source.toString()
}

private fun deviceType(type: Int): String = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_MIC -> "built-in"
    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired"
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
    AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE"
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
    else -> type.toString()
}
