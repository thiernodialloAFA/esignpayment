#!/usr/bin/env bash
###############################################################################
#  Green API Auto-Discover & Analyzer — Bash wrapper
#
#  Port layout (avoid collisions with esign backend):
#    - esign backend:  8080  (target for measurement)
#    - Keycloak:       9090  (token acquisition)
#    - SonarQube:      9100  (Creedengo, separate script)
#
#  Usage:
#    bash green-api-auto-discover.sh                              # defaults
#    bash green-api-auto-discover.sh --target http://localhost:8080
#    bash green-api-auto-discover.sh --swagger ./openapi.yaml --dry-run
#
#  Environment variables (override defaults):
#    TARGET_URL          Base URL (default: http://localhost:8080)
#    SWAGGER_URL         Explicit swagger URL/path (auto-discovered if empty)
#    BEARER_TOKEN        Bearer token for authenticated APIs (auto-acquired from Keycloak if empty)
#    KEYCLOAK_URL        Keycloak URL (default: http://localhost:9090)
#    KEYCLOAK_REALM      Realm name (default: esignpayment)
#    KEYCLOAK_CLIENT_ID  Client ID (default: esignpay-frontend)
#    KEYCLOAK_USER       Username for token (default: admin@test.com)
#    KEYCLOAK_PASSWORD   Password for token (default: password)
#    REPEAT              Repetitions per endpoint (default: 3)
#    SKIP_SPECTRAL       Set to 1 to skip Spectral linting
#    SKIP_DASHBOARD      Set to 1 to skip dashboard generation
###############################################################################
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colors
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║  🌿 Green API Auto-Discover & Analyzer                     ║${NC}"
echo -e "${CYAN}║  Devoxx France 2026 — Green Architecture                   ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# ── Build arguments ──
ARGS=()

# Target URL
TARGET_URL="${TARGET_URL:-http://localhost:8080}"
ARGS+=(--target "$TARGET_URL")

# Swagger (optional)
if [ -n "${SWAGGER_URL:-}" ]; then
    ARGS+=(--swagger "$SWAGGER_URL")
fi

# ── Auto-acquire Keycloak JWT token if BEARER_TOKEN is not set ──
BEARER_TOKEN="${BEARER_TOKEN:-}"
if [ -z "$BEARER_TOKEN" ]; then
    KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:9090}"
    KEYCLOAK_REALM="${KEYCLOAK_REALM:-esignpayment}"
    KEYCLOAK_CLIENT_ID="${KEYCLOAK_CLIENT_ID:-esignpay-frontend}"
    KEYCLOAK_USER="${KEYCLOAK_USER:-admin@test.com}"
    KEYCLOAK_PASSWORD="${KEYCLOAK_PASSWORD:-password}"
    TOKEN_ENDPOINT="${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token"

    echo -e "${YELLOW}━━━ 🔐 Acquiring JWT token from Keycloak ━━━${NC}"
    if curl -sf "${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}" -o /dev/null 2>/dev/null; then
        TOKEN_RESPONSE=$(curl -s --connect-timeout 10 --max-time 15 -X POST "$TOKEN_ENDPOINT" \
            -H "Content-Type: application/x-www-form-urlencoded" \
            -d "grant_type=password" \
            -d "client_id=${KEYCLOAK_CLIENT_ID}" \
            -d "username=${KEYCLOAK_USER}" \
            -d "password=${KEYCLOAK_PASSWORD}" 2>/dev/null || echo "")
        BEARER_TOKEN=$(echo "$TOKEN_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('access_token',''))" 2>/dev/null || echo "")
        if [ -n "$BEARER_TOKEN" ]; then
            echo -e "  ${GREEN}✓ JWT acquired (user: ${KEYCLOAK_USER})${NC}"
        else
            echo -e "  ${YELLOW}⚠ Token acquisition failed — auth endpoints will return 401${NC}"
        fi
    else
        echo -e "  ${YELLOW}⚠ Keycloak not reachable at ${KEYCLOAK_URL} — skipping auth${NC}"
    fi
    echo ""
fi

# Bearer token
if [ -n "${BEARER_TOKEN:-}" ]; then
    ARGS+=(--bearer "$BEARER_TOKEN")
fi

# Repeat
REPEAT="${REPEAT:-3}"
ARGS+=(--repeat "$REPEAT")

# Skip flags
if [ "${SKIP_SPECTRAL:-0}" = "1" ]; then
    ARGS+=(--skip-spectral)
fi
if [ "${SKIP_DASHBOARD:-0}" = "1" ]; then
    ARGS+=(--skip-dashboard)
fi

# Forward extra CLI args
ARGS+=("$@")

# ── Run ──
echo -e "${GREEN}Running:${NC} python3 $SCRIPT_DIR/green-api-auto-discover.py ${ARGS[*]}"
echo ""

python3 "$SCRIPT_DIR/green-api-auto-discover.py" "${ARGS[@]}"
EXIT_CODE=$?

echo ""
if [ $EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}✓ Analysis complete. Open dashboard/index.html to view results.${NC}"
else
    echo -e "\033[0;31m✗ Analysis failed or score below threshold (exit code: $EXIT_CODE).${NC}"
fi

exit $EXIT_CODE

