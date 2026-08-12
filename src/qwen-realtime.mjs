import WebSocket from "ws";
import { randomUUID } from "node:crypto";

const MODEL = "qwen3.5-livetranslate-flash-realtime";
const HOSTS = {
  beijing: "cn-beijing",
  singapore: "ap-southeast-1",
};

export function buildUrl({ workspaceId, region = "beijing", model = MODEL }) {
  if (!workspaceId) throw new Error("DASHSCOPE_WORKSPACE_ID is required");
  const regionHost = HOSTS[region];
  if (!regionHost) throw new Error("region must be 'beijing' or 'singapore'");
  return `wss://${workspaceId}.${regionHost}.maas.aliyuncs.com/api-ws/v1/realtime?model=${encodeURIComponent(model)}`;
}

export function makeSessionUpdate({ sourceLanguage, targetLanguage = "zh" }) {
  const transcription = { model: "qwen3-asr-flash-realtime" };
  if (sourceLanguage && sourceLanguage !== "auto") {
    transcription.language = sourceLanguage;
  }

  return {
    event_id: eventId(),
    type: "session.update",
    session: {
      modalities: ["text"],
      sample_rate: 16000,
      input_audio_format: "pcm",
      input_audio_transcription: transcription,
      // Manual mode makes a file-based smoke test deterministic. The Android
      // always-on mode can switch this to server_vad later.
      turn_detection: null,
      translation: { language: targetLanguage },
    },
  };
}

export async function runTranslation({
  apiKey,
  workspaceId,
  region = "beijing",
  sourceLanguage = "auto",
  targetLanguage = "zh",
  pcm,
  chunkMilliseconds = 100,
  realtime = true,
  timeoutMilliseconds = 45_000,
  onEvent = () => {},
}) {
  if (!apiKey) throw new Error("DASHSCOPE_API_KEY is required");
  if (!Buffer.isBuffer(pcm) || pcm.length === 0) {
    throw new Error("A non-empty 16 kHz mono PCM buffer is required");
  }

  const url = buildUrl({ workspaceId, region });
  const startedAt = performance.now();
  const state = {
    source: "",
    translation: "",
    usage: null,
    connectedMilliseconds: null,
    firstTranslationMilliseconds: null,
  };

  return await new Promise((resolve, reject) => {
    const ws = new WebSocket(url, {
      headers: { Authorization: `Bearer ${apiKey}` },
    });
    let settled = false;
    let streaming = false;
    const timeout = setTimeout(
      () => fail(new Error(`Timed out after ${timeoutMilliseconds} ms`)),
      timeoutMilliseconds,
    );

    const send = (type, extra = {}) => {
      ws.send(JSON.stringify({ event_id: eventId(), type, ...extra }));
    };

    const finish = (result) => {
      if (settled) return;
      settled = true;
      clearTimeout(timeout);
      if (ws.readyState === WebSocket.OPEN) ws.close(1000);
      resolve(result);
    };

    function fail(error) {
      if (settled) return;
      settled = true;
      clearTimeout(timeout);
      ws.terminate();
      reject(error);
    }

    ws.once("open", () => {
      state.connectedMilliseconds = elapsed(startedAt);
      ws.send(JSON.stringify(makeSessionUpdate({ sourceLanguage, targetLanguage })));
    });
    ws.once("error", fail);
    ws.once("close", (code, reason) => {
      if (!settled) fail(new Error(`WebSocket closed early (${code}): ${reason}`));
    });

    ws.on("message", async (raw) => {
      let event;
      try {
        event = JSON.parse(raw.toString());
      } catch {
        fail(new Error(`Server returned invalid JSON: ${raw.toString()}`));
        return;
      }
      onEvent(event);

      switch (event.type) {
        case "session.updated":
          if (!streaming) {
            streaming = true;
            try {
              await streamPcm(ws, pcm, chunkMilliseconds, realtime);
              send("input_audio_buffer.commit");
            } catch (error) {
              fail(error);
            }
          }
          break;
        case "conversation.item.input_audio_transcription.completed":
          state.source = event.transcript ?? "";
          break;
        case "response.text.text":
          if (state.firstTranslationMilliseconds === null) {
            state.firstTranslationMilliseconds = elapsed(startedAt);
          }
          break;
        case "response.text.done":
          state.translation = event.text ?? "";
          break;
        case "response.done":
          state.usage = event.response?.usage ?? null;
          send("session.finish");
          break;
        case "session.finished":
          finish({ ...state, totalMilliseconds: elapsed(startedAt) });
          break;
        case "error":
          fail(new Error(formatApiError(event)));
          break;
      }
    });
  });
}

async function streamPcm(ws, pcm, chunkMilliseconds, realtime) {
  // 16,000 samples/s * 2 bytes/sample.
  const bytesPerChunk = Math.max(2, Math.round(32_000 * chunkMilliseconds / 1000));
  for (let offset = 0; offset < pcm.length; offset += bytesPerChunk) {
    if (ws.readyState !== WebSocket.OPEN) throw new Error("WebSocket closed while sending audio");
    const audio = pcm.subarray(offset, offset + bytesPerChunk).toString("base64");
    ws.send(JSON.stringify({ event_id: eventId(), type: "input_audio_buffer.append", audio }));
    if (realtime) await sleep(chunkMilliseconds);
  }
}

function formatApiError(event) {
  const error = event.error ?? event;
  return [error.code, error.type, error.message].filter(Boolean).join(": ") || "Unknown API error";
}

function eventId() {
  return `event_${randomUUID()}`;
}

function elapsed(startedAt) {
  return Math.round(performance.now() - startedAt);
}

function sleep(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}
