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
