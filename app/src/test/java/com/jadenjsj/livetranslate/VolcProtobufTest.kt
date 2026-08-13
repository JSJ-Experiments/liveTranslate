package com.jadenjsj.livetranslate

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolcProtobufTest {
    @Test
    fun `start session includes AST event and selected languages`() {
        val request = VolcProtobuf.startSession("session-1", "s2t", "zhen", "zhen")
        assertTrue(request.isNotEmpty())
        assertTrue(request.toString(Charsets.ISO_8859_1).contains("session-1"))
        assertTrue(request.toString(Charsets.ISO_8859_1).contains("zhen"))
        // field 2, varint event 100
        assertTrue(request.indices.any { index ->
            index + 1 < request.size && request[index] == 0x10.toByte() && request[index + 1] == 100.toByte()
        })
    }

    @Test
    fun `audio request carries PCM bytes`() {
        val pcm = byteArrayOf(1, 2, 3, 4, 5)
        val request = VolcProtobuf.audio("session-2", pcm)
        assertTrue(request.toList().windowed(pcm.size).any { it == pcm.toList() })
    }

    @Test
    fun `parses subtitle and audio response fields`() {
        val audio = byteArrayOf(9, 8, 7)
        val response = proto {
            int(2, 655)
            bytes(3, audio)
            string(4, "你好")
            int(5, 120)
            int(6, 940)
            int(7, 1)
            message(1) {
                int(3, 20_000_000)
                string(4, "OK")
            }
        }
        val parsed = VolcProtobuf.parseResponse(response)
        assertEquals(655, parsed.event)
        assertEquals("你好", parsed.text)
        assertArrayEquals(audio, parsed.data)
        assertEquals(20_000_000, parsed.statusCode)
        assertEquals(120, parsed.startTime)
        assertEquals(940, parsed.endTime)
        assertTrue(parsed.speakerChanged)
    }

    private fun proto(block: TestWriter.() -> Unit) = TestWriter().apply(block).output()

    private class TestWriter {
        private val out = ByteArrayOutputStream()
        fun int(field: Int, value: Int) { varint((field shl 3).toLong()); varint(value.toLong()) }
        fun string(field: Int, value: String) = bytes(field, value.toByteArray())
        fun bytes(field: Int, value: ByteArray) {
            varint(((field shl 3) or 2).toLong()); varint(value.size.toLong()); out.write(value)
        }
        fun message(field: Int, block: TestWriter.() -> Unit) = bytes(field, TestWriter().apply(block).output())
        fun output() = out.toByteArray()
        private fun varint(input: Long) {
            var value = input
            while (value and -128L != 0L) {
                out.write(((value and 127L) or 128L).toInt()); value = value ushr 7
            }
            out.write(value.toInt())
        }
    }
}
