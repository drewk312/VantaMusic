"""Tidal community proxy client — uses encrypted community endpoints."""
import hashlib
import json
from typing import Optional

import httpx

COMMUNITY_GATEWAY = "https://qobuz-tidal-eclipse.cyrusna29.workers.dev"
GDSTUDIO_URL = "https://music.gdstudio.xyz"

TIDAL_SEARCH_URL = "https://api.tidal.com/v1/search/albums"
TIDAL_TRACK_URL = "https://api.tidal.com/v1/tracks"


class TidalCommunityClient:
    def __init__(self, client: httpx.AsyncClient):
        self.client = client
        self.gateways = [
            COMMUNITY_GATEWAY,
        ]

    async def search(self, query: str, limit: int = 30) -> list[dict]:
        for gw in self.gateways:
            try:
                resp = await self.client.get(
                    f"{gw}/search",
                    params={"q": query, "limit": str(limit)},
                    timeout=8.0,
                )
                if resp.status_code == 200:
                    data = resp.json()
                    tracks = data.get("results", data.get("tracks", []))
                    results = []
                    for t in tracks[:limit]:
                        results.append({
                            "id": str(t.get("id", t.get("trackId", ""))),
                            "title": t.get("title", t.get("name", "")),
                            "artist": self._extract_artist(t),
                            "album": t.get("albumTitle", t.get("album", {}).get("title", "")),
                            "duration": t.get("duration", 0),
                            "isrc": t.get("isrc", ""),
                            "artwork": t.get("image", t.get("album", {}).get("cover", "")),
                            "quality": "lossless",
                            "service": "tidal",
                        })
                    return results
            except Exception:
                continue
        return await self._gdstudio_search(query, limit)

    async def get_stream_url(self, track_id: str, quality: str = "lossless") -> Optional[str]:
        q = "HI_RES" if quality in ("hi-res", "hi_res", "hi_res_lossless") else "HI_RES"
        for gw in self.gateways:
            try:
                resp = await self.client.post(
                    f"{gw}/api/dl",
                    json={"id": track_id, "quality": q, "service": "tidal"},
                    headers={"Content-Type": "application/json", "Accept": "application/json"},
                    timeout=10.0,
                )
                if resp.status_code == 200:
                    data = resp.json()
                    url = data.get("url")
                    if url:
                        return url
            except Exception:
                continue
        return await self._gdstudio_stream(track_id, quality)

    async def _gdstudio_search(self, query: str, limit: int) -> list[dict]:
        return []

    async def _gdstudio_stream(self, track_id: str, quality: str) -> Optional[str]:
        try:
            time_resp = await self.client.get(f"{GDSTUDIO_URL}/time", timeout=5.0)
            ts9 = time_resp.text.strip()[:9]
            encoded_id = httpx.URL(str(track_id)).path
            base = f"music.gdstudio.xyz|20260510|{ts9}|{track_id}"
            import hashlib
            sig = hashlib.md5(base.encode()).hexdigest().upper()[-8:]
            resp = await self.client.post(
                f"{GDSTUDIO_URL}/api.php",
                data={"types": "url", "id": track_id, "source": "tidal", "br": "999", "s": sig},
                timeout=10.0,
            )
            if resp.status_code == 200:
                data = resp.json()
                url = data.get("url") or data.get("data", {}).get("url")
                if url:
                    return url
        except Exception:
            pass
        return None

    def _extract_artist(self, track: dict) -> str:
        artists = track.get("artists", [])
        if artists:
            return artists[0].get("name", "")
        return track.get("artistName", track.get("artist", ""))
