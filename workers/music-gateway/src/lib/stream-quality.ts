/** Map gateway quality tokens and labels to approximate lossless bitrate (kbps). */
export function inferBitrateKbps(quality?: string | null, format?: string | null): number {
  const q = (quality ?? "").toLowerCase();
  const fmt = (format ?? "").toLowerCase();

  if (q.includes("192") || q.includes("dsd")) return 9216;
  if (q.includes("96") || q.includes("88.2")) return 3072;
  if (q.includes("48") && q.includes("24")) return 2304;
  if (q === "24" || q.includes("24-bit") || q.includes("hi-res") || q.includes("hires")) return 1411;
  if (q === "16" || q.includes("16-bit") || q.includes("cd")) return 1411;
  if (q.includes("lossless") || q.includes("flac")) return 1411;
  if (q.includes("320") || fmt.includes("mp3")) return 320;
  if (fmt.includes("aac") || fmt.includes("m4a")) return 256;
  if (fmt.includes("flac")) return 1411;
  return 1411;
}

export function qualityLabelFromBitrate(bitrateKbps: number, format?: string): string {
  if (bitrateKbps >= 5000) return "DSD / Ultra Hi-Res";
  if (bitrateKbps >= 2000) return "Hi-Res 96 kHz";
  if (bitrateKbps >= 1400) return "Lossless FLAC";
  if (bitrateKbps >= 300) return "High Quality";
  return format?.toUpperCase() ?? "Stream";
}

export function hasDolbyAtmosSignal(...values: Array<string | null | undefined>): boolean {
  const text = values.filter(Boolean).join(" ").toLowerCase();
  return text.includes("dolby atmos") ||
    text.includes(" atmos") ||
    text.includes("e-ac-3 joc") ||
    text.includes("eac3-joc") ||
    text.includes("ec-3 joc");
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


export function isHiResSignal(quality?: string | null, format?: string | null, samplingRateKhz?: number | null, bitDepth?: number | null): boolean {
  const q = (quality ?? '').toLowerCase();
  if (q.includes('hires') || q.includes('hi-res') || q.includes('192') || q.includes('96')) return true;
  if (samplingRateKhz != null && samplingRateKhz >= 88.2) return true;
  if (bitDepth != null && bitDepth >= 24) return true;
  return false;
}

export function spatialFormatLabel(track: { isDolbyAtmos?: boolean; isSpatialAudio?: boolean; isSurround?: boolean }): string | null {
  if (track.isDolbyAtmos) return 'Dolby Atmos';
  if (track.isSpatialAudio) return 'Spatial Audio';
  if (track.isSurround) return 'Surround';
  return null;
}

