package com.jadenjsj.livetranslate

enum class TranslationDirection(
    val sourceLanguage: String?,
    val targetLanguage: String,
    val sourceLabel: String,
    val targetLabel: String,
    val shortLabel: String,
) {
    Auto(
        sourceLanguage = null,
        targetLanguage = "zh",
        sourceLabel = "Auto · 原文",
        targetLabel = "Translation · 译文",
        shortLabel = "AUTO",
    ),
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

fun TranslationDirection.sourceLanguage(settings: AppSettings): String? = when (this) {
    TranslationDirection.Auto -> null
    TranslationDirection.EnglishToChinese -> settings.primaryLanguage
    TranslationDirection.ChineseToEnglish -> settings.secondaryLanguage
}

fun TranslationDirection.targetLanguage(settings: AppSettings): String = when (this) {
    TranslationDirection.Auto, TranslationDirection.EnglishToChinese -> settings.secondaryLanguage
    TranslationDirection.ChineseToEnglish -> settings.primaryLanguage
}

fun TranslationDirection.shortLabel(settings: AppSettings): String = when (this) {
    TranslationDirection.Auto -> "AUTO"
    TranslationDirection.EnglishToChinese -> "${settings.primaryLanguage.uppercase()} → ${settings.secondaryLanguage.uppercase()}"
    TranslationDirection.ChineseToEnglish -> "${settings.secondaryLanguage.uppercase()} → ${settings.primaryLanguage.uppercase()}"
}

data class LanguageOption(val code: String, val label: String)

val supportedLanguages = listOf(
    "zh" to "Chinese", "en" to "English", "ar" to "Arabic", "de" to "German",
    "fr" to "French", "es" to "Spanish", "pt" to "Portuguese", "id" to "Indonesian",
    "it" to "Italian", "ko" to "Korean", "ru" to "Russian", "th" to "Thai",
    "vi" to "Vietnamese", "ja" to "Japanese", "tr" to "Turkish", "hi" to "Hindi",
    "ms" to "Malay", "nl" to "Dutch", "ur" to "Urdu", "nb" to "Norwegian Bokmål",
    "sv" to "Swedish", "da" to "Danish", "he" to "Hebrew", "fi" to "Finnish",
    "pl" to "Polish", "is" to "Icelandic", "cs" to "Czech", "fil" to "Filipino",
    "fa" to "Persian", "yue" to "Cantonese", "el" to "Greek", "af" to "Afrikaans",
    "ast" to "Asturian", "be" to "Belarusian", "bg" to "Bulgarian", "bn" to "Bengali",
    "bs" to "Bosnian", "ca" to "Catalan", "ceb" to "Cebuano", "et" to "Estonian",
    "gl" to "Galician", "gu" to "Gujarati", "hr" to "Croatian", "hu" to "Hungarian",
    "jv" to "Javanese", "kk" to "Kazakh", "kn" to "Kannada", "ky" to "Kyrgyz",
    "lv" to "Latvian", "mk" to "Macedonian", "ml" to "Malayalam", "mr" to "Marathi",
    "pa" to "Punjabi", "ro" to "Romanian", "sk" to "Slovak", "sl" to "Slovenian",
    "sw" to "Swahili", "tg" to "Tajik", "az" to "Azerbaijani", "uk" to "Ukrainian",
).map { LanguageOption(it.first, it.second) }

enum class TriggerMode(val label: String) {
    Hold("Hold to talk"),
    Tap("Tap to start / stop"),
}

data class TranslationTurn(
    val id: Long,
    val sourceText: String,
    val translationText: String,
    val sourceLanguage: String?,
    val targetLanguage: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val audioPath: String? = null,
)

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
    val primaryLanguage: String = "en",
    val secondaryLanguage: String = "zh",
    val triggerMode: TriggerMode = TriggerMode.Hold,
    val saveHistory: Boolean = true,
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
    val direction: TranslationDirection = TranslationDirection.Auto,
    val turns: List<TranslationTurn> = emptyList(),
    val sourceText: String = "",
    val translationText: String = "",
    val detectedSourceLanguage: String? = null,
    val activeTargetLanguage: String = "zh",
    val phase: SessionPhase = SessionPhase.Idle,
    val error: String? = null,
    val settingsOpen: Boolean = false,
    val settingsLoaded: Boolean = false,
    val connectionTestResult: String? = null,
) {
    val isActive: Boolean
        get() = phase != SessionPhase.Idle && phase != SessionPhase.Error
}
