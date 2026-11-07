/* PWA_BUILD_VERSION is replaced by the production distribution task. */
const VERSION = 'PWA_BUILD_VERSION';
const PREFIX = `jananisync-shell-${self.registration.scope}-`;
const CACHE = PREFIX + VERSION;
const ROOT = new URL('./', self.location.href);
let assetUrls;

async function assets() {
    if (!assetUrls) {
        const cache = await caches.open(CACHE);
        const response = await cache.match(new URL('pwa-assets.json', ROOT).href);
        assetUrls = new Set((await response.json()).map(path => new URL(path, ROOT).href));
    }
    return assetUrls;
}

self.addEventListener('install', event => {
    event.waitUntil((async () => {
        const response = await fetch(new URL('pwa-assets.json', ROOT), { cache: 'no-store' });
        if (!response.ok) throw new Error('Missing production asset list');
        const paths = await response.clone().json();
        const cache = await caches.open(CACHE);
        try {
            await cache.addAll(paths.map(path => new Request(new URL(path, ROOT), { cache: 'reload' })));
            await cache.put(new URL('pwa-assets.json', ROOT).href, response);
        } catch (error) {
            await caches.delete(CACHE);
            throw error;
        }
        // Updates wait until existing app windows close, avoiding mixed build assets.
    })());
});

self.addEventListener('activate', event => {
    event.waitUntil((async () => {
        for (const key of await caches.keys()) {
            if (key.startsWith(PREFIX) && key !== CACHE) await caches.delete(key);
        }
        await self.clients.claim();
    })());
});

self.addEventListener('fetch', event => {
    const request = event.request;
    const url = new URL(request.url);
    if (request.method !== 'GET' || url.origin !== ROOT.origin ||
        !url.pathname.startsWith(ROOT.pathname) || url.pathname.startsWith('/__/')) return;
    event.respondWith((async () => {
        const cache = await caches.open(CACHE);
        if (request.mode === 'navigate') {
            // Public HTML pages are build assets, not application routes. Ignore
            // query parameters when locating their precached copy.
            const assetUrl = new URL(url);
            assetUrl.search = '';
            if ((await assets()).has(assetUrl.href)) {
                return (await cache.match(assetUrl.href)) || fetch(request);
            }
            return (await cache.match(new URL('index.html', ROOT).href)) || fetch(request);
        }
        // Only build assets are cached. Authentication, API responses and clinical data are excluded.
        if ((await assets()).has(url.href)) return (await cache.match(request)) || fetch(request);
        return fetch(request);
    })());
});
