// Verify production PWA installation and offline reload with a fresh browser profile.
const {spawn} = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {once} = require('node:events');

const url = new URL(process.env.JANANISYNC_TEST_URL || 'http://localhost:8081/');
if (!['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)) throw Error('Use a localhost development server.');
const binary = [process.env.CHROME_BIN, '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  '/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser'].find(value => value && fs.existsSync(value));
if (!binary) throw Error('Set CHROME_BIN to a Chrome/Chromium executable.');
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'jananisync-pwa-'));
const chrome = spawn(binary, ['--headless=new', '--no-first-run', '--no-default-browser-check',
  '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--enable-webgl', '--remote-debugging-port=0',
  `--user-data-dir=${profile}`, 'about:blank'], {stdio: 'ignore'});

(async () => {
  let socket;
  try {
    const portFile = path.join(profile, 'DevToolsActivePort');
    for (let i = 0; i < 100 && !fs.existsSync(portFile); i++) await pause(100);
    if (!fs.existsSync(portFile)) throw Error('Chrome did not start.');
    const port = fs.readFileSync(portFile, 'utf8').split('\n')[0];
    const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
    socket = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {socket.onopen = resolve; socket.onerror = reject;});
    let requestId = 0;
    const pending = new Map(), exceptions = [];
    socket.onmessage = ({data}) => {
      const message = JSON.parse(data);
      if (message.id && pending.has(message.id)) {pending.get(message.id)(message); pending.delete(message.id);}
      if (message.method === 'Runtime.exceptionThrown') exceptions.push(
        message.params.exceptionDetails.exception?.description || message.params.exceptionDetails.text);
    };
    const send = (method, params = {}) => new Promise((resolve, reject) => {
      const id = ++requestId;
      const timer = setTimeout(() => {pending.delete(id); reject(Error(`Chrome timed out: ${method}`));}, 10000);
      pending.set(id, response => {clearTimeout(timer); resolve(response);});
      socket.send(JSON.stringify({id, method, params}));
    });
    await send('Runtime.enable');
    await send('Page.enable');
    const navigation = await send('Page.navigate', {url: url.href});
    if (navigation.result?.errorText) throw Error(navigation.result.errorText);
    let rendered = false;
    // Compose renders inside a shadow root; a document-only canvas query misses it.
    const expression = '(function count(n){return (n.tagName === "CANVAS" ? 1 : 0) + Array.from(n.children || []).reduce((sum,c) => sum + count(c), 0) + (n.shadowRoot ? count(n.shadowRoot) : 0)})(document)';
    for (let i = 0; i < 120 && !exceptions.length; i++) {
      const result = await send('Runtime.evaluate', {expression, returnByValue: true});
      if (result.result?.result?.value > 0) {rendered = true; break;}
      await pause(250);
    }
    await pause(2000);
    if (exceptions.length) throw Error(exceptions.join('\n'));
    if (!rendered) throw Error('The application did not render a Compose canvas. Check webpack errors and server resources.');
    const evaluate = async expression => {
      const response = await send('Runtime.evaluate', {expression, awaitPromise: true, returnByValue: true});
      if (response.result?.exceptionDetails) throw Error(JSON.stringify(response.result.exceptionDetails));
      return response.result.result.value;
    };
    await evaluate(`Promise.race([
      navigator.serviceWorker.ready.then(() => true),
      new Promise((_, reject) => setTimeout(() => reject(Error('Service worker not ready')), 8000))
    ])`);
    const manifest = await send('Page.getAppManifest');
    if (manifest.result?.errors?.length) throw Error(JSON.stringify(manifest.result.errors));
    const precached = await evaluate(`(async () => {
      const registration = await navigator.serviceWorker.ready;
      const names = await caches.keys();
      const name = names.find(name => name.startsWith('jananisync-shell-'));
      const cache = await caches.open(name);
      const assets = await (await cache.match(new URL('pwa-assets.json', location.href).href)).json();
      for (const path of assets) {
        if (!await cache.match(new URL(path, location.href).href)) throw Error('Not cached: ' + path);
      }
      return assets.length;
    })()`);
    exceptions.length = 0;
    await send('Network.enable');
    await send('Network.emulateNetworkConditions', {
      offline: true, latency: 0, downloadThroughput: 0, uploadThroughput: 0
    });
    await send('Page.reload', {ignoreCache: true});
    rendered = false;
    for (let i = 0; i < 120; i++) {
      const result = await send('Runtime.evaluate', {expression, returnByValue: true});
      if (result.result?.result?.value > 0) {rendered = true; break;}
      await pause(250);
    }
    await pause(2000);
    if (exceptions.length) throw Error(exceptions.join('\n'));
    if (!rendered) throw Error('App did not render after offline reload.');
    console.log(`PASS: manifest parsed, ${precached} build assets cached, app rendered after offline reload.`);
  } finally {
    socket?.close();
    const closed = once(chrome, 'close');
    chrome.kill('SIGTERM');
    await Promise.race([closed, pause(3000)]);
    if (chrome.exitCode === null && chrome.signalCode === null) chrome.kill('SIGKILL');
    fs.rmSync(profile, {recursive: true, force: true, maxRetries: 3, retryDelay: 100});
  }
})().catch(error => {console.error(error.message); process.exitCode = 1;});
