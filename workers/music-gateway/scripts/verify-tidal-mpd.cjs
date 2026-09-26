(async () => {
  const r = await fetch("https://vanta-music-gateway.16drewk.workers.dev/api/dl", {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({ id: "274457426", service: "tidal", quality: "16" }),
  });
  const j = await r.json();
  console.log("STATUS", r.status, "url-prefix", j.url?.slice(0, 70));
  const mpd = await fetch(j.url);
  const xml = await mpd.text();
  console.log("MPD status", mpd.status, "type", mpd.headers.get("content-type"));
  const dur = xml.match(/mediaPresentationDuration=\"([^\"]+)\"/);
  console.log("DURATION:", dur ? dur[1] : "(none)");
  const segments = xml.match(/<Segment[Tt]emplate[^>]*media=\"([^\"]+)\"/);
  const segDur = xml.match(/<Representation[^>]*bandwidth=\"(\d+)\"/);
  console.log("seg template:", segments ? segments[1].slice(0, 80) : "(none)");
  console.log("bandwidth:", segDur ? segDur[1] : "(none)");
  const totalSegs = (xml.match(/<SegmentURL/g) || []).length;
  console.log("inline SegmentURL count:", totalSegs);
})();