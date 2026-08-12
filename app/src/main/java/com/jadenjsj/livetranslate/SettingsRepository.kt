package com.jadenjsj.livetranslate

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "qwen_settings")

class SettingsRepository(private val context: Context) {
    private val secretStore = SecretStore(context)

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        AppSettings(
            apiKey = secretStore.readApiKey(),
            workspaceId = preferences[WORKSPACE_ID].orEmpty(),
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
        )
    }

    suspend fun save(settings: AppSettings) {
        secretStore.writeApiKey(settings.apiKey)
        context.settingsDataStore.edit { preferences ->
            preferences[WORKSPACE_ID] = settings.workspaceId.trim()
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
        }
    }

    private companion object {
        val WORKSPACE_ID = stringPreferencesKey("workspace_id")
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
    }
}

private fun isSupportedLanguage(code: String): Boolean = supportedLanguages.any { it.code == code }
