# Progressive web app

The manifest and icons live in `webApp/src/jsMain/resources`. The 192px and 512px
icons are resized copies of `favicon.png`; they use the `any` purpose because the
original artwork has not been designed for maskable cropping.

Build the JavaScript production distribution:

```sh
./gradlew :webApp:jsBrowserDistribution
```

Each distribution generates `pwa-assets.json` from the final output, including
Compose resources and JavaScript chunks. Source maps are
excluded. A content hash versions the service worker cache. Development servers
do not generate this list or install a new worker.

Serve the resulting distribution over HTTPS or localhost. On the first online
visit, keep the app open until the service worker has installed and activated
(DevTools → Application → Service Workers). Then disconnect the network and
reload: the application shell should still load. Installation and first-time
sign-in require a network connection. Cached Firestore data remains governed by
the existing repository and authentication behavior; the service worker caches
only bundled app assets, not clinical records or API responses.

For an automated Chrome check, serve the production directory on localhost port
8081, then run `node tests/browser/pwa-smoke.cjs`. Set `JANANISYNC_TEST_URL` to use
a different localhost port. The check validates the manifest, all cached build
assets, and an offline reload in a fresh browser profile.

Updates install in the background and activate after all app windows using the
previous version close. Reopen the app to use the new version. Test both browser
and installed-app launches, and confirm the manifest and icons in DevTools.
When switching back to local development on the same origin, unregister the
production service worker and clear its caches in DevTools first.

Firebase Hosting configuration is unchanged; no deployment is performed by
these build tasks.
