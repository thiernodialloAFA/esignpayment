#!/usr/bin/env python3
"""Quick test: call every endpoint and report HTTP status codes."""
import urllib.request
import urllib.error
import json
import sys

BASE = "http://localhost:8080"

# Load scenario
try:
    with urllib.request.urlopen(f"{BASE}/api/test/green-score/scenario", timeout=10) as r:
        scenario = json.loads(r.read())
    print("OK  Scenario loaded")
except Exception as e:
    print(f"ERR Cannot load scenario: {e}")
    sys.exit(1)

path_params = scenario.get("pathParams", {})
request_bodies = scenario.get("requestBodies", {})


def fill_path(path):
    import re
    params = path_params.get(path, {})
    return re.sub(r"\{([^}]+)\}", lambda m: str(params.get(m.group(1), "1")), path)


# GET endpoints
get_endpoints = [
    "/api/payments/config",
    "/api/auth/me",
    "/api/account-types",
    "/api/account-applications",
    "/api/documents",
    "/api/payments",
    "/api/sign/verify/{token}",
    "/api/account-applications/{id}",
    "/api/documents/{id}",
    "/api/documents/{id}/download",
    "/api/payments/{id}",
]

# POST endpoints
post_endpoints = [
    "/api/account-applications",
    "/api/account-applications/{id}/kyc",
    "/api/account-applications/{id}/submit",
    "/api/account-applications/{id}/generate-contract",
    "/api/account-applications/{id}/regenerate-contract",
    "/api/documents",
    "/api/documents/{id}/send",
    "/api/documents/{id}/resend",
    "/api/documents/{id}/live-sign/{signerId}",
    "/api/sign/{token}/send-otp",
    "/api/sign/{token}/verify-otp",
    "/api/sign/{token}",
    "/api/payments",
    "/api/payments/confirm",
    "/api/payments/{id}/cancel",
]

# PUT endpoints
put_endpoints = [
    "/api/account-applications/{id}",
]

print("\n=== GET ENDPOINTS ===")
for path in get_endpoints:
    url = BASE + fill_path(path)
    try:
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req, timeout=10) as resp:
            print(f"  {resp.status}  GET  {path}")
    except urllib.error.HTTPError as e:
        print(f"  {e.code}  GET  {path}  <- FAIL")
    except Exception as ex:
        print(f"  ERR  GET  {path}  <- {ex}")

print("\n=== POST ENDPOINTS ===")
for path in post_endpoints:
    url = BASE + fill_path(path)
    body_key = f"post:{path}"
    body = request_bodies.get(body_key)
    body_bytes = json.dumps(body).encode() if body else None
    headers = {"Content-Type": "application/json"} if body_bytes else {}
    try:
        req = urllib.request.Request(url, method="POST", headers=headers, data=body_bytes)
        with urllib.request.urlopen(req, timeout=15) as resp:
            print(f"  {resp.status}  POST {path}")
    except urllib.error.HTTPError as e:
        print(f"  {e.code}  POST {path}  <- FAIL")
    except Exception as ex:
        print(f"  ERR  POST {path}  <- {ex}")

print("\n=== PUT ENDPOINTS ===")
for path in put_endpoints:
    url = BASE + fill_path(path)
    body_key = f"put:{path}"
    body = request_bodies.get(body_key)
    body_bytes = json.dumps(body).encode() if body else None
    headers = {"Content-Type": "application/json"} if body_bytes else {}
    try:
        req = urllib.request.Request(url, method="PUT", headers=headers, data=body_bytes)
        with urllib.request.urlopen(req, timeout=15) as resp:
            print(f"  {resp.status}  PUT  {path}")
    except urllib.error.HTTPError as e:
        print(f"  {e.code}  PUT  {path}  <- FAIL")
    except Exception as ex:
        print(f"  ERR  PUT  {path}  <- {ex}")

