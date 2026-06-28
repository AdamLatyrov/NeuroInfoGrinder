#!/usr/bin/env python3
"""Analyze P0 pipeline coverage gap diagnostics.

Inputs:
- coverage JSONL exported from production
- previous usefulness audit CSV

Outputs:
- Markdown report
- JSON metrics
- CSV row-level coverage reason table
"""

from __future__ import annotations

import argparse
import csv
import json
import statistics
from collections import Counter, defaultdict
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Any


TOP_CHATS = [
    "ОМ: Полезное",
    "Vibecoder Chat [Public]",
    "Vibemode",
    "ОМ: Резюме",
    "Vibe GIG Мастерская",
    "Нейродвиж",
    "Vibe Dev",
    "founderStack / Общение, знакомства",
    "ОМ: Флудилка",
    "API SUPPORT | ModelHub",
]

DRILLDOWN_RAW_IDS = {2609, 2735, 2793, 2866}


@dataclass
class CoverageRow:
    raw_id: int
    chat: str
    message_date: str | None
    text_len: int
    audit_class: str
    usefulness: int
    system_result: str
    coverage_reason: str
    intake_status: str | None
    queue_status: str | None
    batch_status: str | None
    run_id: int | None
    run_status: str | None
    final_decision: str | None
    single_score: float | None
    material_count: int
    embedding_count: int
    preview: str


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8-sig") as handle:
        for line in handle:
            line = line.strip()
            if line:
                rows.append(json.loads(line))
    return rows


