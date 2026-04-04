# 🌿 Green API Score Dashboard

> 📦 **Application : esignpaymentstarter-main**
>
> **Devoxx France 2026 — Green Architecture : moins de gras, plus d'impact !**

📅 *Dernière analyse : 2026-04-04T15:07:58Z*

---

## 🟢 Green Score : **85/100** — Grade **A** 🥇

### 📋 Détail par règle

| Statut | Règle | Score | Max | Détail |
|:------:|-------|------:|----:|--------|
| ✅ | DE11 Pagination | 15 | 15 | Pagination on 3/9 collection endpoint(s) |
| ✅ | DE08 Filtrage champs | 15 | 15 | Field filtering on 12 endpoint(s) |
| ✅ | DE01 Compression | 15 | 15 | Gzip compression active (OpenAPI servers.x-server-compression.enabled=true) |
| ❌ | DE02/03 Cache ETag | 0 | 15 | ETag/304 not detected |
| ✅ | DE06 Delta | 10 | 10 | Delta endpoint(s) found: 3 |
| ✅ | 206 Range | 10 | 10 | Range/206 supported (OpenAPI servers.x-server-range-support.enabled=true) |
| ✅ | LO01 Observabilité | 5 | 5 | Actuator/health detected |
| ✅ | US07 Rate Limit | 5 | 5 | Rate-limit headers detected |
| ✅ | AR02 CBOR | 10 | 10 | Binary format on 1 endpoint(s) |

### 📊 Comparaison Avant / Après

| Mesure | Taille | Temps | HTTP |
|--------|-------:|------:|-----:|
| 🔴 Baseline `/books` (full) | 70 B | 0.024s | 500 |
| 🟢 Pagination (`?page&size`) | 0 B | 0.000s | 0 |
| 🟢 Fields filter (`fields=`) | 0 B | 0.000s | 0 |
| 🟢 Gzip compression | 0 B | 0.000s | 0 |
| 🟢 ETag / 304 | 0 B | 0.000s | 0 |
| 🟢 Delta changes | 70 B | 0.010s | 500 |
| 🟢 Range 206 | 0 B | 0.000s | 0 |
| 🟢 CBOR binary | 0 B | 0.000s | 0 |
| 🟢 Full payload (optimized) | 1.3 KB | 0.033s | 200 |

### 🔍 Auto-Discovery (33 endpoints)

| Méthode | Path | HTTP | Taille | Temps |
|---------|------|-----:|-------:|------:|
| GET | `/api/account-applications/{id}` | 403 | 99 B | 0.009s |
| PUT | `/api/account-applications/{id}` | 403 | 99 B | 0.027s |
| DELETE | `/api/account-applications/{id}` | 403 | 99 B | 0.006s |
| POST | `/api/sign/{token}` | 200 | 1.3 KB | 0.033s |
| POST | `/api/sign/{token}/verify-otp` | 200 | 117 B | 0.007s |
| POST | `/api/sign/{token}/send-otp` | 200 | 135 B | 0.008s |
| GET | `/api/payments` | 200 | 356 B | 0.009s |
| POST | `/api/payments` | 500 | 70 B | 0.060s |
| POST | `/api/payments/{id}/cancel` | 403 | 95 B | 0.005s |
| POST | `/api/payments/confirm` | 403 | 95 B | 0.008s |
| GET | `/api/documents` | 200 | 356 B | 0.012s |
| POST | `/api/documents` | 500 | 70 B | 0.050s |
| POST | `/api/documents/{id}/send` | 403 | 96 B | 0.013s |
| POST | `/api/documents/{id}/resend` | 403 | 96 B | 0.006s |
| POST | `/api/documents/{id}/live-sign/{signerId}` | 403 | 96 B | 0.011s |
| GET | `/api/account-applications` | 200 | 356 B | 0.007s |
| POST | `/api/account-applications` | 500 | 70 B | 0.017s |
| POST | `/api/account-applications/{id}/submit` | 403 | 99 B | 0.011s |
| POST | `/api/account-applications/{id}/regenerate-contract` | 403 | 99 B | 0.010s |
| POST | `/api/account-applications/{id}/kyc` | 403 | 99 B | 0.014s |
| POST | `/api/account-applications/{id}/generate-contract` | 500 | 70 B | 0.281s |
| GET | `/api/sign/verify/{token}` | 200 | 228 B | 0.004s |
| GET | `/api/payments/{id}` | 403 | 95 B | 0.005s |
| GET | `/api/payments/config` | 200 | 79 B | 0.005s |
| GET | `/api/payments/changes` | 500 | 70 B | 0.010s |
| GET | `/api/documents/{id}` | 403 | 96 B | 0.013s |
| DELETE | `/api/documents/{id}` | 403 | 96 B | 0.012s |
| GET | `/api/documents/{id}/download` | 403 | 96 B | 0.013s |
| GET | `/api/documents/changes` | 500 | 70 B | 0.008s |
| GET | `/api/auth/me` | 500 | 70 B | 0.016s |

### 💡 Suggestions d'amélioration

> **Score actuel : 85/100** — Score potentiel avec toutes les suggestions : **100/100** (+15 pts possibles)

🔴 Haute priorité : 5 | 🟡 Moyenne : 0 | ⚪ Basse : 0 | **Total : 5 suggestions**

#### 💾 DE02/03 — Cache ETag/304 (❌ Non validé — +15 pts possibles)

> Les ressources unitaires doivent supporter ETag + If-None-Match -> 304. (0/5 endpoints validés)

| Priorité | Cible | Action | Impact |
|:--------:|-------|--------|--------|
| 🔴 Haute | `GET /api/account-applications/{id}` | Add ETag support and If-None-Match → 304 Not Modified | +15 pts — avoids resending unchanged resources, saves bandwidth |
| 🔴 Haute | `GET /api/sign/verify/{token}` | Add ETag support and If-None-Match → 304 Not Modified | +15 pts — avoids resending unchanged resources, saves bandwidth |
| 🔴 Haute | `GET /api/payments/{id}` | Add ETag support and If-None-Match → 304 Not Modified | +15 pts — avoids resending unchanged resources, saves bandwidth |
| 🔴 Haute | `GET /api/documents/{id}` | Add ETag support and If-None-Match → 304 Not Modified | +15 pts — avoids resending unchanged resources, saves bandwidth |
| 🔴 Haute | `GET /api/documents/{id}/download` | Add ETag support and If-None-Match → 304 Not Modified | +15 pts — avoids resending unchanged resources, saves bandwidth |

<details><summary>🔧 Comment implémenter</summary>

```
Spring Boot: Use ShallowEtagHeaderFilter (zero-code) or manual ETags:

  Option A — Global filter (easiest):
  @Bean
  public FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
    FilterRegistrationBean<ShallowEtagHeaderFilter> reg = new FilterRegistrationBean<>();
    reg.setFilter(new ShallowEtagHeaderFilter());
    reg.addUrlPatterns("/api/*");
    return reg;
  }

  Option B — Manual per endpoint:
  String etag = '"' + DigestUtils.md5DigestAsHex(body.getBytes()) + '"';
  if (request.checkNotModified(etag)) return null; // → 304
  return ResponseEntity.ok().eTag(etag).body(body);
```
</details>

---

🌿 *API Green Score — [Framework](https://github.com/API-Green-Score/APIGreenScore) | [Training](https://github.com/API-Green-Score/training-student) | Devoxx France 2026*

> 📊 Pour le dashboard interactif complet, ouvrez [`dashboard/index.html`](index.html)
