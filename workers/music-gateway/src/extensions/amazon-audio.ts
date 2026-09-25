// Preserve ISO-BMFF offsets while decoding CENC samples. This lets Media3 use
// the original segment index and seek without downloading the entire track.
type Box = { start: number; end: number; type: string };
const text = new TextDecoder();
const u32 = (b: Uint8Array, p: number) => new DataView(b.buffer, b.byteOffset, b.byteLength).getUint32(p);
const typeAt = (b: Uint8Array, p: number) => text.decode(b.subarray(p, p + 4));
function boxes(b: Uint8Array, start = 0, end = b.length): Box[] {
  const result: Box[] = [];
  for (let p = start; p + 8 <= end;) {
    const size = u32(b, p);
    if (size < 8) throw Error("Unsupported MP4 box size");
    if (p + size > end) break;
    result.push({ start: p, end: p + size, type: typeAt(b, p + 4) }); p += size;
  }
  return result;
}
function tree(b: Uint8Array, start = 0, end = b.length, depth = 0): Box[] {
  if (depth > 12) throw Error("MP4 nesting limit");
  return boxes(b, start, end).flatMap((box) => {
    const skip = ["moov", "trak", "mdia", "minf", "stbl", "sinf", "schi", "moof", "traf"].includes(box.type) ? 8 :
      box.type === "stsd" ? 16 : box.type === "enca" ? 36 : 0;
    return [box, ...(skip ? tree(b, box.start + skip, box.end, depth + 1) : [])];
  });
}
function rename(b: Uint8Array, box: Box, name: string) { b.set(new TextEncoder().encode(name), box.start + 4); }

// EC3SpecificBox extension type A is the container's Dolby Atmos/JOC signal.
// Layout follows FFmpeg's mov_write_eac3_tag (libavformat/movenc.c).
export function ec3HasAtmos(data: Uint8Array): boolean {
  let bit = 0;
  const read = (count: number) => { let value = 0; for (let i = 0; i < count; i++, bit++) {
    if (bit >= data.length * 8) throw Error("Truncated EC-3 config");
    value = value * 2 + ((data[bit >> 3] >> (7 - (bit & 7))) & 1);
  } return value; };
  try {
    read(13); const count = read(3) + 1;
    for (let i = 0; i < count; i++) { read(19); const dependents = read(4); read(dependents ? 9 : 1); }
    read(7); return read(1) === 1 && read(8) > 0;
  } catch { return false; }
}

async function amazonHeaderBytes(url: string): Promise<Uint8Array | null> {
  try {
    const r = await fetch(url, { headers: { Range: "bytes=0-32767" }, redirect: "manual", signal: AbortSignal.timeout(4000) });
    if (r.status !== 206 || !r.body) { await r.body?.cancel(); return null; }
    const reader = r.body.getReader(); const bytes = new Uint8Array(32768); let length = 0;
    try { while (true) { const n = await reader.read(); if(n.done) break; if(length+n.value.length>bytes.length) return null; bytes.set(n.value,length); length+=n.value.length; } }
    finally { await reader.cancel(); reader.releaseLock(); }
    return bytes.subarray(0, length);
  } catch { return null; }
}

export async function amazonHasAtmos(url: string): Promise<boolean> {
  const bytes = await amazonHeaderBytes(url);
  if (!bytes) return false;
  const all = tree(bytes), frma = all.find((b)=>b.type === "frma"), dec3 = all.find((b)=>b.type === "dec3");
  return Boolean(frma && dec3 && typeAt(bytes,frma.start+8)==="ec-3" && ec3HasAtmos(bytes.subarray(dec3.start+8,dec3.end)));
}

/** Sony 360 Reality Audio on Amazon is MPEG-H in ISO-BMFF (`mha1` / `mhm1`). */
export function amazonHeaderIsMpegh(header: Uint8Array): boolean {
  const all = tree(header);
  const frma = all.find((b) => b.type === "frma");
  if (frma) {
    const codec = typeAt(header, frma.start + 8);
    return codec === "mha1" || codec === "mhm1";
  }
  return all.some((b) => b.type === "mha1" || b.type === "mhm1");
}

export async function amazonHasMpegh(url: string): Promise<boolean> {
  const bytes = await amazonHeaderBytes(url);
  return bytes ? amazonHeaderIsMpegh(bytes) : false;
}

const AMAZON_SAMPLE_CODECS = ["fLaC", "ec-3", "ac-4", "mha1", "mhm1"] as const;

