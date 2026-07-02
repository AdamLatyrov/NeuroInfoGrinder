#!/usr/bin/env python3
"""Offline Material Selection Lab for NeuroInfoGrinder 2.0.

Reads a fixed local snapshot and writes auditable lab artifacts only under
reports/material-selection-lab and .docs/research/pipeline/material-quality/lab.
It never calls production APIs, databases, providers, model workers, deploys, or
generation endpoints.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import random
import re
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Iterable

from message_lab_core import (
    DOMAIN_RE,
    URL_RE,
    classify_text,
    cluster_messages,
    load_messages,
    normalize_text as core_normalize_text,
    text_preview,
    tokenize,
)


PROJECT_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_INPUT = PROJECT_ROOT / "reports" / "raw-messages-20k-20260630.jsonl"
DEFAULT_OUT = PROJECT_ROOT / "reports" / "material-selection-lab"
DEFAULT_DOCS = PROJECT_ROOT / ".docs" / "research" / "pipeline" / "material-quality" / "lab"

RAW_PATH = Path("data/raw_messages.jsonl")
NORMALIZED_PATH = Path("data/messages_normalized.jsonl")
ROUTED_PATH = Path("out/routes/messages_routed.csv")
CONTEXT_WINDOWS_PATH = Path("out/context/context_windows.jsonl")
CONTEXT_ATTACHED_PATH = Path("out/context/context_attached_messages.csv")
EVIDENCE_GROUPS_PATH = Path("out/evidence/evidence_groups.jsonl")
GOLD_TEMPLATE_PATH = Path("out/gold/gold_labels_template.csv")

USERNAME_RE = re.compile(r"@[a-zA-Z0-9_]{4,}")
ERROR_CODE_RE = re.compile(r"\b(?:HTTP\s*)?(?:400|401|403|404|408|409|422|429|500|502|503|504)\b|\b[A-Z][A-Z0-9_]{3,}\b")
PRICE_RE = re.compile(r"(?:[$€₽]\s?\d+(?:[.,]\d+)?|\b\d+(?:[.,]\d+)?\s?(?:usd|eur|руб|р\.?|₽|токен|tokens?)\b)", re.IGNORECASE)
MODEL_RE = re.compile(r"\b(?:gpt[-\s]?(?:4|4o|5|5\.5)|claude|sonnet|opus|haiku|gemini|qwen|deepseek|llama|mistral|bge[-\s]?m3|o3|o4)\b", re.IGNORECASE)
API_TERMS_RE = re.compile(r"\b(?:api|endpoint|sdk|json|oauth|token|rate\s*limit|docker|postgres|redis|http|webhook|openai-compatible)\b", re.IGNORECASE)
CODE_RE = re.compile(r"(```|\b(?:class|interface|def|function|select|insert|update|curl|npm|pip|docker|kubectl|public|private|async|await)\b)", re.IGNORECASE)
PROFANITY_RE = re.compile(r"\b(?:хуй|хуя|пизд|еба|ёба|бля|сука|shit|fuck)\w*\b", re.IGNORECASE)
FILLER_RE = re.compile(r"\b(?:лол|кек|ах+|хах+|ну|типа|короче|кнч|имхо|ок|спс|ага|угу|да|нет)\b", re.IGNORECASE)
EMOJI_RE = re.compile("[\U0001F300-\U0001FAFF\u2600-\u27BF]")
BOT_RE = re.compile(r"\b(bot|бот|chatkeeper|lolsbot)\b", re.IGNORECASE)
SHORT_ACK_RE = re.compile(r"^(?:да|нет|тоже|у меня тоже|работает|не работает|ок|ага|угу|спасибо|жду|печально|мощни|лол|кек|\+1|\+)$", re.IGNORECASE)
OFFICIAL_DOMAIN_RE = re.compile(r"(?:openai\.com|anthropic\.com|google\.com|ai\.google|mistral\.ai|github\.com|docs\.|documentation)", re.IGNORECASE)

FIRST_PASS_ROUTE_MAP = {
    "HARD_NO_MATERIAL": "REJECT_SAFE",
    "NO_MATERIAL": "CONTEXT_ONLY",
    "RETAIN_CONTEXT_ONLY": "CONTEXT_ONLY",
    "NEEDS_LINK_ENRICHMENT": "NEEDS_ENRICHMENT",
    "SIGNAL_ONLY_RISK_REVIEW": "MANUAL_REVIEW",
    "MANUAL_REVIEW_ONLY": "MANUAL_REVIEW",
    "REVIEW_SIGNAL_OR_DISCUSSION": "REVIEW_HIGH_RECALL",
}

MARKER_ORDER = [
    "short_chatter_marker",
    "rules_onboarding_marker",
    "link_only_marker",
    "job_event_marker",
    "risk_referral_marker",
    "model_pricing_marker",
    "api_troubleshooting_marker",
    "tool_resource_marker",
    "outage_status_marker",
    "workflow_howto_marker",
    "unknown_potential_signal_marker",
]

ROUTE_FIELDS = [
    "lab_message_id", "snapshot_id", "raw_id", "dataset_message_id", "telegram_chat_id", "chat_id", "chat_title",
    "topic_id", "thread_id", "topic_title", "reply_to", "timestamp", "primary_class", "classifier_route", "route",
    "route_reason", "risk", "material_type_hint", "confidence", "marker_id", "domains", "model_names", "error_codes",
    "length_tokens", "has_link", "has_api_terms", "has_code", "preview",
]


@dataclass(frozen=True)
class LabPaths:
    out: Path
    docs: Path

    def p(self, relative: Path | str) -> Path:
        return self.out / relative


def safe_write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def write_jsonl(path: Path, rows: Iterable[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    with path.open("r", encoding="utf-8") as handle:
        return [json.loads(line) for line in handle if line.strip()]


def write_csv(path: Path, rows: Iterable[dict[str, Any]], fieldnames: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def stable_hash(value: str, length: int = 12) -> str:
    return hashlib.sha1((value or "").encode("utf-8")).hexdigest()[:length]


def parse_time(value: Any) -> datetime | None:
    if not value:
        return None
    text = str(value).strip()
    try:
        if text.endswith("Z"):
            text = text[:-1] + "+00:00"
        parsed = datetime.fromisoformat(text)
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=timezone.utc)
        return parsed.astimezone(timezone.utc)
    except ValueError:
        return None


def stable_sample(rows: list[dict[str, Any]], size: int) -> list[dict[str, Any]]:
    if len(rows) <= size:
        return rows
    rng = random.Random(20260701)
    return sorted(rng.sample(rows, size), key=lambda row: row.get("lab_message_id", ""))


def extract_source_id(row: dict[str, Any], fallback_index: int) -> str:
    for key in ("id", "raw_id", "rawId", "dataset_message_id", "datasetMessageId", "messageId"):
        value = row.get(key)
        if value not in (None, ""):
            return str(value)
    text = str(row.get("text") or row.get("message") or "")
    return f"synthetic-{fallback_index}-{stable_hash(text)}"


def extract_raw_text(row: dict[str, Any]) -> str:
    for key in ("text", "message", "preview", "caption", "body", "content"):
        value = row.get(key)
        if value not in (None, ""):
            return str(value)
    return ""


def prepare_row(row: dict[str, Any], index: int, snapshot_id: str) -> dict[str, Any]:
    raw_id = extract_source_id(row, index)
    dataset_message_id = row.get("dataset_message_id") or row.get("datasetMessageId") or raw_id
    return {
        "lab_message_id": f"msg-{index:05d}",
        "snapshot_id": snapshot_id,
        "raw_id": raw_id,
        "dataset_message_id": str(dataset_message_id) if dataset_message_id not in (None, "") else raw_id,
        "telegram_chat_id": row.get("telegramChatId") or row.get("telegram_chat_id") or row.get("chatId") or row.get("chat_id") or "",
        "chat_id": row.get("telegramChatId") or row.get("telegram_chat_id") or row.get("chatId") or row.get("chat_id") or "",
        "chat_title": row.get("chatTitle") or row.get("chat") or row.get("sourceChat") or "",
        "topic_id": row.get("telegramTopicId") or row.get("messageThreadId") or row.get("topicId") or row.get("thread_id") or "",
        "thread_id": row.get("messageThreadId") or row.get("telegramTopicId") or row.get("thread_id") or "",
        "topic_title": row.get("topicTitle") or row.get("topic") or row.get("sourceTopic") or "",
        "reply_to": row.get("replyTo") or row.get("reply_to") or row.get("replyToMessageId") or "",
        "timestamp": row.get("createdAt") or row.get("created_at") or row.get("messageDate") or row.get("date") or row.get("timestamp") or "",
        "sender_id": row.get("senderId") or row.get("sender_id") or "",
        "sender_name": row.get("senderName") or row.get("sender") or row.get("author") or "",
        "content_type": row.get("contentType") or row.get("content_type") or "",
        "raw_text": extract_raw_text(row),
        "raw_json": row,
    }


def display_text(raw_text: str) -> tuple[str, list[str]]:
    changes: list[str] = []
    text = raw_text or ""
    cleaned = text.replace("\u00a0", " ").replace("\r\n", "\n").replace("\r", "\n")
    if cleaned != text:
        changes.append("normalized_whitespace_chars")
    collapsed = re.sub(r"[ \t]+", " ", cleaned).strip()
    if collapsed != cleaned:
        changes.append("collapsed_inline_spaces")
    return collapsed, changes


def normalized_text(raw_text: str) -> tuple[str, list[str]]:
    text, changes = display_text(raw_text)
    lowered = text.lower()
    if lowered != text:
        changes.append("lowercased_for_rules")
    collapsed = re.sub(r"\s+", " ", lowered).strip()
    if collapsed != lowered:
        changes.append("collapsed_all_whitespace_for_matching")
    return collapsed, changes


def feature_text(raw_text: str) -> tuple[str, list[str]]:
    text, changes = normalized_text(raw_text)
    without_urls = URL_RE.sub(" URL ", text)
    if without_urls != text:
        changes.append("url_replaced_with_token_for_semantic_grouping")
    without_usernames = USERNAME_RE.sub(" USERNAME ", without_urls)
    if without_usernames != without_urls:
        changes.append("username_replaced_with_token_for_dedupe")
    compact = re.sub(r"[^a-zа-яё0-9_\-./:]+", " ", without_usernames, flags=re.IGNORECASE)
    compact = re.sub(r"\s+", " ", compact).strip()
    if compact != without_usernames:
        changes.append("punctuation_noise_downweighted")
    return compact, changes


def density(matches: list[str], token_count: int) -> float:
    if token_count <= 0:
        return 0.0
    return round(len(matches) / token_count, 4)


def extract_entities(text: str) -> dict[str, Any]:
    urls = URL_RE.findall(text or "")
    domains = [match.group(1).lower().rstrip(".,)") for match in DOMAIN_RE.finditer(text or "")]
    usernames = USERNAME_RE.findall(text or "")
    model_names = sorted(set(match.group(0).lower() for match in MODEL_RE.finditer(text or "")))
    error_codes = sorted(set(match.group(0).upper() for match in ERROR_CODE_RE.finditer(text or "")))
    return {
        "urls": urls,
        "domains": sorted(set(domains)),
        "usernames": sorted(set(usernames)),
        "model_names": model_names,
        "error_codes": error_codes,
    }


def compute_features(row: dict[str, Any]) -> dict[str, Any]:
    raw_text = row.get("raw_text") or ""
    normalized, _ = normalized_text(raw_text)
    tokens = tokenize(normalized)
    entities = extract_entities(raw_text)
    profanity_matches = PROFANITY_RE.findall(normalized)
    filler_matches = FILLER_RE.findall(normalized)
    emoji_matches = EMOJI_RE.findall(raw_text)
    punctuation_chars = len(re.findall(r"[!?.,:;\-_=+*#]{2,}", raw_text or ""))
    sender = " ".join(str(row.get(key) or "") for key in ("sender_name", "chat_title", "content_type"))
    return {
        "length_chars": len(raw_text),
        "length_tokens": len(tokens),
        "has_link": bool(entities["urls"]),
        "domains": entities["domains"],
        "usernames": entities["usernames"],
        "has_code": bool(CODE_RE.search(raw_text or "")),
        "has_error_code": bool(entities["error_codes"]),
        "error_codes": entities["error_codes"],
        "has_price": bool(PRICE_RE.search(raw_text or "")),
        "has_model_name": bool(entities["model_names"]),
        "model_names": entities["model_names"],
        "has_api_terms": bool(API_TERMS_RE.search(raw_text or "")),
        "has_profanity": bool(profanity_matches),
        "profanity_density": density(profanity_matches, len(tokens)),
        "filler_density": density(filler_matches, len(tokens)),
        "emoji_density": round(len(emoji_matches) / max(1, len(raw_text)), 4),
        "punctuation_noise": round(punctuation_chars / max(1, len(raw_text)), 4),
        "sender_is_bot": bool(BOT_RE.search(sender)),
        "chat_id": row.get("chat_id") or "",
        "topic_id": row.get("topic_id") or "",
        "thread_id": row.get("thread_id") or "",
        "reply_to": row.get("reply_to") or "",
        "timestamp": row.get("timestamp") or "",
        "entity_tokens": sorted(set(entities["domains"] + entities["model_names"] + entities["error_codes"] + entities["usernames"])),
    }


def normalize_one(row: dict[str, Any]) -> dict[str, Any]:
    raw_text = row.get("raw_text") or ""
    disp, display_changes = display_text(raw_text)
    norm, norm_changes = normalized_text(raw_text)
    feat_text, feat_changes = feature_text(raw_text)
    features = compute_features(row)
    changes = sorted(set(display_changes + norm_changes + feat_changes))
    why: list[str] = []
    if changes:
        why.append("derived_representations_support_review_rules_search_and_grouping_without_mutating_raw_text")
    if features["has_link"]:
        why.append("links_require_enrichment_or_source_review_before_materialization")
    if features["has_model_name"] or features["has_price"]:
        why.append("model_or_pricing_claims_need_official_or_corroborating_sources")
    if features["has_error_code"] or features["has_api_terms"]:
        why.append("technical_entities_help_context_and_evidence_grouping")
    if features["has_profanity"] or features["filler_density"] > 0.2:
        why.append("noise_features_are_downweighted_in_feature_text_not_removed_from_raw_evidence")
    return {
        **row,
        "display_text": disp,
        "normalized_text": norm,
        "feature_text": feat_text,
        "normalization_changes": changes,
        "normalization_reason": why or ["raw_text_preserved_no_material_change_needed"],
        "features": features,
    }


def marker_for(row: dict[str, Any], primary_class: str, route: str) -> str:
    features = row.get("features") or compute_features(row)
    text = row.get("normalized_text") or core_normalize_text(row.get("raw_text") or "")
    if primary_class in {"RULES_ONBOARDING", "MODERATION_BOT_EVENT"}:
        return "rules_onboarding_marker"
    if route == "NEEDS_ENRICHMENT" or (features.get("has_link") and features.get("length_tokens", 0) <= 8):
        return "link_only_marker"
    if route == "MANUAL_REVIEW" or primary_class in {"RISK_PROMO_REFERRAL", "REFERRAL_OR_INVITE_LINK", "ABUSE_OR_CIRCUMVENTION"}:
        return "risk_referral_marker"
    if primary_class in {"CAREER_JOB_POST", "EVENT_ANNOUNCEMENT"}:
        return "job_event_marker"
    if primary_class == "MODEL_RUMOR_OR_PRICING_CLAIM" or features.get("has_model_name") or features.get("has_price"):
        return "model_pricing_marker"
    if "как " in text or "инструк" in text or "чеклист" in text:
        return "workflow_howto_marker"
    if primary_class == "OUTAGE_STATUS" or features.get("has_error_code"):
        return "outage_status_marker"
    if features.get("has_api_terms") or features.get("has_code"):
        return "api_troubleshooting_marker"
    if primary_class in {"RESOURCE_LINK", "TECH_SIGNAL", "DIGEST_NEWS"} or features.get("domains"):
        return "tool_resource_marker"
    if features.get("length_tokens", 0) <= 3 or SHORT_ACK_RE.match(text):
        return "short_chatter_marker"
    return "unknown_potential_signal_marker" if route in {"REVIEW_HIGH_RECALL", "SIGNAL_ONLY"} else "short_chatter_marker"


def route_one(row: dict[str, Any]) -> dict[str, Any]:
    text = row.get("raw_text") or ""
    classification = classify_text(text, chat=str(row.get("chat_title") or ""), topic=str(row.get("topic_title") or ""))
    primary = classification.primary_class
    lab_route = FIRST_PASS_ROUTE_MAP.get(classification.material_route, "CONTEXT_ONLY")
    features = row.get("features") or compute_features(row)
    reasons = list(classification.reasons)
    claim_requires_source = features.get("has_model_name") or features.get("has_price") or primary == "MODEL_RUMOR_OR_PRICING_CLAIM"
    official_source = any(OFFICIAL_DOMAIN_RE.search(domain) for domain in features.get("domains", []) or [])

    if primary in {"RULES_ONBOARDING", "MODERATION_BOT_EVENT"}:
        lab_route = "REJECT_SAFE"
        reasons.append("hard_rule_never_material_rules_onboarding_or_moderation")
    elif primary in {"CAREER_JOB_POST", "EVENT_ANNOUNCEMENT"}:
        lab_route = "AGGREGATE_ONLY"
        reasons.append("job_or_event_single_source_not_material")
    elif claim_requires_source and not official_source:
        lab_route = "SIGNAL_ONLY"
        reasons.append("unverified_model_pricing_or_provider_claim")
    elif classification.material_route == "NEEDS_LINK_ENRICHMENT":
        lab_route = "NEEDS_ENRICHMENT"
        reasons.append("link_thin_message_requires_enrichment_first")
    elif classification.material_route in {"SIGNAL_ONLY_RISK_REVIEW", "MANUAL_REVIEW_ONLY"}:
        lab_route = "MANUAL_REVIEW"
        reasons.append("risk_referral_or_abuse_never_auto_material")
    elif features.get("length_tokens", 0) <= 3 and not features.get("entity_tokens") and not row.get("reply_to"):
        lab_route = "CONTEXT_ONLY"
        reasons.append("short_message_without_entities_link_or_reply")
    elif classification.material_route == "REVIEW_SIGNAL_OR_DISCUSSION":
        lab_route = "REVIEW_HIGH_RECALL"
        reasons.append("technical_or_resource_candidate_retained_for_context_evidence_review")

    if lab_route == "REJECT_SAFE" and not reasons:
        reasons.append("auditable_reject_safe_rule")
    return {
        "lab_message_id": row.get("lab_message_id"),
        "snapshot_id": row.get("snapshot_id"),
        "raw_id": row.get("raw_id"),
        "dataset_message_id": row.get("dataset_message_id"),
        "telegram_chat_id": row.get("telegram_chat_id"),
        "chat_id": row.get("chat_id"),
        "chat_title": row.get("chat_title"),
        "topic_id": row.get("topic_id"),
        "thread_id": row.get("thread_id"),
        "topic_title": row.get("topic_title"),
        "reply_to": row.get("reply_to"),
        "timestamp": row.get("timestamp"),
        "primary_class": primary,
        "classifier_route": classification.material_route,
        "route": lab_route,
        "route_reason": ";".join(dict.fromkeys(reasons)),
        "risk": classification.risk,
        "material_type_hint": classification.material_type,
        "confidence": f"{classification.confidence:.3f}",
        "marker_id": marker_for(row, primary, lab_route),
        "domains": ",".join(features.get("domains", []) or []),
        "model_names": ",".join(features.get("model_names", []) or []),
        "error_codes": ",".join(features.get("error_codes", []) or []),
        "length_tokens": features.get("length_tokens", 0),
        "has_link": str(bool(features.get("has_link"))).lower(),
        "has_api_terms": str(bool(features.get("has_api_terms"))).lower(),
        "has_code": str(bool(features.get("has_code"))).lower(),
        "preview": text_preview(text, 280),
    }


def command_prepare(args: argparse.Namespace, paths: LabPaths) -> None:
    input_path = Path(args.input).resolve()
    records = load_messages(input_path)
    snapshot_id = f"snapshot-{stable_hash(str(input_path) + ':' + str(input_path.stat().st_mtime_ns))}"
    rows = [prepare_row(record.raw, index, snapshot_id) for index, record in enumerate(records, start=1)]
    write_jsonl(paths.p(RAW_PATH), rows)
    manifest = {
        "snapshot_id": snapshot_id,
        "created_at_utc": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        "input_path": str(input_path),
        "message_count": len(rows),
        "mode": "offline_read_only",
        "raw_output": str(paths.p(RAW_PATH).relative_to(PROJECT_ROOT)),
        "constraints": ["no_production_writes", "no_deploy", "no_generation", "raw_text_and_raw_json_preserved"],
    }
    safe_write_text(paths.p("data/snapshot_manifest.json"), json.dumps(manifest, ensure_ascii=False, indent=2))
    write_lab_readme(paths)
    print(f"prepared {len(rows)} messages -> {paths.p(RAW_PATH)}")


def command_normalize(args: argparse.Namespace, paths: LabPaths) -> None:
    rows = read_jsonl(paths.p(RAW_PATH))
    normalized_rows = [normalize_one(row) for row in rows]
    write_jsonl(paths.p(NORMALIZED_PATH), normalized_rows)
    feature_rows = []
    for row in normalized_rows:
        features = row["features"]
        feature_rows.append({
            "lab_message_id": row["lab_message_id"],
            "raw_id": row["raw_id"],
            "dataset_message_id": row["dataset_message_id"],
            "length_chars": features["length_chars"],
            "length_tokens": features["length_tokens"],
            "has_link": features["has_link"],
            "domains": ",".join(features["domains"]),
            "usernames": ",".join(features["usernames"]),
            "has_code": features["has_code"],
            "has_error_code": features["has_error_code"],
            "has_price": features["has_price"],
            "has_model_name": features["has_model_name"],
            "has_api_terms": features["has_api_terms"],
            "has_profanity": features["has_profanity"],
            "profanity_density": features["profanity_density"],
            "filler_density": features["filler_density"],
            "emoji_density": features["emoji_density"],
            "punctuation_noise": features["punctuation_noise"],
            "sender_is_bot": features["sender_is_bot"],
            "chat_id": features["chat_id"],
            "topic_id": features["topic_id"],
            "thread_id": features["thread_id"],
            "reply_to": features["reply_to"],
            "timestamp": features["timestamp"],
            "normalization_changes": ";".join(row["normalization_changes"]),
            "normalization_reason": ";".join(row["normalization_reason"]),
            "preview": text_preview(row["raw_text"], 180),
        })
    write_csv(paths.p("out/normalization/message_features.csv"), feature_rows, list(feature_rows[0].keys()) if feature_rows else [])
    write_normalization_report(paths, normalized_rows)
    print(f"normalized {len(normalized_rows)} messages -> {paths.p(NORMALIZED_PATH)}")


def command_route(args: argparse.Namespace, paths: LabPaths) -> None:
    rows = read_jsonl(paths.p(NORMALIZED_PATH))
    routed = [route_one(row) for row in rows]
    write_csv(paths.p(ROUTED_PATH), routed, ROUTE_FIELDS)
    for route_name, filename in [
        ("REJECT_SAFE", "reject_safe_sample.csv"),
        ("REVIEW_HIGH_RECALL", "review_high_recall.csv"),
        ("MANUAL_REVIEW", "manual_review.csv"),
        ("NEEDS_ENRICHMENT", "needs_enrichment.csv"),
        ("AGGREGATE_ONLY", "aggregate_only.csv"),
    ]:
        subset = [row for row in routed if row["route"] == route_name]
        if filename == "reject_safe_sample.csv":
            subset = stable_sample(subset, 250)
        write_csv(paths.p(f"out/routes/{filename}"), subset, ROUTE_FIELDS)
    write_route_summary(paths, routed)
    print(f"routed {len(routed)} messages -> {paths.p(ROUTED_PATH)}")


def command_markers(args: argparse.Namespace, paths: LabPaths) -> None:
    routed = read_csv(paths.p(ROUTED_PATH))
    by_marker: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in routed:
        by_marker[row.get("marker_id") or "unknown_potential_signal_marker"].append(row)

    groups: list[dict[str, Any]] = []
    label_rows: list[dict[str, Any]] = []
    md = ["# Marker Groups", "", "Manual review packets for first-pass routing calibration.", ""]
    for marker_id in MARKER_ORDER:
        rows = by_marker.get(marker_id, [])
        chunks = [rows[i : i + args.marker_size] for i in range(0, len(rows), args.marker_size)] or [[]]
        for index, chunk in enumerate(chunks, start=1):
            group_id = f"{marker_id}-{index:03d}"
            route_counts = Counter(row.get("route") for row in chunk)
            groups.append({
                "marker_group_id": group_id,
                "marker_id": marker_id,
                "message_count": len(chunk),
                "routes": json.dumps(dict(route_counts), ensure_ascii=False),
                "top_raw_ids": ",".join(row.get("raw_id", "") for row in chunk[:10]),
            })
            label_rows.append({
                "marker_group_id": group_id,
                "marker_id": marker_id,
                "message_count": len(chunk),
                "manual_label": "",
                "should_reject_safe": "",
                "should_context_only": "",
                "should_signal_only": "",
                "should_needs_enrichment": "",
                "should_manual_review": "",
                "should_aggregate_only": "",
                "should_review_high_recall": "",
                "notes": "",
            })
            md.extend([f"## {group_id}", "", f"Routes: `{dict(route_counts)}`", "", "Top examples:", ""])
            for row in chunk[:10]:
                md.append(f"- `{row.get('raw_id')}` `{row.get('route')}` {row.get('preview')}")
            if len(chunk) > 10:
                md.extend(["", "Random examples:", ""])
                for row in stable_sample(chunk[10:], min(10, len(chunk[10:]))):
                    md.append(f"- `{row.get('raw_id')}` `{row.get('route')}` {row.get('preview')}")
            md.append("")

    group_fields = ["marker_group_id", "marker_id", "message_count", "routes", "top_raw_ids"]
    label_fields = [
        "marker_group_id", "marker_id", "message_count", "manual_label", "should_reject_safe", "should_context_only",
        "should_signal_only", "should_needs_enrichment", "should_manual_review", "should_aggregate_only", "should_review_high_recall", "notes",
    ]
    write_csv(paths.p("out/markers/marker_groups.csv"), groups, group_fields)
    write_csv(paths.p("out/markers/manual_marker_labels.csv"), label_rows, label_fields)
    safe_write_text(paths.p("out/markers/marker_examples.md"), "\n".join(md))
    print(f"created {len(groups)} marker groups -> {paths.p('out/markers')}")


def load_joined(paths: LabPaths) -> tuple[list[dict[str, Any]], dict[str, dict[str, str]]]:
    normalized = read_jsonl(paths.p(NORMALIZED_PATH))
    routed = {row["lab_message_id"]: row for row in read_csv(paths.p(ROUTED_PATH))}
    return normalized, routed


def entity_set(row: dict[str, Any]) -> set[str]:
    features = row.get("features") or {}
    return set(features.get("entity_tokens", []) or [])


def context_scope_key(row: dict[str, Any]) -> tuple[str, str]:
    return (str(row.get("chat_id") or ""), str(row.get("thread_id") or row.get("topic_id") or ""))


def build_context_windows(rows: list[dict[str, Any]], routed: dict[str, dict[str, str]]) -> tuple[list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]]]:
    by_scope: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        by_scope[context_scope_key(row)].append(row)
    for scoped in by_scope.values():
        scoped.sort(key=lambda item: parse_time(item.get("timestamp")) or datetime.min.replace(tzinfo=timezone.utc))

    windows: list[dict[str, Any]] = []
    short_decisions: list[dict[str, Any]] = []
    attachments: list[dict[str, Any]] = []
    seen_window_ids: set[str] = set()

    for scoped in by_scope.values():
        for index, row in enumerate(scoped):
            route = routed.get(row["lab_message_id"], {})
            route_name = route.get("route", "")
            if route_name not in {"REVIEW_HIGH_RECALL", "SIGNAL_ONLY", "NEEDS_ENRICHMENT", "MANUAL_REVIEW", "AGGREGATE_ONLY"}:
                continue
            created_at = parse_time(row.get("timestamp"))
            left = max(0, index - 10)
            right = min(len(scoped), index + 11)
            row_entities = entity_set(row)
            candidates = []
            why = []
            for neighbor in scoped[left:right]:
                neighbor_time = parse_time(neighbor.get("timestamp"))
                time_close = bool(created_at and neighbor_time and abs(created_at - neighbor_time) <= timedelta(minutes=10))
                entity_overlap = bool(row_entities and row_entities & entity_set(neighbor))
                reply_link = bool(row.get("reply_to") and str(row.get("reply_to")) == str(neighbor.get("raw_id")))
                if neighbor["lab_message_id"] == row["lab_message_id"] or time_close or entity_overlap or reply_link:
                    candidates.append(neighbor)
                    if time_close:
                        why.append("time_window_10m")
                    if entity_overlap:
                        why.append("entity_overlap")
                    if reply_link:
                        why.append("reply_chain")
            window_id = f"ctx-{stable_hash(row['lab_message_id'] + ':' + ','.join(item['lab_message_id'] for item in candidates))}"
            if window_id in seen_window_ids:
                continue
            seen_window_ids.add(window_id)
            window_routes = Counter(routed.get(item["lab_message_id"], {}).get("route", "UNKNOWN") for item in candidates)
            windows.append({
                "context_window_id": window_id,
                "anchor_lab_message_id": row["lab_message_id"],
                "anchor_raw_id": row["raw_id"],
                "anchor_route": route_name,
                "chat_id": row.get("chat_id"),
                "topic_id": row.get("topic_id"),
                "thread_id": row.get("thread_id"),
                "message_count": len(candidates),
                "routes": dict(window_routes),
                "entities": sorted(row_entities),
                "why_windowed": sorted(set(why)) or ["anchor_only_high_recall_candidate"],
                "messages": [
                    {
                        "lab_message_id": item["lab_message_id"],
                        "raw_id": item["raw_id"],
                        "dataset_message_id": item["dataset_message_id"],
                        "timestamp": item.get("timestamp"),
                        "route": routed.get(item["lab_message_id"], {}).get("route", "UNKNOWN"),
                        "preview": text_preview(item.get("raw_text") or "", 220),
                    }
                    for item in candidates
                ],
            })
            for item in candidates:
                if item["lab_message_id"] == row["lab_message_id"]:
                    continue
                attachments.append({
                    "context_window_id": window_id,
                    "anchor_lab_message_id": row["lab_message_id"],
                    "attached_lab_message_id": item["lab_message_id"],
                    "anchor_raw_id": row["raw_id"],
                    "attached_raw_id": item["raw_id"],
                    "attached_route": routed.get(item["lab_message_id"], {}).get("route", "UNKNOWN"),
                    "attach_reason": "corroborating_context_window",
                    "preview": text_preview(item.get("raw_text") or "", 180),
                })

    for row in rows:
        route = routed.get(row["lab_message_id"], {})
        features = row.get("features") or {}
        text = row.get("normalized_text") or ""
        is_short = features.get("length_tokens", 0) <= 3 or bool(SHORT_ACK_RE.match(text))
        if not is_short:
            continue
        attached = any(item["attached_lab_message_id"] == row["lab_message_id"] for item in attachments)
        short_decisions.append({
            "lab_message_id": row["lab_message_id"],
            "raw_id": row["raw_id"],
            "route": route.get("route", "UNKNOWN"),
            "short_decision": "ATTACH_AS_CORROBORATING_EVIDENCE" if attached else "CONTEXT_ONLY",
            "has_reply": bool(row.get("reply_to")),
            "entity_count": len(entity_set(row)),
            "preview": text_preview(row.get("raw_text") or "", 160),
        })
    return windows, short_decisions, attachments


def command_context(args: argparse.Namespace, paths: LabPaths) -> None:
    rows, routed = load_joined(paths)
    windows, short_decisions, attachments = build_context_windows(rows, routed)
    write_jsonl(paths.p(CONTEXT_WINDOWS_PATH), windows)
    write_csv(paths.p("out/context/short_message_decisions.csv"), short_decisions, ["lab_message_id", "raw_id", "route", "short_decision", "has_reply", "entity_count", "preview"])
    write_csv(paths.p(CONTEXT_ATTACHED_PATH), attachments, ["context_window_id", "anchor_lab_message_id", "attached_lab_message_id", "anchor_raw_id", "attached_raw_id", "attached_route", "attach_reason", "preview"])
    print(f"built {len(windows)} context windows -> {paths.p(CONTEXT_WINDOWS_PATH)}")


def build_records_for_cluster(rows: list[dict[str, Any]], routed: dict[str, dict[str, str]], algorithm: str) -> tuple[list[Any], dict[str, Any]]:
    from message_lab_core import Classification, MessageRecord

    records = []
    classifications = {}
    for row in rows:
        route = routed.get(row["lab_message_id"], {})
        if route.get("route") not in {"REVIEW_HIGH_RECALL", "SIGNAL_ONLY", "NEEDS_ENRICHMENT", "MANUAL_REVIEW", "AGGREGATE_ONLY"}:
            continue
        feature_text_value = row.get("feature_text") or row.get("raw_text") or ""
        if algorithm == "entity_overlap":
            feature_text_value = " ".join((row.get("features") or {}).get("entity_tokens", []) or tokenize(feature_text_value))
        elif algorithm == "time_thread_entity":
            feature_text_value = " ".join([str(row.get("thread_id") or row.get("topic_id") or "main"), feature_text_value])
        elif algorithm == "simhash_near_duplicate":
            feature_text_value = re.sub(r"\d+", "#", feature_text_value)
        record = MessageRecord(row["lab_message_id"], feature_text_value, str(row.get("chat_id") or ""), str(row.get("thread_id") or row.get("topic_id") or ""), str(row.get("timestamp") or ""), row)
        classification = classify_text(row.get("raw_text") or "", chat=str(row.get("chat_title") or ""), topic=str(row.get("topic_title") or ""))
        classifications[record.id] = Classification(
            classification.primary_class,
            classification.material_route,
            classification.material_type,
            classification.action,
            classification.confidence,
            classification.risk,
            classification.reasons,
            classification.features,
        )
        records.append(record)
    return records, classifications


def command_cluster(args: argparse.Namespace, paths: LabPaths) -> None:
    rows, routed = load_joined(paths)
    algorithms = [
        ("tfidf_cosine_baseline", 0.34),
        ("bm25_token_overlap_baseline", 0.30),
        ("simhash_near_duplicate", 0.46),
        ("entity_overlap", 0.24),
        ("time_thread_entity", 0.32),
        ("bge_m3_cosine_optional_cached", 0.0),
        ("hybrid_bm25_bge_optional_cached", 0.0),
        ("hdbscan_optional", 0.0),
        ("kmeans_optional", 0.0),
        ("agglomerative_optional", 0.0),
        ("bertopic_like_optional", 0.0),
        ("supervised_classifier_after_gold_labels", 0.0),
    ]
    metrics: list[dict[str, Any]] = []
    md = ["# Algorithm Comparison", "", "Algorithms help grouping only; material-worthiness is decided later by labels and evidence sufficiency.", ""]
    for algorithm, threshold in algorithms:
        if threshold <= 0:
            metrics.append({"algorithm": algorithm, "status": "adapter_not_run", "cluster_count": 0, "review_clusters": 0, "multi_message_review_clusters": 0, "singleton_review_clusters": 0, "notes": "optional dependency/cache required; no production/model-worker call performed"})
            safe_write_text(paths.p(f"out/algorithms/topic_examples_{algorithm}.md"), f"# {algorithm}\n\nAdapter placeholder. Local cached implementation can be added after lab labels exist.\n")
            continue
        records, classifications = build_records_for_cluster(rows, routed, algorithm)
        clusters = cluster_messages(records, classifications, threshold=threshold, max_pairwise=args.max_pairwise)
        write_jsonl(paths.p(f"out/algorithms/clusters_{algorithm}.jsonl"), clusters)
        review_clusters = [item for item in clusters if item["decision"] in {"REVIEW_CLUSTER_FOR_MATERIAL", "REVIEW_SINGLE_SIGNAL", "LINK_ENRICHMENT_FIRST"}]
        multi_review = [item for item in review_clusters if item["size"] > 1]
        metrics.append({
            "algorithm": algorithm,
            "status": "ran_dependency_free_baseline",
            "cluster_count": len(clusters),
            "review_clusters": len(review_clusters),
            "multi_message_review_clusters": len(multi_review),
            "singleton_review_clusters": len(review_clusters) - len(multi_review),
            "avg_review_size": round(sum(item["size"] for item in review_clusters) / max(1, len(review_clusters)), 2),
            "notes": "baseline grouping for human calibration; not importance truth",
        })
        write_topic_examples(paths, algorithm, clusters[:25])
        md.extend([f"## {algorithm}", "", f"Threshold: `{threshold}`", f"Clusters: `{len(clusters)}`; review clusters: `{len(review_clusters)}`; multi-review: `{len(multi_review)}`", ""])
    write_csv(paths.p("out/algorithms/cluster_metrics.csv"), metrics, ["algorithm", "status", "cluster_count", "review_clusters", "multi_message_review_clusters", "singleton_review_clusters", "avg_review_size", "notes"])
    md.append("## Ranking heuristic")
    md.append("")
    for row in sorted([m for m in metrics if m["status"].startswith("ran")], key=lambda item: (-int(item["multi_message_review_clusters"]), int(item["singleton_review_clusters"]))):
        md.append(f"- `{row['algorithm']}`: multi-review `{row['multi_message_review_clusters']}`, singleton-review `{row['singleton_review_clusters']}`")
    safe_write_text(paths.p("out/algorithms/algorithm_comparison.md"), "\n".join(md))
    print(f"compared {len(algorithms)} algorithms -> {paths.p('out/algorithms')}")


def command_cluster_review(args: argparse.Namespace, paths: LabPaths) -> None:
    cluster_files = sorted(paths.p("out/algorithms").glob("clusters_*.jsonl"))
    template_rows: list[dict[str, Any]] = []
    md = ["# Cluster Review", "", "Name clusters manually and mark whether each grouping is useful, mixed, or bad.", ""]
    examples_dir = paths.p("out/cluster_review/cluster_examples")
    examples_dir.mkdir(parents=True, exist_ok=True)
    for cluster_file in cluster_files:
        algorithm = cluster_file.stem.removeprefix("clusters_")
        clusters = read_jsonl(cluster_file)[: args.top_clusters]
        md.extend([f"## {algorithm}", ""])
        for cluster in clusters:
            cluster_id = cluster["id"]
            suggested_name = suggest_cluster_name(cluster)
            template_rows.append({
                "cluster_id": cluster_id,
                "algorithm": algorithm,
                "suggested_name": suggested_name,
                "manual_name": "",
                "cluster_quality": "",
                "route_policy": "",
                "notes": "",
            })
            md.extend([f"### {algorithm} / {cluster_id} / {suggested_name}", "", f"Size: `{cluster['size']}`; decision: `{cluster['decision']}`; qualityScore: `{cluster['qualityScore']}`", "", "Top examples:", ""])
            example_lines = [f"# {algorithm} / {cluster_id}\n"]
            for sample in cluster.get("samples", [])[:20]:
                line = f"- `{sample.get('id')}` `{sample.get('class')}` `{sample.get('route')}` {sample.get('preview')}"
                md.append(line)
                example_lines.append(line)
            safe_write_text(examples_dir / f"{algorithm}_{cluster_id}.md", "\n".join(example_lines) + "\n")
            md.append("")
    write_csv(paths.p("out/cluster_review/cluster_labeling_template.csv"), template_rows, ["cluster_id", "algorithm", "suggested_name", "manual_name", "cluster_quality", "route_policy", "notes"])
    safe_write_text(paths.p("out/cluster_review/top_clusters.md"), "\n".join(md))
    print(f"created cluster review templates -> {paths.p('out/cluster_review')}")


def suggest_cluster_name(cluster: dict[str, Any]) -> str:
    classes = cluster.get("classes", {}) or {}
    routes = cluster.get("routes", {}) or {}
    top_class = next(iter(classes.keys()), "mixed")
    top_route = next(iter(routes.keys()), "mixed")
    domains = Counter()
    for sample in cluster.get("samples", []) or []:
        for domain in DOMAIN_RE.findall(sample.get("preview") or ""):
            domains[domain.lower()] += 1
    if domains:
        return f"{top_class.lower()}_{domains.most_common(1)[0][0]}"
    return f"{top_route.lower()}_{top_class.lower()}"


def build_evidence_groups(paths: LabPaths) -> list[dict[str, Any]]:
    rows, routed = load_joined(paths)
    by_id = {row["lab_message_id"]: row for row in rows}
    windows = read_jsonl(paths.p(CONTEXT_WINDOWS_PATH)) if paths.p(CONTEXT_WINDOWS_PATH).exists() else []
    groups: list[dict[str, Any]] = []
    seen: set[str] = set()
    for window in windows:
        message_ids = [item["lab_message_id"] for item in window.get("messages", []) if item.get("lab_message_id") in by_id]
        if not message_ids:
            continue
        key = ",".join(sorted(message_ids))
        if key in seen:
            continue
        seen.add(key)
        messages = [by_id[mid] for mid in message_ids]
        route_rows = [routed.get(mid, {}) for mid in message_ids]
        groups.append(summarize_evidence_group(f"eg-{len(groups) + 1:05d}", messages, route_rows, ["context_window", *window.get("why_windowed", [])]))
    for route_name in ("SIGNAL_ONLY", "NEEDS_ENRICHMENT", "MANUAL_REVIEW", "AGGREGATE_ONLY", "REVIEW_HIGH_RECALL"):
        buckets: dict[str, list[dict[str, Any]]] = defaultdict(list)
        for row in rows:
            route = routed.get(row["lab_message_id"], {})
            if route.get("route") != route_name:
                continue
            features = row.get("features") or {}
            bucket_key = "|".join(features.get("entity_tokens", [])[:3]) or f"{row.get('chat_id')}|{row.get('thread_id')}|{route_name}"
            buckets[bucket_key].append(row)
        for bucket_rows in buckets.values():
            if len(bucket_rows) < 2 and route_name == "REVIEW_HIGH_RECALL":
                continue
            message_ids = [item["lab_message_id"] for item in bucket_rows[:50]]
            key = ",".join(sorted(message_ids))
            if key in seen:
                continue
            seen.add(key)
            route_rows = [routed.get(mid, {}) for mid in message_ids]
            groups.append(summarize_evidence_group(f"eg-{len(groups) + 1:05d}", bucket_rows[:50], route_rows, ["route_entity_bucket", route_name]))
    return groups


def summarize_evidence_group(group_id: str, messages: list[dict[str, Any]], route_rows: list[dict[str, str]], why: list[str]) -> dict[str, Any]:
    chats = Counter(str(row.get("chat_id") or row.get("chat_title") or "unknown") for row in messages)
    topics = Counter(str(row.get("thread_id") or row.get("topic_id") or row.get("topic_title") or "main") for row in messages)
    authors = Counter(str(row.get("sender_id") or row.get("sender_name") or "unknown") for row in messages)
    entities = sorted(set(entity for row in messages for entity in (row.get("features") or {}).get("entity_tokens", []) or []))
    domains = sorted(set(domain for row in messages for domain in (row.get("features") or {}).get("domains", []) or []))
    times = [parse_time(row.get("timestamp")) for row in messages]
    times = [item for item in times if item]
    routes = Counter(row.get("route") for row in route_rows)
    risks = Counter(row.get("risk") for row in route_rows)
    source_count = len(messages)
    independent_source_count = len(chats) if len(chats) > 1 else max(1, len(authors))
    route_policy = derive_group_policy(routes, risks, source_count, independent_source_count)
    return {
        "group_id": group_id,
        "messages": [{"lab_message_id": row["lab_message_id"], "raw_id": row["raw_id"], "dataset_message_id": row["dataset_message_id"], "preview": text_preview(row.get("raw_text") or "", 180)} for row in messages],
        "authors": dict(authors.most_common(10)),
        "source_count": source_count,
        "independent_source_count": independent_source_count,
        "chats_topics": {"chats": dict(chats.most_common(10)), "topics": dict(topics.most_common(10))},
        "time_span": {"from": min(times).isoformat() if times else "", "to": max(times).isoformat() if times else ""},
        "entities": entities,
        "links_domains": domains,
        "claim_type": infer_claim_type(route_rows, messages),
        "risk_state": risks.most_common(1)[0][0] if risks else "LOW",
        "link_enrichment_state": "NEEDS_LINK_ENRICHMENT" if routes.get("NEEDS_ENRICHMENT") else "NOT_REQUIRED_OR_UNKNOWN",
        "duplicate_status": "POSSIBLE_DUPLICATES" if duplicate_ratio(messages) > 0.35 else "NOT_COLLAPSED",
        "preliminary_topic": infer_topic(entities, route_rows),
        "preliminary_route_policy": route_policy,
        "why_grouped": sorted(set(why)),
    }


def derive_group_policy(routes: Counter, risks: Counter, source_count: int, independent_source_count: int) -> str:
    if risks.get("HIGH") or routes.get("MANUAL_REVIEW"):
        return "MANUAL_REVIEW"
    if routes.get("NEEDS_ENRICHMENT"):
        return "NEEDS_LINK_ENRICHMENT"
    if routes.get("AGGREGATE_ONLY"):
        return "AGGREGATE_ONLY"
    if source_count >= 2 and independent_source_count >= 2 and routes.get("REVIEW_HIGH_RECALL"):
        return "ELIGIBLE_MATERIAL_REVIEW_ONLY_NOT_FINAL"
    if routes.get("SIGNAL_ONLY"):
        return "SIGNAL_ONLY"
    return "CONTEXT_ONLY"


def infer_claim_type(route_rows: list[dict[str, str]], messages: list[dict[str, Any]]) -> str:
    classes = Counter(row.get("primary_class") for row in route_rows)
    if classes.get("CAREER_JOB_POST"):
        return "JOB_POST"
    if classes.get("EVENT_ANNOUNCEMENT"):
        return "EVENT_ANNOUNCEMENT"
    if classes.get("MODEL_RUMOR_OR_PRICING_CLAIM"):
        return "MODEL_PRICING_PROVIDER_CLAIM"
    if any((row.get("features") or {}).get("has_error_code") for row in messages):
        return "OUTAGE_OR_TECHNICAL_PROBLEM"
    if any((row.get("features") or {}).get("has_api_terms") for row in messages):
        return "API_OR_WORKFLOW"
    return "DISCUSSION_OR_SIGNAL"


def duplicate_ratio(messages: list[dict[str, Any]]) -> float:
    hashes = [stable_hash(row.get("feature_text") or row.get("raw_text") or "", 8) for row in messages]
    if not hashes:
        return 0.0
    return 1.0 - (len(set(hashes)) / len(hashes))


def infer_topic(entities: list[str], route_rows: list[dict[str, str]]) -> str:
    if entities:
        return entities[0]
    classes = Counter(row.get("primary_class") for row in route_rows)
    return classes.most_common(1)[0][0].lower() if classes else "unknown"


def command_evidence(args: argparse.Namespace, paths: LabPaths) -> None:
    groups = build_evidence_groups(paths)
    write_jsonl(paths.p(EVIDENCE_GROUPS_PATH), groups)
    summary_rows = [{
        "group_id": group["group_id"],
        "source_count": group["source_count"],
        "independent_source_count": group["independent_source_count"],
        "claim_type": group["claim_type"],
        "risk_state": group["risk_state"],
        "link_enrichment_state": group["link_enrichment_state"],
        "duplicate_status": group["duplicate_status"],
        "preliminary_topic": group["preliminary_topic"],
        "preliminary_route_policy": group["preliminary_route_policy"],
        "why_grouped": ";".join(group["why_grouped"]),
    } for group in groups]
    write_csv(paths.p("out/evidence/evidence_group_summary.csv"), summary_rows, list(summary_rows[0].keys()) if summary_rows else [])
    write_evidence_review(paths, groups[:200])
    print(f"created {len(groups)} evidence groups -> {paths.p(EVIDENCE_GROUPS_PATH)}")


def command_gold_template(args: argparse.Namespace, paths: LabPaths) -> None:
    routed = read_csv(paths.p(ROUTED_PATH))
    selected = []
    for route_name in ["REJECT_SAFE", "CONTEXT_ONLY", "SIGNAL_ONLY", "NEEDS_ENRICHMENT", "MANUAL_REVIEW", "AGGREGATE_ONLY", "REVIEW_HIGH_RECALL"]:
        rows = [row for row in routed if row.get("route") == route_name]
        selected.extend(stable_sample(rows, min(args.per_route, len(rows))))
    fields = [
        "lab_message_id", "raw_id", "dataset_message_id", "route", "route_reason", "marker_id", "primary_class", "important",
        "not_important", "topic", "correct_route", "should_be_signal", "should_be_context", "should_be_enrichment",
        "should_be_manual_review", "should_be_aggregate", "eligible_material", "false_positive", "false_negative", "notes", "preview",
    ]
    template_rows = []
    for row in selected:
        template_rows.append({
            "lab_message_id": row["lab_message_id"],
            "raw_id": row["raw_id"],
            "dataset_message_id": row["dataset_message_id"],
            "route": row["route"],
            "route_reason": row["route_reason"],
            "marker_id": row["marker_id"],
            "primary_class": row["primary_class"],
            "important": "",
            "not_important": "",
            "topic": "",
            "correct_route": "",
            "should_be_signal": "",
            "should_be_context": "",
            "should_be_enrichment": "",
            "should_be_manual_review": "",
            "should_be_aggregate": "",
            "eligible_material": "",
            "false_positive": "",
            "false_negative": "",
            "notes": "",
            "preview": row["preview"],
        })
    write_csv(paths.p(GOLD_TEMPLATE_PATH), template_rows, fields)
    safe_write_text(paths.p("out/gold/gold_labeling_instructions.md"), gold_instructions())
    print(f"created gold template with {len(template_rows)} rows -> {paths.p(GOLD_TEMPLATE_PATH)}")


def command_evaluate(args: argparse.Namespace, paths: LabPaths) -> None:
    gold_path = Path(args.gold) if args.gold else paths.p(GOLD_TEMPLATE_PATH)
    routed = {row["lab_message_id"]: row for row in read_csv(paths.p(ROUTED_PATH))}
    gold = read_csv(gold_path) if gold_path.exists() else []
    labeled = [row for row in gold if any((row.get(field) or "").strip() for field in ["important", "not_important", "correct_route", "eligible_material", "false_positive", "false_negative"])]
    confusion: Counter[tuple[str, str]] = Counter()
    errors: list[dict[str, Any]] = []
    important_total = important_retained = junk_total = junk_filtered = 0
    material_fp = reject_fn = 0
    for row in labeled:
        predicted = routed.get(row["lab_message_id"], {}).get("route", row.get("route", "UNKNOWN"))
        expected = (row.get("correct_route") or "").strip() or predicted
        confusion[(expected, predicted)] += 1
        important = truthy(row.get("important")) or truthy(row.get("eligible_material")) or any(truthy(row.get(field)) for field in ["should_be_signal", "should_be_enrichment", "should_be_manual_review", "should_be_aggregate"])
        junk = truthy(row.get("not_important"))
        if important:
            important_total += 1
            if predicted != "REJECT_SAFE":
                important_retained += 1
            else:
                reject_fn += 1
        if junk:
            junk_total += 1
            if predicted in {"REJECT_SAFE", "CONTEXT_ONLY"}:
                junk_filtered += 1
        if predicted == "MATERIAL_CANDIDATE_PRELIMINARY" and not truthy(row.get("eligible_material")):
            material_fp += 1
        if predicted != expected:
            errors.append({
                "lab_message_id": row["lab_message_id"],
                "raw_id": row.get("raw_id", ""),
                "expected_route": expected,
                "predicted_route": predicted,
                "important": important,
                "notes": row.get("notes", ""),
                "preview": row.get("preview", ""),
            })
    confusion_rows = [{"expected_route": expected, "predicted_route": predicted, "count": count} for (expected, predicted), count in sorted(confusion.items())]
    write_csv(paths.p("out/eval/route_confusion_matrix.csv"), confusion_rows, ["expected_route", "predicted_route", "count"])
    write_csv(paths.p("out/eval/error_analysis.csv"), errors, ["lab_message_id", "raw_id", "expected_route", "predicted_route", "important", "notes", "preview"])
    write_algorithm_ranking(paths)
    report = evaluation_report(labeled, important_total, important_retained, junk_total, junk_filtered, reject_fn, material_fp, errors)
    safe_write_text(paths.p("out/eval/evaluation_report.md"), report)
    write_final_policy(paths)
    print(f"evaluated {len(labeled)} labeled rows -> {paths.p('out/eval/evaluation_report.md')}")


def truthy(value: Any) -> bool:
    return str(value or "").strip().lower() in {"1", "true", "yes", "y", "да", "x", "eligible", "important"}


def write_lab_readme(paths: LabPaths) -> None:
    text = """# Material Selection Lab

