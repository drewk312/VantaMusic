const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36";
const id = process.argv[2] || "37i9dQZF1DXcBWIGoYBM5M";

function findTrackArrays(obj, path = "", out = []) {
  if (!obj || typeof obj !== "object") return out;
  if (Array.isArray(obj)) {
    const sample = obj[0];
    if (sample && typeof sample === "object") {
      const keys = Object.keys(sample);
      if (
        (sample.title || sample.name) &&
        (sample.subtitle || sample.artists || sample.uid || sample.uri || sample.id)
      ) {
        out.push({ path, len: obj.length, sampleKeys: keys, sample });
      }
    }
    for (let i = 0; i < Math.min(obj.length, 3); i++) findTrackArrays(obj[i], `${path}[${i}]`, out);
    return out;
  }
  for (const [k, v] of Object.entries(obj)) {
    if (path.split(".").length > 8) continue;
    findTrackArrays(v, path ? `${path}.${k}` : k, out);
  }
  return out;
}

(async () => {
  const res = await fetch(`https://open.spotify.com/embed/playlist/${id}`, {
    headers: { "User-Agent": UA, Accept: "text/html" },
  });
  const html = await res.text();
  const raw = html.match(/<script id="__NEXT_DATA__" type="application\/json">([^<]+)<\/script>/)?.[1];
  if (!raw) {
    console.log("no NEXT_DATA");
    return;
  }
  const data = JSON.parse(raw);
  const state = data.props?.pageProps?.state;
  console.log("state.data keys", Object.keys(state?.data || {}));
  const entity = state?.data?.entity;
  console.log("entity keys", entity ? Object.keys(entity) : null);
  console.log("entity.name", entity?.name);
  const found = findTrackArrays(state?.data || {});
  for (const hit of found.slice(0, 8)) {
    console.log("---");
    console.log(hit.path, "len=" + hit.len, "keys=" + hit.sampleKeys.join(","));
    console.log(JSON.stringify(hit.sample, null, 2).slice(0, 600));
  }
})();
