# 🌿 Green API Score Dashboard

> **Devoxx France 2026 — Green Architecture : moins de gras, plus d'impact !**

📅 *Dernière analyse : 2026-04-03T20:35:01Z*

---

## 🔴 Green Score : **10/100** — Grade **E** 📉

### 📋 Détail par règle

| Statut | Règle | Score | Max | Détail |
|:------:|-------|------:|----:|--------|
| ❌ | DE11 Pagination | 0 | 15 | No pagination params found on collection endpoints |
| ❌ | DE08 Filtrage champs | 0 | 15 | No fields filter found |
| ❌ | DE01 Compression | 0 | 15 | Gzip not detected |
| ❌ | DE02/03 Cache ETag | 0 | 15 | ETag/304 not detected |
| ❌ | DE06 Delta | 0 | 10 | No delta endpoint found |
| ❌ | 206 Range | 0 | 10 | Range not supported |
| ✅ | LO01 Observabilité | 5 | 5 | Actuator/health detected |
| ✅ | US07 Rate Limit | 5 | 5 | Assumed present (API running) |
| ❌ | AR02 CBOR | 0 | 10 | No binary format endpoint |

### 📊 Comparaison Avant / Après

| Mesure | Taille | Temps | HTTP |
|--------|-------:|------:|-----:|
| 🔴 Baseline `/books` (full) | 70 B | 0.024s | 500 |
| 🟢 Pagination (`?page&size`) | 0 B | 0.000s | 0 |
| 🟢 Fields filter (`fields=`) | 0 B | 0.000s | 0 |
| 🟢 Gzip compression | 0 B | 0.000s | 0 |
| 🟢 ETag / 304 | 0 B | 0.000s | 0 |
| 🟢 Delta changes | 0 B | 0.000s | 0 |
| 🟢 Range 206 | 0 B | 0.000s | 0 |
| 🟢 CBOR binary | 0 B | 0.000s | 0 |
| 🟢 Full payload (optimized) | 79 B | 0.015s | 200 |

### 🔍 Auto-Discovery (28 endpoints)

| Méthode | Path | HTTP | Taille | Temps |
|---------|------|-----:|-------:|------:|
| GET | `/api/account-applications/{id}` | 500 | 70 B | 0.031s |
| PUT | `/api/account-applications/{id}` | 500 | 70 B | 0.015s |
| POST | `/api/webhooks/stripe` | 500 | 70 B | 0.012s |
| POST | `/api/sign/{token}` | 500 | 70 B | 0.012s |
| POST | `/api/sign/{token}/verify-otp` | 500 | 70 B | 0.013s |
| POST | `/api/sign/{token}/send-otp` | 500 | 70 B | 0.012s |
| GET | `/api/payments` | 500 | 70 B | 0.036s |
| POST | `/api/payments` | 500 | 70 B | 0.021s |
| POST | `/api/payments/{id}/cancel` | 500 | 70 B | 0.013s |
| POST | `/api/payments/confirm` | 500 | 70 B | 0.011s |
| GET | `/api/documents` | 500 | 70 B | 0.011s |
| POST | `/api/documents` | 500 | 70 B | 0.010s |
| POST | `/api/documents/{id}/send` | 500 | 70 B | 0.015s |
| POST | `/api/documents/{id}/resend` | 500 | 70 B | 0.031s |
| POST | `/api/documents/{id}/live-sign/{signerId}` | 500 | 70 B | 0.016s |
| GET | `/api/account-applications` | 500 | 70 B | 0.009s |
| POST | `/api/account-applications` | 500 | 70 B | 0.010s |
| POST | `/api/account-applications/{id}/submit` | 500 | 70 B | 0.014s |
| POST | `/api/account-applications/{id}/regenerate-contract` | 500 | 70 B | 0.009s |
| POST | `/api/account-applications/{id}/kyc` | 500 | 70 B | 0.008s |
| POST | `/api/account-applications/{id}/generate-contract` | 500 | 70 B | 0.010s |
| GET | `/api/sign/verify/{token}` | 404 | 76 B | 0.286s |
| GET | `/api/payments/{id}` | 500 | 70 B | 0.014s |
| GET | `/api/payments/config` | 200 | 79 B | 0.015s |
| GET | `/api/documents/{id}` | 500 | 70 B | 0.010s |
| GET | `/api/documents/{id}/download` | 500 | 70 B | 0.010s |
| GET | `/api/auth/me` | 500 | 70 B | 0.015s |
| GET | `/api/account-types` | 200 | 41 B | 0.017s |

---

🌿 *API Green Score — [Framework](https://github.com/API-Green-Score/APIGreenScore) | [Training](https://github.com/API-Green-Score/training-student) | Devoxx France 2026*

> 📊 Pour le dashboard interactif complet, ouvrez [`dashboard/index.html`](index.html)
