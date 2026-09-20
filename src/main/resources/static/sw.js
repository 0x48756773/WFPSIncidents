/*
 * Service worker for the WFPS Active Incident Map.
 *
 * Its job is narrow and deliberately so. It exists to make the site installable — a browser
 * will not offer "add to home screen" without one — and to say something useful when the
 * device is offline. It is not a caching layer for the site.
 *
 * Nothing live is ever cached. Incident data, the page itself, the boundary GeoJSON and the
 * feed all go straight to the network, untouched. A map of emergency calls that quietly
 * served yesterday's data from a cache would be worse than one that failed honestly, and a
 * service worker is sticky: a caching mistake here outlives the deploy that introduced it.
 *
 * The only thing held is the offline notice and the icons it needs, so that a navigation
 * that cannot reach the network has something to show.
 */

const CACHE = 'wfps-shell-v1';
const OFFLINE_URL = '/offline.html';
const SHELL = [OFFLINE_URL, '/favicon-32x32.png', '/android-chrome-192x192.png'];

self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE)
            .then((cache) => cache.addAll(SHELL))
            // An install that fails on one missing asset leaves no worker at all, which
            // takes installability with it. The offline page is a nicety; not having it
            // is not a reason to have no service worker.
            .catch(() => undefined)
            .then(() => self.skipWaiting())
    );
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys()
            .then((keys) => Promise.all(keys.filter((key) => key !== CACHE).map((key) => caches.delete(key))))
            .then(() => self.clients.claim())
    );
});

self.addEventListener('fetch', (event) => {
    const request = event.request;

    // Only navigations are handled, and only to substitute the offline notice when the
    // network is gone. Everything else — data, scripts, tiles — is left entirely alone,
    // which means the browser handles it exactly as it would with no service worker.
    if (request.mode !== 'navigate') {
        return;
    }

    event.respondWith(
        fetch(request).catch(() => caches.match(OFFLINE_URL).then((cached) => cached || Response.error()))
    );
});
