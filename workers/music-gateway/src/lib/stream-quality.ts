/** Map gateway quality tokens and labels to approximate lossless bitrate (kbps). */
export function inferBitrateKbps(quality?: string | null, format?: string | null): number {
  const q = (quality ?? "").toLowerCase();
  const fmt = (format ?? "").toLowerCase();
  const hay = `${q} ${fmt}`;

  if (q.includes("192") || q.includes("dsd")) return 9216;
  if (hasDolbyAtmosSignal(quality, format)) return 0;
  if (q.includes("96") || q.includes("88.2")) return 3072;
  if (q.includes("48") && q.includes("24")) return 2304;
  if (q === "24" || q.includes("24-bit") || q.includes("hi-res") || q.includes("hires")) return 1411;
  if (q === "16" || q.includes("16-bit") || q.includes("cd")) return 1411;
  if (q.includes("lossless") || q.includes("flac") || q.includes("alac") || fmt.includes("flac") || fmt.includes("alac")) {
    return 1411;
  }
  if (q.includes("320") || fmt.includes("mp3")) return 320;
  if (q.includes("256")) return 256;
  if (fmt.includes("aac") && !fmt.includes("alac")) return 256;
  // Never invent 256 for unlabeled M4A — that container is also Atmos / ALAC.
  if (hay.includes("m4a") || hay.includes("mp4")) return 0;
  return 0;
}

export function qualityLabelFromBitrate(bitrateKbps: number, format?: string): string {
  const fmt = (format ?? "").toLowerCase();
  if (hasDolbyAtmosSignal(format)) return "Dolby Atmos";
  if (bitrateKbps >= 5000) return "DSD / Ultra Hi-Res";
  if (bitrateKbps >= 2000 && (fmt.includes("m4a") || fmt.includes("alac") || fmt.includes("mp4"))) {
    return "Hi-Res M4A";
  }
  if (bitrateKbps >= 2000) return "Hi-Res 96 kHz";
  if (bitrateKbps >= 1400) return "Lossless FLAC";
  if (bitrateKbps >= 300) return "High Quality";
  return format?.toUpperCase() ?? "Stream";
}

export function inferContainerFromUrl(url?: string | null): string | undefined {
  if (!url) return undefined;
  const path = url.split("?")[0].split("#")[0].toLowerCase();
  if (path.endsWith(".flac")) return "flac";
  if (path.endsWith(".m4a") || path.endsWith(".mp4")) return "m4a";
  if (path.endsWith(".mp3")) return "mp3";
  if (path.endsWith(".wav")) return "wav";
  if (path.endsWith(".aac")) return "aac";
  return undefined;
}

export function streamFidelityScore(stream: {
  bitrateKbps?: number | null;
  format?: string | null;
  quality?: string | null;
  mimeType?: string | null;
  isDolbyAtmos?: boolean;
  isSpatialAudio?: boolean;
  isSurround?: boolean;
}): number {
  const hay = [stream.format, stream.quality, stream.mimeType].filter(Boolean).join(" ").toLowerCase();
  let score = 0;
  if (stream.isDolbyAtmos || hasDolbyAtmosSignal(hay)) score += 50_000;
  else if (stream.isSpatialAudio || stream.isSurround || hasSpatialAudioSignal(hay)) score += 20_000;
  score += stream.bitrateKbps ?? 0;
  return score;
}

export function isAtmosQuality(quality?: string | null): boolean {
  const q = (quality ?? "").trim().toLowerCase();
  return q === "atmos" || q === "dolby_atmos" || q === "eac3" || q === "eac3_joc" || q === "ac4" || q === "ac-4" || q.includes("atmos");
}

/**
 * Sony 360 Reality Audio request token. Only Amazon Music still carries the
 * 360RA catalog (Tidal dropped it upstream in July 2024), so these requests
 * are routed to Amazon relays exclusively.
 */
export function is360Quality(quality?: string | null): boolean {
  const q = (quality ?? "").trim().toLowerCase();
  return q === "360" || q === "360ra" || q === "sony360" || q === "sony_360" ||
    q === "360_reality_audio" || q.includes("360 reality");
}

export function hasAtmosCodecSignal(...values: Array<string | null | undefined>): boolean {
  const text = values.filter(Boolean).join(" ").toLowerCase();
  const hasJoc = text.includes("eac3_joc") ||
    text.includes("eac3-joc") ||
    text.includes("joc");
  if (hasJoc) return true;

  // E-AC-3 and AC-4 both carry ordinary non-Atmos audio too. Without an
  // explicit immersive profile, treating either codec as Atmos is false.
  const hasEac3 = text.includes("eac3") || text.includes("e-ac-3") || text.includes("ec-3");
  const hasAc4 = text.includes("ac-4") || /(?:^|[^a-z0-9])ac4(?:$|[^a-z0-9])/.test(text);
  const hasImmersiveProfile = text.includes("dolby atmos") ||
    /(?:^|[^a-z0-9])atmos(?:$|[^a-z0-9])/.test(text) ||
    text.includes("immersive");
  return (hasEac3 || hasAc4) && hasImmersiveProfile;
}

export function hasDolbyAtmosSignal(...values: Array<string | null | undefined>): boolean {
  return hasAtmosCodecSignal(...values);
}