Offline/read-only lab for learning Telegram message selection before production automation.

Run order: `prepare -> normalize -> route -> context -> cluster -> markers -> evidence -> gold-template -> evaluate`.

Safety: no production writes, no deletes, no deploy, no LLM generation, no model-worker calls. Raw text and raw JSON are preserved in `data/raw_messages.jsonl`.
"""
    safe_write_text(paths.docs / "README.md", text)


def write_normalization_report(paths: LabPaths, rows: list[dict[str, Any]]) -> None:
    changes = Counter(change for row in rows for change in row.get("normalization_changes", []))
    feature_counts = Counter()
    for row in rows:
        features = row["features"]
        for key in ["has_link", "has_code", "has_error_code", "has_price", "has_model_name", "has_api_terms", "has_profanity", "sender_is_bot"]:
            if features.get(key):
                feature_counts[key] += 1
    md = ["# Normalization Report", "", f"Messages: `{len(rows)}`", "", "## Changes", ""]
    for name, count in changes.most_common():
        md.append(f"- `{name}`: `{count}`")
    md.extend(["", "## Features", ""])
    for name, count in feature_counts.most_common():
        md.append(f"- `{name}`: `{count}`")
    safe_write_text(paths.p("out/normalization/normalization_report.md"), "\n".join(md) + "\n")


def write_route_summary(paths: LabPaths, routed: list[dict[str, Any]]) -> None:
    route_counts = Counter(row["route"] for row in routed)
    class_counts = Counter(row["primary_class"] for row in routed)
    marker_counts = Counter(row["marker_id"] for row in routed)
    md = ["# Route Summary", "", f"Messages: `{len(routed)}`", "", "## Routes", ""]
    for name, count in route_counts.most_common():
        md.append(f"- `{name}`: `{count}`")
    md.extend(["", "## Primary Classes", ""])
    for name, count in class_counts.most_common(30):
        md.append(f"- `{name}`: `{count}`")
    md.extend(["", "## Marker Groups", ""])
    for name, count in marker_counts.most_common():
        md.append(f"- `{name}`: `{count}`")
    md.extend(["", "Every routed row has `route_reason`; `REJECT_SAFE` is auditable and does not delete data.", ""])
    safe_write_text(paths.p("out/routes/route_summary.md"), "\n".join(md))


def write_topic_examples(paths: LabPaths, algorithm: str, clusters: list[dict[str, Any]]) -> None:
    md = [f"# Topic Examples: {algorithm}", ""]
    for cluster in clusters:
        md.extend([f"## {cluster['id']} / {cluster['decision']}", "", f"Size: `{cluster['size']}`; qualityScore: `{cluster['qualityScore']}`", ""])
        for sample in cluster.get("samples", [])[:10]:
            md.append(f"- `{sample.get('id')}` `{sample.get('class')}` {sample.get('preview')}")
        md.append("")
    safe_write_text(paths.p(f"out/algorithms/topic_examples_{algorithm}.md"), "\n".join(md))


def write_evidence_review(paths: LabPaths, groups: list[dict[str, Any]]) -> None:
    md = ["# Evidence Groups For Review", "", "Evidence groups are not materials. They are candidate evidence packets for manual labeling.", ""]
    for group in groups:
        md.extend([
            f"## {group['group_id']} / {group['preliminary_route_policy']}",
            "",
            f"Sources: `{group['source_count']}`; independent: `{group['independent_source_count']}`; claim: `{group['claim_type']}`; risk: `{group['risk_state']}`",
            f"Entities: `{', '.join(group['entities'][:12])}`",
            "",
        ])
        for message in group["messages"][:12]:
            md.append(f"- `{message['raw_id']}` {message['preview']}")
        md.append("")
    safe_write_text(paths.p("out/evidence/evidence_groups_for_review.md"), "\n".join(md))


def gold_instructions() -> str:
    return """# Gold Labeling Instructions

