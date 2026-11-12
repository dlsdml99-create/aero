// Aero 웹앱: 한 번 열면 인터넷 없이도(공항·비행기 안) 열리게 저장하고, 인터넷이 되면 항상 새 버전을 먼저 받아요.
// 이 사이트(github.io/aero/app/) 파일만 다루고, 다른 곳으로는 아무것도 보내지 않아요.
const CACHE = 'aero-__V__';
const FILES = ['./', './index.html', './icon-180.png', './lib/pdf.min.js', './lib/pdf.worker.min.js', './lib/html5-qrcode.min.js'];
self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k.startsWith('aero-') && k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});
self.addEventListener('fetch', e => {
  const u = new URL(e.request.url);
  if (e.request.method !== 'GET' || u.origin !== location.origin || !u.pathname.startsWith(new URL('./', location).pathname)) return;
  e.respondWith(fetch(e.request).then(r => {
    if (r.ok) { const cp = r.clone(); caches.open(CACHE).then(c => c.put(e.request, cp)); }
    return r;
  }).catch(() => caches.match(e.request, { ignoreSearch: true }).then(r => r || caches.match('./index.html'))));
});
