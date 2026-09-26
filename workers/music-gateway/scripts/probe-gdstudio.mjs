import { streamViaGDStudio } from "../src/providers/public-stream.ts";
for (const [id, src] of [["381791126", "qobuz"], ["283628183", "tidal"], ["3818963601", "deezer"]]) {
  const t0 = Date.now();
  try {
    const r = await streamViaGDStudio(id, "16", "https://music.gdstudio.xyz/api.php", src);
    console.log(src, id, r ? `OK url=${r.url.slice(0, 90)} quality=${r.quality}` : "null", `${Date.now() - t0}ms`);
  } catch (e) { console.log(src, id, "ERR", e instanceof Error ? e.message : e); }
}
