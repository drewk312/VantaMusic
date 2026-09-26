"""Authenticated VANTA operator backend for licensed music-source adapters."""
import asyncio
import hmac
import os
import time
from contextlib import asynccontextmanager
from typing import Any, Optional

import httpx
from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.responses import JSONResponse, RedirectResponse

from gdstudio import GDStudioClient
from qobuz_api import QobuzClient
from tidal_community import TidalCommunityClient

PORT = int(os.environ.get("PORT", "8080"))
ALLOW_COMMUNITY_FALLBACKS = os.environ.get("ALLOW_COMMUNITY_FALLBACKS", "false").lower() == "true"

qobuz: Optional[QobuzClient] = None
tidal: Optional[TidalCommunityClient] = None
gdstudio: Optional[GDStudioClient] = None
http_client: Optional[httpx.AsyncClient] = None


def backend_api_key() -> str:
    return (os.environ.get("VANTA_BACKEND_API_KEY") or os.environ.get("VANTA_SECRET") or "").strip()


@asynccontextmanager
async def lifespan(_: FastAPI):
    global qobuz, tidal, gdstudio, http_client
    if not backend_api_key():
        raise RuntimeError("VANTA_BACKEND_API_KEY is required; the backend will not start with a default secret")

    http_client = httpx.AsyncClient(timeout=12.0, follow_redirects=True)
    qobuz = QobuzClient(http_client)
    qobuz_token = os.environ.get("QOBUZ_AUTH_TOKEN", "").strip()
    if qobuz_token:
        qobuz.set_auth_token(qobuz_token)
    await qobuz.initialize()

    # Community adapters are explicitly opt-in and never claim licensed or
    # Atmos capability. Native Tidal/Atmos remains behind the gateway's
    # authenticated hifi-api adapter and Widevine proxy.
    tidal = TidalCommunityClient(http_client) if ALLOW_COMMUNITY_FALLBACKS else None
    gdstudio = GDStudioClient(http_client) if ALLOW_COMMUNITY_FALLBACKS else None
    try:
        yield
    finally:
        await http_client.aclose()


app = FastAPI(
    title="VANTA Music Operator Backend",
    version="2.0.0",
    lifespan=lifespan,
    docs_url=None,
    redoc_url=None,
    openapi_url=None,
)


@app.middleware("http")
async def require_operator_auth(request: Request, call_next):
    if request.url.path in {"/health", "/manifest.json"}:
        return await call_next(request)

    expected = backend_api_key()
    authorization = request.headers.get("Authorization", "")
    bearer = authorization[7:].strip() if authorization.lower().startswith("bearer ") else ""
    provided = request.headers.get("X-Api-Key", "").strip() or request.headers.get("X-Vanta-Secret", "").strip() or bearer
    if not expected or not provided or not hmac.compare_digest(provided.encode(), expected.encode()):
        return JSONResponse({"error": "unauthorized"}, status_code=401)
    return await call_next(request)


@app.get("/health")
async def health():
    return {
        "status": "ok",
        "version": "2.0.0",
        "providers": {
            "qobuz": bool(qobuz and qobuz.is_ready() and qobuz.has_user_session()),
            "communityFallbacks": ALLOW_COMMUNITY_FALLBACKS,
        },
        "ts": int(time.time()),
    }


@app.get("/manifest.json")
async def manifest():
    return {
        "id": "com.vanta.operator-backend",
        "name": "VANTA Music Operator Backend",
        "version": "2.0.0",
        "contract": "vanta-stream-v1",
    }


