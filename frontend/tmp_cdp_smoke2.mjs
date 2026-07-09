import { spawn } from 'node:child_process';
import { setTimeout as sleep } from 'node:timers/promises';

const CHROME = '/usr/bin/google-chrome';
const DEV = 'http://localhost:8001';
const BASE = '/sei-online-code-web';
const CDP = 'http://localhost:9222';
const USER_DATA = '/tmp/cdp-smoke-' + Date.now();

const appUrl = (path) => `${DEV}${BASE}/#${BASE}${path}`;

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
  const eventQueue = [];
  ws.onmessage = ({ data }) => {
    const msg = JSON.parse(data);
    if (msg.id && pending.has(msg.id)) {
      pending.get(msg.id)(msg);
      pending.delete(msg.id);
    } else {
      eventQueue.push(msg);
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

  const waitFor = (method, timeout = 30000) =>
    new Promise((resolve, reject) => {
      const start = Date.now();
      const check = () => {
        const idx = eventQueue.findIndex((e) => e.method === method);
        if (idx >= 0) {
          resolve(eventQueue.splice(idx, 1)[0]);
          return;
        }
        if (Date.now() - start > timeout) {
          reject(new Error(`Timeout waiting for ${method}`));
          return;
        }
        setTimeout(check, 100);
      };
      check();
    });

  const close = () => ws.close();
  return { opened, send, waitFor, close };
}

async function evalExpr(c, expression, awaitPromise = false) {
  const res = await c.send('Runtime.evaluate', {
    expression,
    awaitPromise,
    returnByValue: true,
  });
  if (res.exceptionDetails) {
    throw new Error(`Eval error: ${expression}\n${JSON.stringify(res.exceptionDetails)}`);
  }
  return res.result.value;
}

async function navigate(c, url, settleMs = 3000) {
  await c.send('Page.navigate', { url });
  await c.waitFor('Page.loadEventFired');
  if (settleMs) await sleep(settleMs);
}

async function main() {
  console.log('Starting headless Chrome...');
  const chrome = spawn(
    CHROME,
    [
      '--headless',
      '--disable-gpu',
      '--no-sandbox',
      '--disable-setuid-sandbox',
      '--disable-dev-shm-usage',
      `--user-data-dir=${USER_DATA}`,
      '--remote-debugging-port=9222',
      'about:blank',
    ],
    { stdio: 'ignore' },
  );

  let c;
  try {
    await waitForCdp();
    console.log('Chrome ready');

    // Chrome ≥128 requires PUT on /json/new
    const tab = await fetch(`${CDP}/json/new?${encodeURIComponent(appUrl('/online-code/project?id=PRJ0001'))}`, { method: 'PUT' }).then((r) => r.json());
    c = connect(tab.webSocketDebuggerUrl);
    await c.opened;
    console.log('CDP connected');

    await c.send('Runtime.enable');
    await c.send('Page.enable');

    // Authenticate before any app navigation
    await evalExpr(
      c,
      `sessionStorage.setItem('CURRENT_USER', JSON.stringify({id:'1', userId:'1', loginAccount:'admin', userName:'Admin'})); 'ok'`,
    );
    console.log('Session user injected');

    // 1) Create a project to anchor the requirement flow
    const projectId = await evalExpr(
      c,
      `fetch('/mocker.api/sei-online-code/api/project/save', {
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body: JSON.stringify({name:'Smoke Project', design:'', gitUrl:'', workspacePath:'', autoRunCodingTask:false})
      }).then(r => r.json()).then(j => j.data.id)`,
      true,
    );
    console.log('✓ Created project', projectId);

    // 2) Project detail: "需求" tab with 查看需求 button
    await navigate(c, appUrl('/online-code/project?id=' + projectId));
    const projectText = await evalExpr(c, 'document.body.innerText');
    console.log('Project page sample:', projectText.replace(/\s+/g, ' ').slice(0, 180));
    if (!projectText.includes('查看需求')) {
      throw new Error('Missing 查看需求 button on project page');
    }
    console.log('✓ Project page renders 查看需求');

    // 3) Create a requirement through the in-page MSW layer
    const reqId = await evalExpr(
      c,
      `fetch('/mocker.api/sei-online-code/api/requirement/save', {
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body: JSON.stringify({projectId:'${projectId}', title:'Smoke Requirement', description:'desc'})
      }).then(r => r.json()).then(j => j.data.id)`,
      true,
    );
    console.log('✓ Created requirement', reqId);
    await sleep(800); // let PRD_GENERATING -> PRD_REVIEW complete

    // 4) Requirement list renders the new row
    await navigate(c, appUrl('/online-code/requirements?projectId=' + projectId));
    const listText = await evalExpr(c, 'document.body.innerText');
    if (!listText.includes('Smoke Requirement')) {
      throw new Error('Requirement list did not render Smoke Requirement');
    }
    console.log('✓ Requirement list renders Smoke Requirement');

    // 5) Workspace renders all tabs
    await navigate(c, appUrl('/online-code/requirement?id=' + reqId));
    const wsText = await evalExpr(c, 'document.body.innerText');
    for (const tabName of ['PRD', '概览设计', '详细设计', '编码任务', '运行历史']) {
      if (!wsText.includes(tabName)) {
        throw new Error(`Workspace missing tab: ${tabName}`);
      }
    }
    console.log('✓ Workspace renders tabs:', ['PRD', '概览设计', '详细设计', '编码任务', '运行历史'].join(', '));

    // 6) PRD edit -> save -> UI update
    const editOk = await evalExpr(
      c,
      `fetch('/mocker.api/sei-online-code/api/requirement/${reqId}/editPrd', {
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body: JSON.stringify({prdContent:'# Smoke PRD Updated'})
      }).then(r => r.json()).then(j => j.success)`,
      true,
    );
    if (!editOk) throw new Error('PRD edit endpoint returned failure');
    await navigate(c, appUrl('/online-code/requirement?id=' + reqId));
    const prdText = await evalExpr(c, 'document.body.innerText');
    if (!prdText.includes('Smoke PRD Updated')) {
      throw new Error('PRD edit did not update the UI');
    }
    console.log('✓ PRD edit saves and updates UI');

    // 7) Back navigation returns to requirements list
    await navigate(c, appUrl('/online-code/requirements?projectId=' + projectId));
    const backText = await evalExpr(c, 'document.body.innerText');
    if (!backText.includes('Smoke Requirement')) {
      throw new Error('Back navigation did not return to requirements list');
    }
    console.log('✓ Back navigation returns to requirements list');

    console.log('\n=== SMOKE TEST PASSED ===');
  } catch (e) {
    console.error('\nSMOKE TEST FAILED:', e);
    process.exitCode = 1;
  } finally {
    if (c) c.close();
    chrome.kill();
  }
}

main();
