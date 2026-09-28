<div align="center">

<img src="./screenshots/vanta_logo.jpg" width="150" alt="VANTA logo" />

# VANTA

A free Android music player for people who care how their music sounds.

[![Download](https://img.shields.io/badge/Download-latest_release-E8C99B?style=for-the-badge&labelColor=101113)](https://github.com/drewk312/VantaMusic/releases/latest)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0+-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/drewk312/VantaMusic/releases/latest)
[![Discord](https://img.shields.io/badge/Discord-VANTA_HQ-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/vN6ztK6m6g)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-support-FF5E5B?style=for-the-badge&logo=kofi&logoColor=white)](https://ko-fi.com/drewk312)

</div>

<p align="center">
  <img src="./screenshots/v100_home.png" width="19%" alt="Home" />
  <img src="./screenshots/v100_search.png" width="19%" alt="Search" />
  <img src="./screenshots/v100_player.png" width="19%" alt="Now playing" />
  <img src="./screenshots/v100_radio.png" width="19%" alt="Radio" />
  <img src="./screenshots/v100_eq.png" width="19%" alt="Equalizer" />
</p>

I build VANTA on my own, in my spare time. It plays lossless FLAC and Dolby Atmos, has a proper EQ, and does radio that stays on the genre you picked. There are no ads and no tracking, and it's free.

## Download

Grab `vanta.apk` from the [latest release](https://github.com/drewk312/VantaMusic/releases/latest), open it on your phone or TV, and let Android install it. If you already have VANTA, install it over the top and your library stays put. The app also tells you when an update is out.

Please only download it from this page. Every release lists what changed and a SHA-256 checksum if you want to double-check the file ([how](SECURITY.md)).

## What it does

**Sound**
- Lossless FLAC up to 24-bit, plus local FLAC, ALAC and WAV files.
- Dolby Atmos and Sony 360 Reality Audio, with optional spatial audio and head tracking for headphones.
- A 31-band EQ, a 10-band parametric EQ with AutoEQ preset import, tube amp, Bass Cannon and a convolver. Changes apply while the song plays, including on USB DACs.
- Sound Check to even out volume between tracks.
- File Info shows the real codec, sample rate and bit depth. The Hi-Res badge only appears when the file actually is hi-res.

**Finding music**
- Search that copes with typos and puts the original recording ahead of covers.
- Radio for 147 genres and subgenres that stays on theme, doesn't repeat a song within a day, and learns from your thumbs up and down.
- Artist pages with albums and singles/EPs listed separately.
- Synced lyrics.

**Your library**
- Import Spotify playlists by sharing them to VANTA or pasting the link, with no login.
- Import CSV exports of your playlists or Liked Songs. Each song keeps its exact recording (ISRC).
- Import local music from one folder or the whole phone.
- Download playlists for offline listening.
- Share a song or playlist with a link that opens right in VANTA.

**Everywhere**
- Phone, tablet and Android TV, with a TV layout built for remotes and a landscape mode for car and RV mounts.

<p align="center">
  <img src="./screenshots/vanta_tv_home.png" width="96%" alt="VANTA on Android TV" />
</p>

## Good to know

Spotify only gives out the first 100 songs of a playlist through a shared link, so that's what a link import brings in. For bigger playlists or your Liked Songs, export them as a CSV and import that instead. There's no size limit that way.

## Privacy

- No ads, no analytics, no trackers.
- No microphone permission. The visualizer reacts to the audio itself.
- Releases are signed, and the in-app updater checks the checksum and signature before it installs anything.

## Help and support

- Chat, bugs and ideas: [Discord](https://discord.gg/vN6ztK6m6g)
- Bug reports: [GitHub Issues](https://github.com/drewk312/VantaMusic/issues)
- If you'd like to support VANTA: [ko-fi.com/drewk312](https://ko-fi.com/drewk312)

Thanks to **Inzo184** (`@inzo1848842`), **Ink & Echo Admin** (`@developerbios`), **Riknar** (`@riknarr`), and everyone who has tested builds and sent reports.

## Legal

VANTA is an independent, non-commercial project. It isn't affiliated with or endorsed by Spotify, Apple, Google, Dolby, Sony, or any label, artist or streaming service; their names are only used to describe compatibility. VANTA doesn't host, store, sell or distribute music. You're responsible for how you use it and for following the terms of any service you connect. If you love an artist, support them by buying their music, streaming it officially, and going to their shows.

VANTA is provided as is, with no warranty.

Copyright © drewk312. The VANTA name, logo and branding may not be reused, and unofficial copies must not present themselves as official releases. Official releases are only published on this repository's [Releases page](https://github.com/drewk312/VantaMusic/releases). VANTA includes open-source components under their own licenses. Dolby, Dolby Atmos, Sony 360 Reality Audio, Spotify and other trademarks belong to their owners.

This repository is for downloads and news. The app's source code is private.