Fill `gold_labels_template.csv` manually. Use `true`/`false` or `1`/`0`.

Rules:
- Important messages must not land in `REJECT_SAFE`.
- Junk must not land in `MATERIAL_CANDIDATE_PRELIMINARY`.
- If uncertain, prefer `REVIEW_HIGH_RECALL` over losing a useful signal.
- Mark `eligible_material=true` only when evidence is sufficient and the item should become durable knowledge after enrichment/dedupe checks.
- Single-message, single-source, job/event single-source, rules/onboarding, risk/referral, and link-only-before-enrichment should not be eligible materials.
"""


def evaluation_report(labeled: list[dict[str, str]], important_total: int, important_retained: int, junk_total: int, junk_filtered: int, reject_fn: int, material_fp: int, errors: list[dict[str, Any]]) -> str:
    important_retention = important_retained / important_total if important_total else math.nan
    junk_filter_rate = junk_filtered / junk_total if junk_total else math.nan
    return "\n".join([
        "# Evaluation Report",
        "",
        f"Labeled rows: `{len(labeled)}`",
        f"Important retained outside REJECT_SAFE: `{important_retained}/{important_total}` ({format_rate(important_retention)})",
        f"Junk filtered to REJECT_SAFE/CONTEXT_ONLY: `{junk_filtered}/{junk_total}` ({format_rate(junk_filter_rate)})",
        f"False negatives in REJECT_SAFE: `{reject_fn}`",
        f"False positives in MATERIAL_CANDIDATE_PRELIMINARY: `{material_fp}`",
        f"Route mismatches: `{len(errors)}`",
        "",
        "Promotion target: zero important false negatives in `REJECT_SAFE`, low material-candidate false positives, and stable evidence groups with human-approved labels.",
        "",
    ])


def format_rate(value: float) -> str:
    return "n/a" if math.isnan(value) else f"{value:.1%}"


def write_algorithm_ranking(paths: LabPaths) -> None:
    metrics_path = paths.p("out/algorithms/cluster_metrics.csv")
    if not metrics_path.exists():
        safe_write_text(paths.p("out/eval/algorithm_ranking.md"), "# Algorithm Ranking\n\nRun `cluster` first.\n")
        return
    rows = read_csv(metrics_path)
    runnable = [row for row in rows if row.get("status", "").startswith("ran")]
    ranked = sorted(runnable, key=lambda row: (-int(float(row.get("multi_message_review_clusters") or 0)), int(float(row.get("singleton_review_clusters") or 0))))
    md = ["# Algorithm Ranking", "", "Ranking is provisional until manual cluster labels exist.", ""]
    for index, row in enumerate(ranked, start=1):
        md.append(f"{index}. `{row['algorithm']}`: multi-review `{row.get('multi_message_review_clusters')}`, singleton-review `{row.get('singleton_review_clusters')}`")
    safe_write_text(paths.p("out/eval/algorithm_ranking.md"), "\n".join(md) + "\n")


def write_final_policy(paths: LabPaths) -> None:
    final_dir = paths.p("out/final")
    policy = """# Draft Material Eligibility Policy

