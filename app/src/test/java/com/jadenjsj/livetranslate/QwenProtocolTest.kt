package com.jadenjsj.livetranslate

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QwenProtocolTest {
    @Test
    fun `builds region-specific endpoint`() {
        assertEquals(
            "wss://workspace.cn-beijing.maas.aliyuncs.com/api-ws/v1/realtime?model=qwen3.5-livetranslate-flash-realtime",
            buildQwenUrl("workspace", Region.Beijing),
        )
        assertTrue(buildQwenUrl("workspace", Region.Singapore).contains("ap-southeast-1"))
    }

    @Test
    fun `session requests transcript and Chinese text`() {
        val event = JSONObject(sessionUpdate(TranslationDirection.EnglishToChinese))
        val session = event.getJSONObject("session")
        assertEquals("session.update", event.getString("type"))
        assertEquals(16_000, session.getInt("sample_rate"))
        assertEquals("pcm", session.getString("input_audio_format"))
        assertEquals("en", session.getJSONObject("input_audio_transcription").getString("language"))
        assertEquals("zh", session.getJSONObject("translation").getString("language"))
        assertTrue(session.isNull("turn_detection"))
    }

    @Test
    fun `session includes valid hotword pairs and selected quality`() {
        val settings = AppSettings(
            sampleRate = 8_000,
            hotwords = "Qwen=千问\ninvalid\nVPN = 虚拟专用网络",
        )
        val session = JSONObject(sessionUpdate(TranslationDirection.EnglishToChinese, settings))
            .getJSONObject("session")
        val phrases = session.getJSONObject("translation").getJSONObject("corpus").getJSONObject("phrases")
        assertEquals(8_000, session.getInt("sample_rate"))
        assertEquals("千问", phrases.getString("Qwen"))
        assertEquals("虚拟专用网络", phrases.getString("VPN"))
        assertFalse(phrases.has("invalid"))
    }

    @Test
    fun `automatic direction lets Qwen detect source and can update target`() {
        val session = JSONObject(sessionUpdate(TranslationDirection.Auto, targetLanguage = "en"))
            .getJSONObject("session")
        assertFalse(session.getJSONObject("input_audio_transcription").has("language"))
        assertEquals("en", session.getJSONObject("translation").getString("language"))
    }

    @Test
    fun `parses source and translation previews using confirmed plus tentative text`() {
        assertEquals(
            QwenServerEvent.SourcePreview("How are you?", "en"),
            parseServerEvent(
                """{"type":"conversation.item.input_audio_transcription.text","text":"How ","stash":"are you?","language":"en"}""",
            ),
        )
        assertEquals(
            QwenServerEvent.TranslationPreview("你好吗？"),
            parseServerEvent("""{"type":"response.text.text","text":"你好","stash":"吗？"}"""),
        )
    }

    @Test
    fun `parses final and API error events`() {
        assertEquals(
            QwenServerEvent.SourceDone("Hello", "en"),
            parseServerEvent("""{"type":"conversation.item.input_audio_transcription.completed","transcript":"Hello","language":"en"}"""),
        )
        val error = parseServerEvent(
            """{"type":"error","error":{"code":"invalid_api_key","message":"Unauthorized"}}""",
        ) as QwenServerEvent.Error
        assertTrue(error.message.contains("Unauthorized"))
    }
}
