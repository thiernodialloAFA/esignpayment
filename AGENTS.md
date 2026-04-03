# AGENTS.md — ESignPay Codebase Guide

## Architecture Overview

Full-stack e-signature & payment app: **React 19 frontend** → **Nginx reverse proxy** → **Spring Boot 4 backend** → **PostgreSQL 16**. Authentication via **Keycloak 26** (OAuth2/OIDC). Payments via **Stripe** (3D Secure). OTP signing via **Twilio SMS**. OCR verification via **Tesseract**.

```
frontend/ (React 19 + TS 5, CRA)  →  nginx /api/ proxy  →  backend/ (Java 21, Spring Boot 4)
                                                                ├── PostgreSQL (Flyway migrations)
                                                                ├── Keycloak (JWT validation)
                                                                ├── Stripe (payments + webhooks)
                                                                └── Twilio (SMS OTP)
```

## Key Conventions

- **API response envelope**: All backend endpoints return `ApiResponse<T>` (`{ success, message, data }`). Frontend types mirror this in `frontend/src/types/index.ts`. Always wrap controller responses with `ApiResponse.success()` or `ApiResponse.error()`.
- **Backend package layout**: `com.esign.payment.{config,controller,dto/request,dto/response,model,model/enums,repository,service}` — strict layering, no cross-cutting.
- **Entities use Lombok**: All models use `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`. Use `@Builder.Default` for default values (see `Document.java`, `User.java`).
- **UUIDs everywhere**: All entity IDs are `UUID`, generated with `GenerationType.UUID`. Frontend passes IDs as `string`.
- **Enum mirroring**: Backend enums in `model/enums/` must match frontend union types in `types/index.ts` (e.g., `DocumentStatus`, `PaymentStatus`, `ApplicationStatus`).
- **Frontend API layer**: Each domain has a dedicated API module in `frontend/src/api/` exporting an object literal (e.g., `documentsApi`, `paymentsApi`, `accountApi`). All use the shared `apiClient` from `api/client.ts` which auto-attaches Keycloak JWT Bearer tokens.

## Authentication Flow

- Backend is a stateless OAuth2 Resource Server — **no sessions** (`SessionCreationPolicy.STATELESS`).
- Keycloak roles are extracted from `realm_access.roles` JWT claim (see `SecurityConfig.KeycloakRealmRoleConverter`).
- Users are auto-synced to local DB on first authenticated request via `KeycloakUserService.syncUser()`.
- Public endpoints (no auth): `/api/sign/**`, `/api/webhooks/stripe`, `/actuator/**`. Everything else requires authentication.
- Frontend uses `check-sso` init mode with silent SSO (`public/silent-check-sso.html`). `ProtectedRoute` auto-redirects unauthenticated users to Keycloak login.

## Database & Migrations

- **Flyway** manages schema: `backend/src/main/resources/db/migration/V{n}__*.sql` (currently V1–V6).
- JPA `ddl-auto: validate` — schema changes **must** go through new Flyway migration files.
- Tests use **H2 in PostgreSQL mode** with `ddl-auto: create-drop` and Flyway disabled (`backend/src/test/resources/application.yml`).

## Build & Run Commands

```bash
# Full stack via Docker Compose (recommended)
docker compose up --build

# Backend only (requires running PostgreSQL + Keycloak)
cd backend && mvn spring-boot:run

# Frontend only
cd frontend && npm install && npm start

# Backend tests (H2 in-memory, no external deps needed)
cd backend && mvn test

# Frontend tests
cd frontend && npm test
```

**Ports**: Frontend `:3000` | Backend `:8080` | Keycloak `:9090` | PostgreSQL `:5433` (host) → `:5432` (container) | Mailpit `:8025` (UI) `:1025` (SMTP) | OTEL `:4317`/`:4318`

## Green Score Tooling

The `scripts/` directory contains a **Green API Score Analyzer** (Devoxx France demo tooling) that benchmarks API optimizations (pagination, field filtering, gzip, ETag/304, delta updates, range requests, CBOR). Reports are saved to `reports/` and a dashboard is generated in `dashboard/index.html`.

```powershell
.\scripts\green-score-analyzer.ps1          # Run analysis
.\scripts\generate-dashboard.ps1 reports\latest-report.json dashboard\index.save.html dashboard\index.html
```

## File Upload Pattern

Documents and KYC files are sent as **base64-encoded strings** in JSON request bodies (not multipart). Backend stores files to disk under `uploads/{documents,kyc,contracts}/`. The `Dockerfile` creates these directories at build time.

## Swagger / OpenAPI

- **springdoc-openapi v2.8** auto-generates OpenAPI 3 docs from all controllers in `com.esign.payment.controller`.
- Swagger UI: `http://localhost:8080/api/swagger-ui.html` (direct) or `http://localhost:3000/api/swagger-ui.html` (via Nginx).
- JSON spec: `http://localhost:8080/api/v3/api-docs`.
- All endpoints are publicly accessible (no auth) in `SecurityConfig`.
- JWT Bearer auth scheme (`bearerAuth`) is declared globally; public endpoints (`/api/sign/**`, `/api/webhooks/stripe`) are annotated with empty `@SecurityRequirements` to opt out.
- Controllers use `@Tag(name = "...")` and `@Operation(summary = "...")` for grouping and descriptions.
- Configuration lives in `OpenApiConfig.java` (bean) + `application.yml` (`springdoc.*` section).

## Adding a New Feature Checklist

1. **Backend**: Migration `V{next}__*.sql` → Entity in `model/` → Repository → Service → DTO (request + response) → Controller
2. **Frontend**: Type in `types/index.ts` → API module in `api/` → Page component in `pages/` → Route in `App.tsx`
3. Secure the endpoint in `SecurityConfig.filterChain()` if it needs custom auth rules (default: `.anyRequest().authenticated()`)

