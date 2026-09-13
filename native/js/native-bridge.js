import { registerPlugin } from '@capacitor/core';
const PowerBleNative = registerPlugin('PowerBleNative');
window.PowerBleNative = PowerBleNative;

const DIAG_KEY = 'powerble_smart_diagnostic_v1';
const diag = { startedAt:new Date().toISOString(), events:[], devices:[], environment:{}, summary:{} };
function record(type, data={}) { const e={ts:new Date().toISOString(), type, ...data}; diag.events.push(e); if(diag.events.length>5000)diag.events.shift(); return e; }
function emit(type,data={}){ const e=record(type,data); window.dispatchEvent(new CustomEvent('powerble:diagnostic',{detail:e})); return e; }
window.PowerBleDiagnostic = {
  data:diag,
  log:(type,data)=>emit(type,data),
  clear:()=>{diag.events=[];diag.devices=[];diag.startedAt=new Date().toISOString();emit('diagnostic.cleared')},
  export:()=>{ const blob=new Blob([JSON.stringify(diag,null,2)],{type:'application/json'}); const a=document.createElement('a'); a.href=URL.createObjectURL(blob); a.download=`PowerBLE_Diagnostic_${new Date().toISOString().replace(/[:.]/g,'-')}.json`; a.click(); setTimeout(()=>URL.revokeObjectURL(a.href),2000); emit('diagnostic.exported'); },
  text:()=>diag.events.map(e=>`${e.ts} | ${e.type} | ${JSON.stringify(e)}`).join('\n')
};

PowerBleNative.addListener('diagnostic', e=>emit('native',e));
PowerBleNative.addListener('deviceFound', e=>{ if(!diag.devices.some(d=>d.address===e.address))diag.devices.push(e); emit('ble.deviceFound',e); });
PowerBleNative.addListener('gattState', e=>emit('ble.gattState',e));
PowerBleNative.addListener('servicesDiscovered', e=>emit('ble.servicesDiscovered',e));
PowerBleNative.addListener('serviceDetected', e=>emit('ble.serviceDetected',e));
PowerBleNative.addListener('hrChanged', e=>{window.dispatchEvent(new CustomEvent('powerble:hr',{detail:e}));emit('hr.data',e)});
PowerBleNative.addListener('cadenceChanged', e=>{window.dispatchEvent(new CustomEvent('powerble:cadence',{detail:e}));emit('csc.data',e)});
PowerBleNative.addListener('ftmsData', e=>emit('ftms.data',e));
PowerBleNative.addListener('ftmsStatus', e=>emit('ftms.status',e));
PowerBleNative.addListener('powerConnectionChanged', e=>{window.dispatchEvent(new CustomEvent('powerble:connection',{detail:e}));emit('broadcast.state',e)});
PowerBleNative.addListener('nativeError', e=>{window.dispatchEvent(new CustomEvent('powerble:error',{detail:e}));emit('error.native',e)});

async function nativeDiagnostics(){
  try { const x=await PowerBleNative.getDiagnostics(); diag.environment={...diag.environment,...x}; emit('environment.native',x); return x; }
  catch(e){ emit('environment.native.error',{message:e?.message||String(e)}); return null; }
}
window.PowerBleDiagnostic.nativeDiagnostics=nativeDiagnostics;
window.PowerBleDiagnostic.scan=async()=>{emit('ble.scan.request');try{const r=await PowerBleNative.startScan();emit('ble.scan.started',r);return r}catch(e){emit('ble.scan.error',{message:e?.message||String(e)});throw e}};
window.PowerBleDiagnostic.stopScan=async()=>{try{return await PowerBleNative.stopScan()}finally{emit('ble.scan.stopped')}};
window.PowerBleDiagnostic.connect=async(address)=>{emit('ble.connect.request',{address});try{return await PowerBleNative.connectDevice({address})}catch(e){emit('ble.connect.error',{address,message:e?.message||String(e)});throw e}};
window.PowerBleDiagnostic.disconnect=async()=>PowerBleNative.disconnectDevice();