This is a draft after lab evidence collection, not production behavior.

States:
- `REJECT_NEVER_MATERIAL`: rules, onboarding, moderation bot templates, pure spam/noise.
- `CONTEXT_ONLY`: chatter, short acknowledgements without entity/reply/context, weak surrounding context.
- `SIGNAL_ONLY`: useful but insufficient or unverified claims; keep for trend/source tracking.
- `NEEDS_LINK_ENRICHMENT`: link-only or link-thin messages before content/source extraction.
- `MANUAL_REVIEW`: risk/referral/abuse/security-sensitive content.
- `AGGREGATE_ONLY`: jobs/events/outages/provider claims requiring multiple independent sources.
- `DUPLICATE_OR_UPDATE`: near duplicate of an existing accepted evidence group/material.
- `ELIGIBLE_MATERIAL`: multi-source or context-supported durable knowledge with enough evidence and no risk/enrichment blockers.

Defaults:
- Single-message is not material.
- Single-source is not material.
- Job/event single-source is not material.
- Rules/onboarding never material.
- Risk/referral never guide/material.
- Link-only never material before enrichment.
- Model/pricing/provider claims require official or corroborating source.
"""
    safe_write_text(final_dir / "material_eligibility_policy.md", policy)
    groups = read_jsonl(paths.p(EVIDENCE_GROUPS_PATH)) if paths.p(EVIDENCE_GROUPS_PATH).exists() else []
    shortlist = [group for group in groups if group.get("preliminary_route_policy") == "ELIGIBLE_MATERIAL_REVIEW_ONLY_NOT_FINAL"]
    signals = [group for group in groups if group.get("preliminary_route_policy") in {"SIGNAL_ONLY", "AGGREGATE_ONLY", "NEEDS_LINK_ENRICHMENT", "MANUAL_REVIEW"}]
    write_csv(final_dir / "material_candidate_shortlist.csv", group_rows(shortlist), group_fieldnames())
    write_csv(final_dir / "rejected_but_retained_signals.csv", group_rows(signals), group_fieldnames())
    safe_write_text(final_dir / "next_automation_plan.md", """# Next Automation Plan