export function hasSpatialAudioSignal(...values: Array<string | null | undefined>): boolean {
  const text = values.filter(Boolean).join(" ").toLowerCase();
  return text.includes("spatial audio") ||
    text.includes("spatial") ||
    hasDolbyAtmosSignal(...values);
}

export function hasSurroundSignal(...values: Array<string | null | undefined>): boolean {
  const text = values.filter(Boolean).join(" ").toLowerCase();
  return text.includes("surround") ||
    text.includes("5.1") ||
    text.includes("7.1") ||
    hasDolbyAtmosSignal(...values);
}

/**
 * Sony 360 Reality Audio evidence. MPEG-H and an explicit "360 Reality Audio"
 * / "sony 360" / "360ra" wording are vendor-specific signals. A bare "360"
 * number or an "mha1" sample entry is not: mha1 is shared with IAMF.
 */
export function hasSony360Signal(...values: Array<string | null | undefined>): boolean {
  const text = values.filter(Boolean).join(" ").toLowerCase();
  return /(?:^|[^a-z0-9])mpeg-?h(?:$|[^a-z0-9])/.test(text) ||
    text.includes("360 reality audio") ||
    text.includes("sony 360") ||
    /(?:^|[^a-z0-9])360ra(?:$|[^a-z0-9])/.test(text);
}

/**
 * Eclipsa Audio / IAMF (Immersive Audio Model and Formats, AOMedia). Open and
 * renderable to binaural PCM on any device, unlike Dolby Atmos passthrough.
 */
export function hasIamfSignal(...values: Array<string | null | undefined>): boolean {
  const text = values.filter(Boolean).join(" ").toLowerCase();
  return /(?:^|[^a-z0-9])iamf(?:$|[^a-z0-9])/.test(text) ||
    text.includes("eclipsa audio") ||
    text.includes("eclipsa");
}

/**
 * Immersive stream sample entries (mha1/mhm1/mha2) that prove immersive
 * audio but cannot be attributed to a vendor (shared MPEG-H and IAMF short
 * forms). These count as spatial but as an unknown explicit format.
 */
export function hasImmersiveContainerSignal(...values: Array<string | null | undefined>): boolean {
  const text = values.filter(Boolean).join(" ").toLowerCase();
  return /(?:^|[^a-z0-9])mha[123](?:$|[^a-z0-9])/.test(text) ||
    /(?:^|[^a-z0-9])mhm[12](?:$|[^a-z0-9])/.test(text);
}

export type SpatialFormat =
  | "NONE"
  | "DOLBY_ATMOS"
  | "SONY_360_REALITY_AUDIO"
  | "ECLIPSA_AUDIO"
  | "UNKNOWN_SPATIAL";

/** Derive the explicit spatial format from proven signals only. */
export function deriveSpatialFormat(stream: {
  isDolbyAtmos?: boolean;
  isSpatialAudio?: boolean;
  isSurround?: boolean;
  format?: string | null;
  quality?: string | null;
  mimeType?: string | null;
}): SpatialFormat {
  if (stream.isDolbyAtmos || hasAtmosCodecSignal(stream.format, stream.quality, stream.mimeType)) {
    return "DOLBY_ATMOS";
  }
  if (hasIamfSignal(stream.format, stream.quality, stream.mimeType)) {
    return "ECLIPSA_AUDIO";
  }
  if (hasSony360Signal(stream.format, stream.quality, stream.mimeType)) {
    return "SONY_360_REALITY_AUDIO";
  }
  if (
    stream.isSpatialAudio ||
    stream.isSurround ||
    hasSpatialAudioSignal(stream.format, stream.quality, stream.mimeType) ||
    hasImmersiveContainerSignal(stream.format, stream.quality, stream.mimeType)
  ) {
    return "UNKNOWN_SPATIAL";
  }
  return "NONE";
}

export function isHiResSignal(quality?: string | null, format?: string | null, samplingRateKhz?: number | null, bitDepth?: number | null): boolean {
  const q = (quality ?? "").toLowerCase();
  if (q.includes("hires") || q.includes("hi-res") || q.includes("192") || q.includes("96")) return true;
  if (samplingRateKhz != null && samplingRateKhz >= 88.2) return true;
  if (bitDepth != null && bitDepth >= 24) return true;
  return false;
}

export function spatialFormatLabel(track: { isDolbyAtmos?: boolean; isSpatialAudio?: boolean; isSurround?: boolean }): string | null {
  if (track.isDolbyAtmos) return "Dolby Atmos";
  if (track.isSpatialAudio) return "Spatial Audio";
  if (track.isSurround) return "Surround";
  return null;
}

export function spatialEvidenceFromSignals(
  ...values: Array<string | null | undefined>
): "verified" | "metadata" | "stereo" {
  const text = values.filter(Boolean).join(" ").toLowerCase();
  if (hasAtmosCodecSignal(...values)) {
    return "verified";
  }
  if (
    text.includes("spatial audio") ||
    text.includes("spatial") ||
    text.includes("surround") ||
    text.includes("5.1") ||
    text.includes("7.1")
  ) {
    return "metadata";
  }
  return "stereo";
}
