#!/usr/bin/env python3
"""Automated verification harness for Material Lab v2.

Loads grounded gold test cases (real-shaped messages with expected routes and
cluster decisions) and checks the v2 pipeline against them. Offline-only.

Usage:
    python scripts/verify_material_lab_v2.py
    python scripts/verify_material_lab_v2.py --messages reports/material-selection-lab-v2/gold/message_test_cases.jsonl
"""

from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

import material_lab_v2 as v2  # noqa: E402


def read_jsonl(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8") as handle:
        return [json.loads(line) for line in handle if line.strip()]


def make_row(raw: dict, index: int, snapshot_id: str) -> dict:
    row = v2.prepare_row(raw, index, snapshot_id)
    row["conversation_key"] = v2.conversation_key(row)
    return v2.normalize_one(row)


def verify_messages(cases: list[dict]) -> list[dict]:
    snapshot_id = "verify"
    results = []
    for index, case in enumerate(cases, start=1):
        row = make_row(case, index, snapshot_id)
        routed = v2.route_one(row)
        actual_route = routed.get("route")
        actual_marker = routed.get("marker_id")
        route_ok = actual_route == case.get("expected_route")
        marker_ok = (case.get("expected_marker") is None) or (actual_marker == case.get("expected_marker"))
        results.append({
            "id": case.get("id"),
            "category": case.get("category"),
            "expected_route": case.get("expected_route"),
            "actual_route": actual_route,
            "expected_marker": case.get("expected_marker"),
            "actual_marker": actual_marker,
            "route_ok": route_ok,
            "marker_ok": marker_ok,
            "ok": route_ok and marker_ok,
            "preview": routed.get("preview"),
            "route_reason": routed.get("route_reason"),
            "notes": case.get("notes", ""),
        })
    return results


def verify_clusters(cases: list[dict]) -> list[dict]:
    snapshot_id = "verify"
    results = []
    for case in cases:
        rows = []
        routed = {}
        expected_ids = []
        for index, msg in enumerate(case.get("messages", []), start=1):
            row = make_row(msg, index, snapshot_id)
            rows.append(row)
            routed[row["lab_message_id"]] = v2.route_one(row)
            expected_ids.append(row["raw_id"])
        resolved = v2.resolve_reply_chains(rows)
        local, _ctx = v2.build_conversation_clusters(rows, routed, resolved, 10, 10)
        merged = v2.merge_cross_conversation(local)
        expected = case.get("expected_cluster_decision")
        not_expected = case.get("expected_not_decision")
        actual_decision = "NO_CLUSTER"
        cluster_decisions = []
        for members in merged:
            member_ids = {m["raw_id"] for m in members}
            if expected_ids and any(i in member_ids for i in expected_ids):
                cluster_decisions.append(v2.cluster_decision(members, routed))
        if cluster_decisions:
            actual_decision = cluster_decisions[0]
        if expected:
            ok = actual_decision == expected
        elif not_expected:
            ok = not_expected not in cluster_decisions and actual_decision != not_expected
        else:
            ok = True
        results.append({
            "group_id": case.get("group_id"),
            "category": case.get("category"),
            "expected_cluster_decision": expected or f"not {not_expected}",
            "actual_cluster_decision": actual_decision,
            "all_cluster_decisions": cluster_decisions,
            "ok": ok,
            "notes": case.get("notes", ""),
        })
    return results


def write_report(path: Path, message_results: list[dict], cluster_results: list[dict]) -> None:
    m_ok = sum(1 for r in message_results if r["ok"])
    c_ok = sum(1 for r in cluster_results if r["ok"])
    lines = [
        "# Material Lab v2 Automated Verification",
        "",
        f"Message cases: `{m_ok}/{len(message_results)}` passed",
        f"Cluster cases: `{c_ok}/{len(cluster_results)}` passed",
        "",
    ]
    if any(not r["ok"] for r in message_results):
        lines.append("## Message Failures")
        lines.append("")
        for r in message_results:
            if r["ok"]:
                continue
            lines.append(f"- `{r['id']}` ({r['category']}): expected `{r['expected_route']}` got `{r['actual_route']}`; marker expected `{r['expected_marker']}` got `{r['actual_marker']}`; reason: {r['route_reason']}; preview: {r['preview']}")
        lines.append("")
    if any(not r["ok"] for r in cluster_results):
        lines.append("## Cluster Failures")
        lines.append("")
        for r in cluster_results:
            if r["ok"]:
                continue
            lines.append(f"- `{r['group_id']}` ({r['category']}): expected `{r['expected_cluster_decision']}` got `{r['actual_cluster_decision']}`; {r['notes']}")
        lines.append("")
    lines.append("## Message Case Summary")
    lines.append("")
    lines.append("| id | category | expected | actual | ok |")
    lines.append("|---|---|---|---|---|")
    for r in message_results:
        lines.append(f"| `{r['id']}` | {r['category']} | `{r['expected_route']}` | `{r['actual_route']}` | {'PASS' if r['ok'] else 'FAIL'} |")
    lines.append("")
    lines.append("## Cluster Case Summary")
    lines.append("")
    lines.append("| group | category | expected | actual | ok |")
    lines.append("|---|---|---|---|---|")
    for r in cluster_results:
        lines.append(f"| `{r['group_id']}` | {r['category']} | `{r['expected_cluster_decision']}` | `{r['actual_cluster_decision']}` | {'PASS' if r['ok'] else 'FAIL'} |")
    lines.append("")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")
    json_path = path.with_suffix(".json")
    json_path.write_text(json.dumps({
        "message_results": message_results,
        "cluster_results": cluster_results,
        "message_pass": m_ok,
        "message_total": len(message_results),
        "cluster_pass": c_ok,
        "cluster_total": len(cluster_results),
    }, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description="Automated verification for Material Lab v2")
    parser.add_argument("--messages", type=Path, default=PROJECT_ROOT / "reports" / "material-selection-lab-v2" / "gold" / "message_test_cases.jsonl")
    parser.add_argument("--clusters", type=Path, default=PROJECT_ROOT / "reports" / "material-selection-lab-v2" / "gold" / "cluster_test_cases.jsonl")
    parser.add_argument("--report", type=Path, default=PROJECT_ROOT / "reports" / "material-selection-lab-v2" / "gold" / "verification_report.md")
    args = parser.parse_args()
    message_results = verify_messages(read_jsonl(args.messages))
    cluster_results = verify_clusters(read_jsonl(args.clusters))
    write_report(args.report, message_results, cluster_results)
    m_ok = sum(1 for r in message_results if r["ok"])
    c_ok = sum(1 for r in cluster_results if r["ok"])
    print(f"messages: {m_ok}/{len(message_results)} passed")
    print(f"clusters: {c_ok}/{len(cluster_results)} passed")
    print(f"report -> {args.report}")
    by_cat = Counter(r["category"] for r in message_results if not r["ok"])
    for cat, n in by_cat.most_common():
        print(f"  FAIL category {cat}: {n}")
    if m_ok != len(message_results) or c_ok != len(cluster_results):
        sys.exit(1)


if __name__ == "__main__":
    main()
