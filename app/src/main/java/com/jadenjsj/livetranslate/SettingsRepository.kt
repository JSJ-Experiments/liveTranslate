package com.jadenjsj.livetranslate

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.settingsDataStore by preferencesDataStore(name = "qwen_settings")

class SettingsRepository(private val context: Context) {
    private val secretStore = SecretStore(context)

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        AppSettings(
            provider = runCatching {
                RealtimeProvider.valueOf(preferences[PROVIDER] ?: RealtimeProvider.Qwen.name)
            }.getOrDefault(RealtimeProvider.Qwen),
            apiKey = secretStore.readSecret("qwen_api_key").ifBlank {
                // Migrate the encrypted key written by releases before multi-provider support.
                secretStore.readSecret("api_key")
            },
            workspaceId = preferences[WORKSPACE_ID].orEmpty(),
            openAiApiKey = secretStore.readSecret("openai_api_key"),
            openAiSafetyIdentifier = preferences[OPENAI_SAFETY_ID].orEmpty().ifBlank { defaultSafetyIdentifier(context) },
            volcApiKey = secretStore.readSecret("volc_api_key"),
            volcResourceId = preferences[VOLC_RESOURCE_ID] ?: "volc.service_type.10053",
            region = runCatching {
                Region.valueOf(preferences[REGION] ?: Region.Beijing.name)
            }.getOrDefault(Region.Beijing),
            sampleRate = preferences[SAMPLE_RATE]?.toIntOrNull()?.takeIf { it == 8_000 || it == 16_000 } ?: 16_000,
            chunkMilliseconds = preferences[CHUNK_MILLISECONDS]?.toIntOrNull()?.takeIf { it in setOf(40, 100, 200) } ?: 100,
            hotwords = preferences[HOTWORDS].orEmpty(),
            primaryLanguage = preferences[PRIMARY_LANGUAGE]?.takeIf(::isSupportedLanguage) ?: "en",
            secondaryLanguage = preferences[SECONDARY_LANGUAGE]?.takeIf(::isSupportedLanguage) ?: "zh",
            triggerMode = runCatching {
                TriggerMode.valueOf(preferences[TRIGGER_MODE] ?: TriggerMode.Hold.name)
            }.getOrDefault(TriggerMode.Hold),
            saveHistory = preferences[SAVE_HISTORY] ?: true,
            translationMode = runCatching {
                TranslationMode.valueOf(preferences[TRANSLATION_MODE] ?: TranslationMode.DualEnglishChinese.name)
            }.getOrDefault(TranslationMode.DualEnglishChinese),
            microphoneMode = runCatching {
                MicrophoneMode.valueOf(preferences[MICROPHONE_MODE] ?: MicrophoneMode.Speech.name)
            }.getOrDefault(MicrophoneMode.Speech),
            vadSilenceMilliseconds = preferences[VAD_SILENCE]?.toIntOrNull()
                ?.takeIf { it in setOf(400, 700, 1200) } ?: 700,
            playTranslatedAudio = preferences[PLAY_TRANSLATED_AUDIO] ?: true,
        )
    }

    suspend fun save(settings: AppSettings) {
        secretStore.writeSecret("qwen_api_key", settings.apiKey)
        secretStore.writeSecret("openai_api_key", settings.openAiApiKey)
        secretStore.writeSecret("volc_api_key", settings.volcApiKey)
        context.settingsDataStore.edit { preferences ->
            preferences[PROVIDER] = settings.provider.name
            preferences[WORKSPACE_ID] = settings.workspaceId.trim()
            preferences[OPENAI_SAFETY_ID] = settings.openAiSafetyIdentifier.trim()
            preferences[VOLC_RESOURCE_ID] = settings.volcResourceId.trim()
            preferences[REGION] = settings.region.name
            preferences[SAMPLE_RATE] = settings.sampleRate.toString()
            preferences[CHUNK_MILLISECONDS] = settings.chunkMilliseconds.toString()
            preferences[HOTWORDS] = settings.hotwords.trim()
            preferences[PRIMARY_LANGUAGE] = settings.primaryLanguage
            preferences[SECONDARY_LANGUAGE] = settings.secondaryLanguage
            preferences[TRIGGER_MODE] = settings.triggerMode.name
            preferences[SAVE_HISTORY] = settings.saveHistory
            preferences[TRANSLATION_MODE] = settings.translationMode.name
            preferences[MICROPHONE_MODE] = settings.microphoneMode.name
            preferences[VAD_SILENCE] = settings.vadSilenceMilliseconds.toString()
            preferences[PLAY_TRANSLATED_AUDIO] = settings.playTranslatedAudio
        }
    }

    private companion object {
        val PROVIDER = stringPreferencesKey("provider")
        val WORKSPACE_ID = stringPreferencesKey("workspace_id")
        val OPENAI_SAFETY_ID = stringPreferencesKey("openai_safety_identifier")
        val VOLC_RESOURCE_ID = stringPreferencesKey("volc_resource_id")
        val REGION = stringPreferencesKey("region")
        val SAMPLE_RATE = stringPreferencesKey("sample_rate")
        val CHUNK_MILLISECONDS = stringPreferencesKey("chunk_milliseconds")
        val HOTWORDS = stringPreferencesKey("hotwords")
        val PRIMARY_LANGUAGE = stringPreferencesKey("primary_language")
        val SECONDARY_LANGUAGE = stringPreferencesKey("secondary_language")
        val TRIGGER_MODE = stringPreferencesKey("trigger_mode")
        val SAVE_HISTORY = booleanPreferencesKey("save_history")
        val TRANSLATION_MODE = stringPreferencesKey("translation_mode")
        val MICROPHONE_MODE = stringPreferencesKey("microphone_mode")
        val VAD_SILENCE = stringPreferencesKey("vad_silence_ms")
        val PLAY_TRANSLATED_AUDIO = booleanPreferencesKey("play_translated_audio")
    }
}

private fun isSupportedLanguage(code: String): Boolean = supportedLanguages.any { it.code == code }

private fun defaultSafetyIdentifier(context: Context): String {
    val id = android.provider.Settings.Secure.getString(
        context.contentResolver,
        android.provider.Settings.Secure.ANDROID_ID,
    ).orEmpty().ifBlank { context.packageName }
    return MessageDigest.getInstance("SHA-256")
        .digest("livetranslate:$id".toByteArray())
        .joinToString("") { "%02x".format(it) }
}
