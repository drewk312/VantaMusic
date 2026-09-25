import { afterEach, it } from "node:test";
import assert from "node:assert/strict";
import { amazonIndex, amazonTransform, handleAmazonAudio, ec3HasAtmos, amazonHeaderIsMpegh } from "./amazon-audio";

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });
const keyHex = "00112233445566778899aabbccddeeff";
const join = (...parts: Uint8Array[]) => Buffer.concat(parts);
const n = (...values: number[]) => { const b = Buffer.alloc(values.length * 4); values.forEach((v, i) => b.writeUInt32BE(v, i * 4)); return b; };
const box = (name: string, ...parts: Uint8Array[]) => { const body = join(...parts); return join(n(body.length + 8), Buffer.from(name), body); };

async function fixture(options: { ec3?: boolean; ac4?: boolean; mpegh?: boolean; subsamples?: boolean; clearLead?: boolean; fixed?: boolean } = {}) {
  const key = await crypto.subtle.importKey("raw", Buffer.from(keyHex, "hex"), "AES-CTR", false, ["encrypt"]);
  const tenc = box("tenc", n(0, 0x0108), Buffer.alloc(16));
  const codec = options.ec3 ? "ec-3" : options.ac4 ? "ac-4" : options.mpegh ? "mha1" : "fLaC";
  const config = options.ec3 ? box("dec3", Buffer.from([0,0,0,0,0,1,6])) : options.ac4 ? box("dac4", Buffer.from([0,0,0,0])) : options.mpegh ? box("mhaC", Buffer.from([0,0,0,0])) : box("dfLa", n(0), Buffer.alloc(38));
  const sinf = box("sinf", box("frma", Buffer.from(codec)), box("schm", n(0), Buffer.from("cenc"), n(0x10000)), box("schi", tenc));
  const entry = box("enca", Buffer.alloc(28), config, sinf);
  const entries = options.clearLead ? [box(codec, Buffer.alloc(28), config), entry] : [entry];
  const moov = box("moov", box("trak", box("mdia", box("minf", box("stbl", box("stsd", n(0, entries.length), ...entries))))));
  const clearMoov = Buffer.from(moov); clearMoov.write(codec, clearMoov.indexOf("enca")); clearMoov.write("free", clearMoov.indexOf("sinf"));
  const encrypted: Buffer[] = [], clear: Buffer[] = [];
  for (let segment = 0; segment < 2; segment++) {
    const plain = [Buffer.alloc(options.fixed ? 320 : 311, segment + 19), Buffer.alloc(options.fixed ? 320 : 173, segment + 88)];
    const ivs = plain.map((_, i) => { const iv = Buffer.alloc(16); iv.writeUInt32BE(segment * 10 + i + 1, 4); return iv; });
    const clearLead = options.clearLead && segment === 0;
    const cipher = await Promise.all(plain.map(async (p, i) => {
      if (clearLead) return p;
      const input = options.subsamples ? join(p.subarray(10,23),p.subarray(30)) : p;
      const encrypted = Buffer.from(await crypto.subtle.encrypt({ name: "AES-CTR", counter: ivs[i], length: 64 }, key, input));
      return options.subsamples ? join(p.subarray(0,10),encrypted.subarray(0,13),p.subarray(23,30),encrypted.subarray(13)) : encrypted;
    }));
    const senc = box("senc", n(options.subsamples ? 2 : 0, 2), ...ivs.map((iv,i) => options.subsamples ? join(iv.subarray(0,8),Buffer.from([0,2,0,10]),n(13),Buffer.from([0,7]),n(plain[i].length-30)) : iv.subarray(0,8)));
    const tfhd = box("tfhd", n(options.fixed ? 0x12 : 2, 1, options.clearLead && !clearLead ? 2 : 1), ...(options.fixed ? [n(320)] : []));
    const makeMoof = (offset: number) => box("moof", box("traf", tfhd, box("trun", n(options.fixed ? 1 : 0x201, 2, offset, ...(options.fixed ? [] : plain.map((p) => p.length)))), ...(clearLead ? [] : [senc])));
    const moof = makeMoof(makeMoof(0).length + 8), clearMoof = Buffer.from(moof);
    if (!clearLead) clearMoof.write("free", clearMoof.indexOf("senc"));
    encrypted.push(join(moof, box("mdat", ...cipher))); clear.push(join(clearMoof, box("mdat", ...plain)));
  }
  const sidx = box("sidx", n(0, 1, 44100, 0, 0, 2), ...encrypted.map((p) => n(p.length, 44100, 0)));
  const ftyp = box("ftyp", Buffer.from("iso6"), n(0));
  return { encrypted: join(ftyp, moov, sidx, ...encrypted), clear: join(ftyp, clearMoov, sidx, ...clear), firstSegment: ftyp.length + moov.length + sidx.length };
}

