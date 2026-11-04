const CACHE_NAME = 'fsp-shell-v1';
const SHELL_ASSETS = [
  '/',
  '/css/tokens.css',
  '/css/app.css',
  '/js/voice.js',
  '/manifest.webmanifest'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(SHELL_ASSETS))
  );
  self.skipWaiting();
});

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET' || event.request.url.includes('/api/alerts/stream')) {
    return;
  }
  event.respondWith(
    fetch(event.request).catch(() => caches.match(event.request))
  );
});

self.addEventListener('push', (event) => {
  const payload = event.data ? event.data.json() : {
    title: 'FSP Sinyal Bildirimi',
    body: 'Yeni AL/SAT sinyali üretildi.'
  };
  event.waitUntil(
    self.registration.showNotification(payload.title || 'FSP Sinyal', {
      body: payload.spokenText || payload.body,
      tag: payload.signalId || 'fsp-signal'
    })
  );
});

