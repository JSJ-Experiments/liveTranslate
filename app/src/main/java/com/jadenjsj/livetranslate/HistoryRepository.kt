package com.jadenjsj.livetranslate

import android.content.Context
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class HistoryRepository(context: Context) {
    private val root = File(context.filesDir, "translation_history")

    fun load(): List<TranslationTurn> = root.listFiles()
        .orEmpty()
        .mapNotNull { directory ->
            runCatching {
                val json = JSONObject(File(directory, "turn.json").readText())
                TranslationTurn(
                    id = json.getLong("id"),
                    sourceText = json.optString("sourceText"),
                    translationText = json.optString("translationText"),
                    sourceLanguage = json.optString("sourceLanguage").takeIf(String::isNotBlank),
                    targetLanguage = json.getString("targetLanguage"),
                    createdAtMillis = json.getLong("createdAtMillis"),
                    audioPath = File(directory, "audio.wav").takeIf(File::exists)?.absolutePath,
                ).takeIf { it.sourceText.isNotBlank() || it.translationText.isNotBlank() }
            }.getOrNull()
        }
        .sortedBy(TranslationTurn::createdAtMillis)

    fun begin(id: Long, sampleRate: Int): TurnCapture {
        val directory = File(root, id.toString()).apply { mkdirs() }
        return TurnCapture(directory, sampleRate)
    }

    fun clear() {
        root.deleteRecursively()
    }
}

internal class TurnCapture(
    private val directory: File,
    private val sampleRate: Int,
) {
    private val audioFile = File(directory, "audio.wav")
    private val audio = RandomAccessFile(audioFile, "rw").apply {
        setLength(0)
        write(ByteArray(WAV_HEADER_SIZE))
    }
    private val events = BufferedWriter(FileWriter(File(directory, "events.jsonl"), false))
    private var audioBytes = 0L
    private var closed = false

    @Synchronized
    fun appendAudio(bytes: ByteArray) {
        if (closed) return
        audio.write(bytes)
        audioBytes += bytes.size
    }

    @Synchronized
    fun appendServerEvent(raw: String) {
        if (closed) return
        events.append(raw)
        events.newLine()
        events.flush()
    }

    @Synchronized
    fun complete(turn: TranslationTurn): TranslationTurn {
        if (!closed) {
            writeWavHeader()
            audio.close()
            events.close()
            closed = true
        }
        val saved = turn.copy(audioPath = audioFile.takeIf { audioBytes > 0 }?.absolutePath)
        File(directory, "turn.json").writeText(
            JSONObject()
                .put("id", saved.id)
                .put("sourceText", saved.sourceText)
                .put("translationText", saved.translationText)
                .put("sourceLanguage", saved.sourceLanguage ?: "")
                .put("targetLanguage", saved.targetLanguage)
                .put("createdAtMillis", saved.createdAtMillis)
                .put("sampleRate", sampleRate)
                .put("audioBytes", audioBytes)
                .toString(2),
        )
        return saved
    }

    @Synchronized
    fun discard() {
        if (!closed) {
            audio.close()
            events.close()
            closed = true
        }
        directory.deleteRecursively()
    }

    private fun writeWavHeader() {
        val dataSize = audioBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val header = ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray(Charsets.US_ASCII))
            .putInt(36 + dataSize)
            .put("WAVE".toByteArray(Charsets.US_ASCII))
            .put("fmt ".toByteArray(Charsets.US_ASCII))
            .putInt(16)
            .putShort(1.toShort())
            .putShort(1.toShort())
            .putInt(sampleRate)
            .putInt(sampleRate * 2)
            .putShort(2.toShort())
            .putShort(16.toShort())
            .put("data".toByteArray(Charsets.US_ASCII))
            .putInt(dataSize)
            .array()
        audio.seek(0)
        audio.write(header)
    }

    private companion object {
        const val WAV_HEADER_SIZE = 44
    }
}
