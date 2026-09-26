/* Probe the Tidal Next shards with the wrapper body */
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
  if (!(raw.length >= 110 && raw[0] === 2)) return { status: r.status, plain: raw.toString("utf8").slice(0, 300) };
  return { status: r.status, json: await open(raw, kp) };
}

(async () => {
  for (const q of ["16", "24", "atmos"]) {
    const res = await dl("https://a.tdlxn.qzz.io", "274457426", q);
    console.log("TDL", q, ":", res.status, (res.json || res.plain || "").slice(0, 500));
    await new Promise((r) => setTimeout(r, 1500));
  }
  const amz = await dl("https://a.amzxn.qzz.io", "B0BGDLHBK7", "16");
  console.log("AMZ:", amz.status, (amz.json || amz.plain || "").slice(0, 500));
})();