#!/data/data/com.termux/files/usr/bin/bash

set -u

PROJECT="$HOME/PowerBLE-Smart"

echo
echo "=============================================="
echo "     PowerBLE Smart - Preparar Android"
echo "=============================================="
echo

cd "$PROJECT" || {
    echo "ERRO: projeto não encontrado: $PROJECT"
    exit 1
}

# ============================================================
# [1/10] VERIFICAR PROJETO
# ============================================================

echo "[1/10] Verificando estrutura do projeto..."

for item in package.json capacitor.config.ts.bak src native scripts; do
    if [ ! -e "$item" ]; then
        echo "ERRO: item obrigatório não encontrado: $item"
        exit 1
    fi
done

echo "OK: estrutura básica encontrada."
echo

# ============================================================
# [2/10] MOSTRAR VERSÕES
# ============================================================

echo "[2/10] Verificando Node e npm..."

echo "Node:"
node --version

echo "npm:"
npm --version

echo

# ============================================================
# [3/10] BACKUP DA CONFIGURAÇÃO
# ============================================================

echo "[3/10] Fazendo backup da configuração..."

if [ -f capacitor.config.ts ]; then
    cp capacitor.config.ts capacitor.config.ts.backup-$(date +%Y%m%d-%H%M%S)
    echo "Backup do capacitor.config.ts criado."
fi

echo

# ============================================================
# [4/10] CRIAR CONFIGURAÇÃO JSON
# ============================================================

echo "[4/10] Criando capacitor.config.json..."

cat > capacitor.config.json <<'EOF'
{
  "appId": "com.izpaes.powerblesmart",
  "appName": "PowerBLE Smart",
  "webDir": "dist",
  "bundledWebRuntime": false
}
EOF

if [ ! -f capacitor.config.json ]; then
    echo "ERRO: não foi possível criar capacitor.config.json"
    exit 1
fi

echo "Configuração criada:"
cat capacitor.config.json

echo

# ============================================================
# [5/10] DESATIVAR CONFIG TS
# ============================================================

echo "[5/10] Desativando capacitor.config.ts..."

if [ -f capacitor.config.ts ]; then
    mv capacitor.config.ts capacitor.config.ts.disabled
    echo "capacitor.config.ts -> capacitor.config.ts.disabled"
else
    echo "capacitor.config.ts já não está ativo."
fi

echo

# ============================================================
# [6/10] REMOVER TYPESCRIPT PROBLEMÁTICO
# ============================================================

echo "[6/10] Removendo TypeScript 7..."

npm uninstall typescript

if [ $? -ne 0 ]; then
    echo "AVISO: npm uninstall retornou erro."
    echo "Continuando porque TypeScript não é necessário para a configuração JSON."
fi

echo

# ============================================================
# [7/10] VERIFICAR CAPACITOR
# ============================================================

echo "[7/10] Testando Capacitor..."

CAP_VERSION=$(npx cap --version 2>&1)
CAP_STATUS=$?

echo "$CAP_VERSION"

if [ $CAP_STATUS -ne 0 ]; then
    echo
    echo "ERRO: Capacitor ainda não iniciou corretamente."
    echo
    echo "Não será criada a plataforma Android."
    exit 1
fi

echo
echo "OK: Capacitor funcionando."
echo

# ============================================================
# [8/10] GERAR BUILD WEB
# ============================================================

echo "[8/10] Gerando aplicação web..."

npm run build

if [ $? -ne 0 ]; then
    echo
    echo "ERRO: npm run build falhou."
    exit 1
fi

if [ ! -d dist ]; then
    echo
    echo "ERRO: pasta dist não foi criada."
    exit 1
fi

echo
echo "OK: dist criada."
echo

# ============================================================
# [9/10] CRIAR ANDROID
# ============================================================

echo "[9/10] Criando plataforma Android..."

if [ -d android ]; then
    echo "A pasta android já existe."
    echo "Não será recriada."
else
    npx cap add android

    if [ $? -ne 0 ]; then
        echo
        echo "ERRO: não foi possível criar a plataforma Android."
        exit 1
    fi
fi

echo

# ============================================================
# [10/10] SINCRONIZAR
# ============================================================

echo "[10/10] Sincronizando Capacitor..."

npx cap sync android

if [ $? -ne 0 ]; then
    echo
    echo "ERRO: sincronização Android falhou."
    exit 1
fi

echo
echo "=============================================="
echo "        PREPARAÇÃO CONCLUÍDA"
echo "=============================================="
echo
echo "Projeto:"
echo "$PROJECT"
echo
echo "Android:"
echo "$PROJECT/android"
echo
echo "Arquivos importantes:"
echo "  capacitor.config.json"
echo "  dist/"
echo "  android/"
echo
echo "Estrutura Android:"
ls -la android
echo
echo "=============================================="
echo " PRÓXIMO PASSO: COMPILAR APK"
echo "=============================================="
echo
echo "Execute:"
echo
echo "cd ~/PowerBLE-Smart/android"
echo "chmod +x gradlew"
echo "./gradlew assembleDebug"
echo

