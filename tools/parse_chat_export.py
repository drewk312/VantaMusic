import json
import re
import base64
import zlib
import sys

def decode_blob(s: str) -> str:
    if not s:
        return ""
    # Try plain JSON/text first
    if s.startswith("{") or s.startswith("[") or "playback" in s.lower():
        return s
    try:
        raw = base64.b64decode(s)
        try:
            return zlib.decompress(raw).decode("utf-8", errors="replace")
        except Exception:
            return raw.decode("utf-8", errors="replace")
    except Exception:
        return s

def search_export(path: str, terms: list[str], max_hits=8):
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        data = json.load(f)
    name = data.get("name", path)
    blobs = data.get("blobs", {})
    hits = {t: [] for t in terms}
    decoded_count = 0
    for blob in blobs.values():
        text = decode_blob(blob)
        if len(text) > 50:
            decoded_count += 1
        lower = text.lower()
        for t in terms:
            if len(hits[t]) >= max_hits:
                continue
            if t.lower() in lower:
                for m in re.finditer(re.escape(t), text, re.I):
                    start = max(0, m.start() - 100)
                    end = min(len(text), m.end() + 200)
                    snippet = re.sub(r"\s+", " ", text[start:end])
                    hits[t].append(snippet[:350])
                    break
    print(f"\n=== {name} ({decoded_count} decoded blobs) ===")
    for t, snippets in hits.items():
        if snippets:
            print(f"\n[{t}]")
            for s in snippets[:3]:
                print(" ", s)

if __name__ == "__main__":
    lossless_terms = [
        "instrumental", "api/dl", "tidal", "deezer", "gateway", "qobuz",
        "lossless", "stream", "Blinding Lights", "wrong track",
    ]
    audit_terms = [
        "playback", "resolve", "SourceRegistry", "broken", "horrible",
        "ExternalSource", "playSourceResult",
    ]
    ytm_terms = ["InnerTube", "YTMusic", "playback error", "resolveStream"]
    for p in sys.argv[1:]:
        if "lossless" in p.lower():
            search_export(p, lossless_terms)
        elif "audit" in p.lower():
            search_export(p, audit_terms)
        elif "ytmusic" in p.lower():
            search_export(p, ytm_terms)
        else:
            search_export(p, ["lyrics", "NowPlaying", "design"])
