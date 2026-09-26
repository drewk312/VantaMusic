"""Qobuz API client for authenticated, full-track stream resolution."""
import hashlib
import os
import re
import time
from typing import Any, Optional
from urllib.parse import parse_qs, urlparse

import httpx

SEARCH_URL = "https://www.qobuz.com/api.json/0.2/track/search"
STREAM_URL = "https://www.qobuz.com/api.json/0.2/track/getFileUrl"
CREDENTIAL_URL = "https://open.qobuz.com/track/1"

QUALITY_FORMATS = {
    "hi_res": [27, 7, 6],
    "hi-res": [27, 7, 6],
    "hi_res_lossless": [27, 7, 6],
    "24": [27, 7, 6],
    "lossless": [6],
    "16": [6],
    "flac": [6],
    "16bit": [6],
    "mp3": [5],
    "low": [5],
}


class QobuzClient:
    def __init__(self, client: httpx.AsyncClient):
        self.client = client
        self.app_id = os.environ.get("QOBUZ_APP_ID", "").strip()
        self.app_secret = os.environ.get("QOBUZ_APP_SECRET", "").strip()
        self._ready = bool(self.app_id and self.app_secret)
        self._user_auth_token: Optional[str] = None

    def is_ready(self) -> bool:
        return self._ready

    def has_user_session(self) -> bool:
        return self._ready and bool(self._user_auth_token)

    def set_auth_token(self, token: str):
        self._user_auth_token = token.strip() or None

    async def initialize(self):
        if self._ready:
            return
        creds = await self._scrape_credentials()
        if creds:
            self.app_id, self.app_secret = creds
            self._ready = True

    async def _scrape_credentials(self) -> Optional[tuple[str, str]]:
        try:
            response = await self.client.get(CREDENTIAL_URL, headers={"User-Agent": "Mozilla/5.0"})
            response.raise_for_status()
            js_match = re.search(r'<script[^>]+src="([^"]+/js/main\.js)"', response.text)
            if not js_match:
                return None
            js_url = js_match.group(1)
            if js_url.startswith("/"):
                js_url = "https://open.qobuz.com" + js_url
            script = await self.client.get(js_url, headers={"User-Agent": "Mozilla/5.0"})
            script.raise_for_status()
            match = re.search(r'app_id:"(\d{9})",app_secret:"([a-f0-9]{32})"', script.text)
            return (match.group(1), match.group(2)) if match else None
        except httpx.HTTPError:
            return None

    def _sign(self, path: str, params: dict[str, str]) -> dict[str, str]:
        if not self._ready:
            raise RuntimeError("Qobuz application credentials are unavailable")
        timestamp = str(int(time.time()))
        path_norm = path.strip("/").replace("/", "")
        keys = sorted(key for key in params if key not in {"app_id", "request_ts", "request_sig"})
        payload = path_norm + "".join(key + params[key] for key in keys) + timestamp + self.app_secret
        signature = hashlib.md5(payload.encode(), usedforsecurity=False).hexdigest()
        return {
            **params,
            "app_id": self.app_id,
            "request_ts": timestamp,
            "request_sig": signature,
        }

    async def search(self, query: str, limit: int = 30) -> list[dict[str, Any]]:
        if not self._ready:
            return []
        params = self._sign("track/search", {"query": query, "limit": str(limit)})
        try:
            response = await self.client.get(SEARCH_URL, params=params, headers=self._headers())
            response.raise_for_status()
            data = response.json()
            raw_tracks = data.get("tracks", {})
            tracks = raw_tracks.get("items", []) if isinstance(raw_tracks, dict) else raw_tracks
            return [self._map_track(track) for track in tracks[:limit]]
        except (httpx.HTTPError, ValueError, TypeError):
            return []

    async def get_stream_url(self, track_id: str, quality: str = "24") -> Optional[str]:
        stream = await self.get_stream(track_id, quality)
        return str(stream["url"]) if stream else None

    async def get_stream(self, track_id: str, quality: str = "24") -> Optional[dict[str, Any]]:
        # A user session is required for full files. Public getFileUrl responses
        # are previews and must never be returned as playable tracks.
        if not self.has_user_session():
            return None
        format_ids = QUALITY_FORMATS.get(quality.strip().lower(), QUALITY_FORMATS["24"])
        for format_id in format_ids:
            result = await self._get_file_url(track_id, format_id)
            if result:
                return result
        return None

    async def _get_file_url(self, track_id: str, format_id: int) -> Optional[dict[str, Any]]:
        params = {
            "track_id": track_id,
            "format_id": str(format_id),
            "intent": "stream",
            "user_auth_token": self._user_auth_token or "",
        }
        try:
            response = await self.client.get(STREAM_URL, params=self._sign("track/getFileUrl", params), headers=self._headers())
            if response.status_code != 200:
                return None
            data = response.json()
            url = str(data.get("url") or data.get("file_url") or "").strip()
            if not self._is_full_https_stream(url):
                return None
            bit_depth = self._positive_int(data.get("bit_depth"))
            sample_rate_hz = self._sample_rate_hz(data.get("sampling_rate"))
            mime_type = str(data.get("mime_type") or ("audio/mpeg" if format_id == 5 else "audio/flac"))
            parsed_query = parse_qs(urlparse(url).query)
            expires_at = self._positive_int((parsed_query.get("etsp") or parsed_query.get("expires") or [None])[0])
            hi_res = bool((bit_depth and bit_depth > 16) or (sample_rate_hz and sample_rate_hz > 44_100))
            return {
                "provider": "qobuz",
                "url": url,
                "format": "mp3" if format_id == 5 else "flac",
                "mimeType": mime_type,
                "quality": "Hi-Res FLAC" if hi_res else ("MP3 320" if format_id == 5 else "16-bit / 44.1 kHz FLAC"),
                "bitDepth": bit_depth,
                "sampleRateHz": sample_rate_hz,
                "channelCount": 2,
                "expiresAt": expires_at,
                "isHiRes": hi_res,
                "isDolbyAtmos": False,
            }
        except (httpx.HTTPError, ValueError, TypeError):
            return None

    def _headers(self) -> dict[str, str]:
        return {
            "User-Agent": "Mozilla/5.0",
            "Accept": "application/json",
            "X-App-Id": self.app_id,
        }

    @staticmethod
    def _is_full_https_stream(url: str) -> bool:
        try:
            parsed = urlparse(url)
            if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
                return False
            query = {key.lower() for key in parse_qs(parsed.query)}
            return not bool(query & {"range", "preview", "sample", "range_start", "range_end"})
        except ValueError:
            return False

    @staticmethod
    def _positive_int(value: Any) -> Optional[int]:
        try:
            parsed = int(float(value))
            return parsed if parsed > 0 else None
        except (TypeError, ValueError):
            return None

    @classmethod
    def _sample_rate_hz(cls, value: Any) -> Optional[int]:
        rate = cls._positive_int(value)
        if not rate:
            return None
        return rate * 1000 if rate < 1000 else rate

    def _map_track(self, track: dict[str, Any]) -> dict[str, Any]:
        performer = track.get("performer", {}) or {}
        album = track.get("album", {}) or {}
        return {
            "id": str(track.get("id", "")),
            "title": track.get("title", ""),
            "artist": performer.get("name", ""),
            "album": album.get("title", ""),
            "duration": track.get("duration", 0),
            "isrc": track.get("isrc", ""),
            "artwork": self._get_artwork(track),
            "quality": "hi-res" if track.get("hires", False) else "lossless",
            "service": "qobuz",
        }

    @staticmethod
    def _get_artwork(track: dict[str, Any]) -> str:
        cover = (track.get("album", {}) or {}).get("cover", {}) or {}
        for size in ("large", "medium", "small", "original"):
            if cover.get(size):
                return str(cover[size])
        return ""
