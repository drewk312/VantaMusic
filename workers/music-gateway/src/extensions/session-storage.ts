import type { CommunitySession } from "../providers/community-session";

export interface SealedSession { version: 1; iv: string; ciphertext: string }
interface SessionStorage {
  get<T>(key: string): Promise<T | undefined>;
  put<T>(key: string, value: T): Promise<unknown>;
  delete(key: string): Promise<unknown>;
}
const encoder = new TextEncoder();
const context = encoder.encode("vanta:extension-session:v1");

async function sessionKey(secret: string): Promise<CryptoKey> {
  if (secret.trim().length < 32) throw new Error("Session encryption secret must contain at least 32 characters");
  const digest = await crypto.subtle.digest("SHA-256", encoder.encode(`vanta:session-storage:v1:${secret}`));
  return crypto.subtle.importKey("raw", digest, "AES-GCM", false, ["encrypt", "decrypt"]);
}
function encode(bytes: Uint8Array): string { return btoa(String.fromCharCode(...bytes)); }
function decode(value: string): Uint8Array<ArrayBuffer> { return Uint8Array.from(atob(value), c => c.charCodeAt(0)); }

export async function sealSession(session: CommunitySession, secret: string): Promise<SealedSession> {
  const key = await sessionKey(secret);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = await crypto.subtle.encrypt({ name: "AES-GCM", iv, additionalData: context }, key,
    encoder.encode(JSON.stringify(session)));
  return { version: 1, iv: encode(iv), ciphertext: encode(new Uint8Array(ciphertext)) };
}
export async function unsealSession(sealed: SealedSession, secret: string): Promise<CommunitySession> {
  if (sealed.version !== 1) throw new Error("Unsupported session envelope");
  const clear = await crypto.subtle.decrypt({ name: "AES-GCM", iv: decode(sealed.iv), additionalData: context },
    await sessionKey(secret), decode(sealed.ciphertext));
  return JSON.parse(new TextDecoder().decode(clear)) as CommunitySession;
}
export async function readStoredSession(storage: SessionStorage, secret: string): Promise<CommunitySession | undefined> {
  const sealed = await storage.get<SealedSession>("session:sealed:v1");
  if (sealed) return unsealSession(sealed, secret);
  const legacy = await storage.get<CommunitySession>("session");
  if (legacy) await writeStoredSession(storage, legacy, secret);
  return legacy;
}
export async function writeStoredSession(storage: SessionStorage, session: CommunitySession, secret: string): Promise<void> {
  // Write the authenticated envelope successfully before removing the legacy record.
  await storage.put("session:sealed:v1", await sealSession(session, secret));
  await storage.delete("session");
}
