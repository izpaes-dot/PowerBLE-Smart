import fs from 'node:fs';
import path from 'node:path';

const sourceDir = path.resolve('native/android');
const destinationDir = path.resolve(
  'android/app/src/main/java/com/izpaes/powerblesmart'
);

if (!fs.existsSync(sourceDir)) {
  throw new Error(`Diretório nativo não encontrado: ${sourceDir}`);
}
fs.mkdirSync(destinationDir, { recursive: true });

for (const file of fs.readdirSync(sourceDir)) {
  if (!file.endsWith('.kt')) continue;
  fs.copyFileSync(path.join(sourceDir,file), path.join(destinationDir,file));
  console.log(`✓ ${file}`);
}
console.log('✓ Fontes Kotlin preparados.');
