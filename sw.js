/* Morango service worker: lets the app open without internet.
   - the page itself: network first, last good copy when offline
   - supabase library, fonts, icons: cache first
   - version.json and the database are never cached here */
const C='morango-shell-v1';
self.addEventListener('install',e=>{self.skipWaiting();e.waitUntil(caches.open(C).then(c=>c.addAll(['./','icon-192.png','icon-512.png','manifest.webmanifest']).catch(()=>{})))});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(ks=>Promise.all(ks.filter(k=>k!==C).map(k=>caches.delete(k)))).then(()=>self.clients.claim()))});
self.addEventListener('fetch',e=>{const r=e.request;if(r.method!=='GET')return;const u=new URL(r.url);
  if(u.hostname.endsWith('supabase.co')||u.pathname.endsWith('version.json'))return;
  if(r.mode==='navigate'||(u.origin===location.origin&&(u.pathname==='/'||u.pathname.endsWith('index.html')))){
    e.respondWith(fetch(r).then(res=>{const cp=res.clone();caches.open(C).then(c=>c.put('./',cp));return res}).catch(()=>caches.match('./').then(m=>m||caches.match(r))));return}
  if(u.hostname==='cdn.jsdelivr.net'||u.hostname.includes('fonts.g')||u.origin===location.origin){
    e.respondWith(caches.match(r).then(m=>m||fetch(r).then(res=>{if(res.ok||res.type==='opaque'){const cp=res.clone();caches.open(C).then(c=>c.put(r,cp))}return res})))}});
