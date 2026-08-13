package com.jadenjsj.livetranslate

import java.io.Closeable

internal interface RealtimeTranslationSession : Closeable {
    suspend fun connect()
    fun append(pcm: ByteArray)
    fun finish()
    suspend fun awaitFinished()
}

