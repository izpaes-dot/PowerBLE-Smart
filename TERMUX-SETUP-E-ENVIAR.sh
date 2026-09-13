#!/data/data/com.termux/files/usr/bin/bash
set -e

REPO_DIR="$HOME/storage/downloads/PowerBLE-Smart"

echo "=== PowerBLE Smart - preparação completa ==="

pkg update -y
pkg install -y git nodejs

termux-setup-storage || true

if [ ! -d "$REPO_DIR/.git" ]; then
  echo "Pasta Git não encontrada em $REPO_DIR"
  echo "Clone o repositório primeiro:"
  echo "git clone https://github.com/izpaes-dot/PowerBLE-Smart.git $REPO_DIR"
  exit 1
fi

cd "$REPO_DIR"

npm install

git add package.json package-lock.json capacitor.config.ts index.html src scripts native .github .gitignore
git add -f native/android/*.kt

if ! git diff --cached --quiet; then
  git commit -m "Implement native GPS BLE CSC HR and FTMS Android layer"
fi

git push origin main

echo "Concluído."