function isAmazonSampleCodec(codec: string): boolean {
  return (AMAZON_SAMPLE_CODECS as readonly string[]).includes(codec);
}

function clearDescriptions(header: Uint8Array): number[] {
  const stsd = tree(header).find((b) => b.type === "stsd");
  const entries = stsd ? boxes(header, stsd.start + 16, stsd.end) : [];
  return entries.flatMap((b, i) => {
    if (isAmazonSampleCodec(b.type)) return [i + 1];
    const tenc = b.type === "enca" ? tree(header, b.start + 36, b.end).find((x) => x.type === "tenc") : undefined;
    if (tenc && header[tenc.start + 14] === 0) return [i + 1];
    return [];
  });
}

export function amazonIndex(header: Uint8Array): { ivSize: number; segments: number[]; clearDescriptions: number[] } {
  const all = tree(header);
  const tenc = all.find((b) => b.type === "tenc");
  const scheme = all.find((b) => b.type === "schm");
  const sidx = all.find((b) => b.type === "sidx");
  if (!tenc || !scheme || !sidx || typeAt(header, scheme.start + 12) !== "cenc" || header[tenc.start + 14] !== 1) throw Error("Unsupported Amazon encryption");
  const ivSize = header[tenc.start + 15];
  if (![8, 16].includes(ivSize)) throw Error("Unsupported CENC IV");
  let p = sidx.start + 20;
  const version = header[sidx.start + 8];
  let firstOffset: number;
  if (version === 0) { firstOffset = u32(header, p + 4); p += 8; }
  else if (version === 1) {
    if (u32(header, p + 8) !== 0) throw Error("MP4 offset limit");
    firstOffset = u32(header, p + 12); p += 16;
  } else throw Error("Unsupported sidx version");
  const count = (header[p + 2] << 8) | header[p + 3]; p += 4;
  if (p + count * 12 > sidx.end || count > 10000) throw Error("Invalid segment index");
  let offset = sidx.end + firstOffset; const segments = [offset];
  for (let i = 0; i < count; i++, p += 12) {
    const size = u32(header, p);
    if (size & 0x80000000 || size < 8) throw Error("Unsupported segment reference");
    offset += size; segments.push(offset);
  }
  return { ivSize, segments, clearDescriptions: clearDescriptions(header) };
}

type Sample = { size: number; iv: Uint8Array; clear?: boolean; parts?: Array<{ clear: number; encrypted: number }> };
function fragment(b: Uint8Array, ivSize: number, clearEntries: number[]): Sample[] {
  const all = tree(b);
  if (all.filter((x) => x.type === "traf").length !== 1) throw Error("Expected one audio track");
  const run = all.find((x) => x.type === "trun"), senc = all.find((x) => x.type === "senc");
  const tfhd = all.find((x) => x.type === "tfhd");
  const tfhdFlags = tfhd ? u32(b, tfhd.start + 8) & 0xffffff : 0;
  const description = tfhd && (tfhdFlags & 2) ? u32(b, tfhd.start + 16 + ((tfhdFlags & 1) ? 8 : 0)) : 1;
  const defaultSizeOffset = tfhd ? tfhd.start + 16 + ((tfhdFlags & 1) ? 8 : 0) + ((tfhdFlags & 2) ? 4 : 0) + ((tfhdFlags & 8) ? 4 : 0) : 0;
  const defaultSize = tfhd && (tfhdFlags & 0x10) ? u32(b, defaultSizeOffset) : 0;
  const clear = !senc && clearEntries.includes(description);
  if (!run || (!senc && !clear) || all.filter((x) => x.type === "trun").length !== 1 || (senc && ![0, 2].includes(u32(b, senc.start + 8)))) throw Error(`Unsupported CENC sample layout: description=${description}, clear=${clearEntries.join(",")}, tfhd=${tfhdFlags}, senc=${senc ? u32(b,senc.start+8) : "missing"}`);
  const subsamples = senc && u32(b, senc.start + 8) === 2;
  const flags = u32(b, run.start + 8) & 0xffffff;
  const count = u32(b, run.start + 12);
  if ((!(flags & 0x200) && !defaultSize) || !(flags & 1) || count > 100000 || (senc && (count !== u32(b, senc.start + 12) || senc.start + 16 + count * ivSize > senc.end))) throw Error("Invalid CENC samples");
  let p = run.start + 16;
  if (u32(b, p) !== b.length + 8) throw Error("Unsupported fragment data offset");
  p += 4; if (flags & 4) p += 4;
  const result: Sample[] = [];
  let encryptionOffset = senc ? senc.start + 16 : 0;
  for (let i = 0; i < count; i++) {
    if (flags & 0x100) p += 4;
    const size = flags & 0x200 ? u32(b, p) : defaultSize; if (flags & 0x200) p += 4;
    if (flags & 0x400) p += 4;
    if (flags & 0x800) p += 4;
    if (p > run.end || size < 1 || size > 1024 * 1024) throw Error("Invalid audio sample");
    if (clear) { result.push({ size, iv: new Uint8Array(16), clear: true }); continue; }
    if (!senc) throw Error("Missing CENC samples");
    if (encryptionOffset + ivSize > senc.end) throw Error("Invalid sample IV");
    const iv = new Uint8Array(16); iv.set(b.subarray(encryptionOffset, encryptionOffset + ivSize)); encryptionOffset += ivSize;
    const parts: Sample["parts"] = subsamples ? [] : undefined;
    if (parts) {
      if (encryptionOffset + 2 > senc.end) throw Error("Invalid subsample count");
      const count = (b[encryptionOffset] << 8) | b[encryptionOffset + 1]; encryptionOffset += 2;
      if (encryptionOffset + count * 6 > senc.end) throw Error("Invalid subsample bounds");
      for (let j = 0; j < count; j++, encryptionOffset += 6) parts.push({ clear: (b[encryptionOffset] << 8) | b[encryptionOffset + 1], encrypted: u32(b, encryptionOffset + 2) });
      if (parts.reduce((n, x) => n + x.clear + x.encrypted, 0) !== size) throw Error("Invalid subsample sizes");
    }
    result.push({ size, iv, parts });
  }
  for (const box of all) if (["senc", "saiz", "saio"].includes(box.type)) rename(b, box, "free");
  return result;
}

