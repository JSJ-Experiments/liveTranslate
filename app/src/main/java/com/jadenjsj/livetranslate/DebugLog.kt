package com.jadenjsj.livetranslate

import android.content.Context
import android.util.Log
import java.io.File
import java.time.Instant

internal class DebugLog(context: Context) {
    private val directory = File(context.filesDir, "debug_logs").apply { mkdirs() }
    private val file = File(directory, "livetranslate.log")

    @Synchronized
    fun write(level: String, message: String, error: Throwable? = null) {
        rotateIfNeeded()
        val safeMessage = message
            .replace(Regex("sk-[A-Za-z0-9_-]+"), "sk-REDACTED")
            .replace(Regex("Bearer\\s+[^\\s\"]+", RegexOption.IGNORE_CASE), "Bearer REDACTED")
        val line = buildString {
            append(Instant.now()).append(' ').append(level).append(' ').append(safeMessage)
            error?.let { append(" | ").append(it::class.java.simpleName).append(": ").append(it.message) }
            append('\n')
        }
        file.appendText(line)
        when (level) {
            "ERROR" -> Log.e(TAG, safeMessage, error)
            "WARN" -> Log.w(TAG, safeMessage, error)
            else -> Log.i(TAG, safeMessage)
        }
    }

    fun location(): String = file.absolutePath

    private fun rotateIfNeeded() {
        if (file.length() < MAX_BYTES) return
        val previous = File(directory, "livetranslate.previous.log")
        previous.delete()
        file.renameTo(previous)
    }

    private companion object {
        const val TAG = "LiveTranslate"
        const val MAX_BYTES = 2L * 1024 * 1024
    }
}
