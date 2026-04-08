#!/usr/bin/env python3
"""
🌱 Creedengo Results Extractor
================================
Extracts eco-design issues from SonarQube (with Creedengo plugin) via its Web API
and generates a JSON report compatible with the Green Score dashboard.

Usage:
    python3 creedengo-extract-results.py \
        --sonar-url http://localhost:9100 \
        --sonar-token <TOKEN> \
        --project-key esign-payment-backend \
        --output reports/creedengo-report.json
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from datetime import datetime, timezone
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import URLError, HTTPError
from urllib.parse import urlencode, quote
from base64 import b64encode


# ── Scoring model ──
SEVERITY_PENALTIES = {
    "BLOCKER": 15,
    "CRITICAL": 8,
    "MAJOR": 4,
    "MINOR": 1,
    "INFO": 0.5,
}

GRADE_THRESHOLDS = [
    (95, "A+"),
    (85, "A"),
    (70, "B"),
    (50, "C"),
    (30, "D"),
    (0, "E"),
]

# ── Creedengo rule categories (eco-design concerns) ──
RULE_CATEGORIES = {
    "energy": "⚡ Consommation d'énergie",
    "memory": "💾 Utilisation mémoire",
    "cpu": "🖥️ Utilisation CPU",
    "network": "🌐 Transfert réseau",
    "storage": "💽 Stockage",
    "general": "🌱 Éco-conception générale",
}


def _api_get(base_url: str, path: str, params: dict = None,
             token: str = "", user: str = "", password: str = "",
             max_retries: int = 3, timeout: int = 90) -> dict:
    """Call SonarQube Web API and return JSON, with retry logic."""
    url = f"{base_url}{path}"
    if params:
        url += "?" + urlencode(params, quote_via=quote)

    req = Request(url)
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    elif user:
        creds = b64encode(f"{user}:{password}".encode()).decode()
        req.add_header("Authorization", f"Basic {creds}")

    last_error = None
    for attempt in range(1, max_retries + 1):
        try:
            with urlopen(req, timeout=timeout) as resp:
                return json.loads(resp.read().decode())
        except HTTPError as e:
            body = e.read().decode() if hasattr(e, 'read') else ''
            last_error = e
            # Don't retry on 4xx client errors (except 408 Request Timeout)
            if 400 <= e.code < 500 and e.code != 408:
                print(f"  ⚠ API error {e.code} for {path}: {body[:200]}", file=sys.stderr)
                return {}
            print(f"  ⚠ API error {e.code} for {path} (attempt {attempt}/{max_retries})", file=sys.stderr)
        except (URLError, Exception) as e:
            last_error = e
            print(f"  ⚠ Connection error for {path} (attempt {attempt}/{max_retries}): {e}", file=sys.stderr)

        if attempt < max_retries:
            import time
            wait = 2 ** attempt  # exponential backoff: 2s, 4s, 8s
            print(f"    ↻ Retrying in {wait}s...", file=sys.stderr)
            time.sleep(wait)

    print(f"  ❌ Failed after {max_retries} attempts for {path}: {last_error}", file=sys.stderr)
    return {}


def compute_score(issues: list[dict]) -> tuple[int, str]:
    """Compute Creedengo score (0-100) and grade from issue list."""
    penalty = 0.0
    for issue in issues:
        severity = issue.get("severity", "INFO")
        penalty += SEVERITY_PENALTIES.get(severity, 0.5)

    score = max(0, round(100 - penalty))
    grade = "E"
    for threshold, g in GRADE_THRESHOLDS:
        if score >= threshold:
            grade = g
            break
    return score, grade


def categorize_rule(rule_key: str, rule_name: str, rule_desc: str) -> str:
    """Categorize a Creedengo rule into an eco-design concern."""
    text = f"{rule_key} {rule_name} {rule_desc}".lower()
    if any(w in text for w in ["loop", "concatenat", "stringbuilder", "regex", "cpu", "autobox"]):
        return "cpu"
    if any(w in text for w in ["memory", "object", "collection", "array", "resource", "close", "cursor"]):
        return "memory"
    if any(w in text for w in ["sql", "query", "database", "request"]):
        return "storage"
    if any(w in text for w in ["network", "http", "download", "transfer"]):
        return "network"
    if any(w in text for w in ["energy", "power", "battery"]):
        return "energy"
    return "general"


def extract_results(sonar_url: str, project_key: str,
                    token: str = "", user: str = "", password: str = "",
                    appname: str = "", language: str = "java",
                    sonar_repos: str = "") -> dict:
    """Extract Creedengo analysis results from SonarQube."""
    import time

    auth = dict(token=token, user=user, password=password)

    # ── Wait for CE task to be fully complete before extracting ──
    # NOTE: /api/ce/activity is more reliable than /api/ce/component
    # which often returns 403 with project-level tokens.
    print("  ⏳ Checking analysis task status...")
    ce_timeout = 300
    ce_elapsed = 0
    while ce_elapsed < ce_timeout:
        # Try /api/ce/activity first (more permissive endpoint)
        ce_data = _api_get(sonar_url, "/api/ce/activity",
                           {"component": project_key, "ps": "1"}, **auth)
        tasks = ce_data.get("tasks", [])
        if tasks:
            ce_status = tasks[0].get("status", "PENDING")
        else:
            # Fallback: try /api/ce/component (may work with admin creds)
            ce_data2 = _api_get(sonar_url, "/api/ce/component",
                                {"component": project_key}, **auth)
            ce_status = ce_data2.get("current", {}).get("status", "") if ce_data2 else ""
            if not ce_status:
                # No task at all — analysis may not have been submitted
                ce_status = "NO_TASK"
        if ce_status == "SUCCESS":
            print(f"  ✅ Analysis task complete ({ce_elapsed}s)")
            break
        elif ce_status == "FAILED":
            print(f"  ⚠ Analysis task failed — extracting whatever is available")
            break
        elif ce_status in ("PENDING", "IN_PROGRESS"):
            if ce_elapsed % 15 == 0:
                print(f"    ... waiting for analysis ({ce_elapsed}s/{ce_timeout}s, status: {ce_status})")
            time.sleep(3)
            ce_elapsed += 3
        elif ce_status == "NO_TASK":
            print(f"  ⚠ No analysis task found — analysis may not have been submitted")
            break
        else:
            # Unknown status or no task found — might be first run or already done
            print(f"  ⚠ CE status: {ce_status} — proceeding with extraction")
            break
    if ce_elapsed >= ce_timeout:
        print(f"  ⚠ Timeout waiting for CE task ({ce_timeout}s) — extracting partial results")

    # Determine which repositories to query (multi-language support)
    repo_list = [r.strip() for r in sonar_repos.split(",") if r.strip()] if sonar_repos else [f"creedengo-{language}"]

    # ── Step 1: Fetch all rule KEYS from each repository first ──
    # The /api/issues/search endpoint's "rules" parameter expects actual rule keys
    # (e.g., "creedengo-java:EC1"), NOT repository names ("creedengo-java").
    # Passing a repository name directly causes HTTP 400.
    print("  📋 Fetching rule keys from repositories...")
    repo_rule_keys: dict[str, list[str]] = {}  # repo -> [rule_key, ...]
    for repo in repo_list:
        rules_data = _api_get(sonar_url, "/api/rules/search", {
            "repositories": repo,
            "ps": 500,
        }, **auth)
        keys = [r["key"] for r in rules_data.get("rules", [])]
        repo_rule_keys[repo] = keys
        if keys:
            print(f"    {repo}: {len(keys)} rules found")
        else:
            print(f"    {repo}: no rules found (plugin may not be loaded)")

    # ── Step 2: Fetch Creedengo issues using actual rule keys ──
    print("  📋 Fetching Creedengo issues...")
    all_issues = []
    for repo in repo_list:
        rule_keys = repo_rule_keys.get(repo, [])
        if not rule_keys:
            # No rules for this repo — try querying without filter then filter client-side
            print(f"    {repo}: no rules to query — will search all issues")
            continue

        # SonarQube limits the "rules" parameter length — batch in groups of 20
        batch_size = 20
        for i in range(0, len(rule_keys), batch_size):
            batch = rule_keys[i:i + batch_size]
            rules_param = ",".join(batch)
            page = 1
            while True:
                data = _api_get(sonar_url, "/api/issues/search", {
                    "componentKeys": project_key,
                    "rules": rules_param,
                    "ps": 500,
                    "p": page,
                    "resolved": "false",
                }, **auth)

                issues_page = data.get("issues", [])
                all_issues.extend(issues_page)

                total = data.get("total", 0)
                if len(issues_page) == 0 or page * 500 >= total:
                    break
                page += 1

        repo_issue_count = sum(1 for iss in all_issues if iss.get("rule", "").startswith(f"{repo}:"))
        if repo_issue_count > 0:
            print(f"    {repo}: {repo_issue_count} issues")

    print(f"  Found {len(all_issues)} Creedengo issues total")

    # If no creedengo-specific issues found, also try fetching ALL project issues
    # and filter client-side for any creedengo/ecocode rules
    if not all_issues:
        print("  📋 No creedengo-specific issues via rules filter. Trying broader search...")
        page = 1
        broader_issues = []
        while True:
            data = _api_get(sonar_url, "/api/issues/search", {
                "componentKeys": project_key,
                "ps": 500,
                "p": page,
                "resolved": "false",
            }, **auth)
            page_issues = data.get("issues", [])
            broader_issues.extend(page_issues)
            total = data.get("total", 0)
            if not page_issues or page * 500 >= total:
                break
            page += 1

        # Filter for creedengo/ecocode rules
        creedengo_prefixes = tuple(f"{r}:" for r in repo_list)
        ecocode_prefixes = tuple(f"ecocode-{r.replace('creedengo-', '')}:" for r in repo_list)
        all_prefixes = creedengo_prefixes + ecocode_prefixes
        creedengo_issues = [i for i in broader_issues if i.get("rule", "").startswith(all_prefixes)]

        if creedengo_issues:
            all_issues = creedengo_issues
            print(f"  Found {len(all_issues)} creedengo issues from broad search")
        else:
            # Use all issues as fallback (general code quality)
            all_issues = broader_issues
            print(f"  Found {len(all_issues)} total project issues (no creedengo-specific)")

    # ── Fetch Creedengo rules metadata (reuse from step 1 if already fetched) ──
    print("  📋 Fetching rule definitions...")
    rules_meta = {}
    for repo in repo_list:
        # We already fetched rules above — but we need the full metadata
        rules_data = _api_get(sonar_url, "/api/rules/search", {
            "repositories": repo,
            "ps": 500,
        }, **auth)
        for rule in rules_data.get("rules", []):
            rules_meta[rule["key"]] = {
                "name": rule.get("name", ""),
                "severity": rule.get("severity", "INFO"),
                "type": rule.get("type", "CODE_SMELL"),
                "htmlDesc": rule.get("htmlDesc", ""),
                "tags": rule.get("tags", []),
            }
        rule_count = rules_data.get("total", 0)
        if rule_count > 0:
            print(f"    {repo}: {rule_count} rules defined")
        else:
            print(f"    {repo}: 0 rules defined — plugin may not be loaded")

    # ── Fetch project measures ──
    print("  📋 Fetching project measures...")
    measures_data = _api_get(sonar_url, "/api/measures/component", {
        "component": project_key,
        "metricKeys": "ncloc,bugs,vulnerabilities,code_smells,complexity,cognitive_complexity",
    }, **auth)
    measures = {}
    for m in measures_data.get("component", {}).get("measures", []):
        measures[m["metric"]] = m.get("value", "0")

    # ── Build structured issues list ──
    issues_list = []
    severity_counts = {"BLOCKER": 0, "CRITICAL": 0, "MAJOR": 0, "MINOR": 0, "INFO": 0}
    rules_count: dict[str, dict] = {}

    for issue in all_issues:
        rule_key = issue.get("rule", "")
        severity = issue.get("severity", "INFO")
        component = issue.get("component", "")
        # Remove project prefix from component path
        file_path = component.replace(f"{project_key}:", "")

        issue_entry = {
            "rule": rule_key,
            "severity": severity,
            "message": issue.get("message", ""),
            "file": file_path,
            "line": issue.get("line", 0),
            "effort": issue.get("effort", ""),
            "type": issue.get("type", "CODE_SMELL"),
        }
        issues_list.append(issue_entry)

        severity_counts[severity] = severity_counts.get(severity, 0) + 1

        # Aggregate by rule
        if rule_key not in rules_count:
            meta = rules_meta.get(rule_key, {})
            rules_count[rule_key] = {
                "key": rule_key,
                "name": meta.get("name", rule_key.split(":")[-1] if ":" in rule_key else rule_key),
                "severity": severity,
                "type": issue.get("type", "CODE_SMELL"),
                "description": meta.get("htmlDesc", "")[:300],
                "category": categorize_rule(rule_key,
                                            meta.get("name", ""),
                                            meta.get("htmlDesc", "")),
                "count": 0,
                "files": [],
            }
        rules_count[rule_key]["count"] += 1
        if file_path not in rules_count[rule_key]["files"]:
            rules_count[rule_key]["files"].append(file_path)

    # ── Compute score ──
    score, grade = compute_score(issues_list)

    # ── Build files summary (top files by issue count) ──
    files_count: dict[str, int] = {}
    for issue in issues_list:
        f = issue["file"]
        files_count[f] = files_count.get(f, 0) + 1
    top_files = sorted(files_count.items(), key=lambda x: -x[1])[:20]

    # ── Build category summary ──
    category_summary = {}
    for rule_data in rules_count.values():
        cat = rule_data["category"]
        if cat not in category_summary:
            category_summary[cat] = {
                "key": cat,
                "label": RULE_CATEGORIES.get(cat, cat),
                "issues_count": 0,
                "rules_count": 0,
            }
        category_summary[cat]["issues_count"] += rule_data["count"]
        category_summary[cat]["rules_count"] += 1

    # ── Total effort ──
    total_effort_min = 0
    for issue in issues_list:
        effort = issue.get("effort", "")
        if effort:
            # Parse "5min", "1h", "1h30min" etc
            mins = 0
            if "h" in effort:
                parts = effort.split("h")
                mins += int(parts[0]) * 60
                rest = parts[1].replace("min", "").strip()
                if rest:
                    mins += int(rest)
            elif "min" in effort:
                mins += int(effort.replace("min", "").strip())
            total_effort_min += mins

    effort_str = ""
    if total_effort_min >= 60:
        effort_str = f"{total_effort_min // 60}h{total_effort_min % 60:02d}min"
    else:
        effort_str = f"{total_effort_min}min"

    # ── Assemble report ──
    report = {
        "timestamp": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "appname": appname,
        "project": project_key,
        "language": language,
        "analyzer": "creedengo",
        "analyzer_version": "SonarQube + Creedengo Plugin",
        "repos_analyzed": repo_list,
        "creedengo_score": {
            "total": score,
            "max": 100,
            "grade": grade,
            "issues_count": len(issues_list),
            "severity_breakdown": severity_counts,
            "total_effort": effort_str,
            "total_effort_minutes": total_effort_min,
        },
        "measures": {
            "ncloc": int(measures.get("ncloc", 0)),
            "bugs": int(measures.get("bugs", 0)),
            "vulnerabilities": int(measures.get("vulnerabilities", 0)),
            "code_smells": int(measures.get("code_smells", 0)),
            "complexity": int(measures.get("complexity", 0)),
            "cognitive_complexity": int(measures.get("cognitive_complexity", 0)),
        },
        "rules_summary": sorted(
            list(rules_count.values()),
            key=lambda r: (
                {"BLOCKER": 0, "CRITICAL": 1, "MAJOR": 2, "MINOR": 3, "INFO": 4}.get(r["severity"], 5),
                -r["count"],
            ),
        ),
        "categories": sorted(
            list(category_summary.values()),
            key=lambda c: -c["issues_count"],
        ),
        "top_files": [{"file": f, "count": c} for f, c in top_files],
        "issues": issues_list[:200],  # Cap at 200 for report size
        "all_creedengo_rules": len(rules_meta),
        "rules_violated": len(rules_count),
    }

    return report


def main() -> int:
    parser = argparse.ArgumentParser(description="Extract Creedengo results from SonarQube")
    parser.add_argument("--sonar-url", required=True, help="SonarQube URL")
    parser.add_argument("--sonar-token", default="", help="SonarQube auth token")
    parser.add_argument("--sonar-user", default="", help="SonarQube username")
    parser.add_argument("--sonar-password", default="", help="SonarQube password")
    parser.add_argument("--project-key", required=True, help="SonarQube project key")
    parser.add_argument("--output", required=True, help="Output JSON file path")
    parser.add_argument("--appname", default="", help="Application name")
    parser.add_argument("--language", default="java", help="Primary language (java, python, javascript, csharp)")
    parser.add_argument("--sonar-repos", default="", help="Comma-separated SonarQube rule repositories (e.g. creedengo-java,creedengo-python)")
    args = parser.parse_args()

    report = extract_results(
        sonar_url=args.sonar_url,
        project_key=args.project_key,
        token=args.sonar_token,
        user=args.sonar_user,
        password=args.sonar_password,
        appname=args.appname,
        language=args.language,
        sonar_repos=args.sonar_repos,
    )

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"  ✅ Creedengo report written to {output_path}")
    print(f"     Score: {report['creedengo_score']['total']}/100 "
          f"(Grade: {report['creedengo_score']['grade']}) "
          f"— {report['creedengo_score']['issues_count']} issues")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

