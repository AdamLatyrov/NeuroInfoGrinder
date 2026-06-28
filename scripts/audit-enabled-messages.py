#!/usr/bin/env python3
"""Read-only message usefulness audit for NeuroInfoGrinder exports.

Input: JSONL rows exported from production with raw message and pipeline evidence.
Output: Markdown summary, CSV candidates, JSON metrics.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import re
from collections import Counter, defaultdict
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Any


TECH_TERMS = re.compile(
    r"\b(api|sdk|cli|http|json|jwt|oauth|oauth2|pkce|webhook|cursor|claude|gpt|llm|model|embedding|"
    r"postgres|postgresql|sql|redis|docker|compose|kubernetes|k8s|nginx|linux|windows|powershell|bash|"
    r"typescript|javascript|python|java|spring|react|svelte|vue|angular|tailwind|shadcn|tdlib|telegram|"
    r"openai|anthropic|gemini|qwen|deepseek|mcp|agent|prompt|workflow|pipeline|database|cache|queue|"
    r"server|backend|frontend|deploy|ci|cd|github|git|test|pytest|vitest|playwright|xunit)\b",
    re.IGNORECASE,
)

RU_TECH_TERMS = re.compile(
    r"(апи|модель|модели|промпт|промты|агент|агенты|воркфлоу|пайплайн|ошибк|деплой|сервер|"
    r"бэкенд|фронтенд|база|бд|кэш|очеред|тест|провер|интеграц|автоматизац|нейрон|телеграм|"
    r"инструкц|гайд|чеклист|шаблон|конфиг|настройк|воркера|контейнер)",
    re.IGNORECASE,
)

GUIDE_TERMS = re.compile(
    r"(мини[- ]?гайд|гайд|инструкция|чеклист|как сделать|как проверить|как настроить|как запустить|"
    r"пошагово|шаг \d|\d+\.\s|во-первых|сначала|затем|дальше|итого|важно:|"
    r"how to|guide|checklist|steps?|first,|then|finally|workflow)",
    re.IGNORECASE,
)

TROUBLE_TERMS = re.compile(
    r"(ошибка|баг|фикс|почин|проблем|не работает|падает|упал|сломал|workaround|"
    r"exception|stack trace|traceback|error|failed|failure|timeout|refused|forbidden|unauthorized|"
    r"nullpointer|badsql|fatal|crash|fix|bug|issue|root cause)",
    re.IGNORECASE,
)

PROMPT_TERMS = re.compile(
    r"(prompt|промпт|system prompt|шаблон|template|роль|инструкция для|скопируй|"
    r"ты .*ассистент|you are|act as|задача:|контекст:|формат ответа)",
    re.IGNORECASE | re.DOTALL,
)

RELEASE_TERMS = re.compile(
    r"(релиз|release|новая версия|анонс|запустил|вышел|доступен|pricing|цена|тариф|"
    r"библиотек|инструмент|tool|model released|добавили|обновили)",
    re.IGNORECASE,
)

QUESTION_ANSWER_TERMS = re.compile(
    r"(потому что|дело в том|ответ|решение|лучше|стоит|нужно|надо|можно|"
    r"because|the reason|solution|you should|you can|best practice)",
    re.IGNORECASE,
)

NOISE_SHORT = re.compile(
    r"^(ок|окей|да|нет|ага|угу|спасибо|пасиб|лол|хаха|😂|👍|\+|\+1|done|yes|no|thanks|thank you|понял|принял)[.!?\s]*$",
    re.IGNORECASE,
)

URL_RE = re.compile(r"https?://\S+", re.IGNORECASE)
CODE_RE = re.compile(r"(```|\bSELECT\b|\bINSERT\b|\bUPDATE\b|\bcurl\b|npm |pnpm |yarn |docker |git |ssh |psql |\{\s*\"|=>|function\s|class\s)", re.IGNORECASE)


@dataclass
class AuditRow:
    raw_id: int
    chat: str
    telegram_chat_id: int | None
    message_date: str | None
    audit_class: str
    usefulness: int
    system_result: str
    miss_reason: str
    text_len: int
    preview: str
    intake_status: str | None
    rule_decision: str | None
    classifier_label: str | None
    material_count: int
    embedding_count: int
    replay_count: int
    reasons: str


def text_of(row: dict[str, Any]) -> str:
    return (row.get("text") or row.get("caption") or "").strip()


def preview(text: str, limit: int = 180) -> str:
    compact = re.sub(r"\s+", " ", text).strip()
    return compact if len(compact) <= limit else compact[: limit - 1].rstrip() + "..."


def labels_to_text(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, str):
        return value
    try:
        return json.dumps(value, ensure_ascii=False)
    except TypeError:
        return str(value)


def classify(row: dict[str, Any]) -> tuple[str, int, list[str]]:
    text = text_of(row)
    lower = text.lower()
    length = len(text)
    words = len(re.findall(r"[\wА-Яа-яЁё]+", text))
    links = int(row.get("links_count") or 0)
    has_media = bool(row.get("has_media"))
    reasons: list[str] = []
    score = 0

    if not text:
        return "NOT_USEFUL", 0, ["no_text"]
    if NOISE_SHORT.match(text) or (length < 12 and links == 0):
        return "NOT_USEFUL", 0, ["short_reaction"]

    if length >= 120:
        score += 18
        reasons.append("long_enough")
    if length >= 350:
        score += 12
        reasons.append("substantial_text")
    if words >= 45:
        score += 10
        reasons.append("many_words")
    if GUIDE_TERMS.search(text):
        score += 32
        reasons.append("guide_language")
    if TROUBLE_TERMS.search(text):
        score += 25
        reasons.append("troubleshooting_signal")
    if PROMPT_TERMS.search(text):
        score += 28
        reasons.append("prompt_template_signal")
    if RELEASE_TERMS.search(text):
        score += 16
        reasons.append("tool_release_signal")
    if TECH_TERMS.search(text) or RU_TECH_TERMS.search(text):
        score += 14
        reasons.append("technical_or_work_signal")
    if CODE_RE.search(text):
        score += 16
        reasons.append("code_or_command")
    if links > 0:
        score += 8
        reasons.append("has_links")
    if QUESTION_ANSWER_TERMS.search(text) and length >= 80:
        score += 10
        reasons.append("explanation_language")
    if has_media and length >= 40:
        score += 4
        reasons.append("captioned_media")

    # Penalties for obvious conversational fragments.
    if length < 60 and links == 0 and not (TROUBLE_TERMS.search(text) or TECH_TERMS.search(text) or RU_TECH_TERMS.search(text)):
        score -= 18
        reasons.append("short_context_dependent")
    if text.count("?") >= 1 and length < 140 and not QUESTION_ANSWER_TERMS.search(text):
        score -= 8
        reasons.append("question_needs_answer")
    if links > 0 and length < 80:
        score -= 6
        reasons.append("link_low_context")

    score = max(0, min(100, score))

    if score >= 70 and GUIDE_TERMS.search(text):
        klass = "GUIDE_READY"
    elif score >= 64 and TROUBLE_TERMS.search(text):
        klass = "TROUBLESHOOTING"
    elif score >= 62 and PROMPT_TERMS.search(text):
        klass = "PROMPT_OR_TEMPLATE"
    elif score >= 58 and RELEASE_TERMS.search(text):
        klass = "TOOL_OR_RELEASE"
    elif score >= 58 and QUESTION_ANSWER_TERMS.search(text):
        klass = "ANSWER_OR_EXPLANATION"
    elif score >= 50:
        klass = "USEFUL_SIGNAL"
    elif score >= 34 and (row.get("reply_to_message_id") or row.get("message_thread_id")):
        klass = "DISCUSSION_SEGMENT"
    elif score >= 30:
        klass = "UNKNOWN_NEEDS_CONTEXT"
    else:
        klass = "NOT_USEFUL"

    return klass, score, reasons


def system_result(row: dict[str, Any]) -> str:
    material_count = int(row.get("material_count") or 0)
    if material_count > 0:
        return "MATERIALIZED"
    if int(row.get("candidate_count") or 0) > 0:
        return "CANDIDATE_ONLY"
    if int(row.get("microcluster_count") or 0) > 0 or int(row.get("macrocluster_count") or 0) > 0:
        return "CLUSTERED_NO_MATERIAL"
    if int(row.get("embedding_count") or 0) > 0:
        return "EMBEDDED_NO_MATERIAL"
    if int(row.get("replay_count") or 0) > 0:
        return "REPLAYED_NO_EMBEDDING"
    if row.get("intake_status"):
        return f"INTAKE_{row.get('intake_status')}"
    return "RAW_ONLY"


def miss_reason(row: dict[str, Any], klass: str, score: int, result: str, reasons: list[str]) -> str:
    if klass == "NOT_USEFUL":
        return "GOOD_REJECTION" if result != "MATERIALIZED" else "POSSIBLE_FALSE_POSITIVE"
    if result == "MATERIALIZED":
        return "ALREADY_MATERIALIZED"
    if "link_low_context" in reasons and int(row.get("links_count") or 0) > 0:
        return "LINK_CONTEXT_LOSS"
    if row.get("has_media") and len(text_of(row)) < 80:
        return "MEDIA_CONTEXT_LOSS"
    if klass in {"DISCUSSION_SEGMENT", "UNKNOWN_NEEDS_CONTEXT"}:
        return "MISSED_DISCUSSION_CONTEXT"
    if result in {"EMBEDDED_NO_MATERIAL", "CLUSTERED_NO_MATERIAL", "REPLAYED_NO_EMBEDDING"}:
        return "LOW_SCORE_OR_SINGLE_MESSAGE_FALSE_NEGATIVE"
    if str(row.get("intake_status") or "") in {"SKIPPED", "FAILED"}:
        return "SUPPRESSED_USEFUL"
    if result.startswith("INTAKE_") or result == "RAW_ONLY":
        return "PIPELINE_GAP"
    return "MISSED_SINGLE_MESSAGE"


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    rows = []
    with path.open("r", encoding="utf-8-sig") as handle:
        for line in handle:
            line = line.strip()
            if line:
                rows.append(json.loads(line))
    return rows


def write_csv(path: Path, rows: list[AuditRow]) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(asdict(rows[0]).keys()) if rows else [])
        if rows:
            writer.writeheader()
            for row in rows:
                writer.writerow(asdict(row))


def pct(part: int, total: int) -> str:
    if total == 0:
        return "0.0%"
    return f"{part / total * 100:.1f}%"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--out-dir", required=True, type=Path)
    parser.add_argument("--stamp", required=True)
    args = parser.parse_args()

    rows = load_jsonl(args.input)
    args.out_dir.mkdir(parents=True, exist_ok=True)

    audited: list[AuditRow] = []
    for row in rows:
        klass, score, reasons = classify(row)
        result = system_result(row)
        audited.append(
            AuditRow(
                raw_id=int(row["raw_id"]),
                chat=str(row.get("chat_title") or ""),
                telegram_chat_id=row.get("telegram_chat_id"),
                message_date=row.get("message_date"),
                audit_class=klass,
                usefulness=score,
                system_result=result,
                miss_reason=miss_reason(row, klass, score, result, reasons),
                text_len=len(text_of(row)),
                preview=preview(text_of(row)),
                intake_status=row.get("intake_status"),
                rule_decision=row.get("rule_decision"),
                classifier_label=row.get("classifier_label"),
                material_count=int(row.get("material_count") or 0),
                embedding_count=int(row.get("embedding_count") or 0),
                replay_count=int(row.get("replay_count") or 0),
                reasons=", ".join(reasons),
            )
        )

    total = len(audited)
    meaningful = [r for r in audited if r.text_len >= 12 and r.audit_class != "NOT_USEFUL"]
    useful = [r for r in audited if r.audit_class != "NOT_USEFUL" and r.usefulness >= 34]
    guide_ready = [r for r in audited if r.audit_class == "GUIDE_READY"]
    discussion = [r for r in audited if r.audit_class in {"DISCUSSION_SEGMENT", "UNKNOWN_NEEDS_CONTEXT"}]
    materialized = [r for r in audited if r.system_result == "MATERIALIZED"]
    missed = [r for r in useful if r.system_result != "MATERIALIZED"]
    false_positive = [r for r in audited if r.audit_class == "NOT_USEFUL" and r.system_result == "MATERIALIZED"]

    class_counts = Counter(r.audit_class for r in audited)
    result_counts = Counter(r.system_result for r in audited)
    miss_counts = Counter(r.miss_reason for r in audited)
    chat_counts = Counter(r.chat for r in audited)
    missed_by_chat = Counter(r.chat for r in missed)

    top_missed = sorted(missed, key=lambda r: (r.usefulness, r.text_len), reverse=True)[:80]
    top_materialized = sorted(materialized, key=lambda r: (r.usefulness, r.text_len), reverse=True)[:40]
    top_false_positive = sorted(false_positive, key=lambda r: (r.text_len, r.raw_id), reverse=True)[:30]

    csv_path = args.out_dir / f"enabled-message-usefulness-audit-{args.stamp}.csv"
    json_path = args.out_dir / f"enabled-message-usefulness-audit-{args.stamp}.json"
    md_path = args.out_dir / f"enabled-message-usefulness-audit-{args.stamp}.md"
    write_csv(csv_path, sorted(audited, key=lambda r: (r.usefulness, r.raw_id), reverse=True))

    metrics = {
        "total_raw_messages": total,
        "meaningful_potentially_useful": len(meaningful),
        "potentially_useful": len(useful),
        "guide_ready": len(guide_ready),
        "discussion_or_context_candidates": len(discussion),
        "already_materialized": len(materialized),
        "missed_useful": len(missed),
        "possible_false_positive_materials": len(false_positive),
        "estimated_recall_on_useful": (len([r for r in useful if r.system_result == "MATERIALIZED"]) / len(useful)) if useful else 0,
        "estimated_material_precision": (len([r for r in materialized if r.audit_class != "NOT_USEFUL"]) / len(materialized)) if materialized else 0,
        "class_counts": dict(class_counts),
        "system_result_counts": dict(result_counts),
        "miss_reason_counts": dict(miss_counts),
        "chat_counts": dict(chat_counts),
        "missed_by_chat": dict(missed_by_chat),
        "top_missed_raw_ids": [r.raw_id for r in top_missed[:20]],
    }
    json_path.write_text(json.dumps(metrics, ensure_ascii=False, indent=2), encoding="utf-8")

    def table(rows_for_table: list[AuditRow], limit: int) -> str:
        lines = ["| raw_id | chat | class | score | system | miss reason | preview |", "|---:|---|---|---:|---|---|---|"]
        for r in rows_for_table[:limit]:
            safe_preview = r.preview.replace("|", "\\|")
            lines.append(f"| {r.raw_id} | {r.chat} | {r.audit_class} | {r.usefulness} | {r.system_result} | {r.miss_reason} | {safe_preview} |")
        return "\n".join(lines)

    md = f"""# Enabled Message Usefulness Audit

