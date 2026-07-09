import { spawn } from 'node:child_process';
import { setTimeout as sleep } from 'node:timers/promises';

setTimeout(() => { console.error('WATCHDOG: 35s elapsed, exiting'); process.exit(2); }, 35000);

console.log('A: starting chrome');
const chrome = spawn('/usr/bin/google-chrome', [
  '--headless','--disable-gpu','--no-sandbox','--disable-setuid-sandbox','--disable-dev-shm-usage',
  '--user-data-dir=/tmp/cdp-smoke3','--remote-debugging-port=9222','about:blank'
], {stdio:'ignore'});
console.log('A: chrome pid', chrome.pid);

async function waitCdp(){
  for(let i=0;i<30;i++){
    try {
      const v = await fetch('http://localhost:9222/json/version').then(r=>r.json());
      if(v.Browser) return v;
    } catch {}
    await sleep(300);
  }
  throw new Error('cdp not ready');
}

console.log('B: waiting cdp');
const ver = await waitCdp();
console.log('B: cdp ready', ver.Browser);

console.log('C: create tab');
const tab = await fetch('http://localhost:9222/json/new?'+encodeURIComponent('http://localhost:8001/online-code/project?id=PRJ0001')).then(r=>r.json());
console.log('C: tab id', tab.id, 'ws', tab.webSocketDebuggerUrl);

console.log('D: connect ws');
const ws = new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,reject)=>{ ws.onopen=r; ws.onerror=(e)=>reject(new Error('ws error '+e.message)); });
console.log('D: ws open');

let id=0;
function send(method,params={}){
  return new Promise((resolve,reject)=>{
    id++;
    const myId=id;
    const handler = (msg)=>{
      const d=JSON.parse(msg.data);
      if(d.id===myId){ ws.onmessage=null; if(d.error) reject(d.error); else resolve(d.result); }
    };
    ws.onmessage=handler;
    ws.send(JSON.stringify({id:myId,method,params}));
  });
}

console.log('E: enable');
await send('Runtime.enable');
console.log('E1');
await send('Page.enable');
console.log('E2');

console.log('F: set user');
await send('Runtime.evaluate',{expression:"sessionStorage.setItem('CURRENT_USER', JSON.stringify({id:'1'})); 'ok'", returnByValue:true});
console.log('F done');

console.log('G: navigate');
await send('Page.navigate',{url:'http://localhost:8001/online-code/project?id=PRJ0001'});
console.log('G sent');
await new Promise(r=>{ ws.onmessage=({data})=>{ if(JSON.parse(data).method==='Page.loadEventFired') r(); }; });
console.log('G loaded');
await sleep(3000);

console.log('H: eval text');
const txt = await send('Runtime.evaluate',{expression:'document.body.innerText', returnByValue:true});
console.log('TEXT:', txt.result.value.slice(0,300));

ws.close();
chrome.kill();
console.log('DONE');
