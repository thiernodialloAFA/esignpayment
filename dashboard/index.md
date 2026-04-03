# 🌿 Green API Score Dashboard

> **Devoxx France 2026 — Green Architecture : moins de gras, plus d'impact !**

📅 *Dernière analyse : 2026-04-03T22:13:11Z*

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
| 🟢 Full payload (optimized) | 6.8 KB | 0.009s | 200 |

### 🔍 Auto-Discovery (27 endpoints)

| Méthode | Path | HTTP | Taille | Temps |
|---------|------|-----:|-------:|------:|
| GET | `/api/account-applications/{id}` | 200 | 917 B | 0.013s |
| PUT | `/api/account-applications/{id}` | 200 | 938 B | 0.029s |
| POST | `/api/sign/{token}` | 200 | 1.3 KB | 0.032s |
| POST | `/api/sign/{token}/verify-otp` | 200 | 117 B | 0.007s |
| POST | `/api/sign/{token}/send-otp` | 200 | 135 B | 0.006s |
| GET | `/api/payments` | 200 | 1.0 KB | 0.004s |
| POST | `/api/payments` | 201 | 492 B | 0.038s |
| POST | `/api/payments/{id}/cancel` | 200 | 373 B | 0.005s |
| POST | `/api/payments/confirm` | 200 | 384 B | 0.008s |
| GET | `/api/documents` | 200 | 5.2 KB | 0.009s |
| POST | `/api/documents` | 201 | 756 B | 0.012s |
| POST | `/api/documents/{id}/send` | 200 | 749 B | 0.006s |
| POST | `/api/documents/{id}/resend` | 200 | 770 B | 0.005s |
| POST | `/api/documents/{id}/live-sign/{signerId}` | 200 | 765 B | 2.062s |
| GET | `/api/account-applications` | 200 | 6.8 KB | 0.009s |
| POST | `/api/account-applications` | 201 | 930 B | 0.016s |
| POST | `/api/account-applications/{id}/submit` | 200 | 1.9 KB | 0.008s |
| POST | `/api/account-applications/{id}/regenerate-contract` | 200 | 1.3 KB | 0.024s |
| POST | `/api/account-applications/{id}/kyc` | 201 | 363 B | 0.011s |
| POST | `/api/account-applications/{id}/generate-contract` | 200 | 1.1 KB | 0.031s |
| GET | `/api/sign/verify/{token}` | 200 | 229 B | 0.003s |
| GET | `/api/payments/{id}` | 200 | 359 B | 0.004s |
| GET | `/api/payments/config` | 200 | 79 B | 0.002s |
| GET | `/api/documents/{id}` | 200 | 721 B | 0.003s |
| GET | `/api/documents/{id}/download` | 200 | 220 B | 0.004s |
| GET | `/api/auth/me` | 200 | 207 B | 0.003s |
| GET | `/api/account-types` | 200 | 559 B | 0.005s |

---

🌿 *API Green Score — [Framework](https://github.com/API-Green-Score/APIGreenScore) | [Training](https://github.com/API-Green-Score/training-student) | Devoxx France 2026*

> 📊 Pour le dashboard interactif complet, ouvrez [`dashboard/index.html`](index.html)