Generated: {args.stamp}

Scope: enabled `telegram_chats.is_enabled = true` only. This is a read-only audit of raw messages and existing pipeline/material evidence. It does not requeue, reprocess, generate materials, or change thresholds.

## Executive Summary

| Metric | Value |
|---|---:|
| Total enabled raw messages | {total} |
| Potentially useful messages | {len(useful)} ({pct(len(useful), total)}) |
| Guide-ready messages | {len(guide_ready)} ({pct(len(guide_ready), total)}) |
| Discussion/context candidates | {len(discussion)} ({pct(len(discussion), total)}) |
| Already materialized messages | {len(materialized)} ({pct(len(materialized), total)}) |
| Missed useful messages | {len(missed)} ({pct(len(missed), len(useful))} of useful) |
| Possible false-positive materials | {len(false_positive)} |
| Estimated recall on useful | {metrics['estimated_recall_on_useful'] * 100:.1f}% |
| Estimated material precision | {metrics['estimated_material_precision'] * 100:.1f}% |

## Audit Class Counts

| Class | Count |
|---|---:|
"""
    for key, count in class_counts.most_common():
        md += f"| {key} | {count} |\n"

    md += "\n## System Result Counts\n\n| Result | Count |\n|---|---:|\n"
    for key, count in result_counts.most_common():
        md += f"| {key} | {count} |\n"

    md += "\n## Miss Reason Counts\n\n| Reason | Count |\n|---|---:|\n"
    for key, count in miss_counts.most_common():
        md += f"| {key} | {count} |\n"

    md += "\n## Missed Useful By Chat\n\n| Chat | Missed useful |\n|---|---:|\n"
    for key, count in missed_by_chat.most_common():
        md += f"| {key} | {count} |\n"

    md += "\n## Top Missed Useful Candidates\n\n"
    md += table(top_missed, 40)

    md += "\n\n## Top Already Materialized Candidates\n\n"
    md += table(top_materialized, 25)

    if top_false_positive:
        md += "\n\n## Possible False Positive Materials\n\n"
        md += table(top_false_positive, 20)

    md += f"""

## Interpretation Notes

- `MATERIALIZED` means the raw message can be linked to an existing `knowledge_item` through `knowledge_item_sources` and `dataset_messages` evidence.
- `EMBEDDED_NO_MATERIAL` usually means the message reached semantic processing but did not become a material. For single strong messages this is a likely false negative in scoring/single-message handling.
- `PIPELINE_GAP` means the raw message looks useful in this independent audit but did not reach material-producing evidence.
- `MISSED_DISCUSSION_CONTEXT` means the message is likely only useful with neighbor/reply/thread context.
- Scores are heuristic and meant for triage, not final human labeling. Use the CSV for row-level review.

## Output Files

- CSV: `{csv_path.name}`
- Metrics JSON: `{json_path.name}`
"""
    md_path.write_text(md, encoding="utf-8")
    print(str(md_path))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
