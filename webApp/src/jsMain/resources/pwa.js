// The asset list is generated only for production distributions.
if ('serviceWorker' in navigator) {
    window.addEventListener('load', async () => {
        try {
            const response = await fetch('pwa-assets.json', { cache: 'no-store' });
            if (!response.ok) return;
            const assets = await response.json();
            if (!Array.isArray(assets) || !assets.includes('index.html')) return;
            await navigator.serviceWorker.register('sw.js', { updateViaCache: 'none' });
        } catch (error) {
            console.info('Offline app setup unavailable; continuing online.', error);
        }
    });
}
