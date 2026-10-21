// Cache per l'uso offline: l'interfaccia subito, i suoni man mano che vengono ascoltati.
const CACHE = "chiarella-v1";
const SHELL = ["./", "index.html", "style.css", "data.js", "app.js", "icon-512.png", "manifest.webmanifest"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))));
  self.clients.claim();
});

self.addEventListener("fetch", (e) => {
  if (e.request.method !== "GET") return;
  const isSound = e.request.url.endsWith(".ogg");
  e.respondWith(
    isSound
      ? caches.match(e.request).then((hit) => hit || fetch(e.request).then((res) => {
          if (res.ok) { const copy = res.clone(); caches.open(CACHE).then((c) => c.put(e.request, copy)); }
          return res;
        }))
      // Interfaccia: prima la rete (così gli aggiornamenti arrivano subito), poi la cache.
      : fetch(e.request).then((res) => {
          if (res.ok) { const copy = res.clone(); caches.open(CACHE).then((c) => c.put(e.request, copy)); }
          return res;
        }).catch(() => caches.match(e.request))
  );
});
