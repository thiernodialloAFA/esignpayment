# 🌿 Green API Score Dashboard

> 📦 **Application : esignpaym-api**
>
> **Devoxx France 2026 — Green Architecture : moins de gras, plus d'impact !**

📅 *Dernière analyse : 2026-04-04T21:34:30Z*

---

## 🟡 Green Score : **65/100** — Grade **B** 🥈

### 📋 Détail par règle

| Statut | Règle | Score | Max | Endpoints | Détail |
|:------:|-------|------:|----:|:---------:|--------|
| ⚠️ | DE11 Pagination | 5 | 15 | 3/9 | Pagination on 3/9 collection endpoint(s) |
| ⚠️ | DE08 Filtrage champs | 13 | 15 | 12/14 | Field filtering on 12 endpoint(s) |
| ✅ | DE01 Compression | 15 | 15 | 34/34 | Gzip compression active (OpenAPI servers.x-server-compression.enabled=true) |
| ✅ | DE02/03 Cache ETag | 15 | 15 | 5/5 | ETag + 304 supported (OpenAPI servers.x-server-etag-support.enabled=true) |
| ⚠️ | DE06 Delta | 2 | 10 | 3/14 | Delta endpoint(s) found: 3 |
| ✅ | 206 Range | 10 | 10 | 14/14 | Range/206 supported (OpenAPI servers.x-server-range-support.enabled=true) |
| ⚠️ | LO01 Observabilité | 4 | 5 | 4/5 | Actuator/health detected |
| ⚠️ | US07 Rate Limit | 1 | 5 | 6/34 | Rate-limit headers detected |
| ❌ | AR02 CBOR | 0 | 10 | 1/34 | Binary format on 1 endpoint(s) |

### 📊 Comparaison Avant / Après

| Mesure | Taille | Temps | HTTP |
|--------|-------:|------:|-----:|
| 🔴 Baseline `/books` (full) | 70 B | 0.024s | 500 |
| 🟢 Pagination (`?page&size`) | 0 B | 0.000s | 0 |
| 🟢 Fields filter (`fields=`) | 0 B | 0.000s | 0 |
| 🟢 Gzip compression | 0 B | 0.000s | 0 |
| 🟢 ETag / 304 | 0 B | 0.000s | 0 |
| 🟢 Delta changes | 70 B | 0.009s | 500 |
| 🟢 Range 206 | 0 B | 0.000s | 0 |
| 🟢 CBOR binary | 0 B | 0.000s | 0 |
| 🟢 Full payload (optimized) | 559 B | 0.005s | 200 |

### 🔍 Auto-Discovery (33 endpoints)

| Méthode | Path | HTTP | Taille | Temps |
|---------|------|-----:|-------:|------:|
| GET | `/api/account-applications/{id}` | 403 | 99 B | 0.061s |
| PUT | `/api/account-applications/{id}` | 403 | 99 B | 0.027s |
| DELETE | `/api/account-applications/{id}` | 403 | 99 B | 0.012s |
| POST | `/api/sign/{token}` | 409 | 80 B | 0.016s |
| POST | `/api/sign/{token}/verify-otp` | 500 | 70 B | 0.051s |
| POST | `/api/sign/{token}/send-otp` | 500 | 70 B | 0.018s |
| GET | `/api/payments` | 200 | 356 B | 0.007s |
| POST | `/api/payments` | 500 | 70 B | 0.020s |
| POST | `/api/payments/{id}/cancel` | 403 | 95 B | 0.006s |
| POST | `/api/payments/confirm` | 403 | 95 B | 0.010s |
| GET | `/api/documents` | 200 | 356 B | 0.008s |
| POST | `/api/documents` | 500 | 70 B | 0.026s |
| POST | `/api/documents/{id}/send` | 403 | 96 B | 0.010s |
| POST | `/api/documents/{id}/resend` | 403 | 96 B | 0.009s |
| POST | `/api/documents/{id}/live-sign/{signerId}` | 403 | 96 B | 0.007s |
| GET | `/api/account-applications` | 200 | 356 B | 0.007s |
| POST | `/api/account-applications` | 500 | 70 B | 0.024s |
| POST | `/api/account-applications/{id}/submit` | 403 | 99 B | 0.009s |
| POST | `/api/account-applications/{id}/regenerate-contract` | 403 | 99 B | 0.010s |
| POST | `/api/account-applications/{id}/kyc` | 403 | 99 B | 0.008s |
| POST | `/api/account-applications/{id}/generate-contract` | 500 | 70 B | 0.046s |
| GET | `/api/sign/verify/{token}` | 200 | 229 B | 0.006s |
| GET | `/api/payments/{id}` | 403 | 95 B | 0.007s |
| GET | `/api/payments/config` | 200 | 79 B | 0.003s |
| GET | `/api/payments/changes` | 500 | 70 B | 0.009s |
| GET | `/api/documents/{id}` | 403 | 96 B | 0.007s |
| DELETE | `/api/documents/{id}` | 403 | 96 B | 0.005s |
| GET | `/api/documents/{id}/download` | 403 | 96 B | 0.006s |
| GET | `/api/documents/changes` | 500 | 70 B | 0.008s |
| GET | `/api/auth/me` | 500 | 70 B | 0.015s |

