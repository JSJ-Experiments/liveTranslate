# Live Translate for Android

A small, native push-to-talk translator using Alibaba Cloud's
`qwen3.5-livetranslate-flash-realtime` WebSocket API.

## MVP features

- English → Chinese and Chinese → English.
- Live source transcript and tentative/final translation text.
- Explicit connection states: disconnected, connecting, live, sending, and translating.
- On-device connection test with authenticated WebSocket handshake latency.
- In-app settings for API key, workspace, Beijing/Singapore region, 8/16 kHz
  capture, 40/100/200 ms packets, and translation hotwords.
- Credentials remain in Android app-private DataStore and are never baked into
  the APK or CI.

The app records immediately when push-to-talk is held. While the WebSocket is
connecting, audio is buffered locally. Once authenticated, it is streamed to
Qwen; releasing the button visibly enters the sending/finalizing states.

## Why PCM instead of Opus?

The API advertises Opus, but its realtime documentation does not specify the
required packet/container framing. The official browser demo currently streams
16 kHz mono PCM. This MVP therefore favors the known-good PCM16 path instead of
shipping an untestable codec switch. At 16 kHz it sends about 32 KB/s before
Base64/WebSocket overhead; the 8 kHz data-saver option halves that.

## Setup

1. Install the debug APK from the Blacksmith workflow artifact.
2. Open **Settings**.
3. Paste a Model Studio API key and its matching workspace ID.
4. Select the matching region and tap **Test connection**.
5. Save, choose a direction, then hold the microphone button while speaking.

API keys are region-bound. A long-lived cloud key inside a client app is only
appropriate for a personal prototype. A public release should use short-lived
credentials from a backend.

## Stack

- Kotlin, Jetpack Compose, Material 3, edge-to-edge UI
- AGP 9.3 built-in Kotlin and its compatible Kotlin/Compose compiler
- Android 17 compile/target SDK; supports Android 8 through Android 17,
  including Android 16
- StateFlow + ViewModel, DataStore, coroutines, OkHttp WebSocket
- One app module; no DI framework, database, navigation graph, or background
  service until the product needs them

## Build and test

Builds intentionally run on Blacksmith, not a developer machine. Push a branch
or manually dispatch `.github/workflows/android.yml`. CI runs:

```text
:app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The APK is uploaded as the `liveTranslate-debug` workflow artifact.

The pre-Android API spike remains in `scripts/`, `src/`, and `test/`. Its unit
tests can be run with `npm test`; a real credentialed smoke test is available
through `npm run smoke`.

## Protocol references

- [Qwen3.5 LiveTranslate realtime API](https://help.aliyun.com/en/model-studio/qwen3-5-livetranslate-flash-realtime)
- [Client events](https://help.aliyun.com/en/model-studio/live-translator-client-events)
- [Server events](https://help.aliyun.com/en/model-studio/live-translator-server-events)
