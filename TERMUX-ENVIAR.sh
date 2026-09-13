#!/data/data/com.termux/files/usr/bin/bash
set -e

REPO_DIR="$HOME/storage/downloads/PowerBLE-Smart"

echo "=== PowerBLE Smart - envio pelo Termux ==="

if [ ! -d "$REPO_DIR" ]; then
  echo "ERRO: pasta não encontrada:"
  echo "$REPO_DIR"
  exit 1
fi

cd "$REPO_DIR"

echo "[1/5] Verificando arquivos..."
test -f package.json
test -f scripts/prepare-android.mjs
test -f native/android/PowerBleNativePlugin.kt

echo "[2/5] Corrigindo .gitignore..."
if ! grep -q '^!native/' .gitignore 2>/dev/null; then
cat >> .gitignore <<'EOF'
!native/
!native/android/
!native/android/*.kt
EOF
fi

echo "[3/5] Instalando dependências..."
npm install

echo "[4/5] Adicionando arquivos..."
git add package.json package-lock.json capacitor.config.ts index.html src scripts native .github .gitignore
git add -f native/android/*.kt

if git diff --cached --quiet; then
  echo "Nenhuma alteração para enviar."
else
  git commit -m "Implement native GPS BLE CSC HR and FTMS Android layer"
fi

echo "[5/5] Enviando para GitHub..."
git push origin main

echo
echo "=========================================="
echo "ENVIO CONCLUÍDO"
echo "GitHub: https://github.com/izpaes-dot/PowerBLE-Smart"
echo "=========================================="
