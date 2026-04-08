#!/usr/bin/env bash
###############################################################################
#  Green Score Analyzer WITH DISCOVERY (Fully Dynamic)
#  ====================================================
#  All endpoint measurements are discovered from the OpenAPI/Swagger spec.
#  No hardcoded endpoints — works with any API.
#
#  This script is a thin wrapper around green-api-auto-discover.py which
#  handles all discovery, measurement, scoring, and reporting.
#
#  Port layout (avoid collisions with esign backend):
#    - esign backend:  8080  (target for measurement)
#    - Keycloak:       9090  (token acquisition)
#    - SonarQube:      9100  (Creedengo analysis, separate script)
#    - OTEL Collector: 4317/4318
#
#  Usage:
#    bash green-score-analyzer_withdiscovery.sh
#    bash green-score-analyzer_withdiscovery.sh --debug
#    OPTIMIZED_PORT=8080 bash green-score-analyzer_withdiscovery.sh
#    SWAGGER_URL=http://localhost:8080/api/v3/api-docs bash green-score-analyzer_withdiscovery.sh
###############################################################################
set -euo pipefail

# ── Parse options ──
DEBUG_MODE=false
EXTRA_ARGS=()
for arg in "$@"; do
  case "$arg" in
    --debug) DEBUG_MODE=true ;;
    *) EXTRA_ARGS+=("$arg") ;;
  esac
done

# ── Configuration (env vars with defaults) ──
OPTIMIZED_PORT=${OPTIMIZED_PORT:-${BASELINE_PORT:-8080}}
TARGET_URL=${TARGET_URL:-"http://localhost:${OPTIMIZED_PORT}"}
SWAGGER_URL=${SWAGGER_URL:-""}
BEARER_TOKEN=${BEARER_TOKEN:-""}
REPEAT=${REPEAT:-3}
SKIP_SPECTRAL=${SKIP_SPECTRAL:-true}
APPNAME=${APPNAME:-$(basename "$(cd "$(dirname "$0")/.." && pwd)")}

# ── Keycloak settings for auto-token acquisition ──
KEYCLOAK_URL=${KEYCLOAK_URL:-"http://localhost:9090"}
KEYCLOAK_REALM=${KEYCLOAK_REALM:-"esignpayment"}
KEYCLOAK_CLIENT_ID=${KEYCLOAK_CLIENT_ID:-"esignpay-frontend"}
KEYCLOAK_USER=${KEYCLOAK_USER:-"admin@test.com"}
KEYCLOAK_PASSWORD=${KEYCLOAK_PASSWORD:-"password"}

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_DIR="${ROOT_DIR}/reports"
AUTODISCOVER_PY="${SCRIPT_DIR}/green-api-auto-discover.py"

# ── Colors ──
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║  🌿 Green API Score Analyzer — Fully Dynamic Discovery     ║${NC}"
echo -e "${CYAN}║  Devoxx France 2026 — Green Architecture                   ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

###############################################################################
# Pre-flight checks
###############################################################################

# Verify Python 3 is available
if ! command -v python3 &>/dev/null; then
  echo -e "${RED}❌ python3 is required but not found${NC}"
  exit 1
fi

# Verify the auto-discover script exists
if [ ! -f "$AUTODISCOVER_PY" ]; then
  echo -e "${RED}❌ green-api-auto-discover.py not found at: $AUTODISCOVER_PY${NC}"
  exit 1
fi

# Wait for the target API to be available
echo -e "${YELLOW}━━━ ⏳ Waiting for API at ${TARGET_URL} ━━━${NC}"
MAX_WAIT=90
for i in $(seq 1 $MAX_WAIT); do
  if curl -s -o /dev/null -w '' "${TARGET_URL}/actuator/health" 2>/dev/null; then
    echo -e "  ${GREEN}✓ API is up${NC}"
    break
  fi
  if [ "$i" -eq "$MAX_WAIT" ]; then
    echo -e "  ${RED}✗ API not reachable after ${MAX_WAIT}s${NC}"
    exit 1
  fi
  sleep 1
