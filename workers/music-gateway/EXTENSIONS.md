# SpotiFLAC sources in VANTA

The supplied registry is a reviewed reference snapshot, not a download catalog.
The gateway adapts extension protocols to VANTA's existing `/api/dl` playback
contract. The Android app can stream these responses through its normal player;
installing `.sflx` files or downloading songs is not required.

| Extension | Gateway integration |
| --- | --- |
| Deezer 1.3.4 | Signed session and ticket, incremental audio decoding, FLAC/MP3 byte-range streaming. Live FLAC and seeking verified. |
| Amazon 2.3.3 | Public catalog search, connected signed session, FLAC and verified E-AC-3/JOC Atmos streaming with seeking. FFmpeg decoded both 24-bit FLAC and Dolby Digital Plus + Dolby Atmos, including encrypted segments. |
| Qobuz 1.2.10 | Extension session connected with automatic refresh. The upstream audio service returned timeouts/unavailable; exact ISRC and catalog matching now provide working fallback audio for Qobuz selections. |
| Tidal 1.2.2 | Extension session connected with automatic refresh. Full FLAC/DASH verified. Its Atmos endpoint returned upstream errors in testing; matching Amazon Atmos is used when available. |
| SoundCloud 1.0.8 | Public progressive MP3/Opus and plain HLS adapter enabled in search. Live public MP3 playback and Range requests verified. Availability varies by track; protected HLS is unsupported. |
| YouTube Music 2.4.1 | VANTA's existing native Android YouTube Music provider supplies playback. The extension's old relay returned 404 and is not used. |
| Spotify 1.10.2 | Anonymous extension web bootstrap and GraphQL catalog search enabled. No client credentials required. Spotify selection to matching Amazon Atmos was verified. The extension supplies metadata, not Spotify audio. |
| Apple Music 1.4.4 | Existing Apple catalog metadata and recording resolution; this extension is not a direct audio source. |

## Sessions

From `workers/music-gateway`, run:

```powershell
node scripts/setup-extension-sessions.mjs amazon deezer
```

The local setup page opens the provider's verification flow, exchanges the grant,
and saves provider-specific installation IDs and session credentials as Worker
secrets. The script also accepts `qobuz` and `tidal`. No provider credentials are
printed or committed. Temporary secret files are removed after upload.

The `ExtensionSessions` Durable Object stores refreshed sessions independently
for each provider and schedules refresh alarms before expiration. Revoked or
unrefreshable sessions require the setup flow again. `src/worker.ts` exports the
Worker and Durable Object, and `wrangler.toml` includes its SQLite migration.

`EXTENSION_PROXY_SECRET` is a random operator-managed Worker secret. It encrypts
short-lived audio tickets so upstream URLs and audio keys are not exposed in
playback responses. `/audio/extension` accepts GET/HEAD and authenticated tickets,
supports single start/end byte ranges, and does not buffer complete tracks.
Amazon preserves MP4 offsets while decoding individual audio samples, including clear lead segments and subsample encryption; Deezer
aligns encrypted blocks and trims output to the requested range. Both cancel
upstream work when the requested range has been delivered.

Amazon's streaming decoder supports single-track fragmented CENC FLAC and E-AC-3 with
8- or 16-byte per-sample IVs and a segment index. Other encryption layouts and
AC-4 conversion are not supported by this adapter. Existing authenticated
Atmos backends remain available. Audio quality is reported from actual codec
and bit-depth evidence.

## Verification

```powershell
npm run typecheck
npm test
node node_modules/wrangler/bin/wrangler.js deploy --dry-run
```

Tests cover chunked audio decoding, byte-exact seeking, malformed tickets,
unsupported encryption, truncated samples, public catalog bootstraps, late recording matches, and the full `/v2` signing path.
Live tests use `/api/dl` followed by bounded Range requests to its playback URL.
Do not parse `/stream/:id` as JSON: success redirects to audio.

Source snapshot: https://raw.githubusercontent.com/spotiflacapp/spotiflac-extension/main/registry.json.
Package hashes were checked before adapting their protocols. Upstream JavaScript
is not evaluated dynamically by the Worker.

Live checks also verified Amazon and Spotify search-to-Atmos playback, Atmos decoding beyond the clear lead, and byte-exact seeking inside encrypted audio. Spotify, Deezer, and Tidal catalog selections resolved to the same Amazon Atmos recording. The tested song was BIRDS OF A FEATHER (Amazon B0CZW15FZZ).
