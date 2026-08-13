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

val volcS2tLanguages = supportedLanguages.filter { it.code in setOf(
    "zh", "en", "de", "fr", "es", "id", "ja", "pt", "ko", "tr", "ms", "nl",
    "ro", "pl", "cs", "ar", "th", "vi", "ru", "it",
) }

val volcS2sLanguages = supportedLanguages.filter { it.code in setOf(
    "zh", "en", "de", "fr", "es", "id", "ja", "pt",
) }

enum class TriggerMode(val label: String) {
    Hold("Hold to talk"),
    Tap("Tap to start / stop"),
}

enum class RealtimeProvider(val label: String, val shortLabel: String) {
    Qwen("Qwen LiveTranslate", "Qwen"),
    OpenAI("GPT Realtime Translate", "GPT"),
    Volcengine("ByteDance / Volcengine 同声传译 2.0", "Volc"),
}

enum class TranslationMode(
    val provider: RealtimeProvider,
    val label: String,
    val description: String,
) {
    DualEnglishChinese(
        RealtimeProvider.Qwen,
        "EN ↔ 中文 · locked pair",
        "Two target streams with source recognition forced to English and Chinese. Prevents unrelated-language detections; roughly doubles audio input usage.",
    ),
    DetectedPair(
        RealtimeProvider.Qwen,
        "Auto source → one target",
        "One stream detects the spoken language and translates into your selected target. Cheaper than two-way mode.",
    ),
    ManualForward(RealtimeProvider.Qwen, "Fixed A → B", "One stream with an explicitly selected source and target."),
    ManualReverse(RealtimeProvider.Qwen, "Fixed B → A", "One stream with the selected pair reversed."),
    OpenAiForward(
        RealtimeProvider.OpenAI,
        "Simultaneous → B",
        "One native simultaneous translation stream. Source speech is detected while translated text and speech stream to language B.",
    ),
    OpenAiReverse(
        RealtimeProvider.OpenAI,
        "Simultaneous → A",
        "The same full-duplex stream with language A as the output target.",
    ),
    VolcBidirectionalText(
        RealtimeProvider.Volcengine,
        "True EN ↔ 中文 · text",
        "One native zhen stream automatically reverses English and Chinese and returns source and translated subtitles.",
    ),
    VolcBidirectionalSpeech(
        RealtimeProvider.Volcengine,
        "True EN ↔ 中文 · speech",
        "One full-duplex zhen S2S stream automatically reverses English and Chinese and speaks with cloned input voice.",
    ),
    VolcForwardText(RealtimeProvider.Volcengine, "Fixed A → B · text", "S2T simultaneous subtitles for a fixed supported pair."),
    VolcReverseText(RealtimeProvider.Volcengine, "Fixed B → A · text", "S2T simultaneous subtitles with the selected pair reversed."),
    VolcForwardSpeech(RealtimeProvider.Volcengine, "Fixed A → B · speech", "S2S simultaneous speech using cloned input voice."),
    VolcReverseSpeech(RealtimeProvider.Volcengine, "Fixed B → A · speech", "S2S simultaneous speech with the selected pair reversed."),
}

val TranslationMode.usesSpeechOutput: Boolean
    get() = this in setOf(
        TranslationMode.OpenAiForward,
        TranslationMode.OpenAiReverse,
        TranslationMode.VolcBidirectionalSpeech,
        TranslationMode.VolcForwardSpeech,
        TranslationMode.VolcReverseSpeech,
    )

enum class MicrophoneMode(val label: String, val description: String) {
    Speech(
        "Speech recognition · recommended",
        "Prioritizes Android's speech-recognition processing and lets Xiaomi route its microphone array.",
    ),
    Communication(
        "Speakerphone / noisy room",
        "Uses voice-communication echo and noise processing; useful while the phone is playing sound.",
    ),
    Unprocessed(
        "Raw / unprocessed",
        "Avoids speech processing when supported. Mainly useful for comparison and debugging.",
    ),
}

data class TranslationSegment(
    val id: Long,
    val sourceText: String,
    val translationText: String,
    val sourceLanguage: String?,
    val targetLanguage: String,
)

data class TranslationTurn(
    val id: Long,
    val sourceText: String,
    val translationText: String,
    val sourceLanguage: String?,
    val targetLanguage: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val audioPath: String? = null,
    val durationMillis: Long = 0,
    val segments: List<TranslationSegment> = emptyList(),
)

enum class Region(val hostPart: String, val label: String) {
    Beijing("cn-beijing", "Beijing · 北京"),
    Singapore("ap-southeast-1", "Singapore · 新加坡"),
}

data class AppSettings(
    val provider: RealtimeProvider = RealtimeProvider.Qwen,
    val apiKey: String = "",
    val workspaceId: String = "",
    val openAiApiKey: String = "",
    val openAiSafetyIdentifier: String = "",
    val volcApiKey: String = "",
    val volcResourceId: String = "volc.service_type.10053",
    val region: Region = Region.Beijing,
    val sampleRate: Int = 16_000,
    val chunkMilliseconds: Int = 100,
    val hotwords: String = "",
    val primaryLanguage: String = "en",
    val secondaryLanguage: String = "zh",
    val triggerMode: TriggerMode = TriggerMode.Hold,
    val saveHistory: Boolean = true,
    val translationMode: TranslationMode = TranslationMode.DualEnglishChinese,
    val microphoneMode: MicrophoneMode = MicrophoneMode.Speech,
    val vadSilenceMilliseconds: Int = 700,
    val playTranslatedAudio: Boolean = true,
) {
    val isComplete: Boolean get() = when (provider) {
        RealtimeProvider.Qwen -> apiKey.isNotBlank() && workspaceId.isNotBlank()
        RealtimeProvider.OpenAI -> openAiApiKey.isNotBlank() && openAiSafetyIdentifier.isNotBlank()
        RealtimeProvider.Volcengine -> volcApiKey.isNotBlank() && volcResourceId.isNotBlank()
    }

    val effectiveSampleRate: Int get() = when (provider) {
        RealtimeProvider.Qwen -> sampleRate
        RealtimeProvider.OpenAI -> 24_000
        RealtimeProvider.Volcengine -> 16_000
    }

    val effectiveChunkMilliseconds: Int get() = when (provider) {
        RealtimeProvider.Volcengine -> 80
        else -> chunkMilliseconds
    }
}

enum class SessionPhase {
    Idle,
    Testing,
    Connecting,
    Queued,
    Listening,
    Sending,
    Translating,
    Error,
}

data class TranslationUiState(
    val settings: AppSettings = AppSettings(),
    val direction: TranslationDirection = TranslationDirection.Auto,
    val turns: List<TranslationTurn> = emptyList(),
    val liveTurns: List<TranslationTurn> = emptyList(),
    val sourceText: String = "",
    val translationText: String = "",
    val detectedSourceLanguage: String? = null,
    val activeTargetLanguage: String = "zh",
    val phase: SessionPhase = SessionPhase.Idle,
    val error: String? = null,
    val settingsOpen: Boolean = false,
    val historyOpen: Boolean = false,
    val settingsLoaded: Boolean = false,
    val connectionTestResult: String? = null,
    val isOnline: Boolean = true,
    val selectedHistoryId: Long? = null,
    val playback: PlaybackState = PlaybackState(),
) {
    val isActive: Boolean
        get() = phase != SessionPhase.Idle && phase != SessionPhase.Error
}

data class PlaybackState(
    val sessionId: Long? = null,
    val isPlaying: Boolean = false,
    val positionMillis: Long = 0,
    val durationMillis: Long = 0,
    val speed: Float = 1f,
)
