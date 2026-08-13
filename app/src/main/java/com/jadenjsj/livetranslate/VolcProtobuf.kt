package com.jadenjsj.livetranslate

import java.io.ByteArrayOutputStream

/** Minimal protobuf wire codec for Volcengine AST's small request/response surface. */
internal object VolcProtobuf {
    fun startSession(
        sessionId: String,
        mode: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): ByteArray = Writer().apply {
        message(1) { string(6, sessionId) }
        int(2, 100)
        message(3) {
            string(1, "livetranslate")
            string(2, "android")
            string(3, "Android 16")
            string(4, "0.7.0")
        }
        message(4) {
            string(4, "wav")
            string(5, "raw")
            int(7, 16_000)
            int(8, 16)
            int(9, 1)
        }
        if (mode == "s2s") {
            message(5) {
                string(4, "pcm")
                int(7, 16_000)
                int(8, 16)
                int(9, 1)
            }
        }
        message(6) {
            string(1, mode)
            string(2, sourceLanguage)
            string(3, targetLanguage)
            bool(6, true)
        }
        bool(7, true)
    }.bytes()

    fun audio(sessionId: String, pcm: ByteArray): ByteArray = Writer().apply {
        message(1) { string(6, sessionId) }
        int(2, 200)
        message(4) { bytes(14, pcm) }
    }.bytes()

    fun finishSession(sessionId: String): ByteArray = Writer().apply {
        message(1) { string(6, sessionId) }
        int(2, 102)
    }.bytes()

    fun parseResponse(bytes: ByteArray): Response {
        val reader = Reader(bytes)
        var event = 0
        var data = ByteArray(0)
        var text = ""
        var statusCode = 0
        var message = ""
        var startTime = 0
        var endTime = 0
        var speakerChanged = false
        while (reader.hasNext()) {
            val (field, wire) = reader.tag()
            when (field) {
                1 -> {
                    val meta = Reader(reader.lengthDelimited(wire))
                    while (meta.hasNext()) {
                        val (metaField, metaWire) = meta.tag()
                        when (metaField) {
                            3 -> statusCode = meta.varint(metaWire).toInt()
                            4 -> message = meta.string(metaWire)
                            else -> meta.skip(metaWire)
                        }
                    }
                }
                2 -> event = reader.varint(wire).toInt()
                3 -> data = reader.lengthDelimited(wire)
                4 -> text = reader.string(wire)
                5 -> startTime = reader.varint(wire).toInt()
                6 -> endTime = reader.varint(wire).toInt()
                7 -> speakerChanged = reader.varint(wire) != 0L
                else -> reader.skip(wire)
            }
        }
        return Response(event, data, text, statusCode, message, startTime, endTime, speakerChanged)
    }

    data class Response(
        val event: Int,
        val data: ByteArray,
        val text: String,
        val statusCode: Int,
        val message: String,
        val startTime: Int,
        val endTime: Int,
        val speakerChanged: Boolean,
    )

    private class Writer {
        private val out = ByteArrayOutputStream()

        fun int(field: Int, value: Int) {
            tag(field, 0)
            varint(value.toLong())
        }

        fun bool(field: Int, value: Boolean) = int(field, if (value) 1 else 0)

        fun string(field: Int, value: String) {
            if (value.isNotEmpty()) bytes(field, value.toByteArray(Charsets.UTF_8))
        }

        fun bytes(field: Int, value: ByteArray) {
            tag(field, 2)
            varint(value.size.toLong())
            out.write(value)
        }

        fun message(field: Int, block: Writer.() -> Unit) {
            val value = Writer().apply(block).bytes()
            bytes(field, value)
        }

        fun bytes() = out.toByteArray()

        private fun tag(field: Int, wire: Int) = varint(((field shl 3) or wire).toLong())

        private fun varint(input: Long) {
            var value = input
            while (value and -128L != 0L) {
                out.write(((value and 127L) or 128L).toInt())
                value = value ushr 7
            }
            out.write(value.toInt())
        }
    }

    private class Reader(private val data: ByteArray) {
        private var position = 0
        fun hasNext() = position < data.size

        fun tag(): Pair<Int, Int> {
            val value = rawVarint().toInt()
            require(value != 0) { "Invalid protobuf tag" }
            return (value ushr 3) to (value and 7)
        }

        fun varint(wire: Int): Long {
            require(wire == 0) { "Expected protobuf varint" }
            return rawVarint()
        }

        fun string(wire: Int) = lengthDelimited(wire).toString(Charsets.UTF_8)

        fun lengthDelimited(wire: Int): ByteArray {
            require(wire == 2) { "Expected length-delimited protobuf field" }
            val size = rawVarint().toInt()
            require(size >= 0 && position + size <= data.size) { "Truncated protobuf field" }
            return data.copyOfRange(position, position + size).also { position += size }
        }

        fun skip(wire: Int) {
            when (wire) {
                0 -> rawVarint()
                1 -> position += 8
                2 -> position += rawVarint().toInt()
                5 -> position += 4
                else -> error("Unsupported protobuf wire type $wire")
            }
            require(position <= data.size) { "Truncated protobuf message" }
        }

        private fun rawVarint(): Long {
            var result = 0L
            var shift = 0
            while (shift < 64) {
                require(position < data.size) { "Truncated protobuf varint" }
                val byte = data[position++].toInt() and 0xff
                result = result or ((byte and 0x7f).toLong() shl shift)
                if (byte and 0x80 == 0) return result
                shift += 7
            }
            error("Invalid protobuf varint")
        }
    }
}

