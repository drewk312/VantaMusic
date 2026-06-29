import type { GatewayTrack } from "../types";
import { isAllowedVocalTrack } from "./vocal-recording";

/** Reject live streams, karaoke, tribute, and other non-studio inventory. */
export function isAllowedTrack(track: GatewayTrack, query = ""): boolean {
  if (!isAllowedVocalTrack(track, query)) return false;

  const title = (track.title ?? "").toLowerCase();
  const artist = (track.artist ?? "").toLowerCase();
  const durationSec = track.duration ?? 0;

  if (durationSec <= 0 || durationSec > 14_400) return false; // >4h or invalid

  const liveKeywords = [
    "beats to relax",
    "beats to study",
    "beats to sleep",
    "beats to chill",
    "radio 🌌",
    "live stream",
    "24/7",
    "streaming now",
    "playing now",
  ];
  if (liveKeywords.some((k) => title.includes(k))) return false;

  const bannedArtists = ["lofi girl", "steezyasfuck", "chilledcow"];
  if (bannedArtists.some((a) => artist.includes(a))) return false;

  const junkKeywords = [
    "acapella",
    "a cappella",
    "8-bit",
    "8bit",
    "chiptune",
    "midi",
    "fl studio",
    "garageband",
    "piano off",
    "piano project",
    "original mix",
    "emulation",
    "weeknights",
    "retro weeknd",
  ];
  const stack = `${title} ${artist} ${(track.album ?? "").toLowerCase()}`;
  if (junkKeywords.some((k) => stack.includes(k))) return false;

  return true;
}

export function filterTracks(tracks: GatewayTrack[], query = ""): GatewayTrack[] {
  return tracks.filter((track) => isAllowedTrack(track, query));
}
