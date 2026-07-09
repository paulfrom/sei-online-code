import { spawn } from 'node:child_process';
import { setTimeout as sleep } from 'node:timers/promises';

const CHROME = '/usr/bin/google-chrome';
const CDP = 'http://localhost:9222';
const USER_DATA = '/tmp/cdp-diag2-' + Date.now();

async function waitForCdp(retries = 40) {
  for (let i = 0; i < retries; i++) {
    try {
      const info = await fetch(`${CDP}/json/version`).then((r) => r.json());
      if (info?.Browser) return info;
    } catch {}
    await sleep(500);
  }
  throw new Error('Chrome DevTools not ready');
}

function connect(wsUrl) {
  const ws = new WebSocket(wsUrl);
  let openResolve;
  const opened = new Promise((r) => (openResolve = r));
  ws.onopen = () => openResolve();
  let nextId = 0;
  const pending = new Map();
  ws.onmessage = ({ data }) => {
    const msg = JSON.parse(data);
    if (msg.id && pending.has(msg.id)) {
      pending.get(msg.id)(msg);
      pending.delete(msg.id);
    } else {
      if (msg.method === 'Runtime.consoleAPICalled') {
        const args = (msg.params.args || []).map(a => a.value ?? a.description ?? a.type).join(' ');
        console.log('[CONSOLE]', msg.params.type, args);
      } else if (msg.method === 'Log.entryAdded') {
        console.log('[LOG]', msg.params.entry);
      } else {
        console.log('[CDP EVENT]', msg.method);
      }
    }
  };
  const send = (method, params = {}) =>
    new Promise((resolve, reject) => {
      nextId += 1;
      const id = nextId;
      pending.set(id, (msg) => {
        if (msg.error) reject(new Error(`${method}: ${JSON.stringify(msg.error)}`));
        else resolve(msg.result);
      });
      ws.send(JSON.stringify({ id, method, params }));
    });
  const close = () => ws.close();
  return { opened, send, close };
}

async function evalExpr(c, expression, awaitPromise = false) {
  const res = await c.send('Runtime.evaluate', { expression, awaitPromise, returnByValue: true });
  if (res.exceptionDetails) {
    throw new Error(`Eval error: ${expression}\n${JSON.stringify(res.exceptionDetails)}`);
  }
  return res.result.value;
}

async function navigate(c, url, settleMs = 5000) {
  await c.send('Page.navigate', { url });
  // wait a bit for load + settle
  await sleep(settleMs + 3000);
}

async function main() {
  const chrome = spawn(CHROME, ['--headless','--disable-gpu','--no-sandbox','--disable-setuid-sandbox','--disable-dev-shm-usage',`--user-data-dir=${USER_DATA}`,'--remote-debugging-port=9222','about:blank'], { stdio: 'ignore' });
  let c;
  try {
    await waitForCdp();
    const tab = await fetch(`${CDP}/json/new?${encodeURIComponent('http://localhost:8001/sei-online-code-web/#/online-code/project?id=PRJ0001')}`, { method: 'PUT' }).then((r) => r.json());
    c = connect(tab.webSocketDebuggerUrl);
    await c.opened;
    await c.send('Runtime.enable');
    await c.send('Page.enable');
    await c.send('Log.enable');
    await evalExpr(c, `sessionStorage.setItem('CURRENT_USER', JSON.stringify({id:'1', userId:'1', loginAccount:'admin', userName:'Admin'})); 'ok'`);
    await navigate(c, 'http://localhost:8001/sei-online-code-web/#/online-code/project?id=PRJ0001', 8000);
    const innerText = await evalExpr(c, 'document.body.innerText');
    console.log('innerText sample:', innerText.replace(/\s+/g, ' ').slice(0, 400));
    const loc = await evalExpr(c, 'JSON.stringify({href:location.href, pathname:location.pathname, hash:location.hash})');
    console.log('location:', loc);
  } finally {
    if (c) c.close();
    chrome.kill();
  }
}

main().catch(e => { console.error(e); process.exit(1); });
