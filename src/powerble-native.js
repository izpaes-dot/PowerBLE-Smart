import { registerPlugin } from '@capacitor/core';

const Native = registerPlugin('PowerBleNativePlugin');

export const PowerBLE = {
  on(event, callback) {
    return Native.addListener(event, callback);
  },
  scan() {
    return Native.scan();
  },
  connect(deviceId) {
    return Native.connect({ deviceId });
  },
  startGps() {
    return Native.startGps();
  },
  stopGps() {
    return Native.stopGps();
  },
  startFtms() {
    return Native.startFtms();
  },
  stopFtms() {
    return Native.stopFtms();
  },
  updateFtms(data) {
    return Native.updateFtms(data);
  },
  stopAll() {
    return Native.stopAll();
  }
};
