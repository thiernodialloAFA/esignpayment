# ESignPay — E-Signature & Payment Starter

A full-stack application for managing document e-signatures and payments.

## Architecture

```
esignpaymentstarter/
├── backend/          # Java 21 · Spring Boot 4.0 · PostgreSQL
├── frontend/         # React 19 · TypeScript 5 · react-router-dom
├── docker-compose.yml
└── otel-collector-config.yaml
```

## Data Model

| Table | Description |
|-------|-------------|
| `users` | Users synced from Keycloak with OAuth2/OIDC |
| `documents` | Uploaded files awaiting signature |
| `document_signers` | Per-document signers with unique one-time tokens |
| `signatures` | Captured signature data (base64 PNG) |
| `payments` | Payment records per user |

## Backend API — Quick Reference

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET`  | `/api/auth/me` | ✓ | Get current user (synced from Keycloak) |
| `POST` | `/api/documents` | ✓ | Upload document + signers |
| `GET`  | `/api/documents` | ✓ | List my documents |
| `GET`  | `/api/documents/{id}` | ✓ | Get document detail |
| `POST` | `/api/documents/{id}/send` | ✓ | Send for signature |
| `DELETE` | `/api/documents/{id}` | ✓ | Delete document |
| `GET`  | `/api/sign/verify/{token}` | ✗ | Verify signature token |
| `POST` | `/api/sign/{token}` | ✗ | Submit signature |
| `POST` | `/api/payments` | ✓ | Create payment |
| `GET`  | `/api/payments` | ✓ | List my payments |
| `GET`  | `/api/payments/{id}` | ✓ | Get payment detail |
| `POST` | `/api/payments/{id}/cancel` | ✓ | Cancel payment |
| `GET`  | `/actuator/**` | ✗ | Health, metrics, info endpoints |

## Authentication

Authentication is handled by **Keycloak** (OAuth2/OIDC).

- **Backend**: Acts as an OAuth2 Resource Server, validating JWT tokens from Keycloak
- **Frontend**: Uses `keycloak-js` adapter for login/logout/registration flows
- Users are automatically synced to the local database on first authenticated request

### Keycloak Setup

1. Start Keycloak via Docker Compose (runs on port 9090)
2. Access Keycloak admin at http://localhost:9090 (admin/admin)
3. Create a realm called `esignpayment`
4. Create a client called `esignpay-frontend` (public client, Standard Flow)
5. Configure redirect URIs: `http://localhost:3000/*`
6. Add realm roles: `ROLE_USER`, `ROLE_ADMIN`

## Observability

**OpenTelemetry** is integrated for traces, metrics, and logs.

- Uses Spring Boot 4's built-in `spring-boot-starter-opentelemetry`
- OTLP exporter sends data to the OpenTelemetry Collector
- Actuator endpoints expose health, metrics, and info
- Configure `OTEL_EXPORTER_OTLP_ENDPOINT` to point to your collector

## Quick Start with Docker Compose

```bash
docker compose up --build
```

## Scripts de démarrage & analyse Green Score

Deux modes de lancement sont disponibles dans `scripts/` :

### Mode Full (`start.sh`)

Le mode **full** effectue un cycle complet : **arrêt de tous les conteneurs** → **rebuild** → **démarrage** → **analyse Green Score** (+ Creedengo en option). Idéal pour un démarrage propre ou une démo depuis zéro.

```bash
# Lancement full (rebuild complet + analyse Green Score)
bash scripts/start.sh

# Full + mode debug (logs détaillés)
bash scripts/start.sh --debug

# Full + analyse éco-conception Creedengo (SonarQube + plugins Green Code)
bash scripts/start.sh --creedengo

# Full + debug + Creedengo
bash scripts/start.sh --debug --creedengo

# Full avec un nom d'application personnalisé (pour les rapports)
bash scripts/start.sh --appname mon-app
```

| Option | Description |
|--------|-------------|
| `--debug` | Active les logs détaillés durant l'analyse |
| `--creedengo` | Lance aussi l'analyse éco-conception Creedengo (nécessite Docker) |
| `--appname <name>` | Nom de l'application utilisé dans les rapports (défaut : nom du dossier racine) |

> ⚠️ Le mode full ouvre automatiquement un **nouveau terminal** pour `docker compose up --build --force-recreate`. Ne fermez pas ce terminal pendant l'exécution.

### Mode Light (`start_light.sh`)

Le mode **light** suppose que les conteneurs sont **déjà démarrés**. Il attend que les services soient prêts puis lance l'analyse directement, sans rebuild.

```bash
# Lancement light (pas de rebuild, analyse seulement)
bash scripts/start_light.sh

# Light + Creedengo
bash scripts/start_light.sh --creedengo
```

> 💡 Utilisez `start_light.sh` quand vous avez déjà lancé `docker compose up` séparément.

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- Keycloak: http://localhost:9090 (admin/admin)
- PostgreSQL: localhost:5432
- OTEL Collector (gRPC): localhost:4317
- OTEL Collector (HTTP): localhost:4318

## Local Development

### Backend

Requires Java 21 and a running PostgreSQL + Keycloak instance.

```bash
cd backend
# Configure DB and Keycloak connection in application.yml or via env vars
mvn spring-boot:run
```

Environment variables (or `application.yml` overrides):

| Variable | Default |
|----------|---------|
| `DB_HOST` | `localhost` |
| `DB_PORT` | `5432` |
| `DB_NAME` | `esignpayment` |
| `DB_USER` | `postgres` |
| `DB_PASSWORD` | `postgres` |
| `KEYCLOAK_ISSUER_URI` | `http://localhost:9090/realms/esignpayment` |
| `KEYCLOAK_JWK_SET_URI` | `http://localhost:9090/realms/esignpayment/protocol/openid-connect/certs` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318` |
| `OTEL_SAMPLING_PROBABILITY` | `1.0` |

### Frontend

```bash
cd frontend
npm install
REACT_APP_API_URL=http://localhost:8080 \
REACT_APP_KEYCLOAK_URL=http://localhost:9090 \
REACT_APP_KEYCLOAK_REALM=esignpayment \
REACT_APP_KEYCLOAK_CLIENT_ID=esignpay-frontend \
npm start
```

## Frontend Pages

| Route | Description |
|-------|-------------|
| `/login` | Redirects to Keycloak login |
| `/dashboard` | Overview stats + recent items |
| `/documents` | Upload & manage documents |
| `/payments` | Create & track payments |
| `/sign/:token` | Signer-facing document signing page |

## Tech Stack

**Backend**
- Java 21, Spring Boot 4.0
- Spring Security + OAuth2 Resource Server (Keycloak)
- Spring Data JPA + Hibernate
- PostgreSQL 16, Flyway migrations
- OpenTelemetry (traces, metrics, logs)
- Spring Boot Actuator
- Lombok, Bean Validation

**Frontend**
- React 19, TypeScript 5
- keycloak-js 26 (OAuth2/OIDC)
- react-router-dom v6
- react-hook-form
- react-signature-canvas
- axios 1.13

**Infrastructure**
- Docker & Docker Compose
- Nginx (frontend static serving + API proxy)
- Keycloak 26 (Identity Provider)
- OpenTelemetry Collector