### 💡 Suggestions d'amélioration

> **Score actuel : 65/100** — Score potentiel avec toutes les suggestions : **98/100** (+33 pts possibles)

🔴 Haute priorité : 5 | 🟡 Moyenne : 4 | ⚪ Basse : 2 | **Total : 11 suggestions**

#### 📄 DE11 — Pagination (⚠️ Partiel (3/9) — +10 pts possibles)

> Les endpoints de collection doivent supporter la pagination (page/size ou limit/offset). (3/9 endpoints validés)

| Priorité | Cible | Action | Impact |
|:--------:|-------|--------|--------|
| 🔴 Haute | `GET /api/payments/changes` | Add pagination parameters (page & size) | +1.7 pts/endpoint (total gap: 10 pts) — reduces payload size for large collections |
| 🔴 Haute | `GET /api/documents/changes` | Add pagination parameters (page & size) | +1.7 pts/endpoint (total gap: 10 pts) — reduces payload size for large collections |
| 🔴 Haute | `GET /api/account-types` | Add pagination parameters (page & size) | +1.7 pts/endpoint (total gap: 10 pts) — reduces payload size for large collections |
| 🔴 Haute | `GET /api/account-applications/changes` | Add pagination parameters (page & size) | +1.7 pts/endpoint (total gap: 10 pts) — reduces payload size for large collections |

<details><summary>🔧 Comment implémenter</summary>

```
Spring Boot: Change return type from List<T> to Page<T> and add @RequestParam defaultValue parameters:
  @GetMapping
  public ApiResponse<Page<T>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(repository.findAll(PageRequest.of(page, size)));
  }
OpenAPI: params 'page' and 'size' will appear automatically via springdoc.
```
</details>

#### 📦 AR02 — Format binaire (CBOR) (⚠️ Partiel (1/34) — +10 pts possibles)

> Un endpoint en format binaire (CBOR, protobuf...) doit exister. (1/34 endpoints validés)

| Priorité | Cible | Action | Impact |
|:--------:|-------|--------|--------|
| ⚪ Basse | `GET /api/payments  (add CBOR variant)` | Add a binary format alternative (CBOR or Protobuf) | +10 pts — binary formats are 30-50% smaller than JSON |
| ⚪ Basse | `GET /api/documents  (add CBOR variant)` | Add a binary format alternative (CBOR or Protobuf) | +10 pts — binary formats are 30-50% smaller than JSON |

<details><summary>🔧 Comment implémenter</summary>

