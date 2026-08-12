package com.jadenjsj.livetranslate

enum class TranslationDirection(
    val sourceLanguage: String,
    val targetLanguage: String,
    val sourceLabel: String,
    val targetLabel: String,
    val shortLabel: String,
) {
    EnglishToChinese(
        sourceLanguage = "en",
        targetLanguage = "zh",
        sourceLabel = "English · 原文",
        targetLabel = "中文 · Translation",
        shortLabel = "EN  →  中文",
    ),
    ChineseToEnglish(
        sourceLanguage = "zh",
        targetLanguage = "en",
        sourceLabel = "中文 · 原文",
        targetLabel = "English · Translation",
        shortLabel = "中文  →  EN",
    ),
}

enum class Region(val hostPart: String, val label: String) {
    Beijing("cn-beijing", "Beijing · 北京"),
    Singapore("ap-southeast-1", "Singapore · 新加坡"),
}

data class AppSettings(
    val apiKey: String = "",
    val workspaceId: String = "",
    val region: Region = Region.Beijing,
    val sampleRate: Int = 16_000,
    val chunkMilliseconds: Int = 100,
    val hotwords: String = "",
) {
    val isComplete: Boolean get() = apiKey.isNotBlank() && workspaceId.isNotBlank()
}

enum class SessionPhase {
    Idle,
    Testing,
    Connecting,
    Listening,
    Sending,
    Translating,
    Error,
}

data class TranslationUiState(
    val settings: AppSettings = AppSettings(),
    val direction: TranslationDirection = TranslationDirection.EnglishToChinese,
    val sourceText: String = "",
    val translationText: String = "",
    val phase: SessionPhase = SessionPhase.Idle,
    val error: String? = null,
    val settingsOpen: Boolean = false,
    val settingsLoaded: Boolean = false,
    val connectionTestResult: String? = null,
) {
    val isActive: Boolean
        get() = phase != SessionPhase.Idle && phase != SessionPhase.Error
}
