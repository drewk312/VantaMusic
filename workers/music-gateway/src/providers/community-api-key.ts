const SEED_PARTS = ["spotif", "lac:co", "mmunity:apikey:v1"];
const AAD = new TextEncoder().encode("spotiflac|community|apikey|v1");

const NONCE = new Uint8Array([
  0x20, 0x5c, 0x92, 0x4b, 0x61, 0xc2, 0x79, 0xd3, 0xea, 0x5d, 0xdd, 0xd4,
]);
const CIPHERTEXT = new Uint8Array([
  0x51, 0x0b, 0x26, 0xaf, 0xac, 0x6f, 0xf6, 0x41, 0x79, 0xde, 0x8d, 0x36, 0x83, 0x46, 0xb5, 0xd5,
  0x96, 0xef, 0xad, 0xed, 0xe0, 0xd0, 0xc7, 0xc2, 0x90, 0x01, 0x50, 0x5f, 0x55, 0x59, 0x9f, 0xac,
  0x1f, 0xd0, 0x70, 0x18, 0x91, 0x4f, 0x7a, 0x32,
]);
const TAG = new Uint8Array([
  0x56, 0xb0, 0x28, 0x68, 0x9f, 0x39, 0x0d, 0xbc, 0xc0, 0x8e, 0xfb, 0x52, 0x3a, 0xd6, 0x18, 0xae,
]);

let keyPromise: Promise<CryptoKey> | null = null;
let cachedApiKey: string | null | undefined;

async function communityKey(): Promise<CryptoKey> {
  if (!keyPromise) {
    keyPromise = (async () => {
      const parts = SEED_PARTS.map((p) => new TextEncoder().encode(p));
      const total = parts.reduce((n, p) => n + p.length, 0);
      const merged = new Uint8Array(total);
      let offset = 0;
      for (const part of parts) {
        merged.set(part, offset);
        offset += part.length;
      }
      const hash = await crypto.subtle.digest("SHA-256", merged);
      return crypto.subtle.importKey("raw", hash, { name: "AES-GCM" }, false, ["decrypt"]);
    })();
  }
  return keyPromise;
}

/** SpotiFLAC community relay auth — zero-config, no user API key. */
export async function getCommunityApiKey(): Promise<string | null> {
  if (cachedApiKey !== undefined) return cachedApiKey;
  try {
    const key = await communityKey();
    const sealed = new Uint8Array(CIPHERTEXT.length + TAG.length);
    sealed.set(CIPHERTEXT);
    sealed.set(TAG, CIPHERTEXT.length);
    const plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: NONCE, additionalData: AAD }, key, sealed);
    cachedApiKey = new TextDecoder().decode(plain);
    return cachedApiKey;
  } catch {
    cachedApiKey = null;
    return null;
  }
}
