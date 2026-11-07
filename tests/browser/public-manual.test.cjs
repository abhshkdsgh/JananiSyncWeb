const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const workerSource = fs.readFileSync(path.join(__dirname,
  '../../webApp/src/jsMain/resources/sw.js'), 'utf8');

// Exercise the real fetch handler with its production asset manifest contract.
// A public page must remain public after an app visit installs the worker.
function worker({scope = 'https://example.test/', manualCached = true, offline = false} = {}) {
  const handlers = new Map();
  const requests = [];
  const absolute = value => new URL(typeof value === 'string' ? value : value.url, scope).href;
  const entries = new Map([
    [absolute('pwa-assets.json'), {json: async () => ['index.html', 'user-manual.html', 'webApp.js']}],
    [absolute('index.html'), {body: 'APP'}],
    ...(manualCached ? [[absolute('user-manual.html'), {body: 'PUBLIC MANUAL'}]] : [])
  ]);
  const cache = {match: async request => entries.get(absolute(request))};
  vm.runInNewContext(workerSource, {
    URL,
    self: {
      registration: {scope}, location: {href: absolute('sw.js')},
      addEventListener: (name, handler) => handlers.set(name, handler)
    },
    caches: {open: async () => cache},
    fetch: async request => {
      requests.push(request.url);
      if (offline) throw Error('offline');
      return {body: 'NETWORK MANUAL', url: request.url};
    }
  });
  return {
    requests,
    navigate(relative) {
      let response;
      handlers.get('fetch')({
        request: {url: absolute(relative), method: 'GET', mode: 'navigate'},
        respondWith: value => {response = value;}
      });
      return response;
    }
  };
}

test('public manual is served directly after the app worker controls navigation', async () => {
  const app = worker();
  assert.equal((await app.navigate('user-manual.html')).body, 'PUBLIC MANUAL');
  assert.deepEqual(app.requests, []);
});

test('public manual query strings use the same offline page', async () => {
  const app = worker({offline: true});
  assert.equal((await app.navigate('user-manual.html?from=login')).body, 'PUBLIC MANUAL');
  assert.deepEqual(app.requests, []);
});

test('a known public page missing from cache uses the network, not the app shell', async () => {
  const app = worker({manualCached: false});
  const response = await app.navigate('user-manual.html?from=settings');
  assert.equal(response.body, 'NETWORK MANUAL');
  assert.deepEqual(app.requests, ['https://example.test/user-manual.html?from=settings']);
});

test('unknown application routes retain the offline app shell fallback', async () => {
  const app = worker({offline: true});
  assert.equal((await app.navigate('wards/example')).body, 'APP');
  assert.equal((await app.navigate('./')).body, 'APP');
  assert.deepEqual(app.requests, []);
});

test('manual and app routing also work under a deployment subdirectory', async () => {
  const app = worker({scope: 'https://example.test/jananisync/', offline: true});
  assert.equal((await app.navigate('user-manual.html?from=login')).body, 'PUBLIC MANUAL');
  assert.equal((await app.navigate('wards/example')).body, 'APP');
});