export async function amazonTransform(hexKey: string, ivSize: number, clearEntries: number[] = []): Promise<TransformStream<Uint8Array, Uint8Array>> {
  if (!/^[a-f\d]{32}$/i.test(hexKey)) throw Error("Invalid audio key");
  const key = await crypto.subtle.importKey("raw", Uint8Array.from(hexKey.match(/../g)!, (s) => parseInt(s, 16)), "AES-CTR", false, ["decrypt"]);
  let pending = new Uint8Array(0), mediaBytes = 0, sampleIndex = 0;
  let samples: Sample[] = [];
  return new TransformStream({
    async transform(chunk, controller) {
      const b = new Uint8Array(pending.length + chunk.length); b.set(pending); b.set(chunk, pending.length);
      let p = 0;
      while (p < b.length) {
        if (mediaBytes) {
          const sample = samples[sampleIndex];
          if (!sample || sample.size > mediaBytes) throw Error("Invalid mdat sample bounds");
          if (p + sample.size > b.length) break;
          const output = b.slice(p, p + sample.size);
          if (sample.clear) {
            // Clear lead segments explicitly select an unencrypted stsd entry.
          } else if (sample.parts) {
            const encrypted = new Uint8Array(sample.parts.reduce((n, x) => n + x.encrypted, 0));
            let source = 0, target = 0;
            for (const part of sample.parts) { source += part.clear; encrypted.set(output.subarray(source, source + part.encrypted), target); source += part.encrypted; target += part.encrypted; }
            if (encrypted.length) {
              const decoded = new Uint8Array(await crypto.subtle.decrypt({ name: "AES-CTR", counter: sample.iv, length: 64 }, key, encrypted));
              source = 0; target = 0;
              for (const part of sample.parts) { target += part.clear; output.set(decoded.subarray(source, source + part.encrypted), target); source += part.encrypted; target += part.encrypted; }
            }
          } else output.set(new Uint8Array(await crypto.subtle.decrypt({ name: "AES-CTR", counter: sample.iv, length: 64 }, key, output)));
          controller.enqueue(output); p += sample.size; mediaBytes -= sample.size; sampleIndex++;
          if (!mediaBytes && sampleIndex !== samples.length) throw Error("Incomplete mdat");
          continue;
        }
        if (p + 8 > b.length) break;
        const size = u32(b, p), type = typeAt(b, p + 4);
        if (size < 8) throw Error("Unsupported MP4 box size");
        if (type === "mdat") {
          if (!samples.length || samples.reduce((n, s) => n + s.size, 0) !== size - 8) throw Error("Invalid fragment size");
          controller.enqueue(b.slice(p, p + 8)); p += 8; mediaBytes = size - 8; sampleIndex = 0; continue;
        }
        if (size > 1024 * 1024) throw Error("MP4 metadata limit");
        if (p + size > b.length) break;
        const box = b.slice(p, p + size);
        if (type === "moov") {
          clearEntries = clearDescriptions(box);
          const all = tree(box); const original = all.find((x) => x.type === "frma");
          const codec = original ? typeAt(box, original.start + 8) : "";
          if (!isAmazonSampleCodec(codec)) throw Error("Unsupported Amazon audio codec");
          for (const x of all) {
            if (x.type === "enca") rename(box, x, codec);
            if (["sinf", "pssh"].includes(x.type)) rename(box, x, "free");
          }
        } else if (type === "moof") samples = fragment(box, ivSize, clearEntries);
        controller.enqueue(box); p += size;
      }
      pending = b.slice(p);
      if (pending.length > 1024 * 1024) throw Error("Audio buffer limit");
    },
    flush() { if (pending.length || mediaBytes) throw Error("Truncated Amazon audio"); },
  });
}

