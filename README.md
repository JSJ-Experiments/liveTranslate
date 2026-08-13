# Live Translate for Android

A small, native push-to-talk and simultaneous translator with first-party
WebSocket transports for Qwen, OpenAI, and Volcengine.

## MVP features

- Reliable automatic English ↔ Chinese direction is the default and uses two
  parallel Qwen targets with same-language skipping. Cheaper one-stream and
  fixed-direction modes remain available in Settings.
- GPT Realtime Translation mode streams source transcription, translated text,
  and optional translated PCM speech while recording continues. Its output
  target remains explicit, matching OpenAI's native protocol.
- Volcengine Seed LiveInterpret 2.0 supports one-stream native `zhen`
  English ↔ Chinese reversal in S2T and full-duplex S2S modes, plus fixed
  direction modes for its documented language sets.
- Compact live source/translation segments use server VAD; completed history is
  kept on a separate screen rather than consuming interpreter space.
  It follows new text until the user scrolls away, and starting another tap/hold
  session no longer clears the visible conversation.
- The toolbar + action starts a blank visible conversation without deleting any
  saved history sessions.
- Full-width hold-to-talk interaction with visible microphone glow and release-to-send.
- Configurable hold-to-talk or tap-to-start/tap-to-send microphone interaction.
- Optional app-private full-session WAV and provider-tagged raw event archive.
  History is organized by session, with a transcript detail view, seeker,
  play/pause, and playback speed controls.
- Rotating app-private connection/error log at `files/debug_logs/livetranslate.log`.
  History's Export action shares a readable diagnostics ZIP containing logs,
  raw events, transcripts, and audio.
- Explicit connection states: disconnected, connecting, live, sending, and translating,
  with a force-retry action. VPN reachability is confirmed by the Qwen probe rather
  than Android's sometimes-stale network validation bit.
- On-device connection test with authenticated WebSocket handshake latency.
- In-app provider credentials, modes, language pairs, translated speech toggle,
  Qwen regions/quality/VAD/hotwords, and provider-mandated wire audio details.
- Settings auto-save after edits and are also flushed when the sheet is dismissed.
- Provider credentials are separately AES-256-GCM encrypted with a
  non-exportable Android Keystore key. They are never baked into the APK or
  build output.

The app records immediately when push-to-talk is held. While the WebSocket is
connecting, audio is buffered locally. Releasing before connection queues the
complete recording in memory and keeps reconnecting until it can be sent (or
the user cancels). Once authenticated, it is streamed to the selected provider.

Qwen's server VAD automatically splits speech into utterances. The exposed
end-of-speech silence option does not manually split text; it only tunes how
long a pause must be before server VAD considers the utterance complete.

## Why PCM instead of Opus for now?

The current API documents Opus support, but does not specify the raw packet or
container framing expected from Android's encoder. This build keeps the
known-good PCM16 path instead of exposing an interoperability gamble. At 16 kHz
it sends about 32 KB/s before Base64/WebSocket overhead; the 8 kHz data-saver
option halves that.

## Setup

1. Install the signed `arm64-v8a` APK from the GitHub pre-release or Blacksmith artifact.
2. Open **Settings**.
3. Select a provider and enter its requested credentials.
4. Select a provider-native translation mode and tap **Test connection**.
5. Save, leave direction on **Auto** (or select a manual direction), then hold
   the microphone button while speaking and release to send.

Qwen can auto-detect the source language but still requires one target language.
The default English/Chinese mode therefore sends the same audio to two Qwen
sessions, one targeting English and one Chinese, and asks each to skip audio
already in its target language. Their source recognizers are explicitly locked
to English and Chinese rather than language auto-detection. Each stream keeps its own source transcript so
asynchronous results cannot be attached to the wrong sentence. The cheaper auto-source mode uses one fixed,
user-selectable target. Manual directions remain available as a fallback.

Qwen API keys are region-bound. All stored provider keys are encrypted at rest with Android
Keystore and backup is disabled. This protects it from ordinary app-to-app
access and offline extraction, but a compromised/rooted device or an attacker
controlling the running app can still use it. A public release should use
short-lived, narrowly scoped credentials from a backend instead of a long-lived
Model Studio key.

## Stack

- Kotlin, Jetpack Compose, Material 3, edge-to-edge UI
- AGP 9.3 built-in Kotlin and its compatible Kotlin/Compose compiler
- Android 16 compile/target SDK; supports Android 8 and newer
- `arm64-v8a` only, covering modern Armv8 and Armv9 phones
- StateFlow + ViewModel, DataStore, coroutines, OkHttp WebSocket, AudioTrack
- One app module; no DI framework, database, navigation graph, or background
  service until the product needs them
- Stable application ID: `com.jadenjsj.livetranslate`
- Stable release signing key; release assets include a machine-readable metadata JSON

## Stable prerelease update URLs

Update clients should fetch the rolling metadata first:

```text
https://github.com/JSJ-Experiments/liveTranslate/releases/download/latest-prerelease/LiveTranslate-latest-metadata.json
```

The metadata contains the current version code, SHA-256, byte size, source
commit, versioned release URL, and APK URL. A stable direct APK URL is also
available, though checking its metadata and hash first is recommended:

```text
https://github.com/JSJ-Experiments/liveTranslate/releases/download/latest-prerelease/LiveTranslate-latest-arm64-v8a.apk
```

## Build and test

Builds intentionally run on Blacksmith, not a developer machine. Push a branch
or manually dispatch `.github/workflows/android.yml`. CI runs:

```text
:app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

The signed, R8-optimized APK and its metadata JSON are uploaded together as a
versioned workflow artifact.

The pre-Android API spike remains in `scripts/`, `src/`, and `test/`. Its unit
tests can be run with `npm test`; a real credentialed smoke test is available
through `npm run smoke`.

## Protocol references

- [Qwen3.5 LiveTranslate realtime API](https://help.aliyun.com/en/model-studio/qwen3-5-livetranslate-flash-realtime)
- [Client events](https://help.aliyun.com/en/model-studio/live-translator-client-events)
- [Server events](https://help.aliyun.com/en/model-studio/live-translator-server-events)
- [OpenAI Realtime Translation](https://developers.openai.com/api/docs/guides/realtime-translation)
- [Volcengine Seed LiveInterpret 2.0](https://www.volcengine.com/docs/6561/1631605?lang=zh)