@app.get("/search")
async def search(q: str = Query(..., min_length=1, max_length=200), limit: int = Query(30, ge=1, le=50)):
    tasks: list[tuple[str, Any]] = []
    if qobuz and qobuz.is_ready():
        tasks.append(("qobuz", qobuz.search(q, limit)))
    if ALLOW_COMMUNITY_FALLBACKS and tidal and gdstudio:
        tasks.extend([("tidal", tidal.search(q, limit)), ("gdstudio", gdstudio.search(q, limit))])

    done = await asyncio.gather(*(task for _, task in tasks), return_exceptions=True)
    results: list[dict[str, Any]] = []
    for (source, _), result in zip(tasks, done):
        if isinstance(result, BaseException):
            continue
        for track in result:
            track["source"] = source
            results.append(track)

    seen: set[tuple[str, str, str]] = set()
    deduped: list[dict[str, Any]] = []
    for track in results:
        key = (
            str(track.get("isrc", "")),
            str(track.get("title", "")).lower().strip(),
            str(track.get("artist", "")).lower().strip(),
        )
        if key not in seen:
            seen.add(key)
            deduped.append(track)
    return {"results": deduped[:limit]}


@app.get("/search/qobuz")
async def search_qobuz(q: str = Query(..., min_length=1, max_length=200), limit: int = Query(30, ge=1, le=50)):
    if not qobuz or not qobuz.is_ready():
        raise HTTPException(503, "Qobuz is not configured")
    return {"results": await qobuz.search(q, limit)}


async def resolve_stream_data(provider: str, track_id: str, quality: str) -> dict[str, Any]:
    service = provider.strip().lower()
    clean_id = track_id.strip()
    if not clean_id or len(clean_id) > 256:
        raise HTTPException(400, "Invalid track id")

    if service == "qobuz":
        if not qobuz or not qobuz.has_user_session():
            raise HTTPException(503, "Qobuz licensed session is not configured")
        result = await qobuz.get_stream(clean_id, quality)
        if result:
            return result
    elif service in {"tidal", "gdstudio"} and ALLOW_COMMUNITY_FALLBACKS:
        if quality.lower() in {"atmos", "dolby_atmos", "eac3_joc"}:
            raise HTTPException(404, "Community adapters do not provide verified Atmos")
        if service == "tidal" and tidal:
            url = await tidal.get_stream_url(clean_id, quality)
            if url:
                return {
                    "provider": "tidal",
                    "url": url,
                    "format": "m4a",
                    "mimeType": "audio/mp4",
                    "quality": quality,
                    "isDolbyAtmos": False,
                }
        if service == "gdstudio" and gdstudio:
            url, mime_type = await gdstudio.get_stream(clean_id, quality)
            if url:
                return {
                    "provider": "gdstudio",
                    "url": url,
                    "format": mime_type,
                    "mimeType": mime_type,
                    "quality": quality,
                    "isDolbyAtmos": False,
                }
    elif service in {"tidal", "amazon", "deezer", "pandora"}:
        raise HTTPException(503, f"{service} licensed adapter is not configured on this backend")
    else:
        raise HTTPException(400, f"Unknown provider: {service}")

    raise HTTPException(404, f"No full stream found for {service}:{clean_id}")


@app.get("/v1/streams/{provider}/{track_id}")
async def v1_stream(provider: str, track_id: str, quality: str = Query("24", max_length=32)):
    return await resolve_stream_data(provider, track_id, quality)


@app.get("/resolve")
async def resolve(track_id: str = Query(...), service: str = Query("qobuz"), quality: str = Query("24")):
    return await resolve_stream_data(service, track_id, quality)


@app.get("/stream/{track_id}")
async def stream_redirect(track_id: str, service: str = Query("qobuz"), quality: str = Query("24")):
    result = await resolve_stream_data(service, track_id, quality)
    return RedirectResponse(url=result["url"], status_code=302)


@app.get("/search/{query}")
async def search_path(query: str, limit: int = Query(30, ge=1, le=50)):
    return await search(q=query, limit=limit)


@app.post("/api/dl")
async def api_dl(body: dict[str, Any]):
    track_id = str(body.get("id", ""))
    quality = str(body.get("quality", "24"))
    service = str(body.get("service") or body.get("provider") or "qobuz")
    return await resolve_stream_data(service, track_id, quality)


@app.get("/{track_id}")
async def upstream_resolve(
    track_id: str,
    quality: str = Query("24"),
    provider: str = Query("qobuz"),
    service: Optional[str] = Query(None),
):
    return await resolve_stream_data(service or provider, track_id, quality)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=PORT)