def load_audit(path: Path) -> dict[int, dict[str, Any]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return {int(row["raw_id"]): row for row in csv.DictReader(handle)}


def int_value(value: Any) -> int:
    if value in (None, ""):
        return 0
    return int(float(value))


def float_value(value: Any) -> float | None:
    if value in (None, ""):
        return None
    return float(value)


def bool_value(value: Any) -> bool:
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        return value.lower() == "true"
    return bool(value)


def processing_state(row: dict[str, Any]) -> str:
    active = bool_value(row.get("active_dialog"))
    explicit = bool_value(row.get("explicit_auto_enabled"))
    if active and explicit:
        return "ENABLED_PROCESSABLE"
    if active:
        return "DISPLAY_ONLY_AUTO_DISABLED"
    return "DISPLAY_ONLY_NOT_ACTIVE"


def coverage_reason(row: dict[str, Any]) -> str:
    text_len = int_value(row.get("text_len"))
    has_text = bool_value(row.get("has_text")) or text_len > 0
    active = bool_value(row.get("active_dialog"))
    explicit = bool_value(row.get("explicit_auto_enabled"))
    has_intake = bool_value(row.get("has_intake"))
    has_queue = bool_value(row.get("has_queue"))
    batch_id = row.get("batch_id")
    has_run = bool_value(row.get("has_run"))
    has_replay = bool_value(row.get("has_replay_message"))
    embedding_count = int_value(row.get("embedding_count"))
    material_count = int_value(row.get("material_count"))
    final_decision = row.get("final_decision")
    rejection = row.get("single_message_rejection_reason")
    intake_status = row.get("intake_status")
    queue_status = row.get("queue_status")
    trace_errors = row.get("trace_error_codes") or ""

    if material_count > 0:
        return "materialized"
    if not has_text:
        return "text_extraction_empty_or_no_text"
    if not active:
        return "blocked_by_active_dialog_guard"
    if not explicit:
        return "suppressed_by_scope_no_explicit_auto_setting"
    if not has_intake:
        return "no_intake_row"
    if intake_status == "SKIPPED":
        return "intake_skipped"
    if intake_status == "PENDING" and not has_queue:
        return "intake_pending_not_queued"
    if has_queue and not batch_id:
        return "queued_but_no_batch"
    if batch_id and not has_run:
        return "batch_but_no_run"
    if has_run and not has_replay:
        return "run_created_but_no_replay_messages"
    if has_replay and embedding_count == 0:
        return "replay_message_exists_but_no_embedding"
    if embedding_count > 0 and final_decision in (None, ""):
        return "embedding_exists_but_no_single_message_decision"
    if final_decision == "REJECTED_SINGLE_MESSAGE":
        return f"single_message_rejected_{rejection or 'unknown'}"
    if final_decision in {"SINGLE_MESSAGE_MATERIAL_CANDIDATE", "DIRECT_MATERIAL_READY"} and material_count == 0:
        return "candidate_without_material"
    if "NO_MATERIAL_CANDIDATES" in trace_errors:
        return "no_material_candidates"
    if queue_status:
        return f"queue_status_{queue_status.lower()}"
    return "unknown"


def table(rows: list[list[Any]], headers: list[str]) -> str:
    out = ["| " + " | ".join(headers) + " |", "|" + "|".join(["---" for _ in headers]) + "|"]
    for row in rows:
        safe = [str(cell if cell is not None else "").replace("|", "\\|").replace("\n", " ") for cell in row]
        out.append("| " + " | ".join(safe) + " |")
    return "\n".join(out)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--coverage", required=True, type=Path)
    parser.add_argument("--audit", required=True, type=Path)
    parser.add_argument("--out-dir", required=True, type=Path)
    parser.add_argument("--stamp", required=True)
    args = parser.parse_args()

    coverage_rows = load_jsonl(args.coverage)
    audit = load_audit(args.audit)
    args.out_dir.mkdir(parents=True, exist_ok=True)

    enriched: list[CoverageRow] = []
    for row in coverage_rows:
        raw_id = int(row["raw_id"])
        audit_row = audit.get(raw_id, {})
        enriched.append(
            CoverageRow(
                raw_id=raw_id,
                chat=row.get("chat_title") or "",
                message_date=row.get("message_date"),
                text_len=int_value(row.get("text_len")),
                audit_class=audit_row.get("audit_class", "UNKNOWN"),
                usefulness=int_value(audit_row.get("usefulness")),
                system_result=audit_row.get("system_result", "UNKNOWN"),
                coverage_reason=coverage_reason(row),
                intake_status=row.get("intake_status"),
                queue_status=row.get("queue_status"),
                batch_status=row.get("batch_status"),
                run_id=int(row["run_id"]) if row.get("run_id") is not None else None,
                run_status=row.get("run_status"),
                final_decision=row.get("final_decision"),
                single_score=float_value(row.get("single_message_score")),
                material_count=int_value(row.get("material_count")),
                embedding_count=int_value(row.get("embedding_count")),
                preview=row.get("text_preview") or "",
            )
        )

    useful = [r for r in enriched if r.audit_class != "NOT_USEFUL" and r.usefulness >= 34]
    intake_pending = [r for r in enriched if r.intake_status == "PENDING"]
    useful_pending = [r for r in useful if r.intake_status == "PENDING"]
    embedded_no_material = [r for r in enriched if r.embedding_count > 0 and r.material_count == 0]

    reason_counts = Counter(r.coverage_reason for r in enriched)
    pending_reason_counts = Counter(r.coverage_reason for r in intake_pending)
    useful_reason_counts = Counter(r.coverage_reason for r in useful)

    by_raw = {int(row["raw_id"]): row for row in coverage_rows}

    top_chat_rows: list[list[Any]] = []
    for chat in TOP_CHATS:
        rows = [r for r in coverage_rows if r.get("chat_title") == chat]
        if not rows:
            top_chat_rows.append([chat, "NOT_FOUND", "", "", "", "", "", "", "", "", ""])
            continue
        first = rows[0]
        enriched_chat = [r for r in enriched if r.chat == chat]
        top_chat_rows.append([
            chat,
            first.get("telegram_chat_id"),
            bool_value(first.get("active_dialog")),
            bool_value(first.get("explicit_auto_enabled")),
            processing_state(first),
            len(rows),
            sum(1 for r in rows if r.get("intake_status") == "PENDING"),
            sum(1 for r in rows if int_value(r.get("embedding_count")) > 0),
            sum(1 for r in rows if int_value(r.get("material_count")) > 0),
            max((r.get("message_date") or "" for r in rows), default=""),
            sum(1 for r in enriched_chat if r.audit_class != "NOT_USEFUL" and r.usefulness >= 34 and r.material_count == 0),
        ])

    drilldown_rows: list[list[Any]] = []
    for raw_id in sorted(DRILLDOWN_RAW_IDS):
        row = by_raw.get(raw_id)
        erow = next((r for r in enriched if r.raw_id == raw_id), None)
        if not row or not erow:
            drilldown_rows.append([raw_id, "NOT_FOUND", "", "", "", "", "", "", "", "", ""])
            continue
        material_count = int_value(row.get("material_count"))
        duplicate_materialized = any(
            other.raw_id != raw_id
            and other.material_count > 0
            and erow.preview[:80]
            and other.preview[:80] == erow.preview[:80]
            for other in enriched
        )
        if material_count > 0:
            status = "already_materialized"
        elif duplicate_materialized:
            status = "already_materialized_duplicate"
        elif erow.final_decision == "REJECTED_SINGLE_MESSAGE":
            status = "false_negative_due_scoring" if (erow.single_score or 0) >= 0.45 else "correctly_or_low_score_rejected"
        elif erow.embedding_count > 0 and not erow.final_decision:
            status = "false_negative_due_old_prefix_or_missing_decision"
        elif erow.coverage_reason in {"intake_pending_not_queued", "pipeline_gap"}:
            status = "missing_reprocess_or_queue"
        else:
            status = erow.coverage_reason
        drilldown_rows.append([
            raw_id,
            erow.chat,
            erow.audit_class,
            erow.usefulness,
            erow.coverage_reason,
            status,
            erow.run_id,
            erow.embedding_count,
            erow.final_decision,
            erow.single_score,
            row.get("single_message_rejection_reason"),
            int_value(row.get("provider_call_count")),
            erow.material_count,
            erow.preview,
        ])

    # Discussion/context notes grouped by chat/topic/thread proxy.
    context_candidates = [r for r in enriched if r.audit_class in {"DISCUSSION_SEGMENT", "UNKNOWN_NEEDS_CONTEXT"}]
    raw_lookup = {int(row["raw_id"]): row for row in coverage_rows}
    chains: dict[tuple[Any, Any, Any], list[CoverageRow]] = defaultdict(list)
    for row in context_candidates:
        raw = raw_lookup[row.raw_id]
        key = (row.chat, raw.get("telegram_topic_id"), raw.get("message_thread_id"))
        chains[key].append(row)
    chain_lengths = [len(v) for v in chains.values()]
    top_chains = sorted(chains.items(), key=lambda kv: len(kv[1]), reverse=True)[:10]

    row_csv = args.out_dir / f"p0-coverage-gap-row-reasons-{args.stamp}.csv"
    with row_csv.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(asdict(enriched[0]).keys()))
        writer.writeheader()
        for row in enriched:
            writer.writerow(asdict(row))

    metrics = {
        "total_enabled_raw": len(enriched),
        "audit_total_raw": len(enriched),
        "potentially_useful": len(useful),
        "guide_ready": sum(1 for r in enriched if r.audit_class == "GUIDE_READY"),
        "context_needed": len(context_candidates),
        "already_materialized": sum(1 for r in enriched if r.material_count > 0),
        "useful_not_materialized": sum(1 for r in useful if r.material_count == 0),
        "estimated_recall": (sum(1 for r in useful if r.material_count > 0) / len(useful)) if useful else 0,
        "estimated_precision": (sum(1 for r in enriched if r.material_count > 0 and r.audit_class != "NOT_USEFUL") / sum(1 for r in enriched if r.material_count > 0)) if any(r.material_count > 0 for r in enriched) else 0,
        "reason_counts": dict(reason_counts),
        "pending_reason_counts": dict(pending_reason_counts),
        "useful_reason_counts": dict(useful_reason_counts),
        "intake_pending_total": len(intake_pending),
        "useful_intake_pending": len(useful_pending),
        "embedded_no_material_total": len(embedded_no_material),
        "context_chain_count": len(chains),
        "context_chain_median_length": statistics.median(chain_lengths) if chain_lengths else 0,
        "context_chain_max_length": max(chain_lengths) if chain_lengths else 0,
    }

    json_path = args.out_dir / f"p0-coverage-gap-analysis-{args.stamp}.json"
    json_path.write_text(json.dumps(metrics, ensure_ascii=False, indent=2), encoding="utf-8")

    md_path = args.out_dir / f"p0-coverage-gap-analysis-{args.stamp}.md"
    md = f"""# P0 Pipeline Coverage Gap Analysis

Generated: {args.stamp}

Scope: production clean DB, enabled `telegram_chats.is_enabled=true` raw messages only. Read-only diagnostics. No requeue, no reprocess, no threshold changes, no material generation.

## Audit Baseline

| Metric | Value |
|---|---:|
| Enabled raw total | {metrics['total_enabled_raw']} |
| Potentially useful | {metrics['potentially_useful']} |
| Guide-ready | {metrics['guide_ready']} |
| Context/discussion-needed | {metrics['context_needed']} |
| Already materialized | {metrics['already_materialized']} |
| Useful not materialized | {metrics['useful_not_materialized']} |
| Estimated recall | {metrics['estimated_recall'] * 100:.1f}% |
| Estimated precision | {metrics['estimated_precision'] * 100:.1f}% |

## Overall Coverage Reasons

"""
    md += table([[k, v] for k, v in reason_counts.most_common()], ["reason", "count"])
    md += "\n\n## INTAKE_PENDING Breakdown\n\n"
    md += table([[k, v] for k, v in pending_reason_counts.most_common()], ["reason", "count"])
    md += "\n\n## Useful Candidate Breakdown\n\n"
    md += table([[k, v] for k, v in useful_reason_counts.most_common()], ["reason", "count"])

    examples: list[list[Any]] = []
    for reason, _ in useful_reason_counts.most_common():
        picks = [r for r in useful if r.coverage_reason == reason]
        for r in sorted(picks, key=lambda x: (x.usefulness, x.text_len), reverse=True)[:3]:
            examples.append([reason, r.raw_id, r.chat, r.audit_class, r.usefulness, r.preview[:160]])
    md += "\n\n## Reason Examples\n\n"
    md += table(examples, ["reason", "raw_id", "chat", "class", "score", "preview"])

    md += "\n\n## Top Chat Coverage\n\n"
    md += table(top_chat_rows, ["chat", "chat_id", "activeDialog", "explicitAuto", "processingState", "enabledRaw", "intakePending", "withEmbeddings", "withMaterials", "latestRaw", "missedUseful"])

    md += "\n\n## Required Raw-ID Drilldown\n\n"
    md += table(drilldown_rows, ["raw_id", "chat", "class", "score", "coverage_reason", "status", "run_id", "embeddings", "final_decision", "single_score", "rejection", "provider_calls", "materials", "preview"])

    md += "\n\n## Discussion Segment Data Notes\n\n"
    md += table([
        ["context_candidate_messages", len(context_candidates)],
        ["context_chain_count", len(chains)],
        ["median_chain_length", metrics["context_chain_median_length"]],
        ["max_chain_length", metrics["context_chain_max_length"]],
    ], ["metric", "value"])
    md += "\n\n## Top Context Chains\n\n"
    md += table([[key[0], key[1], key[2], len(rows), rows[0].raw_id, rows[0].preview[:160]] for key, rows in top_chains], ["chat", "topic", "thread", "candidate_count", "example_raw", "preview"])

    md += f"""

## Root-Cause Interpretation

- `intake_pending_not_queued` means raw and intake exist, but no `auto_pipeline_queue` row exists. This is a coverage gap before batch/run creation, not a scoring problem.
- `suppressed_by_scope_no_explicit_auto_setting` means the chat is display-enabled but not processable under the current P0 guard because explicit auto scope is absent.
- `single_message_rejected_LOW_SINGLE_MESSAGE_SCORE` means the message reached replay, embeddings, and single-message detection, but failed candidate threshold.
- `embedding_exists_but_no_single_message_decision` points to old/pre-fix or incomplete replay evidence where embeddings exist but no single-message decision was recorded.
- `candidate_without_material` means local candidate exists but provider/material stage did not complete.

## Output Files

- Row-level reason CSV: `{row_csv.name}`
- Metrics JSON: `{json_path.name}`
"""
    md_path.write_text(md, encoding="utf-8")
    print(str(md_path))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