1. Finish marker, cluster, and gold labeling.
2. Tune deterministic routing until important false negatives in `REJECT_SAFE` are zero on the labeled sample.
3. Tune evidence grouping until duplicate collapse and cluster quality are acceptable by manual review.
4. Port only validated hard routes, evidence sufficiency rules, and pre-LLM admission logging to backend.
5. Do not port exploratory cluster names, aggressive normalization, optional algorithms, or generation/prompt changes until selection quality is proven.
""")


def group_rows(groups: list[dict[str, Any]]) -> list[dict[str, Any]]:
    rows = []
    for group in groups:
        rows.append({
            "group_id": group.get("group_id"),
            "source_count": group.get("source_count"),
            "independent_source_count": group.get("independent_source_count"),
            "claim_type": group.get("claim_type"),
            "risk_state": group.get("risk_state"),
            "link_enrichment_state": group.get("link_enrichment_state"),
            "duplicate_status": group.get("duplicate_status"),
            "preliminary_topic": group.get("preliminary_topic"),
            "preliminary_route_policy": group.get("preliminary_route_policy"),
            "raw_ids": ",".join(str(message.get("raw_id")) for message in group.get("messages", [])[:25]),
        })
    return rows


def group_fieldnames() -> list[str]:
    return ["group_id", "source_count", "independent_source_count", "claim_type", "risk_state", "link_enrichment_state", "duplicate_status", "preliminary_topic", "preliminary_route_policy", "raw_ids"]


def command_all(args: argparse.Namespace, paths: LabPaths) -> None:
    command_prepare(args, paths)
    command_normalize(args, paths)
    command_route(args, paths)
    command_context(args, paths)
    command_cluster(args, paths)
    command_cluster_review(args, paths)
    command_markers(args, paths)
    command_evidence(args, paths)
    command_gold_template(args, paths)
    command_evaluate(args, paths)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Offline Material Selection Lab")
    parser.add_argument("command", choices=["prepare", "normalize", "route", "context", "cluster", "cluster-review", "markers", "evidence", "gold-template", "evaluate", "all"])
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT, help="Fixed local snapshot/export path")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT, help="Output root under reports/material-selection-lab")
    parser.add_argument("--docs", type=Path, default=DEFAULT_DOCS, help="Lab docs directory")
    parser.add_argument("--marker-size", type=int, default=75, help="Messages per manual marker packet")
    parser.add_argument("--max-pairwise", type=int, default=600, help="Pairwise comparisons per clustering bucket")
    parser.add_argument("--top-clusters", type=int, default=40, help="Top clusters per algorithm for review")
    parser.add_argument("--per-route", type=int, default=150, help="Gold template sample size per route")
    parser.add_argument("--gold", type=Path, default=None, help="Completed gold labels CSV for evaluation")
    return parser


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()
    paths = LabPaths(args.out.resolve(), args.docs.resolve())
    if not str(paths.out).startswith(str((PROJECT_ROOT / "reports" / "material-selection-lab").resolve())):
        raise SystemExit("Refusing to write outside reports/material-selection-lab")
    if not str(paths.docs).startswith(str((PROJECT_ROOT / ".docs" / "research" / "pipeline" / "material-quality" / "lab").resolve())):
        raise SystemExit("Refusing to write outside .docs/research/pipeline/material-quality/lab")
    dispatch = {
        "prepare": command_prepare,
        "normalize": command_normalize,
        "route": command_route,
        "context": command_context,
        "cluster": command_cluster,
        "cluster-review": command_cluster_review,
        "markers": command_markers,
        "evidence": command_evidence,
        "gold-template": command_gold_template,
        "evaluate": command_evaluate,
        "all": command_all,
    }
    dispatch[args.command](args, paths)


if __name__ == "__main__":
    main()
