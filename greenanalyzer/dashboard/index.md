# 🌿 Green API Score Dashboard

> 📦 **Application : esignpay**
>
> **Devoxx France 2026 — Green Architecture : moins de gras, plus d'impact !**

📅 *Dernière analyse : 2026-04-08T19:57:27Z*

---

## 🟡 Green Score : **69/100** — Grade **B** 🥈

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
| ✅ | US07 Rate Limit | 5 | 5 | 32/34 | Rate-limit headers detected |
| ❌ | AR02 CBOR | 0 | 10 | 1/34 | Binary format on 1 endpoint(s) |

### 📊 Mesures par endpoint (API découverte)

| Méthode | Endpoint | Taille | Temps | HTTP |
|:-------:|----------|-------:|------:|-----:|
| GET | `/api/account-applications/{id}` | 917 B | 0.026s | 200 |
| PUT | `/api/account-applications/{id}` | 941 B | 0.050s | 200 |
| DELETE | `/api/account-applications/{id}` | 60 B | 0.026s | 200 |
| POST | `/api/sign/{token}` | 1.3 KB | 0.044s | 200 |
| POST | `/api/sign/{token}/verify-otp` | 117 B | 0.014s | 200 |
| POST | `/api/sign/{token}/send-otp` | 135 B | 0.011s | 200 |
| GET | `/api/payments` | 1.3 KB | 0.012s | 200 |
| POST | `/api/payments` | 498 B | 0.071s | 201 |
| POST | `/api/payments/{id}/cancel` | 372 B | 0.013s | 200 |
| POST | `/api/payments/confirm` | 384 B | 0.021s | 200 |
| GET | `/api/documents` | 5.6 KB | 0.011s | 200 |
| POST | `/api/documents` | 767 B | 0.019s | 201 |
| POST | `/api/documents/{id}/send` | 752 B | 0.011s | 200 |
| POST | `/api/documents/{id}/resend` | 770 B | 0.008s | 200 |
| POST | `/api/documents/{id}/live-sign/{signerId}` | 774 B | 0.324s | 200 |
| GET | `/api/account-applications` | 6.3 KB | 0.019s | 200 |
| POST | `/api/account-applications` | 940 B | 0.015s | 201 |
| POST | `/api/account-applications/{id}/submit` | 2.0 KB | 0.014s | 200 |
| POST | `/api/account-applications/{id}/regenerate-contract` | 1.3 KB | 0.041s | 200 |
| POST | `/api/account-applications/{id}/kyc` | 366 B | 0.015s | 201 |
| POST | `/api/account-applications/{id}/generate-contract` | 1.1 KB | 0.035s | 200 |
| GET | `/api/sign/verify/{token}` | 229 B | 0.007s | 200 |
| GET | `/api/payments/{id}` | 359 B | 0.006s | 200 |
| GET | `/api/payments/config` | 79 B | 0.007s | 200 |
| GET | `/api/payments/changes` | 1.3 KB | 0.010s | 200 |
| GET | `/api/documents/{id}` | 721 B | 0.007s | 200 |
| DELETE | `/api/documents/{id}` | 70 B | 0.011s | 200 |
| GET | `/api/documents/{id}/download` | 106 B | 0.007s | 404 |
| GET | `/api/documents/changes` | 6.0 KB | 0.009s | 200 |
| GET | `/api/auth/me` | 209 B | 0.006s | 200 |
| GET | `/api/account-types` | 559 B | 0.007s | 200 |
| GET | `/api/account-applications/changes` | 8.1 KB | 0.016s | 200 |
| DELETE | `/api/account-applications/{id}/kyc/{kycId}` | 61 B | 0.009s | 200 |

### 🔑 Métriques clés

- **Endpoints mesurés** : 33
- **Transfert total** : 44.5 KB
- **Transfert moyen / endpoint** : 1.3 KB
- **Temps moyen** : 0.027s
- **⚡ Énergie totale / appel** : 0.0087 Wh
- **🌍 CO₂ / appel** : 0.00046 g (France — 53 gCO₂/kWh)

