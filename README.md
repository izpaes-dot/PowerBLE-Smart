# PowerBLE Smart Android

Projeto Capacitor 8 baseado no `powerblesmart.html` fornecido.

## V1
- UI e motor web originais preservados.
- Android via Capacitor.
- GATT Server nativo.
- Cycling Power Service `0x1818`.
- Cycling Power Measurement `0x2A63`.
- Advertising BLE como PowerBLE Smart.
- JavaScript envia potência para o periférico nativo.
- GitHub Actions gera APK Debug.

## Build
```bash
npm install
npm run build
npx cap add android
npm run prepare:android
npx cap sync android
npx cap open android
```

Ou `cd android && ./gradlew assembleDebug`.

## Importante
A V1 prioriza validar o ponto crítico: Android como BLE Power Meter Peripheral. HR e cadência nativos ficam preparados para a V1.1; o HTML original continua podendo usar Web Bluetooth onde suportado.