it("decodes fragmented CENC audio without changing indexed byte offsets", async () => {
  const f = await fixture();
  const index = amazonIndex(f.encrypted);
  assert.equal(index.ivSize, 8); assert.equal(index.segments[0], f.firstSegment);
  const input = new ReadableStream<Uint8Array>({ start(c) { for (let i = 0; i < f.encrypted.length; i += 37) c.enqueue(f.encrypted.subarray(i, i + 37)); c.close(); } });
  const output = Buffer.from(await new Response(input.pipeThrough(await amazonTransform(keyHex, 8))).arrayBuffer());
  assert.deepEqual(output, f.clear);
});

it("serves matching bytes when seeking inside the second encrypted fragment", async () => {
  const f = await fixture(), index = amazonIndex(f.encrypted);
  const requests: string[] = [];
  globalThis.fetch = (async (_url, init) => {
    const range = new Headers(init?.headers).get("Range")!; requests.push(range);
    const match = range.match(/^bytes=(\d+)-(\d+)$/)!;
    const start = Number(match[1]), end = Math.min(Number(match[2]), f.encrypted.length - 1);
    return new Response(f.encrypted.subarray(start, end + 1), { status: 206, headers: { "Content-Range": `bytes ${start}-${end}/${f.encrypted.length}`, "Content-Length": String(end - start + 1) } });
  }) as typeof fetch;
  const start = index.segments[1] + 200, end = start + 67;
  const r = await handleAmazonAudio(new Request("https://gateway.example/audio", { headers: { Range: `bytes=${start}-${end}` } }), "https://audio.example/track", keyHex);
  assert.equal(r.status, 206); assert.equal(r.headers.get("Content-Type"), "audio/mp4");
  assert.equal(r.headers.get("Content-Range"), `bytes ${start}-${end}/${f.encrypted.length}`);
  assert.deepEqual(Buffer.from(await r.arrayBuffer()), f.clear.subarray(start, end + 1));
  assert.equal(requests[1], `bytes=${index.segments[1]}-${f.encrypted.length - 1}`);
});

it("rejects an unsupported encryption scheme and truncated samples", async () => {
  const f = await fixture();
  const bad = Buffer.from(f.encrypted); bad.write("cbcs", bad.indexOf("cenc"));
  assert.throws(() => amazonIndex(bad), /Unsupported Amazon encryption/);
  const truncated = new Response(f.encrypted.subarray(0, -1)).body!;
  await assert.rejects(new Response(truncated.pipeThrough(await amazonTransform(keyHex, 8))).arrayBuffer(), /Truncated/);
});

it("preserves Atmos samples across clear lead, fixed sample sizes, and partial encryption", async () => {
  const f = await fixture({ ec3: true, subsamples: true, clearLead: true, fixed: true });
  const output = await new Response(new Response(f.encrypted).body!.pipeThrough(await amazonTransform(keyHex, 8))).arrayBuffer();
  assert.deepEqual(Buffer.from(output), f.clear);
  const index = amazonIndex(f.encrypted);
  assert.deepEqual(index.clearDescriptions, [1]);
  const start = index.segments[1];
  const seek = await new Response(new Response(f.encrypted.subarray(start)).body!.pipeThrough(await amazonTransform(keyHex, 8, index.clearDescriptions))).arrayBuffer();
  assert.deepEqual(Buffer.from(seek), f.clear.subarray(start));
});

it("requires an explicit EC-3 extension type A flag for Atmos", () => {
  assert.equal(ec3HasAtmos(Buffer.from([0,0,0,0,0,1,6])), true);
  assert.equal(ec3HasAtmos(Buffer.from([0,0,0,0,0,0,6])), false);
  assert.equal(ec3HasAtmos(Buffer.from([0,0,0,0,0])), false);
});

it("decrypts Amazon AC-4 CENC without changing indexed byte offsets", async () => {
  const f = await fixture({ ac4: true });
  const index = amazonIndex(f.encrypted);
  assert.equal(index.ivSize, 8);
  const input = new ReadableStream<Uint8Array>({ start(c) { for (let i = 0; i < f.encrypted.length; i += 37) c.enqueue(f.encrypted.subarray(i, i + 37)); c.close(); } });
  const output = Buffer.from(await new Response(input.pipeThrough(await amazonTransform(keyHex, 8))).arrayBuffer());
  assert.deepEqual(output, f.clear);
});

it("decrypts Amazon MPEG-H (mha1) CENC without changing indexed byte offsets", async () => {
  const f = await fixture({ mpegh: true });
  assert.equal(amazonHeaderIsMpegh(f.encrypted), true);
  const index = amazonIndex(f.encrypted);
  assert.equal(index.ivSize, 8);
  const input = new ReadableStream<Uint8Array>({ start(c) { for (let i = 0; i < f.encrypted.length; i += 37) c.enqueue(f.encrypted.subarray(i, i + 37)); c.close(); } });
  const output = Buffer.from(await new Response(input.pipeThrough(await amazonTransform(keyHex, 8))).arrayBuffer());
  assert.deepEqual(output, f.clear);
});
