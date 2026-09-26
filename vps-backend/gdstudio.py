"""GDStudio stream resolver — community fallback for multiple services."""
import hashlib
from typing import Optional

import httpx

GDSTUDIO_API = "https://music.gdstudio.xyz/api.php"
GDSTUDIO_TIME = "https://music.gdstudio.xyz/time"


class GDStudioClient:
    def __init__(self, client: httpx.AsyncClient):
        self.client = client

    async def search(self, query: str, limit: int = 30) -> list[dict]:
        return []

    async def get_stream(self, track_id: str, quality: str = "lossless") -> tuple[Optional[str], Optional[str]]:
        service = "qobuz"
        br = "740" if quality in ("lossless", "flac", "16bit") else "999"
        if track_id.startswith("tidal:"):
            service = "tidal"
            track_id = track_id.removeprefix("tidal:")
        try:
            time_resp = await self.client.get(GDSTUDIO_TIME, timeout=5.0)
            ts9 = time_resp.text.strip()[:9]
            base = f"music.gdstudio.xyz|20260510|{ts9}|{track_id}"
            sig = hashlib.md5(base.encode()).hexdigest().upper()[-8:]
            resp = await self.client.post(
                GDSTUDIO_API,
                data={"types": "url", "id": track_id, "source": service, "br": br, "s": sig},
                timeout=10.0,
            )
            if resp.status_code == 200:
                data = resp.json()
                url = data.get("url") or (data.get("data", {}) or {}).get("url")
                if url:
                    fmt = "audio/flac" if br == "740" else "audio/aac"
                    return url, fmt
        except Exception:
            pass
        return None, None
