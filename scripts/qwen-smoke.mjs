#!/usr/bin/env node
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { parseArgs } from "node:util";
import { runTranslation } from "../src/qwen-realtime.mjs";
import { readPcm16Mono16k } from "../src/wav.mjs";

const { values } = parseArgs({
  options: {
    input: { type: "string", short: "i" },
    source: { type: "string", default: "auto" },
    target: { type: "string", default: "zh" },
    region: { type: "string", default: process.env.DASHSCOPE_REGION ?? "beijing" },
    fast: { type: "boolean", default: false },
    verbose: { type: "boolean", short: "v", default: false },
    help: { type: "boolean", short: "h", default: false },
  },
  strict: true,
});

if (values.help) {
  console.log(`Usage: npm run smoke -- --input speech.wav [options]

Required environment:
  DASHSCOPE_API_KEY       Alibaba Cloud Model Studio API key
  DASHSCOPE_WORKSPACE_ID  Model Studio workspace ID

Options:
  -i, --input <wav>       16 kHz, mono, signed 16-bit PCM WAV
      --source <code>     Source language, or auto (default: auto)
      --target <code>     Target language (default: zh)
      --region <region>   beijing or singapore (default: beijing)
      --fast              Upload without real-time pacing
  -v, --verbose           Print every server event
`);
  process.exit(0);
}

if (!values.input) throw new Error("--input is required (try --help)");
const pcm = readPcm16Mono16k(await readFile(resolve(values.input)));

console.log(`Testing Qwen realtime translation: ${values.source} -> ${values.target}`);
console.log(`Audio: ${(pcm.length / 32_000).toFixed(2)} s; region: ${values.region}`);

const result = await runTranslation({
  apiKey: process.env.DASHSCOPE_API_KEY,
  workspaceId: process.env.DASHSCOPE_WORKSPACE_ID,
  region: values.region,
  sourceLanguage: values.source,
  targetLanguage: values.target,
  pcm,
  realtime: !values.fast,
  onEvent: values.verbose ? (event) => console.error(JSON.stringify(event)) : undefined,
});

console.log(`\nSource / 原文:      ${result.source || "(not returned)"}`);
console.log(`Translation / 译文: ${result.translation || "(not returned)"}`);
console.log(`First translation:  ${result.firstTranslationMilliseconds ?? "n/a"} ms`);
console.log(`Total:              ${result.totalMilliseconds} ms`);
if (result.usage) console.log(`Usage:              ${JSON.stringify(result.usage)}`);
