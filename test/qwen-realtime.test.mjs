import assert from "node:assert/strict";
import test from "node:test";
import { buildUrl, makeSessionUpdate } from "../src/qwen-realtime.mjs";
import { readPcm16Mono16k } from "../src/wav.mjs";

test("builds region-specific Model Studio URLs", () => {
  assert.equal(
    buildUrl({ workspaceId: "ws123", region: "beijing" }),
    "wss://ws123.cn-beijing.maas.aliyuncs.com/api-ws/v1/realtime?model=qwen3.5-livetranslate-flash-realtime",
  );
  assert.match(buildUrl({ workspaceId: "ws123", region: "singapore" }), /ap-southeast-1/);
});

test("session enables source transcript and Chinese text translation", () => {
  const event = makeSessionUpdate({ sourceLanguage: "en", targetLanguage: "zh" });
  assert.equal(event.type, "session.update");
  assert.deepEqual(event.session.modalities, ["text"]);
  assert.equal(event.session.input_audio_transcription.language, "en");
  assert.equal(event.session.translation.language, "zh");
  assert.equal(event.session.turn_detection, null);
});

test("auto source language omits a fixed language", () => {
  const event = makeSessionUpdate({ sourceLanguage: "auto", targetLanguage: "zh" });
  assert.equal("language" in event.session.input_audio_transcription, false);
});

test("reads a 16 kHz mono PCM WAV", () => {
  const pcm = Buffer.from([1, 2, 3, 4]);
  assert.deepEqual(readPcm16Mono16k(makeWav(pcm)), pcm);
});

test("rejects incompatible WAV audio", () => {
  assert.throws(() => readPcm16Mono16k(makeWav(Buffer.alloc(4), 44100)), /Expected 16 kHz/);
});

function makeWav(pcm, sampleRate = 16000) {
  const wav = Buffer.alloc(44 + pcm.length);
  wav.write("RIFF", 0);
  wav.writeUInt32LE(36 + pcm.length, 4);
  wav.write("WAVEfmt ", 8);
  wav.writeUInt32LE(16, 16);
  wav.writeUInt16LE(1, 20);
  wav.writeUInt16LE(1, 22);
  wav.writeUInt32LE(sampleRate, 24);
  wav.writeUInt32LE(sampleRate * 2, 28);
  wav.writeUInt16LE(2, 32);
  wav.writeUInt16LE(16, 34);
  wav.write("data", 36);
  wav.writeUInt32LE(pcm.length, 40);
  pcm.copy(wav, 44);
  return wav;
}
