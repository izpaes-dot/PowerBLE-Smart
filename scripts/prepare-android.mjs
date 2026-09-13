import fs from 'node:fs';
import path from 'node:path';

const source = path.resolve('native/android/PowerBleNativePlugin.kt');

const destination = path.resolve(
  'android/app/src/main/java/com/izpaes/powerblesmart/PowerBleNativePlugin.kt'
);

const destinationDir = path.dirname(destination);

if (!fs.existsSync(source)) {
  throw new Error(`Arquivo nativo não encontrado: ${source}`);
}

fs.mkdirSync(destinationDir, { recursive: true });

fs.copyFileSync(source, destination);

console.log('✓ PowerBleNativePlugin.kt copiado para:');
console.log(destination);
