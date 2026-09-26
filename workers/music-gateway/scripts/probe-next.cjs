/* End-to-end live check: seal wrapper -> POST -> decrypt -> probe stream URL */
const crypto = require("crypto");
const { subtle } = crypto;

const B64_SERVER_KEY =
  "BNV5TIGu2QTUN+bPqd4CAHiqDedLaixISDpxko/h6Q8e6vaeskKkfECeYJ2n6UehSbHxUjfLz4hUebG5w8HcBzg=";
const TOKEN = process.env.NEXT_REQUEST_TOKEN;
if (!TOKEN) {
  console.error("Missing NEXT_REQUEST_TOKEN env var (see .dev.vars.example). Refusing to embed the literal here.");
  process.exit(1);
}

async function seal(plaintext) {
  const serverRaw = Buffer.from(B64_SERVER_KEY, "base64");
  const serverKey = await subtle.importKey("raw", serverRaw, { name: "ECDH", namedCurve: "P-256" }, false, []);
  const kp = await subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, false, ["deriveBits"]);
  const shared = new Uint8Array(await subtle.deriveBits({ name: "ECDH", public: serverKey }, kp.privateKey, 256));
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const nonce = crypto.getRandomValues(new Uint8Array(12));
  const hkdfKey = await subtle.importKey("raw", shared, { name: "HKDF" }, false, ["deriveBits"]);
  const info = new TextEncoder().encode("spotiflac-req-v2");
  const keyBytes = new Uint8Array(await subtle.deriveBits({ name: "HKDF", hash: "SHA-256", salt, info }, hkdfKey, 256));
  const aes = await subtle.importKey("raw", keyBytes, { name: "AES-GCM" }, false, ["encrypt"]);
  const ct = new Uint8Array(await subtle.encrypt({ name: "AES-GCM", iv: nonce, tagLength: 128 }, aes, new TextEncoder().encode(plaintext)));
  const pub = new Uint8Array(await subtle.exportKey("raw", kp.publicKey));
  return { env: Buffer.concat([Buffer.from([0x02]), pub, salt, nonce, ct]), kp };
}

async function open(raw, kp) {
  const serverPub = raw.subarray(1, 66);
  const sSalt = raw.subarray(66, 82);
  const sNonce = raw.subarray(82, 94);
  const sCt = raw.subarray(94);
  const sKey = await subtle.importKey("raw", serverPub, { name: "ECDH", namedCurve: "P-256" }, false, []);
  const sShared = new Uint8Array(await subtle.deriveBits({ name: "ECDH", public: sKey }, kp.privateKey, 256));
  const sHkdf = await subtle.importKey("raw", sShared, { name: "HKDF" }, false, ["deriveBits"]);
  const sInfo = new TextEncoder().encode("spotiflac-resp-v2");
  const sKeyBytes = new Uint8Array(await subtle.deriveBits({ name: "HKDF", hash: "SHA-256", salt: sSalt, info: sInfo }, sHkdf, 256));
  const sAes = await subtle.importKey("raw", sKeyBytes, { name: "AES-GCM" }, false, ["decrypt"]);
  const plain = await subtle.decrypt({ name: "AES-GCM", iv: sNonce, tagLength: 128 }, sAes, sCt);
  return Buffer.from(plain).toString("utf8");
}

async function dl(base, trackId, quality) {
  const bodyText = JSON.stringify({
    token: TOKEN,
    body: { id: trackId, quality },
    ts: Math.floor(Date.now() / 1000),
  });
  const { env, kp } = await seal(bodyText);
  const r = await fetch(`${base}/api/dl`, {
    method: "POST",
    headers: {
      "Content-Type": "application/octet-stream",
      Accept: "application/octet-stream",
      "X-Installation-ID": "vanta-gateway-probe",
      "X-App-Version": "1.5.4",
    },
    body: env,
  });
  const raw = Buffer.from(await r.arrayBuffer());
  if (!(raw.length >= 110 && raw[0] === 2)) return { status: r.status, plain: raw.toString("utf8").slice(0, 200) };
  return { status: r.status, json: await open(raw, kp) };
}

(async () => {
  const q16 = await dl("https://a.qbzxn.qzz.io", "381791126", "16");
  console.log("QBZ 16:", q16.status, (q16.json || q16.plain || "").slice(0, 300));
  await new Promise((r) => setTimeout(r, 1500));
  const q24 = await dl("https://b.qbzxn.qzz.io", "381791126", "24");
  console.log("QBZ 24:", q24.status, (q24.json || q24.plain || "").slice(0, 300));

  const urlStr = q16.json ? JSON.parse(q16.json).url : null;
  if (urlStr) {
    const head = await fetch(urlStr, { method: "HEAD" });
    console.log("STREAM HEAD:", head.status, "content-length:", head.headers.get("content-length"), "content-type:", head.headers.get("content-type"));
    const range = await fetch(urlStr, { headers: { Range: "bytes=0-3" } });
    const bytes = Buffer.from(await range.arrayBuffer());
    console.log("STREAM RANGE:", range.status, "magic:", bytes.toString("latin1").slice(0, 4));
  }
})();
