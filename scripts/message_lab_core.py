#!/usr/bin/env python3
"""Offline message classification and clustering primitives for NeuroInfoGrinder lab work.

This module is intentionally dependency-free and does not call production APIs,
providers, databases, or model workers. It is a calibration harness: once Adam is
happy with the behavior, the same contract can be ported into the application.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import re
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable


URL_RE = re.compile(r"https?://\S+", re.IGNORECASE)
DOMAIN_RE = re.compile(r"https?://(?:www\.)?([^/\s?#]+)", re.IGNORECASE)
WORD_RE = re.compile(r"[a-zа-яё0-9][a-zа-яё0-9_\-]{1,}", re.IGNORECASE)

RULES_RE = re.compile(
    r"\b(правила|приветствуем|добро\s+пожаловать|ознаком(ьтесь|ились)|подтвердите|"
    r"помощник\s+ом|путеводитель\s+по\s+сообществу|важно:\s*при\s+добавлении)\b",
    re.IGNORECASE,
)
ABUSE_RE = re.compile(
    r"\b(bin|cvv|carding|слив\s+карт|обход\s+лимит|фарм\s+аккаунт|"
    r"vpn\s*для\s*триал|sms\s*activation|подмен[аы]\s+номера|free\s*trial\s*bypass|"
    r"заабузить\s+доступ|абуз\s+доступ|abuse\s+access|exploit\s+access)\b",
    re.IGNORECASE,
)
RISK_PROMO_RE = re.compile(
    r"\b(ref=|ref_|start=ref|реферал|рефк|инвайт|партнерск|бесконечн\w+\s+подп|"
    r"получи\s+бесплатн|бесплатн\w+\s+токен|бесплатн\w+\s+модел|нитро|discord\s+boost)\b",
    re.IGNORECASE,
)
MODERATION_BOT_RE = re.compile(
    r"\b(lols\s+ban|заблокировал[аи]?|тебя\s+заблокировали|бан\b|разбан|"
    r"mute|мут\b|предупреждени[ея]|кикнут|удален[аы]?\s+из\s+чата)\b",
    re.IGNORECASE,
)
DIGEST_RE = re.compile(
    r"\b(дайджест|digest|новост[ьи]|релиз|анонс|вышел|запустил[аи]?|"
    r"представил[аи]?|обновлени[ея]|release\s+notes)\b",
    re.IGNORECASE,
)
MODEL_CLAIM_RE = re.compile(
    r"\b(sonnet\s*5|claude\s+fable|claude\s+mythos|opus\s*4\.8|gpt\s*-?\s*5\.5|"
    r"whatllm|цены\s+на\s+api|api-цен|стоимость\s+api|доступност[ьи]\s+модел|"
    r"заявленн\w+\s+параметр)\b",
    re.IGNORECASE,
)
EVENT_ANNOUNCEMENT_RE = re.compile(
    r"\b(доклад|митап|вебинар|конференц|мероприят|маршрут|экскурс|по\s+средам|"
    r"регистрация\s+на|анонс\s+встречи)\b",
    re.IGNORECASE,
)
GUIDE_RE = re.compile(
    r"\b(как\s+(проверить|сделать|настроить|запустить|починить)|чеклист|инструкция|"
    r"пошагов|шаг\s*\d+|что\s+делать|проверь(те)?\s+что)\b",
    re.IGNORECASE,
)
QNA_RE = re.compile(r"\?|\b(почему|зачем|что\s+если|как\s+понять|кто\s+знает)\b", re.IGNORECASE)
STATUS_RE = re.compile(
    r"\b(упал|не\s+работает|ошибка|outage|status|лимит|rate\s*limit|429|502|503|"
    r"недоступ|не\s+отвечает|зависает|таймаут)\b",
    re.IGNORECASE,
)
OUTAGE_STATUS_RE = re.compile(
    r"\b(401|403|404|429|500|502|503|504|unauthorized|forbidden|rate\s*limit|"
    r"timeout|таймаут|ошибка\s+доступа|не\s+коннект|не\s+подключ|"
    r"не\s+доступ|недоступ|не\s+работает|упал[ао]?|отвалил[ао]?сь)\b",
    re.IGNORECASE,
)
CODE_RE = re.compile(
    r"(```|/\*|\*/|\b(class|interface|public|private|async|await|select|insert|update|"
    r"docker|kubernetes|api|endpoint|json|yaml|java|python|typescript|react|spring|postgres|redis)\b)",
    re.IGNORECASE,
)
RESOURCE_RE = re.compile(
    r"\b(github|репозитор|библиотек|фреймворк|сервис|tool|инструмент|sdk|api|модель|"
    r"provider|router|claude|openai|gemini|qwen|deepseek|bge|mcp)\b",
    re.IGNORECASE,
)
SOCIAL_LINK_RE = re.compile(r"\b(youtube|youtu\.be|tiktok|instagram|x\.com|twitter|vk\.com|reddit)\b", re.IGNORECASE)
TELEGRAM_LINK_RE = re.compile(r"\b(t\.me|telegram\.me|telegram\.dog)\b", re.IGNORECASE)
REPO_RESOURCE_RE = re.compile(r"\b(github\.com|gitlab\.com|huggingface\.co|docs\.|documentation|репозитор|sdk|api\s+docs)\b", re.IGNORECASE)
JOB_RE = re.compile(r"\b(ищу\s+работу|ваканси|резюме|собеседован|стажк|hr\b|офер|зарплат)\b", re.IGNORECASE)
JOB_POST_RE = re.compile(r"\b(ваканси|ищем|нанимаем|резюме|hr\b|офер|зарплат|вилка\s+\d|директор\s+по\s+маркетинг)\b", re.IGNORECASE)
INTERVIEW_TASK_RE = re.compile(
    r"\b(собеседован|яндекс|авито|озон|тинькофф|java|постамат|mvp|api|состояни[ея]\s+заказ|"
    r"спроектировать|задач[аи]\s+с\s+собеседован)\b",
    re.IGNORECASE,
)
NOISE_RE = re.compile(
    r"^(да|нет|кто|изи|ах+|хах+|лол|понял|жду|ок|го|\+\+?|\-\-?|спасибо|привет)$",
    re.IGNORECASE,
)


STOPWORDS = {
    "это", "как", "что", "если", "или", "для", "так", "там", "тут", "уже", "еще", "ещё",
    "надо", "можно", "будет", "быть", "меня", "тебя", "все", "всё", "они", "она", "оно",
    "the", "and", "for", "with", "from", "this", "that", "you", "are", "was", "were",
    "http", "https", "com", "www",
}


@dataclass
class MessageRecord:
    id: str
    text: str
    chat: str = ""
    topic: str = ""
    created_at: str = ""
    raw: dict[str, Any] = field(default_factory=dict)


@dataclass
class Classification:
    primary_class: str
    material_route: str
    material_type: str
    action: str
    confidence: float
    risk: str
    reasons: list[str]
    features: dict[str, Any]

    def to_dict(self) -> dict[str, Any]:
        return {
            "primaryClass": self.primary_class,
            "materialRoute": self.material_route,
            "materialType": self.material_type,
            "action": self.action,
            "confidence": round(self.confidence, 3),
            "risk": self.risk,
            "reasons": self.reasons,
            "features": self.features,
        }


def normalize_text(text: str) -> str:
    text = (text or "").replace("\u00a0", " ")
    text = re.sub(r"\s+", " ", text).strip().lower()
    return text


def text_preview(text: str, limit: int = 220) -> str:
    text = re.sub(r"\s+", " ", text or "").strip()
    return text[:limit]


def tokenize(text: str) -> list[str]:
    tokens = [match.group(0).lower() for match in WORD_RE.finditer(text or "")]
    return [token for token in tokens if token not in STOPWORDS]


def stable_id(text: str) -> str:
    return hashlib.sha1((text or "").encode("utf-8")).hexdigest()[:12]


def text_features(text: str) -> dict[str, Any]:
    normalized = normalize_text(text)
    tokens = tokenize(normalized)
    urls = URL_RE.findall(text or "")
    domains = [match.group(1).lower() for match in DOMAIN_RE.finditer(text or "")]
    return {
        "length": len(normalized),
        "tokenCount": len(tokens),
        "urlCount": len(urls),
        "hasUrl": bool(urls),
        "hasCode": bool(CODE_RE.search(text or "")),
        "hasRules": bool(RULES_RE.search(normalized)),
        "hasAbuse": bool(ABUSE_RE.search(normalized)),
        "hasRiskPromo": bool(RISK_PROMO_RE.search(normalized) or any(ref_marker in url.lower() for url in urls for ref_marker in ("ref=", "ref_", "start=ref"))),
        "hasModerationBotEvent": bool(MODERATION_BOT_RE.search(normalized)),
        "hasDigest": bool(DIGEST_RE.search(normalized)),
        "hasModelClaim": bool(MODEL_CLAIM_RE.search(normalized)),
        "hasEventAnnouncement": bool(EVENT_ANNOUNCEMENT_RE.search(normalized)),
        "hasGuideIntent": bool(GUIDE_RE.search(normalized)),
        "hasQuestion": bool(QNA_RE.search(normalized)),
        "hasStatus": bool(STATUS_RE.search(normalized)),
        "hasOutageStatus": bool(OUTAGE_STATUS_RE.search(normalized)),
        "hasResource": bool(RESOURCE_RE.search(normalized)),
        "hasSocialLink": bool(SOCIAL_LINK_RE.search(normalized) or any(SOCIAL_LINK_RE.search(domain) for domain in domains)),
        "hasTelegramLink": bool(TELEGRAM_LINK_RE.search(normalized) or any(TELEGRAM_LINK_RE.search(domain) for domain in domains)),
        "hasRepoResourceLink": bool(REPO_RESOURCE_RE.search(normalized) or any(REPO_RESOURCE_RE.search(domain) for domain in domains)),
        "hasJobSignal": bool(JOB_RE.search(normalized)),
        "hasJobPost": bool(JOB_POST_RE.search(normalized)),
        "isShortNoise": bool(NOISE_RE.match(normalized)) or len(tokens) <= 2,
        "urls": urls,
        "domains": domains,
        "tokens": tokens,
    }


def classify_text(text: str, *, chat: str = "", topic: str = "") -> Classification:
    features = text_features(text)
    combined_context = normalize_text(" ".join([text or "", chat or "", topic or ""]))
    features["hasInterviewTask"] = bool(INTERVIEW_TASK_RE.search(combined_context))
    reasons: list[str] = []
    risk = "LOW"
    primary = "LOW_VALUE_CHATTER"
    route = "NO_MATERIAL"
    material_type = "NONE"
    action = "DROP"
    confidence = 0.5

    if not normalize_text(text):
        return Classification(
            "MEDIA_ONLY_OR_EMPTY",
            "RETAIN_CONTEXT_ONLY",
            "NONE",
            "RETAIN_CONTEXT",
            0.9,
            "LOW",
            ["empty_text"],
            {k: v for k, v in features.items() if k != "tokens"},
        )

    if features["hasRules"]:
        return Classification(
            "RULES_ONBOARDING",
            "HARD_NO_MATERIAL",
            "NONE",
            "DROP_OR_SUPPRESS",
            0.96,
            "LOW",
            ["rules_or_onboarding_template"],
            {k: v for k, v in features.items() if k != "tokens"},
        )

    if features["hasModerationBotEvent"]:
        return Classification(
            "MODERATION_BOT_EVENT",
            "HARD_NO_MATERIAL",
            "NONE",
            "DROP_OR_SUPPRESS",
            0.93,
            "LOW",
            ["moderation_or_ban_bot_event"],
            {k: v for k, v in features.items() if k != "tokens"},
        )

    if features["hasJobPost"] and not features["hasInterviewTask"]:
        return Classification(
            "CAREER_JOB_POST",
            "RETAIN_CONTEXT_ONLY",
            "NONE",
            "RETAIN_CONTEXT",
            0.82,
            "LOW",
            ["job_or_career_post_not_material"],
            {k: v for k, v in features.items() if k != "tokens"},
        )

    if features["hasEventAnnouncement"] and not features["hasGuideIntent"]:
        return Classification(
            "EVENT_ANNOUNCEMENT",
            "RETAIN_CONTEXT_ONLY",
            "NONE",
            "RETAIN_CONTEXT",
            0.8,
            "LOW",
            ["event_or_meetup_announcement_not_material"],
            {k: v for k, v in features.items() if k != "tokens"},
        )

    if features["hasModelClaim"]:
        return Classification(
            "MODEL_RUMOR_OR_PRICING_CLAIM",
            "RETAIN_CONTEXT_ONLY",
            "NONE",
            "RETAIN_CONTEXT_UNTIL_OFFICIAL_SOURCE",
            0.84,
            "MEDIUM",
            ["model_pricing_or_availability_claim_requires_source"],
            {k: v for k, v in features.items() if k != "tokens"},
        )

    if features["hasAbuse"]:
        return Classification(
            "ABUSE_OR_CIRCUMVENTION",
            "MANUAL_REVIEW_ONLY",
            "NONE",
            "BLOCK_MATERIAL_REVIEW_RISK",
            0.94,
            "HIGH",
            ["abuse_or_access_circumvention_terms"],
            {k: v for k, v in features.items() if k != "tokens"},
        )

    if features["hasRiskPromo"]:
        primary = "REFERRAL_OR_INVITE_LINK" if features["hasUrl"] else "RISK_PROMO_REFERRAL"
        route = "SIGNAL_ONLY_RISK_REVIEW"
        action = "STORE_SIGNAL_NO_MATERIAL"
        risk = "MEDIUM"
        confidence = 0.86
        reasons.append("risk_promo_or_referral_marker")
        if features["hasUrl"]:
            reasons.append("link_requires_enrichment")
        return Classification(primary, route, material_type, action, confidence, risk, reasons, {k: v for k, v in features.items() if k != "tokens"})

    if features["hasUrl"] and features["tokenCount"] <= 8:
        link_class = "LINK_SHARE"
        reasons = ["link_only_or_link_thin_context"]
        confidence = 0.88
        if features["hasRepoResourceLink"]:
            link_class = "RESOURCE_LINK"
            reasons.append("repo_or_docs_domain")
            confidence = 0.86
        elif features["hasSocialLink"]:
            link_class = "SOCIAL_MEDIA_LINK"
            reasons.append("social_media_domain")
            confidence = 0.84
        elif features["hasTelegramLink"]:
            link_class = "INTERNAL_TELEGRAM_LINK"
            reasons.append("telegram_link_domain")
            confidence = 0.84
        return Classification(
            link_class,
            "NEEDS_LINK_ENRICHMENT",
            "NONE",
            "ENRICH_LINK_THEN_REVIEW",
            confidence,
            "LOW",
            reasons,
            {k: v for k, v in features.items() if k != "tokens"},
        )

    score = 0.0
    if features["hasGuideIntent"]:
        score += 0.35
        material_type = "GUIDE"
        reasons.append("guide_intent")
    if features["hasCode"]:
        score += 0.25
        reasons.append("technical_or_code_terms")
    if features["hasResource"]:
        score += 0.18
        if material_type == "NONE":
            material_type = "REFERENCE"
        reasons.append("resource_or_tool_terms")
    if features["hasStatus"]:
        score += 0.18
        if material_type == "NONE":
            material_type = "SUMMARY"
        reasons.append("status_or_outage_terms")
    if features["hasOutageStatus"]:
        score += 0.16
        if material_type == "NONE":
            material_type = "SUMMARY"
        reasons.append("concrete_outage_or_error_status")
    if features["hasQuestion"]:
        score += 0.08
        if material_type == "NONE":
            material_type = "ANSWER"
        reasons.append("question_or_qna_shape")
    if features["hasJobSignal"]:
        score += 0.06
        reasons.append("career_or_interview_signal")
    if features["hasInterviewTask"] and (features["tokenCount"] >= 18 or features["hasCode"] or features["hasResource"]):
        score += 0.26
        if material_type == "NONE":
            material_type = "GUIDE"
        reasons.append("interview_or_product_design_task")
    if features["tokenCount"] >= 45:
        score += 0.16
        reasons.append("substantial_text")
    elif features["tokenCount"] >= 20:
        score += 0.08
        reasons.append("moderate_text")

    if score >= 0.55 or (score >= 0.45 and (features["hasCode"] or features["hasResource"]) and features["tokenCount"] >= 18):
        if features["hasOutageStatus"] and (features["hasCode"] or features["hasResource"]):
            primary = "OUTAGE_STATUS"
        elif features["hasDigest"] and not features["hasGuideIntent"]:
            primary = "DIGEST_NEWS"
        else:
            primary = "TECH_SIGNAL" if (features["hasCode"] or features["hasResource"] or features["hasGuideIntent"]) else "POTENTIAL_DISCUSSION_SIGNAL"
        route = "REVIEW_SIGNAL_OR_DISCUSSION"
        action = "REVIEW_FOR_CLUSTER_OR_MATERIAL"
        confidence = min(0.95, 0.58 + score / 2)
        if material_type == "NONE":
            material_type = "SUMMARY"
    elif score >= 0.32:
        primary = "WEAK_SIGNAL"
        route = "RETAIN_CONTEXT_ONLY"
        material_type = "NONE"
        action = "RETAIN_CONTEXT"
        confidence = 0.62
    elif features["isShortNoise"]:
        primary = "LOW_VALUE_CHATTER"
        route = "NO_MATERIAL"
        material_type = "NONE"
        action = "DROP"
        confidence = 0.9
        reasons.append("short_or_ack_noise")
    else:
        primary = "LOW_VALUE_CHATTER"
        route = "NO_MATERIAL"
        material_type = "NONE"
        action = "DROP"
        confidence = 0.72
        reasons.append("no_material_evidence")

    if not reasons:
        reasons.append("default_classification")

    return Classification(primary, route, material_type, action, confidence, risk, reasons, {k: v for k, v in features.items() if k != "tokens"})


def semantic_pool(record: MessageRecord, classification: Classification) -> str:
    primary = classification.primary_class
    route = classification.material_route
    if primary in {"RULES_ONBOARDING", "MODERATION_BOT_EVENT"} or route == "HARD_NO_MATERIAL":
        return "blocked_templates"
    if primary in {"ABUSE_OR_CIRCUMVENTION"} or route == "MANUAL_REVIEW_ONLY":
        return "manual_risk"
    if primary in {"RISK_PROMO_REFERRAL", "REFERRAL_OR_INVITE_LINK"} or route == "SIGNAL_ONLY_RISK_REVIEW":
        return "risk_links"
    if primary in {"LINK_SHARE", "RESOURCE_LINK", "SOCIAL_MEDIA_LINK", "INTERNAL_TELEGRAM_LINK"} or route == "NEEDS_LINK_ENRICHMENT":
        return "links"
    if primary in {"TECH_SIGNAL", "OUTAGE_STATUS", "DIGEST_NEWS", "POTENTIAL_DISCUSSION_SIGNAL"} or route == "REVIEW_SIGNAL_OR_DISCUSSION":
        return "review_candidates"
    if primary in {"CAREER_JOB_POST", "EVENT_ANNOUNCEMENT", "MODEL_RUMOR_OR_PRICING_CLAIM"}:
        return "context"
    if route == "RETAIN_CONTEXT_ONLY" or primary == "WEAK_SIGNAL":
        return "context"
    return "noise"


def load_messages(path: Path) -> list[MessageRecord]:
    suffix = path.suffix.lower()
    if suffix == ".jsonl":
        rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    elif suffix == ".json":
        data = json.loads(path.read_text(encoding="utf-8"))
        rows = extract_rows_from_json(data)
    elif suffix == ".csv":
        with path.open("r", encoding="utf-8-sig", newline="") as handle:
            rows = list(csv.DictReader(handle))
    else:
        text = path.read_text(encoding="utf-8")
        rows = [{"text": block.strip()} for block in re.split(r"\n\s*---\s*\n", text) if block.strip()]
    return [row_to_message(row, index) for index, row in enumerate(rows, start=1)]


def extract_rows_from_json(data: Any) -> list[dict[str, Any]]:
    if isinstance(data, list):
        return data
    if isinstance(data, dict):
        for key in ("messages", "rawMessages", "rawRows", "items", "rows"):
            value = data.get(key)
            if isinstance(value, list):
                return value
        # Daily audit snapshots keep rich messages inside rawClusters.
        clusters = data.get("rawClusters")
        if isinstance(clusters, list):
            rows: list[dict[str, Any]] = []
            for cluster in clusters:
                for message in cluster.get("messages", []) or []:
                    row = dict(message)
                    row.setdefault("clusterId", cluster.get("id"))
                    rows.append(row)
            if rows:
                return rows
    raise ValueError("Unsupported JSON shape; expected list or object with messages/rawMessages/rawClusters")


def row_to_message(row: dict[str, Any], index: int) -> MessageRecord:
    text = first_present(row, "text", "message", "preview", "caption", "body", "content") or ""
    message_id = first_present(row, "id", "rawId", "raw_id", "messageId") or stable_id(text) or str(index)
    chat = first_present(row, "chat", "chatTitle", "sourceChat", "group", "channel") or ""
    topic = first_present(row, "topic", "topicTitle", "sourceTopic", "thread") or ""
    created_at = first_present(row, "createdAt", "createdAtMsk", "messageDate", "date", "timestamp") or ""
    return MessageRecord(str(message_id), str(text), str(chat), str(topic), str(created_at), row)


def first_present(row: dict[str, Any], *keys: str) -> Any:
    for key in keys:
        value = row.get(key)
        if value not in (None, ""):
            return value
    return None


def weighted_tokens(record: MessageRecord, classification: Classification) -> set[str]:
    tokens = set(tokenize(record.text))
    tokens.update(tokenize(record.chat)[:4])
    tokens.update(tokenize(record.topic)[:6])
    if classification.primary_class not in {"LOW_VALUE_CHATTER", "MEDIA_ONLY_OR_EMPTY"}:
        tokens.add(classification.primary_class.lower())
        tokens.add(semantic_pool(record, classification))
    for domain in classification.features.get("domains", []) or []:
        tokens.add(domain)
    return tokens


def jaccard(a: set[str], b: set[str]) -> float:
    if not a or not b:
        return 0.0
    return len(a & b) / len(a | b)


def char_ngrams(text: str, n: int = 5) -> set[str]:
    normalized = normalize_text(text)
    if len(normalized) <= n:
        return {normalized} if normalized else set()
    return {normalized[i : i + n] for i in range(0, len(normalized) - n + 1)}


def similarity(a: MessageRecord, b: MessageRecord, ca: Classification, cb: Classification) -> float:
    token_sim = jaccard(weighted_tokens(a, ca), weighted_tokens(b, cb))
    char_sim = jaccard(char_ngrams(a.text), char_ngrams(b.text))
    same_chat = 0.08 if a.chat and a.chat == b.chat else 0.0
    same_topic = 0.08 if a.topic and a.topic == b.topic else 0.0
    same_class = 0.12 if ca.primary_class == cb.primary_class else 0.0
    return min(1.0, (0.55 * token_sim) + (0.25 * char_sim) + same_chat + same_topic + same_class)


def cluster_messages(
    records: list[MessageRecord],
    classifications: dict[str, Classification],
    threshold: float = 0.34,
    max_pairwise: int = 600,
) -> list[dict[str, Any]]:
    parent = {record.id: record.id for record in records}

    def find(x: str) -> str:
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    def union(a: str, b: str) -> None:
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[rb] = ra

    buckets: dict[str, list[MessageRecord]] = defaultdict(list)
    for record in records:
        classification = classifications[record.id]
        bucket = f"{classification.primary_class}|{record.chat.lower()}|{record.topic.lower()}"
        buckets[bucket].append(record)

    for bucket_records in buckets.values():
        sample = bucket_records[:max_pairwise]
        for i, left in enumerate(sample):
            for right in sample[i + 1 :]:
                score = similarity(left, right, classifications[left.id], classifications[right.id])
                if score >= threshold:
                    union(left.id, right.id)

    grouped: dict[str, list[MessageRecord]] = defaultdict(list)
    for record in records:
        grouped[find(record.id)].append(record)

    clusters = [summarize_cluster(index, rows, classifications) for index, rows in enumerate(grouped.values(), start=1)]
    return sorted(clusters, key=lambda item: (-item["qualityScore"], -item["size"], item["id"]))


def summarize_cluster(index: int, records: list[MessageRecord], classifications: dict[str, Classification]) -> dict[str, Any]:
    classes = Counter(classifications[record.id].primary_class for record in records)
    routes = Counter(classifications[record.id].material_route for record in records)
    material_types = Counter(classifications[record.id].material_type for record in records if classifications[record.id].material_type != "NONE")
    risks = Counter(classifications[record.id].risk for record in records)
    chats = Counter(record.chat or "unknown" for record in records)
    topics = Counter(record.topic or "main" for record in records)
    useful = sum(1 for record in records if classifications[record.id].material_route == "REVIEW_SIGNAL_OR_DISCUSSION")
    blocked = sum(1 for record in records if classifications[record.id].material_route in {"HARD_NO_MATERIAL", "MANUAL_REVIEW_ONLY"})
    link_enrichment = sum(1 for record in records if classifications[record.id].material_route == "NEEDS_LINK_ENRICHMENT")
    context_only = sum(1 for record in records if classifications[record.id].material_route == "RETAIN_CONTEXT_ONLY")
    source_diversity = len(chats)
    avg_confidence = sum(classifications[record.id].confidence for record in records) / max(1, len(records))
    quality_score = (useful * 1.0) + (link_enrichment * 0.35) + (context_only * 0.1) + min(source_diversity, 3) * 0.15 - (blocked * 0.6)
    if len(records) == 1 and useful:
        quality_score -= 0.25
    decision = "NO_MATERIAL"
    if blocked and blocked == len(records):
        decision = "BLOCKED"
    elif useful >= 2 and len(records) >= 2:
        decision = "REVIEW_CLUSTER_FOR_MATERIAL"
    elif useful == 1:
        decision = "REVIEW_SINGLE_SIGNAL"
    elif link_enrichment:
        decision = "LINK_ENRICHMENT_FIRST"
    elif context_only:
        decision = "RETAIN_CONTEXT"

    return {
        "id": f"lab-cluster-{index}",
        "size": len(records),
        "decision": decision,
        "qualityScore": round(quality_score, 3),
        "avgConfidence": round(avg_confidence, 3),
        "classes": dict(classes.most_common()),
        "routes": dict(routes.most_common()),
        "materialTypes": dict(material_types.most_common()),
        "risks": dict(risks.most_common()),
        "sourceChats": dict(chats.most_common(5)),
        "sourceTopics": dict(topics.most_common(5)),
        "sampleMessageIds": [record.id for record in records[:12]],
        "samples": [
            {
                "id": record.id,
                "createdAt": record.created_at,
                "chat": record.chat,
                "topic": record.topic,
                "class": classifications[record.id].primary_class,
                "route": classifications[record.id].material_route,
                "preview": text_preview(record.text, 260),
            }
            for record in records[:20]
        ],
    }


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def markdown_table(rows: list[list[Any]], headers: list[str]) -> str:
    lines = ["| " + " | ".join(headers) + " |", "|" + "|".join("---" for _ in headers) + "|"]
    for row in rows:
        escaped = [str(value).replace("|", "\\|").replace("\n", " ") for value in row]
        lines.append("| " + " | ".join(escaped) + " |")
    return "\n".join(lines)


def add_common_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--output-json", type=Path, default=None, help="Write machine-readable JSON output")
    parser.add_argument("--output-md", type=Path, default=None, help="Write Markdown report output")