### 💡 Suggestions d'amélioration

> **Score actuel : 69/100** — Score potentiel avec toutes les suggestions : **98/100** (+29 pts possibles)

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

#### 🚦 US07 — Rate Limiting (⚠️ Partiel (32/34))

> Un mecanisme de rate limiting doit etre present. (32/34 endpoints validés)

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


---

## 🌱 Creedengo Éco-Design : **0/100** — Grade **E** 🔴

> Analyse statique de l'éco-conception du code source via [Creedengo](https://github.com/green-code-initiative) / SonarQube

- **Langages détectés** : java, python, typescript
- **Principal** : java (spring-boot)
- **Plugins Creedengo** : java, javascript, python

🟠 CRITICAL: 45 | 🟡 MAJOR: 50 | ⚪ MINOR: 84 | 🔵 INFO: 1

- **Issues** : 180
- **Règles violées** : 28 / 17 analysées
- **Effort de remédiation** : 31h44min

- **Lignes de code** : 4,889

### 🏷️ Catégories éco-design

| Catégorie | Issues | Règles |
|-----------|-------:|-------:|
| 🌱 Éco-conception générale | 180 | 28 |

### 📋 Règles Creedengo violées

| Sévérité | Règle | Issues | Catégorie |
|:--------:|-------|-------:|-----------|
| 🟠 CRITICAL | **S2696** — S2696 | 23 | general |
| 🟠 CRITICAL | **S1192** — S1192 | 12 | general |
| 🟠 CRITICAL | **S3776** — S3776 | 6 | general |
| 🟠 CRITICAL | **S6809** — S6809 | 3 | general |
| 🟠 CRITICAL | **S2119** — S2119 | 1 | general |
| 🟡 MAJOR | **S5778** — S5778 | 16 | general |
| 🟡 MAJOR | **S6204** — S6204 | 8 | general |
| 🟡 MAJOR | **S112** — S112 | 6 | general |
| 🟡 MAJOR | **S125** — S125 | 6 | general |
| 🟡 MAJOR | **S6126** — S6126 | 4 | general |
| 🟡 MAJOR | **S1172** — S1172 | 3 | general |
| 🟡 MAJOR | **S106** — S106 | 2 | general |
| 🟡 MAJOR | **S3011** — S3011 | 2 | general |
| 🟡 MAJOR | **S107** — S107 | 1 | general |
| 🟡 MAJOR | **S1141** — S1141 | 1 | general |
| 🟡 MAJOR | **S2142** — S2142 | 1 | general |
| ⚪ MINOR | **S1104** — S1104 | 23 | general |
| ⚪ MINOR | **S3008** — S3008 | 23 | general |
| ⚪ MINOR | **S1444** — S1444 | 23 | general |
| ⚪ MINOR | **S1481** — S1481 | 5 | general |
| | *… et 8 autres* | | |

### 📁 Fichiers les plus impactés

| Fichier | Issues |
|---------|-------:|
| `config/TestDataInitializer.java` | 96 |
| `service/AccountApplicationServiceTest.java` | 18 |
| `service/AccountApplicationService.java` | 7 |
| `service/DocumentService.java` | 7 |
| `config/OpenApiConfig.java` | 7 |
| `service/ContractPdfService.java` | 7 |
| `service/OcrService.java` | 7 |
| `controller/GreenScoreTestController.java` | 5 |
| `config/TestDataInitializer.java` | 5 |
| `config/TestProfileStubsConfig.java` | 3 |
| *… et 10 autres* | |

📅 *2026-04-08T19:59:37Z*

---

🌿 *API Green Score — [Framework](https://github.com/API-Green-Score/APIGreenScore) | [Training](https://github.com/API-Green-Score/training-student) | 🌱 [Creedengo](https://github.com/green-code-initiative) | Devoxx France 2026*

> 📊 Pour le dashboard interactif complet, ouvrez [`dashboard/index.html`](index.html)
