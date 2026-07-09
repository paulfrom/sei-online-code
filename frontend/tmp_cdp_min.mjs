import { spawn } from 'node:child_process';
import { setTimeout as sleep } from 'node:timers/promises';

async function waitCdp() {
  for (let i=0;i<30;i++){
    try { return await fetch('http://localhost:9222/json/version').then(r=>r.json()); }
    catch { await sleep(500); }
  }
  throw new Error('no cdp');
}

const chrome = spawn('/usr/bin/google-chrome', [
  '--headless','--disable-gpu','--no-sandbox','--disable-setuid-sandbox','--disable-dev-shm-usage',
  '--user-data-dir=/tmp/cdp-min','--remote-debugging-port=9222','about:blank'
], {stdio:'ignore'});

await waitCdp();
console.log('cdp ready');
const tab = await fetch('http://localhost:9222/json/new?'+encodeURIComponent('http://localhost:8001/online-code/project?id=PRJ0001')).then(r=>r.json());
console.log('tab', tab.id);
const ws = new WebSocket(tab.webSocketDebuggerUrl);
await new Promise(r=>ws.onopen=r);
console.log('ws open');
let id=0;
function send(method,params={}){
  id++; ws.send(JSON.stringify({id,method,params}));
  return new Promise((resolve,reject)=>{
    const handler=(msg)=>{
      const d=JSON.parse(msg.data);
      if(d.id===id){
        ws.removeEventListener('message',handler);
        if(d.error) reject(d.error); else resolve(d.result);
      }
    };
    ws.addEventListener('message',handler);
  });
}
await send('Runtime.enable');
await send('Page.enable');
console.log('enabled');
await send('Runtime.evaluate',{expression:"sessionStorage.setItem('CURRENT_USER', JSON.stringify({id:'1', loginAccount:'admin'})); 'ok'", returnByValue:true});
console.log('user set');
await send('Page.navigate',{url:'http://localhost:8001/online-code/project?id=PRJ0001'});
console.log('navigate sent');
await new Promise(r=>{
  const h=(msg)=>{ const d=JSON.parse(msg.data); if(d.method==='Page.loadEventFired'){ ws.removeEventListener('message',h); r(); } };
  ws.addEventListener('message',h);
});
console.log('load fired');
await sleep(3000);
const txt = await send('Runtime.evaluate',{expression:'document.body.innerText', returnByValue:true});
console.log('TEXT:', txt.result.value.slice(0,500));
ws.close(); chrome.kill();