```
Spring Boot + CBOR:
  1. Add dependency: com.fasterxml.jackson.dataformat:jackson-dataformat-cbor
  2. Register the converter:
     @Bean
     public HttpMessageConverter<Object> cborConverter(ObjectMapper mapper) {
       ObjectMapper cborMapper = new ObjectMapper(new CBORFactory());
       return new MappingJackson2CborHttpMessageConverter(cborMapper);
     }
  3. Clients send: Accept: application/cbor

Alternative (Protobuf):
  Add spring-boot-starter-protobuf and define .proto schemas.
  Register ProtobufHttpMessageConverter.
```
</details>

#### 🔄 DE06 — Delta / Changes (⚠️ Partiel (3/14) — +8 pts possibles)

> Un endpoint /changes?since= ou equivalent doit exister. (3/14 endpoints validés)

| Priorité | Cible | Action | Impact |
|:--------:|-------|--------|--------|
| 🟡 Moyenne | `GET /api/payments/changes  (new endpoint)` | Add a delta/changes endpoint with a 'since' parameter | +0.7 pts/endpoint (total gap: 8 pts) — clients fetch only what changed since last sync |
| 🟡 Moyenne | `GET /api/documents/changes  (new endpoint)` | Add a delta/changes endpoint with a 'since' parameter | +0.7 pts/endpoint (total gap: 8 pts) — clients fetch only what changed since last sync |
| 🟡 Moyenne | `GET /api/account-applications/changes  (new endpoint)` | Add a delta/changes endpoint with a 'since' parameter | +0.7 pts/endpoint (total gap: 8 pts) — clients fetch only what changed since last sync |

<details><summary>🔧 Comment implémenter</summary>

```
Spring Boot: Add a new endpoint that filters by updatedAt:
  @GetMapping("/changes")
  public ApiResponse<List<T>> getChanges(
      @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime since) {
    return ApiResponse.success(
        repository.findByUpdatedAtAfter(since));
  }

Prerequisite: Add an 'updatedAt' column with @UpdateTimestamp
to your entity, and a repository method findByUpdatedAtAfter().
Alternative: Add @RequestParam 'since' to existing /api/payments.
```
</details>

#### 🚦 US07 — Rate Limiting (⚠️ Partiel (6/34) — +4 pts possibles)

> Un mecanisme de rate limiting doit etre present. (6/34 endpoints validés)

| Priorité | Cible | Action | Impact |
|:--------:|-------|--------|--------|
| 🟡 Moyenne | `ALL endpoints (server-level)` | Add rate-limit response headers | +5 pts — protects the API from abuse and signals limits to clients |

<details><summary>🔧 Comment implémenter</summary>

```
Option 1 — Spring Boot filter:
  Add a HandlerInterceptor or OncePerRequestFilter that adds:
    X-RateLimit-Limit: 100
    X-RateLimit-Remaining: 97
    X-RateLimit-Reset: 1620000000

Option 2 — Use Bucket4j + Spring Boot Starter:
  <dependency>com.bucket4j:bucket4j-spring-boot-starter</dependency>
  Configure rate limits in application.yml per endpoint.

Option 3 — Nginx:
  limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;
  location /api/ { limit_req zone=api burst=20; }
  add_header X-RateLimit-Limit 100;
```
</details>

#### 👁️ LO01 — Observabilité (⚠️ Partiel (4/5) — +1 pts possibles)

> Actuator / health / metrics doit etre expose. (4/5 endpoints validés)

| Priorité | Cible | Action | Impact |
|:--------:|-------|--------|--------|
| 🔴 Haute | `/actuator/health, /actuator/metrics` | Expose Spring Boot Actuator endpoints | +5 pts — essential for production monitoring |

<details><summary>🔧 Comment implémenter</summary>

```
Spring Boot application.yml:
  management:
    endpoints:
      web:
        exposure:
          include: health,info,metrics
    endpoint:
      health:
        show-details: when-authorized

Add dependency: spring-boot-starter-actuator (likely already present).
```
</details>

---

🌿 *API Green Score — [Framework](https://github.com/API-Green-Score/APIGreenScore) | [Training](https://github.com/API-Green-Score/training-student) | Devoxx France 2026*

> 📊 Pour le dashboard interactif complet, ouvrez [`dashboard/index.html`](index.html)
