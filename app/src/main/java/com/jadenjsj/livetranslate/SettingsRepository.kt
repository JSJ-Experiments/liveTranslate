package com.jadenjsj.livetranslate

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
        }
    }

    private companion object {
        val WORKSPACE_ID = stringPreferencesKey("workspace_id")
        val REGION = stringPreferencesKey("region")
        val SAMPLE_RATE = stringPreferencesKey("sample_rate")
        val CHUNK_MILLISECONDS = stringPreferencesKey("chunk_milliseconds")
        val HOTWORDS = stringPreferencesKey("hotwords")
    }
}
