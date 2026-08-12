# Live Translate for Android

A small, native push-to-talk translator using Alibaba Cloud's
`qwen3.5-livetranslate-flash-realtime` WebSocket API.

## MVP features

- Reliable automatic English ↔ Chinese direction is the default and uses two
  parallel Qwen targets with same-language skipping. Cheaper one-stream and
  fixed-direction modes remain available in Settings.
- Compact live source/translation segments use server VAD; completed history is
  kept on a separate screen rather than consuming interpreter space.
  It follows new text until the user scrolls away.
- True hold-to-talk interaction with visible microphone glow and release-to-send.
- Configurable hold-to-talk or tap-to-start/tap-to-send microphone interaction.
- Optional app-private full-session WAV and target-tagged raw Qwen JSONL archive,
  with playback from History rather than the live interpreter.
- Rotating app-private connection/error log at
  `files/debug_logs/livetranslate.log`, accessible with `adb run-as`.
- Explicit connection states: disconnected, connecting, live, sending, and translating.
- On-device connection test with authenticated WebSocket handshake latency.
- In-app settings for API key, workspace, Beijing/Singapore region, 8/16 kHz
  capture, 40/100/200 ms packets, and translation hotwords.
- The API key is AES-256-GCM encrypted with a non-exportable Android Keystore
  key. It is never baked into the APK or build output.

The app records immediately when push-to-talk is held. While the WebSocket is
connecting, audio is buffered locally. Once authenticated, it is streamed to
Qwen; releasing the button visibly enters the sending/finalizing states.

## Why PCM instead of Opus for now?

The current API documents Opus support, but does not specify the raw packet or
container framing expected from Android's encoder. This build keeps the
known-good PCM16 path instead of exposing an interoperability gamble. At 16 kHz
it sends about 32 KB/s before Base64/WebSocket overhead; the 8 kHz data-saver
option halves that.

## Setup

1. Install the signed `arm64-v8a` APK from the GitHub pre-release or Blacksmith artifact.
2. Open **Settings**.
3. Paste a Model Studio API key and its matching workspace ID.
4. Select the matching region and tap **Test connection**.
5. Save, leave direction on **Auto** (or select a manual direction), then hold
   the microphone button while speaking and release to send.

Qwen can auto-detect the source language but still requires one target language.
In Auto mode the app listens for Qwen's detected source code while audio streams,
updates the target to the other side of the selected pair, then commits
the push-to-talk turn. Manual direction buttons remain available as a fallback.

API keys are region-bound. The stored key is encrypted at rest with Android
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
- StateFlow + ViewModel, DataStore, coroutines, OkHttp WebSocket
- One app module; no DI framework, database, navigation graph, or background
  service until the product needs them
- Stable application ID: `com.jadenjsj.livetranslate`
- Stable release signing key; release assets include a machine-readable metadata JSON

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
