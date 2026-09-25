import type { ProviderHealth } from "./lib/health";

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (character) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#039;",
  })[character]!);
}

export function gatewayPage(name: string, version: string, providers: ProviderHealth[]): Response {
  const healthyCount = providers.filter((provider) => provider.healthy).length;
  const isHealthy = healthyCount === providers.length;
  const statusLabel = isHealthy ? "All systems listening" : "Listening in reduced mode";
  const providerRows = providers.map((provider) => `
    <li>
      <span class="signal ${provider.healthy ? "online" : "offline"}" aria-hidden="true"></span>
      <span>${escapeHtml(provider.provider)}</span>
      <strong>${provider.healthy ? "Available" : "Unavailable"}</strong>
    </li>`).join("");

  const html = `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="theme-color" content="#0a090b">
  <meta name="description" content="The private listening infrastructure behind VANTA.">
  <title>VANTA — Listening Gateway</title>
  <style>
    :root { color-scheme: dark; --ink:#0a090b; --surface:#151217; --ivory:#f4eee5; --muted:#aaa1aa; --gold:#d7ad72; --line:rgba(244,238,229,.11); --success:#7ec5a2; }
    * { box-sizing:border-box; }
    body { margin:0; min-height:100vh; background:var(--ink); color:var(--ivory); font-family:Inter,ui-sans-serif,system-ui,-apple-system,"Segoe UI",sans-serif; letter-spacing:0; }
    main { width:min(1080px,calc(100% - 40px)); margin:auto; padding:36px 0 56px; position:relative; }
    nav { display:flex; justify-content:space-between; align-items:center; border-bottom:1px solid var(--line); padding-bottom:20px; }
    .brand { font-size:15px; font-weight:700; letter-spacing:.22em; }
    .version { color:var(--muted); font-size:12px; }
    .hero { min-height:430px; display:grid; align-content:center; max-width:760px; }
    .eyebrow { color:var(--gold); font-size:12px; font-weight:700; text-transform:uppercase; letter-spacing:.16em; }
    h1 { margin:18px 0; font-family:Georgia,"Times New Roman",serif; font-size:clamp(46px,6.5vw,76px); line-height:1; font-weight:400; overflow-wrap:anywhere; }
    .intro { margin:0; color:var(--muted); font-size:clamp(17px,2vw,21px); line-height:1.55; max-width:620px; }
    .status { display:inline-flex; align-items:center; gap:10px; margin-top:30px; font-size:13px; }
    .signal { width:8px; height:8px; border-radius:50%; background:#8d5960; box-shadow:0 0 0 4px rgba(141,89,96,.12); flex:0 0 auto; }
    .signal.online { background:var(--success); box-shadow:0 0 0 4px rgba(126,197,162,.12); }
    .grid { display:grid; grid-template-columns:1.25fr .75fr; gap:48px; border-top:1px solid var(--line); padding-top:40px; }
    h2 { margin:0 0 12px; font-size:20px; font-weight:600; }
    .copy { margin:0; color:var(--muted); line-height:1.7; }
    ul { list-style:none; margin:22px 0 0; padding:0; border-top:1px solid var(--line); }
    li { min-height:48px; display:grid; grid-template-columns:18px 1fr auto; align-items:center; gap:8px; border-bottom:1px solid var(--line); font-size:13px; }
    li strong { color:var(--muted); font-size:11px; font-weight:600; }
    .links { display:flex; gap:12px; margin-top:22px; flex-wrap:wrap; }
    a { color:var(--ivory); text-decoration:none; border-bottom:1px solid var(--gold); padding:8px 0; font-size:13px; }
    footer { margin-top:56px; padding-top:20px; border-top:1px solid var(--line); color:var(--muted); font-size:11px; display:flex; justify-content:space-between; gap:16px; }
    @media (max-width:720px) { main{width:min(100% - 32px,1080px);padding-top:24px}.hero{min-height:390px}.grid{grid-template-columns:1fr;gap:36px}footer{flex-direction:column} }
    @media (prefers-reduced-motion:no-preference) { .hero>* { animation:reveal .65s ease both; } .hero>*:nth-child(2){animation-delay:.06s}.hero>*:nth-child(3){animation-delay:.12s}.hero>*:nth-child(4){animation-delay:.18s}@keyframes reveal{from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:none}} }
  </style>
</head>
<body>
  <main>
    <nav><span class="brand">VANTA</span><span class="version">Gateway ${escapeHtml(version || "live")}</span></nav>
    <section class="hero">
      <span class="eyebrow">Private listening infrastructure</span>
      <h1>Every note,<br><em>without friction.</em></h1>
      <p class="intro">${escapeHtml(name)} quietly connects discovery, playback, and library sync so the VANTA app can stay focused on the music.</p>
      <div class="status"><span class="signal ${isHealthy ? "online" : "offline"}"></span>${statusLabel} · ${healthyCount}/${providers.length} providers ready</div>
    </section>
    <section class="grid">
      <div><h2>Designed to disappear</h2><p class="copy">The gateway resolves the best available source while VANTA preserves track identity, playback continuity, and honest quality labels. No catalog activity is presented as verified audio quality until playback confirms it.</p><div class="links"><a href="/health">Health JSON</a><a href="/status">Provider status</a><a href="/manifest.json">Manifest</a></div></div>
      <div><h2>Source availability</h2><p class="copy">Live operational snapshot</p><ul>${providerRows}</ul></div>
    </section>
    <footer><span>VANTA · Listen closely</span><span>Search, stream resolution, and encrypted library sync</span></footer>
  </main>
</body>
</html>`;

  return new Response(html, {
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "public, max-age=30",
      "Content-Security-Policy": "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'",
      "X-Content-Type-Options": "nosniff",
      "Referrer-Policy": "no-referrer",
    },
  });
}