done
echo ""

###############################################################################
# Auto-acquire Keycloak JWT token (if not already set)
###############################################################################
if [ -z "$BEARER_TOKEN" ]; then
  echo -e "${YELLOW}━━━ 🔐 Acquiring JWT token from Keycloak (${KEYCLOAK_URL}) ━━━${NC}"
  TOKEN_ENDPOINT="${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token"

  # Wait for Keycloak to be ready (max 30s)
  KC_READY=false
  for kc_i in $(seq 1 30); do
    if curl -sf "${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}" -o /dev/null 2>/dev/null; then
      KC_READY=true
      break
    fi
    sleep 1
  done

  if [ "$KC_READY" = true ]; then
    TOKEN_RESPONSE=$(curl -s --connect-timeout 10 --max-time 15 -X POST "$TOKEN_ENDPOINT" \
      -H "Content-Type: application/x-www-form-urlencoded" \
      -d "grant_type=password" \
      -d "client_id=${KEYCLOAK_CLIENT_ID}" \
      -d "username=${KEYCLOAK_USER}" \
      -d "password=${KEYCLOAK_PASSWORD}" 2>/dev/null || echo "")

    BEARER_TOKEN=$(echo "$TOKEN_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('access_token',''))" 2>/dev/null || echo "")

    if [ -n "$BEARER_TOKEN" ]; then
      echo -e "  ${GREEN}✓ JWT token acquired (user: ${KEYCLOAK_USER})${NC}"
    else
      ERROR_DESC=$(echo "$TOKEN_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('error_description','unknown'))" 2>/dev/null || echo "no response")
      echo -e "  ${YELLOW}⚠ Could not acquire token: ${ERROR_DESC}${NC}"
      echo -e "  ${YELLOW}  → Endpoints requiring auth will return 401${NC}"
      echo -e "  ${YELLOW}  💡 Set BEARER_TOKEN env var or check Keycloak at ${KEYCLOAK_URL}${NC}"
    fi
  else
    echo -e "  ${YELLOW}⚠ Keycloak not reachable at ${KEYCLOAK_URL}${NC}"
    echo -e "  ${YELLOW}  → Endpoints requiring auth will return 401${NC}"
    echo -e "  ${YELLOW}  💡 Set BEARER_TOKEN env var manually${NC}"
  fi
  echo ""
fi

###############################################################################
# Run the fully dynamic Python analyzer
###############################################################################

echo -e "${YELLOW}━━━ 🔍 Running Auto-Discover Analyzer ━━━${NC}"
echo -e "  Target:   ${CYAN}${TARGET_URL}${NC}"
echo -e "  Repeat:   ${CYAN}${REPEAT}${NC}"
if [ -n "$SWAGGER_URL" ]; then
  echo -e "  Swagger:  ${CYAN}${SWAGGER_URL}${NC}"
fi
if [ -n "$BEARER_TOKEN" ]; then
  echo -e "  Auth:     ${CYAN}Bearer ****${NC}"
fi
echo ""

# Build the command
CMD=(python3 "$AUTODISCOVER_PY"
  --target "$TARGET_URL"
  --repeat "$REPEAT"
  --appname "$APPNAME"
)

if [ -n "$SWAGGER_URL" ]; then
  CMD+=(--swagger "$SWAGGER_URL")
fi

if [ -n "$BEARER_TOKEN" ]; then
  CMD+=(--bearer "$BEARER_TOKEN")
fi

if [ "$SKIP_SPECTRAL" = true ]; then
  CMD+=(--skip-spectral)
fi

