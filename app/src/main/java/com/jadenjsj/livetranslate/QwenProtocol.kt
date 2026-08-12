package com.jadenjsj.livetranslate

import org.json.JSONObject
import java.util.UUID

private const val MODEL = "qwen3.5-livetranslate-flash-realtime"

internal fun buildQwenUrl(workspaceId: String, region: Region): String {
    require(workspaceId.isNotBlank()) { "Workspace ID is required" }
    return "wss://${workspaceId.trim()}.${region.hostPart}.maas.aliyuncs.com" +
        "/api-ws/v1/realtime?model=$MODEL"
}

internal fun sessionUpdate(direction: TranslationDirection, settings: AppSettings = AppSettings()): String = event("session.update") {
    val translation = JSONObject().put("language", direction.targetLanguage)
    parseHotwords(settings.hotwords).takeIf { it.length() > 0 }?.let { phrases ->
        translation.put("corpus", JSONObject().put("phrases", phrases))
    }
    put(
        "session",
        JSONObject()
            .put("modalities", jsonArrayOf("text"))
            .put("sample_rate", settings.sampleRate)
            .put("input_audio_format", "pcm")
            .put(
                "input_audio_transcription",
                JSONObject()
                    .put("model", "qwen3-asr-flash-realtime")
                    .put("language", direction.sourceLanguage),
            )
            .put("turn_detection", JSONObject.NULL)
            .put("translation", translation),
    )
}.toString()

internal fun appendAudio(base64Pcm: String): String = event("input_audio_buffer.append") {
    put("audio", base64Pcm)
}.toString()

internal fun simpleEvent(type: String): String = event(type).toString()

internal sealed interface QwenServerEvent {
    data object SessionUpdated : QwenServerEvent
    data object ResponseDone : QwenServerEvent
    data object SessionFinished : QwenServerEvent
    data class SourcePreview(val text: String) : QwenServerEvent
    data class SourceDone(val text: String) : QwenServerEvent
    data class TranslationPreview(val text: String) : QwenServerEvent
    data class TranslationDone(val text: String) : QwenServerEvent
    data class Error(val message: String) : QwenServerEvent
    data object Ignored : QwenServerEvent
}

internal fun parseServerEvent(raw: String): QwenServerEvent {
    val json = runCatching { JSONObject(raw) }.getOrElse {
        return QwenServerEvent.Error("The server returned invalid data")
    }
    return when (json.optString("type")) {
        "session.updated" -> QwenServerEvent.SessionUpdated
        "session.finished" -> QwenServerEvent.SessionFinished
        "response.done" -> QwenServerEvent.ResponseDone
        "conversation.item.input_audio_transcription.text" ->
            QwenServerEvent.SourcePreview(json.optString("text") + json.optString("stash"))
        "conversation.item.input_audio_transcription.completed" ->
            QwenServerEvent.SourceDone(json.optString("transcript"))
        "response.text.text" ->
            QwenServerEvent.TranslationPreview(json.optString("text") + json.optString("stash"))
        "response.text.done" -> QwenServerEvent.TranslationDone(json.optString("text"))
        "error" -> {
            val error = json.optJSONObject("error") ?: json
            val parts = listOf(
                error.optString("code"),
                error.optString("type"),
                error.optString("message"),
            ).filter { it.isNotBlank() }
            QwenServerEvent.Error(parts.joinToString(": ").ifBlank { "Unknown Qwen API error" })
        }
        else -> QwenServerEvent.Ignored
    }
}

private fun event(type: String, block: JSONObject.() -> Unit = {}): JSONObject =
    JSONObject()
        .put("event_id", "event_${UUID.randomUUID()}")
        .put("type", type)
        .apply(block)

private fun jsonArrayOf(vararg values: String) = org.json.JSONArray().apply {
    values.forEach(::put)
}

private fun parseHotwords(value: String): JSONObject = JSONObject().apply {
    value.lineSequence().forEach { line ->
        val (source, target) = line.split("=", limit = 2).map { it.trim() }.let {
            if (it.size == 2) it else return@forEach
        }
        if (source.isNotBlank() && target.isNotBlank()) put(source, target)
    }
}
