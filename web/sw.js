// I suoni sono precaricati dall'app, anche alla prima visita senza worker attivo.
const CACHE = "chiarella-v6";
const SOUND_CACHE = "chiarella-audio-v2";
const SHELL = ["./", "index.html", "style.css", "data.js", "backup.js", "app.js", "icon-512.png", "manifest.webmanifest"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k.startsWith("chiarella-") && k !== CACHE && k !== SOUND_CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});

self.addEventListener("fetch", (e) => {
  if (e.request.method !== "GET") return;
  const isSound = new URL(e.request.url).pathname.endsWith(".ogg");
  e.respondWith(
    isSound
      ? caches.open(SOUND_CACHE).then((c) => c.match(e.request)).then((hit) => hit || fetch(e.request).then((res) => {
          if (res.ok) { const copy = res.clone(); e.waitUntil(caches.open(SOUND_CACHE).then((c) => c.put(e.request, copy)).catch(() => {})); }
          return res;
        }))
      // Interfaccia: prima la rete (così gli aggiornamenti arrivano subito), poi la cache.
      : fetch(e.request).then((res) => {
          if (res.ok) { const copy = res.clone(); caches.open(CACHE).then((c) => c.put(e.request, copy)); }
          return res;
        }).catch(() => caches.match(e.request))
  );
});