export async function handleAmazonAudio(request: Request, url: string, key: string): Promise<Response> {
  const match = request.headers.get("Range")?.match(/^bytes=(\d+)-(\d*)$/);
  if (request.headers.has("Range") && !match) return new Response(null, { status: 416 });
  try {
    const head = await fetch(url, { headers: { Range: "bytes=0-65535" }, redirect: "manual", signal: request.signal });
    const contentRange = head.headers.get("Content-Range")?.match(/^bytes 0-\d+\/(\d+)$/);
    if (head.status !== 206 || !contentRange || Number(head.headers.get("Content-Length")) > 65536) { await head.body?.cancel(); throw Error("Invalid Amazon header range"); }
    if (!head.body) throw Error("Missing Amazon header");
    const reader = head.body.getReader(), parts: Uint8Array[] = [];
    let bytes = 0;
    try {
      while (true) {
        const next = await reader.read(); if (next.done) break;
        bytes += next.value.length;
        if (bytes > 65536) { await reader.cancel(); throw Error("Amazon header limit"); }
        parts.push(next.value);
      }
    } finally { reader.releaseLock(); }
    const header = new Uint8Array(bytes); let offset = 0;
    for (const part of parts) { header.set(part, offset); offset += part.length; }
    const index = amazonIndex(header), total = Number(contentRange[1]);
    const start = match ? Number(match[1]) : 0, end = Math.min(match?.[2] ? Number(match[2]) : total - 1, total - 1);
    if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start > end || start >= total) return new Response(null, { status: 416, headers: { "Content-Range": `bytes */${total}` } });
    const aligned = index.segments.filter((p) => p <= start).at(-1) ?? 0;
    const sourceEnd = index.segments.find((p) => p > end) ?? total;
    const responseHeaders = new Headers({ "Content-Type": "audio/mp4", "Accept-Ranges": "bytes", "Cache-Control": "private, no-store", "Access-Control-Allow-Origin": "*", "Content-Length": String(end - start + 1) });
    if (match) responseHeaders.set("Content-Range", `bytes ${start}-${end}/${total}`);
    if (request.method === "HEAD") return new Response(null, { status: match ? 206 : 200, headers: responseHeaders });
    const upstream = await fetch(url, { headers: { Range: `bytes=${aligned}-${sourceEnd - 1}` }, redirect: "manual", signal: request.signal });
    if (upstream.status !== 206 || upstream.headers.get("Content-Range") !== `bytes ${aligned}-${sourceEnd - 1}/${total}` || !upstream.body) { await upstream.body?.cancel(); throw Error("Invalid Amazon media range"); }
    let skip = start - aligned, remaining = end - start + 1;
    const trim = new TransformStream<Uint8Array, Uint8Array>({ transform(b, c) {
      const drop = Math.min(skip, b.length); skip -= drop;
      const part = b.subarray(drop, drop + Math.min(remaining, b.length - drop));
      if (part.length) { c.enqueue(part); remaining -= part.length; }
      if (!remaining) c.terminate();
    } });
    return new Response(upstream.body.pipeThrough(await amazonTransform(key, index.ivSize, index.clearDescriptions)).pipeThrough(trim), { status: match ? 206 : 200, headers: responseHeaders });
  } catch { return new Response("Amazon audio unavailable", { status: 502 }); }
}
