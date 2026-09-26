import * as fs from "node:fs";
const devVars = fs.readFileSync(".dev.vars", "utf8");
const env = Object.fromEntries(devVars.split("\n").filter(l=>l.includes("=")).map(l=>{ const [k,...v]=l.split("="); return [k,v.join("=")] }));
const sessionId = env.COMMUNITY_SESSION_ID;
const sessionSecret = env.COMMUNITY_SESSION_SECRET;
const appVersion = "unknown";
const platform = "desktop";
async function sign(method, pathname, search, body){
  const bodyHash = await crypto.subtle.digest("SHA-256", body).then(b=>Array.from(new Uint8Array(b)).map(x=>x.toString(16).padStart(2,"0")).join(""));
  const ts = new Date().toISOString().replace(/\.\d+Z$/, ".000Z");
  const nonce = [...crypto.getRandomValues(new Uint8Array(12))].map(b=>b.toString(16).padStart(2,"0")).join("");
  const window = (Math.floor(Date.parse(ts)/1000/300))|0;
  const enc=new TextEncoder();
  const key=await crypto.subtle.importKey("raw", enc.encode(sessionSecret), {name:"HMAC", hash:"SHA-256"}, false, ["sign"]);
  const rollingKeyRaw=await crypto.subtle.sign("HMAC", key, enc.encode(`${window}:${sessionId}`));
  const rollingKey=await crypto.subtle.importKey("raw", new Uint8Array(rollingKeyRaw), {name:"HMAC", hash:"SHA-256"}, false, ["sign"]);
  const input=["SPOTIFLAC-HMAC-V1",method,pathname,search,bodyHash,ts,nonce,sessionId,appVersion,platform].join("\n");
  const sigRaw=await crypto.subtle.sign("HMAC", rollingKey, enc.encode(input));
  const sig=Buffer.from(sigRaw).toString("base64url");
  return {"X-Sig-Session":sessionId,"X-Sig-Timestamp":ts,"X-Sig-Nonce":nonce,"X-Sig-Body-SHA256":bodyHash,"X-Sig-Signature":sig,"X-Sig-App-Version":appVersion,"X-Sig-Platform":platform,"Content-Type":"application/json","Accept":"application/json"};
}
async function tryHost(base, id, q){
  const endpoint=`${base}/api/dl`;
  const bodyText=JSON.stringify({id,q});
  const body=new TextEncoder().encode(bodyText);
  const url=new URL(endpoint);
  const headers=await sign("POST",url.pathname,url.search,body);
  const res=await fetch(endpoint,{method:"POST",headers,body:bodyText});
  const text=await res.text();
  console.log(base, "->", res.status, text.slice(0,400).replace(/\n/g," "));
}
for (let i=1;i<=5;i++){
  await tryHost(`https://qbz-${i}.spotbye.qzz.io`, "266725029", "24");
}
await tryHost("https://qbz-oss.spotbye.qzz.io", "266725029", "24");
for (let i=1;i<=5;i++){
  await tryHost(`https://tdl-${i}.spotbye.qzz.io`, "222282692", "24");
}
for (let i=1;i<=5;i++){
  await tryHost(`https://dzr-${i}.spotbye.qzz.io`, "3135556", "16");
}
