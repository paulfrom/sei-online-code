import { spawn } from 'node:child_process';
import { setTimeout as sleep } from 'node:timers/promises';

const CHROME = '/usr/bin/google-chrome';
const CDP = 'http://localhost:9222';
const USER_DATA = '/tmp/cdp-diag3-' + Date.now();

async function waitForCdp(retries = 40) {
  for (let i = 0; i < retries; i++) {
    try { if ((await fetch(`${CDP}/json/version`).then(r=>r.json()))?.Browser) return; } catch {}
    await sleep(500);
  }
  throw new Error('not ready');
}
function connect(wsUrl) {
  const ws = new WebSocket(wsUrl);
  let openResolve; const opened = new Promise(r=>openResolve=r);
  ws.onopen = () => openResolve();
  let nextId=0; const pending=new Map();
  ws.onmessage = ({data}) => {
    const msg=JSON.parse(data);
    if (msg.id && pending.has(msg.id)) { pending.get(msg.id)(msg); pending.delete(msg.id); }
  };
  const send=(method,params={})=>new Promise((resolve,reject)=>{nextId++;const id=nextId;pending.set(id,(msg)=>{if(msg.error) reject(new Error(JSON.stringify(msg.error))); else resolve(msg.result);});ws.send(JSON.stringify({id,method,params}));});
  const close=()=>ws.close();
  return {opened,send,close};
}
async function evalExpr(c,expression,awaitPromise=false){const res=await c.send('Runtime.evaluate',{expression,awaitPromise,returnByValue:true}); if(res.exceptionDetails) throw new Error(JSON.stringify(res.exceptionDetails)); return res.result.value;}

async function main() {
  const chrome = spawn(CHROME, ['--headless','--disable-gpu','--no-sandbox','--disable-setuid-sandbox','--disable-dev-shm-usage',`--user-data-dir=${USER_DATA}`,'--remote-debugging-port=9222','about:blank'], {stdio:'ignore'});
  let c;
  try {
    await waitForCdp();
    const tab = await fetch(`${CDP}/json/new?${encodeURIComponent('http://localhost:8001/sei-online-code-web/#/online-code/project')}`, {method:'PUT'}).then(r=>r.json());
    c = connect(tab.webSocketDebuggerUrl); await c.opened;
    await c.send('Runtime.enable'); await c.send('Page.enable');
    await evalExpr(c, `sessionStorage.setItem('CURRENT_USER', JSON.stringify({id:'1',userId:'1',loginAccount:'admin',userName:'Admin'})); 'ok'`);
    await c.send('Page.navigate', {url:'http://localhost:8001/sei-online-code-web/#/online-code/project'});
    await sleep(8000);
    const result = await evalExpr(c, `
      (async () => {
        const r = await fetch('/mocker.api/sei-online-code/api/project/save', {
          method:'POST', headers:{'Content-Type':'application/json'},
          body: JSON.stringify({name:'Diag Project', design:'', gitUrl:'', workspacePath:'', autoRunCodingTask:false})
        });
        const text = await r.text();
        return {status:r.status, statusText:r.statusText, text:text.slice(0,500)};
      })()
    `, true);
    console.log('FETCH RESULT', JSON.stringify(result,null,2));
  } finally { if(c)c.close(); chrome.kill(); }
}
main().catch(e=>{console.error(e);process.exit(1);});
