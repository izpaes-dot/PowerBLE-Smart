import { PowerBLE } from './powerble-native.js';
import { PowerEngine } from './power-engine.js';

const app = document.querySelector('#app');
const engine = new PowerEngine();

app.innerHTML = `
<style>
body{font-family:system-ui;background:#111;color:#eee;margin:0;padding:16px}
button{padding:12px;margin:4px;border:0;border-radius:8px}
.card{background:#1d1d1d;padding:12px;border-radius:12px;margin:10px 0}
.grid{display:grid;grid-template-columns:repeat(2,1fr);gap:8px}
.value{font-size:24px;font-weight:700}
small{color:#aaa}
</style>
<h1>PowerBLE Smart</h1>
<div class="card">
<button id="scan">🔵 Procurar BLE</button>
<button id="gps">📍 Iniciar GPS</button>
<button id="ftms">📡 Iniciar FTMS</button>
<button id="stop">⏹ Parar</button>
</div>
<div class="card grid">
<div>RPM<div id="cad" class="value">--</div></div>
<div>BPM<div id="hr" class="value">--</div></div>
<div>Potência<div id="power" class="value">-- W</div></div>
<div>Velocidade<div id="speed" class="value">-- km/h</div></div>
<div>Inclinação<div id="grade" class="value">-- %</div></div>
<div>GPS<div id="gpsq" class="value">OFF</div></div>
</div>
<div class="card"><small id="log">Pronto.</small></div>
`;

const $=id=>document.getElementById(id);
const log=m=>$('log').textContent=m;

PowerBLE.on('cadence', d => {
  $('cad').textContent = Math.round(d.cadence ?? 0);
  engine.setCadence(d.cadence ?? 0);
  updatePower();
});
PowerBLE.on('heartRate', d => $('hr').textContent = Math.round(d.heartRate ?? 0));
PowerBLE.on('gps', d => {
  $('gpsq').textContent = d.accuracy != null ? `${Math.round(d.accuracy)} m` : 'OK';
  $('speed').textContent = `${((d.speedMps||0)*3.6).toFixed(1)} km/h`;
  $('grade').textContent = `${(d.grade||0).toFixed(1)} %`;
  engine.setGps(d);
  updatePower();
});
PowerBLE.on('bleDeviceFound', d => log(`BLE: ${d.name || d.address || 'dispositivo encontrado'}`));
PowerBLE.on('ftmsStatus', d => log(`FTMS: ${d.status}`));
PowerBLE.on('error', d => log(`Erro: ${d.message}`));

function updatePower(){
  const p=engine.calculate();
  $('power').textContent=`${Math.round(p)} W`;
  PowerBLE.updateFtms({
    powerWatts: p,
    cadence: engine.cadence,
    speedKmh: engine.speedKmh
  }).catch(()=>{});
}

$('scan').onclick=()=>PowerBLE.scan();
$('gps').onclick=()=>PowerBLE.startGps();
$('ftms').onclick=()=>PowerBLE.startFtms();
$('stop').onclick=()=>PowerBLE.stopAll();
