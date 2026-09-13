import { registerPlugin } from '@capacitor/core';
const PowerBleNative = registerPlugin('PowerBleNative');
window.PowerBleNative = PowerBleNative;
PowerBleNative.addListener('hrChanged', e => window.dispatchEvent(new CustomEvent('powerble:hr',{detail:e})));
PowerBleNative.addListener('cadenceChanged', e => window.dispatchEvent(new CustomEvent('powerble:cadence',{detail:e})));
PowerBleNative.addListener('powerConnectionChanged', e => window.dispatchEvent(new CustomEvent('powerble:connection',{detail:e})));
PowerBleNative.addListener('nativeError', e => window.dispatchEvent(new CustomEvent('powerble:error',{detail:e})));


// When running inside Capacitor, replace the browser-only BLE Power broadcaster
// with the Android GATT Server implementation. The original web app remains unchanged
// when opened in a normal browser.
window.addEventListener('DOMContentLoaded', () => {
  if (!window.PowerBleNative || typeof window.FTMSBroadcaster !== 'function') return;
  const Native = window.PowerBleNative;
  const startWeb = window.FTMSBroadcaster.prototype.start;
  window.FTMSBroadcaster.prototype.start = async function() {
    try {
      await Native.startPower({name:'PowerBLE Smart'});
      this.active = true; this.supported = true; this.lastError = null;
      return true;
    } catch (e) { this.lastError = e?.message || String(e); return false; }
  };
  window.FTMSBroadcaster.prototype.stop = function() {
    Native.stopPower().catch(()=>{});
    this.active = false; this.characteristic = null;
  };
  window.FTMSBroadcaster.prototype.updatePower = function(power, cadence) {
    this.currentPower = Math.round(Math.max(0, power || 0));
    this.currentCadence = Math.round(cadence || 0);
    if (this.active) Native.updatePower({power:this.currentPower, cadence:this.currentCadence}).catch(()=>{});
    this.packetsSent++;
  };
  window.FTMSBroadcaster.prototype.getStats = function() {
    return {active:this.active, packetsSent:this.packetsSent, currentPower:this.currentPower, currentCadence:this.currentCadence, supported:true, lastError:this.lastError};
  };
  console.log('PowerBLE Smart: Android native Cycling Power bridge active');
});
