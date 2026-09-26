import { fetchText } from "../src/providers/shared.ts";
const host = "music.gdstudio.xyz";
const tsRaw = await fetchText(`https://${host}/time`);
const ts = tsRaw?.startsWith("t") ? tsRaw.slice(1) : (tsRaw ?? "");
console.log("ts=", ts);
const body = new URLSearchParams({ types: "url", id: "381791126", source: "qobuz", br: "999", s: "x" });
const res = await fetch(`https://${host}/api.php`, { method: "POST", headers: { Origin: `https://${host}`, Referer: `https://${host}/`, "Content-Type": "application/x-www-form-urlencoded" }, body });
console.log(res.status, (await res.text()).slice(0, 300));