# Pass any extra args through
if [ ${#EXTRA_ARGS[@]} -gt 0 ]; then
  CMD+=("${EXTRA_ARGS[@]}")
fi

# Execute
"${CMD[@]}"
ANALYZER_EXIT=$?

if [ $ANALYZER_EXIT -ne 0 ]; then
  echo -e "${RED}❌ Analyzer exited with code $ANALYZER_EXIT${NC}"
  exit $ANALYZER_EXIT
fi

echo ""

###############################################################################
# Post-processing: Badge + Dashboard + Summary
###############################################################################

LATEST_REPORT="${OUTPUT_DIR}/latest-report.json"

if [ ! -f "$LATEST_REPORT" ]; then
  echo -e "${RED}❌ Report not generated: $LATEST_REPORT${NC}"
  exit 1
fi

# Generate badge
if [ -f "$ROOT_DIR/scripts/generate-badge.sh" ]; then
  echo -e "${YELLOW}━━━ 🏷️  Generating Badge ━━━${NC}"
  bash "$ROOT_DIR/scripts/generate-badge.sh" "$LATEST_REPORT" "$ROOT_DIR/badges/green-score.svg" || true
fi

# Generate dashboard
if [ -f "$ROOT_DIR/scripts/generate-dashboard.sh" ]; then
  echo -e "${YELLOW}━━━ 📊 Generating Dashboard ━━━${NC}"
  bash "$ROOT_DIR/scripts/generate-dashboard.sh" "$LATEST_REPORT" "$ROOT_DIR/dashboard/index.save.html" "$ROOT_DIR/dashboard/index.html" || true
fi

# ── Display summary ──
echo ""
TOTAL=$(python3 -c "import json;d=json.load(open('$LATEST_REPORT'));r=d.get('report',d);print(r['green_score']['total'])" 2>/dev/null || echo "?")
GRADE=$(python3 -c "import json;d=json.load(open('$LATEST_REPORT'));r=d.get('report',d);print(r['green_score']['grade'])" 2>/dev/null || echo "?")
EP_DISC=$(python3 -c "import json;d=json.load(open('$LATEST_REPORT'));r=d.get('report',d);print(r.get('auto_discovery',{}).get('endpoints_discovered',0))" 2>/dev/null || echo "0")
EP_MEAS=$(python3 -c "import json;d=json.load(open('$LATEST_REPORT'));r=d.get('report',d);print(r.get('auto_discovery',{}).get('endpoints_measured',0))" 2>/dev/null || echo "0")
APP_DISPLAY=$(python3 -c "import json;d=json.load(open('$LATEST_REPORT'));print(d.get('appname','$APPNAME'))" 2>/dev/null || echo "$APPNAME")

echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}📄 Report: ${LATEST_REPORT}${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

echo ""
echo -e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║  📦 APP: ${GREEN}${APP_DISPLAY}${CYAN}                                        ║${NC}"
echo -e "${CYAN}║  🌿 GREEN SCORE:  ${GREEN}${TOTAL}/100${CYAN}   Grade: ${GREEN}${GRADE}${CYAN}                    ║${NC}"
echo -e "${CYAN}║  🔍 Endpoints discovered: ${GREEN}${EP_DISC}${CYAN}  measured: ${GREEN}${EP_MEAS}${CYAN}              ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

if [ "$DEBUG_MODE" = true ]; then
  echo -e "${YELLOW}━━━ 🐛 DEBUG: Score breakdown ━━━${NC}"
  python3 -c "
import json
d = json.load(open('$LATEST_REPORT'))
r = d.get('report', d)
gs = r['green_score']
print(f'  App:   {d.get(\"appname\", \"unknown\")}')
print(f'  Total: {gs[\"total\"]}/{gs[\"max\"]}  Grade: {gs[\"grade\"]}')
print()
for rule, score in gs.get('breakdown', {}).items():
    detail = gs.get('details', {}).get(rule, {}).get('note', '')
    icon = '✅' if score > 0 else '❌'
    print(f'  {icon} {rule:25s} {score:5}  {detail}')
print()
disc = r.get('auto_discovery', {})
for ep in disc.get('discovered_endpoints', [])[:20]:
    print(f'    {ep[\"method\"]:6s} {ep[\"path\"]:50s} {ep.get(\"http_code\",0):3d}  {ep.get(\"size_download\",0):>8} B  {ep.get(\"time_total\",0):.3f}s')
" 2>/dev/null || true
fi

echo -e "Open the dashboard: ${YELLOW}open dashboard/index.html${NC}"
