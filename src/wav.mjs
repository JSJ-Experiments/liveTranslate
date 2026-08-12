export function readPcm16Mono16k(wav) {
  if (!Buffer.isBuffer(wav) || wav.length < 44) throw new Error("Not a valid WAV file");
  if (wav.toString("ascii", 0, 4) !== "RIFF" || wav.toString("ascii", 8, 12) !== "WAVE") {
    throw new Error("Not a RIFF/WAVE file");
  }

  let format;
  let pcm;
  for (let offset = 12; offset + 8 <= wav.length;) {
    const id = wav.toString("ascii", offset, offset + 4);
    const size = wav.readUInt32LE(offset + 4);
    const start = offset + 8;
    const end = start + size;
    if (end > wav.length) throw new Error(`Truncated WAV chunk: ${id}`);

    if (id === "fmt ") {
      format = {
        encoding: wav.readUInt16LE(start),
        channels: wav.readUInt16LE(start + 2),
        sampleRate: wav.readUInt32LE(start + 4),
        bitsPerSample: wav.readUInt16LE(start + 14),
      };
    } else if (id === "data") {
      pcm = wav.subarray(start, end);
    }
    offset = end + (size % 2);
  }

  if (!format || !pcm) throw new Error("WAV must contain fmt and data chunks");
  const actual = `${format.sampleRate} Hz, ${format.channels} channel(s), ${format.bitsPerSample}-bit, encoding ${format.encoding}`;
  if (format.encoding !== 1 || format.channels !== 1 || format.sampleRate !== 16000 || format.bitsPerSample !== 16) {
    throw new Error(`Expected 16 kHz mono 16-bit PCM WAV; got ${actual}`);
  }
  return pcm;
}
