import type { StreamResult } from "../types";

/** Accept only public HTTPS destinations before caching, returning, or redirecting. */
export function normalizePublicHttpsUrl(value: string | null | undefined): string | null {
  if (!value?.trim()) return null;
  try {
    const parsed = new URL(value.trim());
    if (parsed.protocol !== "https:" || parsed.username || parsed.password) return null;
    if (isPrivateHostname(parsed.hostname)) return null;
    return parsed.toString();
  } catch {
    return null;
  }
}

export function normalizeStreamResult(result: StreamResult | null | undefined): StreamResult | null {
  if (!result) return null;
  const safeUrl = normalizePublicHttpsUrl(result.streamUrl ?? result.url);
  if (!safeUrl) return null;
  return { ...result, url: safeUrl, streamUrl: safeUrl };
}

function isPrivateHostname(rawHostname: string): boolean {
  const hostname = rawHostname.toLowerCase().replace(/^\[|\]$/g, "").replace(/\.$/, "");
  if (!hostname) return true;
  if (
    hostname === "localhost" ||
    hostname.endsWith(".localhost") ||
    hostname.endsWith(".local") ||
    hostname.endsWith(".internal") ||
    hostname === "metadata.google.internal"
  ) {
    return true;
  }

  if (hostname.includes(":")) {
    return hostname === "::" || hostname === "::1" || hostname.startsWith("fc") ||
      hostname.startsWith("fd") || hostname.startsWith("fe8") || hostname.startsWith("fe9") ||
      hostname.startsWith("fea") || hostname.startsWith("feb");
  }

  const octets = hostname.split(".").map((part) => Number(part));
  if (octets.length !== 4 || octets.some((part) => !Number.isInteger(part) || part < 0 || part > 255)) {
    return false;
  }
  const [a, b] = octets;
  return a === 0 || a === 10 || a === 127 ||
    (a === 100 && b >= 64 && b <= 127) ||
    (a === 169 && b === 254) ||
    (a === 172 && b >= 16 && b <= 31) ||
    (a === 192 && b === 168) ||
    a >= 224;
}