function installPanel(){
  if(document.getElementById('powerbleDiag'))return;
  const style=document.createElement('style'); style.textContent=`#powerbleDiag{position:fixed;inset:0;z-index:9999;background:rgba(4,8,15,.96);color:#e5edf7;font:14px system-ui;padding:14px;display:none;overflow:auto}#powerbleDiag.open{display:block}.pbd-head{display:flex;justify-content:space-between;align-items:center;gap:8px;position:sticky;top:0;background:#0b1220;padding:8px 0}.pbd-title{font-weight:800;color:#00d9ff}.pbd-btn{border:1px solid #334155;background:#111827;color:#fff;border-radius:8px;padding:9px 11px;font-weight:700}.pbd-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(145px,1fr));gap:7px;margin:10px 0}.pbd-card{background:#111827;border:1px solid #263449;border-radius:9px;padding:9px}.pbd-v{font-size:1.25rem;font-weight:800}.pbd-ok{color:#10b981}.pbd-bad{color:#ef4444}.pbd-warn{color:#f59e0b}.pbd-log{white-space:pre-wrap;background:#050a12;border:1px solid #263449;border-radius:8px;padding:9px;font:11px monospace;max-height:42vh;overflow:auto}.pbd-dev{display:flex;justify-content:space-between;gap:8px;align-items:center;background:#0b1220;border:1px solid #263449;border-radius:8px;padding:8px;margin:5px 0}`;document.head.appendChild(style);
  const root=document.createElement('div');root.id='powerbleDiag';root.innerHTML=`<div class="pbd-head"><div class="pbd-title">🔬 PowerBLE Smart — Diagnóstico</div><div><button class="pbd-btn" id="pbdClose">Fechar</button></div></div><div class="pbd-grid" id="pbdSummary"></div><div><button class="pbd-btn" id="pbdTest">▶ Testar BLE</button> <button class="pbd-btn" id="pbdExport">⬇ Exportar JSON</button> <button class="pbd-btn" id="pbdClear">Limpar</button></div><h3>Dispositivos encontrados</h3><div id="pbdDevices"></div><h3>Eventos</h3><div class="pbd-log" id="pbdLog"></div>`;document.body.appendChild(root);
  const refresh=()=>{const env=diag.environment||{};const counts={gps:!!navigator.geolocation,bt:!!env.bluetoothAvailable,btOn:!!env.bluetoothEnabled,scan:!!env.scanPermission,conn:!!env.connectPermission,adv:!!env.advertisePermission};document.getElementById('pbdSummary').innerHTML=Object.entries({'GPS API':counts.gps,'Bluetooth':counts.bt,'BT ligado':counts.btOn,'SCAN perm.':counts.scan,'CONNECT perm.':counts.conn,'ADVERTISE perm.':counts.adv,'Eventos':diag.events.length,'Dispositivos':diag.devices.length}).map(([k,v])=>`<div class="pbd-card"><div>${k}</div><div class="pbd-v ${typeof v==='number'?'':v?'pbd-ok':'pbd-bad'}">${typeof v==='number'?v:(v?'OK':'FALHA')}</div></div>`).join('');document.getElementById('pbdDevices').innerHTML=diag.devices.map(d=>`<div class="pbd-dev"><span><b>${d.name||'(sem nome)'}</b><br><small>${d.address} · RSSI ${d.rssi} · ${d.services||''}</small></span><button class="pbd-btn" data-address="${d.address}">Conectar</button></div>`).join('')||'<small>Nenhum dispositivo encontrado.</small>';document.getElementById('pbdLog').textContent=PowerBleDiagnostic.text();};
  window.addEventListener('powerble:diagnostic',refresh); root.querySelector('#pbdClose').onclick=()=>root.classList.remove('open');root.querySelector('#pbdExport').onclick=()=>PowerBleDiagnostic.export();root.querySelector('#pbdClear').onclick=()=>{PowerBleDiagnostic.clear();refresh()};root.querySelector('#pbdTest').onclick=async()=>{await nativeDiagnostics();try{await PowerBleDiagnostic.scan()}catch(e){}setTimeout(()=>refresh(),8000)};root.querySelector('#pbdDevices').addEventListener('click',e=>{const a=e.target.closest('[data-address]')?.dataset.address;if(a)PowerBleDiagnostic.connect(a).catch(()=>{})});
  window.PowerBleDiagnostic.open=()=>{root.classList.add('open');nativeDiagnostics().finally(refresh)};
  refresh();
}

window.addEventListener('DOMContentLoaded',()=>{
  installPanel();
  nativeDiagnostics();
  const Native=window.PowerBleNative;
  if(!Native || typeof window.FTMSBroadcaster!=='function') return;
  window.FTMSBroadcaster.prototype.start=async function(){try{await Native.startPower({name:'PowerBLE Smart'});this.active=true;this.supported=true;this.lastError=null;return true}catch(e){this.lastError=e?.message||String(e);emit('ftms.broadcast.start.error',{message:this.lastError});return false}};
  window.FTMSBroadcaster.prototype.stop=function(){Native.stopPower().catch(()=>{});this.active=false;this.characteristic=null};
  window.FTMSBroadcaster.prototype.updatePower=function(power,cadence){this.currentPower=Math.round(Math.max(0,power||0));this.currentCadence=Math.round(cadence||0);if(this.active)Native.updatePower({power:this.currentPower,cadence:this.currentCadence}).catch(()=>{});this.packetsSent++};
  window.FTMSBroadcaster.prototype.getStats=function(){return {active:this.active,packetsSent:this.packetsSent,currentPower:this.currentPower,currentCadence:this.currentCadence,supported:true,lastError:this.lastError}};
  emit('bridge.ready',{capacitor:true});
});
