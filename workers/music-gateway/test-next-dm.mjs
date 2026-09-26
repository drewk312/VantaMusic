import * as fs from "node:fs";
const devVars = fs.readFileSync(".dev.vars", "utf8");
const env = Object.fromEntries(devVars.split("\n").filter(l=>l.includes("=")).map(l=>{const [k,...v]=l.split("=");return [k,v.join("=")] }));
const sid=env.COMMUNITY_SESSION_ID, sec=env.COMMUNITY_SESSION_SECRET;
async function sign(method, pathname, search, body){
  const bodyHash=await crypto.subtle.digest("SHA-256", body).then(b=>Array.from(new Uint8Array(b)).map(x=>x.toString(16).padStart(2,"0")).join(""));
  const ts=new Date().toISOString().replace(/\.\d+Z$/, ".000Z");
  const nonce=[...crypto.getRandomValues(new Uint8Array(12))].map(b=>b.toString(16).padStart(2,"0")).join("");
  const window=(Math.floor(Date.parse(ts)/1000/300))|0;
  const enc=new TextEncoder();
  const key=await crypto.subtle.importKey("raw", enc.encode(sec), {name:"HMAC", hash:"SHA-256"}, false, ["sign"]);
  const rkRaw=await crypto.subtle.sign("HMAC", key, enc.encode(`${window}:${sid}`));
  const rk=await crypto.subtle.importKey("raw", new Uint8Array(rkRaw), {name:"HMAC", hash:"SHA-256"}, false, ["sign"]);
  const input=["SPOTIFLAC-HMAC-V1",method,pathname,search,bodyHash,ts,nonce,sid,"unknown","desktop"].join("\n");
  const sigRaw=await crypto.subtle.sign("HMAC", rk, enc.encode(input));
  const sig=Buffer.from(sigRaw).toString("base64url");
  return {"X-Sig-Session":sid,"X-Sig-Timestamp":ts,"X-Sig-Nonce":nonce,"X-Sig-Body-SHA256":bodyHash,"X-Sig-Signature":sig,"X-Sig-App-Version":"unknown","X-Sig-Platform":"desktop","Content-Type":"application/json","Accept":"application/json"};
}
async function tryEndpoint(base, path, id, q){
  const endpoint=`${base}${path}`;
  const bodyText=JSON.stringify({id,q});
  const body=new TextEncoder().encode(bodyText);
  const url=new URL(endpoint);
  const headers=await sign("POST",url.pathname,url.search,body);
  const res=await fetch(endpoint,{method:"POST",headers,body:bodyText});
  const text=await res.text();
  console.log(`${base}${path} id=${id} q=${q} -> ${res.status} ${text.slice(0,300)}`);
}
const bases=["https://qbz-1.spotbye.qzz.io","https://tdl-1.spotbye.qzz.io","https://amz-1.spotbye.qzz.io","https://dzr-1.spotbye.qzz.io"];
const paths=["/api/dl","/api/dm","/api/download","/api/v1/dl","/api/track","/api/dm/qobuz","/api/dl/qobuz"];
for(const b of bases){
  for(const p of paths){
    await tryEndpoint(b,p,"266725029","24");
    await new Promise(r=>setTimeout(r,200));
  }
}
