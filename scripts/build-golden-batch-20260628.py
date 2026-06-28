from __future__ import annotations

import csv
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPORTS = ROOT / "reports"


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as fh:
        return list(csv.DictReader(fh))


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def find_row(rows: list[dict[str, str]], key: str, value: str) -> dict[str, str] | None:
    for row in rows:
        if row.get(key) == value:
            return row
    return None


def main() -> None:
    expected_materials = read_csv(REPORTS / "manual-expected-materials-20260626.csv")
    usefulness_dry_run = read_csv(REPORTS / "message-usefulness-classifier-dry-run-20260627.csv")
    judge_dry_run = (REPORTS / "message-usefulness-llm-judge-dry-run-20260627.md").read_text(encoding="utf-8")
    overnight = read_json(REPORTS / "overnight-expected-materials-audit-20260627.json")
    source_recovery = read_json(REPORTS / "expected-message-source-recovery-20260627.json")

    source_recovery_rows = {row["expectedId"]: row for row in source_recovery.get("rows", [])}
    overnight_rows = {row["manual_id"]: row for row in overnight.get("messages", [])}

    batch = {
        "batchVersion": "golden-real-posts-20260628-v1",
        "generatedFrom": [
            "reports/manual-expected-materials-20260626.csv",
            "reports/message-usefulness-classifier-dry-run-20260627.csv",
            "reports/message-usefulness-llm-judge-dry-run-20260627.md",
            "reports/overnight-expected-materials-audit-20260627.json",
            "reports/expected-message-source-recovery-20260627.json",
        ],
        "summary": {
            "purpose": "Initial real-post golden batch for regression checks across single-message, discussion-segment, reference, and hard reject paths.",
            "night_window_msk": "2026-06-27 03:40:00 - 2026-06-27 04:15:00",
            "night_window_raw_messages": overnight["counts"]["rawWindow"],
            "night_window_discussion_segments": overnight["counts"]["discussionSegments"],
        },
        "cases": [
            {
                "caseId": "GB01",
                "kind": "single_message_reference",
                "rawId": 11142,
                "source": "message-usefulness judge dry-run",
                "expected": "ACCEPT_AS_REFERENCE_NOT_GUIDE",
                "current": "JUDGE_ACCEPTED_REFERENCE",
                "evidence": {
                    "classifier": find_row(usefulness_dry_run, "raw_id", "11142"),
                    "judgeAccepted": "11142 | NEWS_UPDATE | SINGLE_MESSAGE | REFERENCE" in judge_dry_run,
                    "sourceRecovery": source_recovery_rows.get("M01"),
                },
            },
            {
                "caseId": "GB02",
                "kind": "single_message_reference",
                "rawId": 11484,
                "source": "message-usefulness judge dry-run",
                "expected": "ACCEPT_AS_REFERENCE_NOT_GUIDE",
                "current": "JUDGE_ACCEPTED_REFERENCE",
                "evidence": {
                    "classifier": find_row(usefulness_dry_run, "raw_id", "11484"),
                    "judgeAccepted": "11484 | RESOURCE_REFERENCE | SINGLE_MESSAGE | REFERENCE" in judge_dry_run,
                    "overnight": overnight_rows.get("M09"),
                },
            },
            {
                "caseId": "GB03",
                "kind": "single_message_negative_control",
                "rawId": 11193,
                "source": "message-usefulness judge dry-run",
                "expected": "REJECT_LINK_ONLY_NEEDS_ENRICHMENT",
                "current": "PRE_GATE_REJECTED",
                "evidence": {
                    "classifier": find_row(usefulness_dry_run, "raw_id", "11193"),
                    "judgeRejected": "11193 | LINK_ONLY | n/a | PRE_GATE_REJECTED" in judge_dry_run,
                    "sourceRecovery": source_recovery_rows.get("M11"),
                },
            },
            {
                "caseId": "GB04",
                "kind": "discussion_segment_guide",
                "clusterId": "CL01",
                "source": "manual expected materials",
                "expected": "DISCUSSION_SEGMENT_GUIDE",
                "current": "NOT_CAUGHT_TODAY",
                "evidence": find_row(expected_materials, "expected_id", "E01"),
            },
            {
                "caseId": "GB05",
                "kind": "discussion_segment_guide",
                "clusterId": "CL02",
                "source": "manual expected materials",
                "expected": "DISCUSSION_SEGMENT_GUIDE",
                "current": "NOT_CAUGHT_TODAY",
                "evidence": find_row(expected_materials, "expected_id", "E02"),
            },
            {
                "caseId": "GB06",
                "kind": "single_or_pair_resume_answer",
                "clusterId": "CL05",
                "source": "manual expected materials",
                "expected": "GUIDE_OR_STRONG_ANSWER",
                "current": "SHOULD_BE_CAUGHT_TODAY_PER_MANUAL_AUDIT",
                "evidence": find_row(expected_materials, "expected_id", "E05"),
            },
            {
                "caseId": "GB07",
                "kind": "safety_sensitive_context",
                "clusterId": "CL07",
                "source": "manual expected materials",
                "expected": "NEEDS_SAFETY_SUMMARY_OR_REJECT",
                "current": "CORRECT_TO_KEEP_BLOCKED",
                "evidence": find_row(expected_materials, "expected_id", "E07"),
            },
            {
                "caseId": "GB08",
                "kind": "promo_noise",
                "clusterId": "CL10",
                "source": "manual expected materials",
                "expected": "REJECT_PROMO",
                "current": "CORRECT_TO_REJECT",
                "evidence": find_row(expected_materials, "expected_id", "E10"),
            },
        ],
    }

    json_path = REPORTS / "golden-batch-real-posts-20260628.json"
    md_path = REPORTS / "golden-batch-real-posts-20260628.md"
    json_path.write_text(json.dumps(batch, ensure_ascii=False, indent=2), encoding="utf-8")

    lines = [
        "# Golden Batch Real Posts - 2026-06-28",
        "",
        "Initial real-post regression batch assembled from existing audits and dry-runs.",
        "",
        "## Night Ingest Fact Check",
        "",
        f"- Night window: `{batch['summary']['night_window_msk']}`",
        f"- Raw messages in window: `{batch['summary']['night_window_raw_messages']}`",
        f"- Discussion segments in window: `{batch['summary']['night_window_discussion_segments']}`",
        "- Conclusion: night ingest existed; expected useful cases mostly failed due to window mismatch or downstream candidate/routing loss, not because raw traffic was zero.",
        "",
        "## Cases",
        "",
        "| case_id | kind | expected | current | anchor |",
        "|---|---|---|---|---|",
    ]
    for case in batch["cases"]:
        anchor = str(case.get("rawId") or case.get("clusterId"))
        lines.append(f"| {case['caseId']} | {case['kind']} | {case['expected']} | {case['current']} | {anchor} |")
    lines += [
        "",
        "## Recommended First Regression Slice",
        "",
        "- Positive single-message reference: `11142`, `11484`.",
        "- Negative single-message control: `11193`.",
        "- Discussion guide misses: `E01`, `E02`.",
        "- Safety holdout: `E07`.",
        "- Promo reject: `E10`.",
    ]
    md_path.write_text("\n".join(lines), encoding="utf-8")

    print(json_path)
    print(md_path)


if __name__ == "__main__":
    main()
