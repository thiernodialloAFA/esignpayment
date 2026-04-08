#!/usr/bin/env bash
###############################################################################
#  Start baseline + optimized (local dev)
#  Usage: bash greenanalyzer/scripts/start_light.sh [--analyze] [--debug] [--appname <name>]
###############################################################################
set -uo pipefail   # pas de -e : on gère les erreurs manuellement
GREEN_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="$(cd "$GREEN_DIR/.." && pwd)"

# Parse options
DEBUG_FLAG=""
APPNAME="${APPNAME:-}"
args=("$@")
i=0
while [ $i -lt ${#args[@]} ]; do
  case "${args[$i]}" in
    --debug) DEBUG_FLAG="--debug" ;;
    --appname)
      i=$((i + 1))
      APPNAME="${args[$i]:-}"
      ;;
  esac
  i=$((i + 1))
done

# Default APPNAME = root folder basename
APPNAME="${APPNAME:-$(basename "$ROOT")}"
export APPNAME

# Détection automatique : docker ou podman ?
source "$GREEN_DIR/scripts/_container-runtime.sh"

# Suppress Podman "Executing external compose provider" warning (ignoré si docker)
export PODMAN_COMPOSE_WARNING_LOGS=false

# (Optionnel) Décommenter pour lancer compose depuis ce script :
# $CONTAINER_COMPOSE down --remove-orphans --timeout 5 2>/dev/null || true
# if [[ "$(uname -s)" == Darwin ]]; then
#   osascript -e "tell application \"Terminal\" to do script \"cd '$ROOT' && $CONTAINER_COMPOSE up --build --force-recreate --remove-orphans\""
# else
#   mintty --title "Container Compose" -e bash -c "cd '$ROOT' && $CONTAINER_COMPOSE up --build --force-recreate --remove-orphans; read -p 'Appuyez sur Entrée pour fermer...'" &
# fi

echo "⏳ Attente du démarrage des services 20s..."
sleep 20

ANALYZE=false
if [[ "${1:-}" == "--analyze" ]] || [[ "${2:-}" == "--analyze" ]]; then
  ANALYZE=true
fi

# --- Attente du démarrage des 2 services (max 30s) ---
echo ""
echo "⏳ Attente du démarrage des services (max 30s)..."
TIMEOUT=30
ELAPSED=0
BASE_READY=false
OPT_READY=false

while [ "$ELAPSED" -lt "$TIMEOUT" ]; do
  # Vérifier que les processus tournent encore

  if ! $BASE_READY; then
    if curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; then
      BASE_READY=true
      echo "  ✅ Baseline (8080) prêt après ${ELAPSED}s"
    fi
  fi

  if $BASE_READY; then
    echo "🚀 Les services sont démarrés !"
    break
  fi
  sleep 1
  ELAPSED=$((ELAPSED + 1))
done

if ! $BASE_READY; then
  echo ""
  echo "⚠️  Timeout (${TIMEOUT}s) — services non prêts :"
  $BASE_READY || echo "    ❌ Baseline (8080) non disponible"
  $OPT_READY || echo "    ❌ Optimized (8081) non disponible"
  echo ""
  echo "🛑 Arrêt — l'analyse ne sera pas lancée."
  exit 1
fi
echo ""

# --- Attente de Keycloak (port 9090, max 30s) pour acquérir le JWT ---
KEYCLOAK_URL=${KEYCLOAK_URL:-"http://localhost:9090"}
KEYCLOAK_REALM=${KEYCLOAK_REALM:-"esignpayment"}
KEYCLOAK_CLIENT_ID=${KEYCLOAK_CLIENT_ID:-"esignpay-frontend"}
KEYCLOAK_USER=${KEYCLOAK_USER:-"admin@test.com"}
KEYCLOAK_PASSWORD=${KEYCLOAK_PASSWORD:-"password"}

echo "⏳ Attente de Keycloak (${KEYCLOAK_URL})..."
KC_READY=false
for kc_i in $(seq 1 30); do
  if curl -sf "${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}" -o /dev/null 2>/dev/null; then
    KC_READY=true
    echo "  ✅ Keycloak prêt"
    break
  fi
  sleep 1
done

BEARER_TOKEN=""
if [ "$KC_READY" = true ]; then
  echo "🔐 Acquisition du token JWT..."
  TOKEN_ENDPOINT="${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token"
  TOKEN_RESPONSE=$(curl -s --connect-timeout 10 --max-time 15 -X POST "$TOKEN_ENDPOINT" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password" \
    -d "client_id=${KEYCLOAK_CLIENT_ID}" \
    -d "username=${KEYCLOAK_USER}" \
    -d "password=${KEYCLOAK_PASSWORD}" 2>/dev/null || echo "")
  BEARER_TOKEN=$(echo "$TOKEN_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('access_token',''))" 2>/dev/null || echo "")
  if [ -n "$BEARER_TOKEN" ]; then
    echo "  ✅ JWT token acquis (user: ${KEYCLOAK_USER})"
  else
    echo "  ⚠️  Impossible d'acquérir le token — les endpoints protégés retourneront 401"
  fi
else
  echo "  ⚠️  Keycloak non disponible — les endpoints protégés retourneront 401"
fi
export BEARER_TOKEN
echo ""

echo "Running Green Score analyzer..."
bash "$GREEN_DIR/scripts/green-score-analyzer_withdiscovery.sh" $DEBUG_FLAG || true

# ── Creedengo eco-design analysis (optional, requires Docker) ──
RUN_CREEDENGO=false
for arg in "$@"; do
  case "$arg" in
    --creedengo) RUN_CREEDENGO=true ;;
  esac
done

if [ "$RUN_CREEDENGO" = true ]; then
  echo ""
  echo "Running Creedengo eco-design code analyzer..."
  bash "$GREEN_DIR/scripts/creedengo-analyzer.sh" $DEBUG_FLAG --skip-build || true
else
  echo ""
  echo "💡 Tip: run with --creedengo to also run Creedengo eco-design code analysis"
fi

echo "Press Ctrl+C to stop."
trap - EXIT    # désactive le cleanup auto, on attend manuellement
wait

