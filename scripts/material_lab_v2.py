#!/usr/bin/env python3
"""Offline Material Selection Lab v2 for NeuroInfoGrinder 2.0.

Conversation-first clustering with strong-entity merge.

Reads a fixed local rich snapshot and writes auditable lab artifacts only under
reports/material-selection-lab-v2. It never calls production APIs, databases,
providers, model workers, deploys, or generation endpoints.

Key differences vs v1:
- Rich snapshot includes reply_to_message_id, sender_id, sender_name, raw_json.
- Clustering is conversation-first (chat+thread, reply chain, time window),
  then cross-conversation merge ONLY by strong shared entity.
- size-2 clusters are SIGNAL_PAIR / WEAK_PAIR, not material candidates.
- independent_source_count uses sender_id, not chat count.
- Cluster names come from shared strong entities (Russian), not broad classes.
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
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Iterable

from message_lab_core import (
    DOMAIN_RE,
    URL_RE,
    classify_text,
    normalize_text as core_normalize_text,
    text_preview,
    tokenize,
)


PROJECT_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_INPUT = PROJECT_ROOT / "reports" / "material-selection-lab-v2" / "data" / "raw_messages_20k_rich.jsonl"
DEFAULT_OUT = PROJECT_ROOT / "reports" / "material-selection-lab-v2"
DEFAULT_DOCS = PROJECT_ROOT / ".docs" / "research" / "pipeline" / "material-quality" / "lab"

USERNAME_RE = re.compile(r"@[a-zA-Z0-9_]{4,}")
ERROR_CODE_RE = re.compile(r"(?<![\d.])(?:HTTP\s*)?(?:400|401|403|404|408|409|422|429|500|502|503|504|522|524)(?![\d.])", re.IGNORECASE)
ERROR_CONTEXT_RE = re.compile(r"\b(?:ошибк|error|status|упал|не\s+работает|timeout|таймаут|лимит|rate\s*limit|недоступ|forbidden|unauthorized|bad\s*gateway|отвалил|не\s+коннект|не\s+подключ)\b", re.IGNORECASE)
PRICE_RE = re.compile(r"(?:[$€₽]\s?\d+(?:[.,]\d+)?|\b\d+(?:[.,]\d+)?\s?(?:usd|eur|руб|р\.?|₽|токен|tokens?)\b)", re.IGNORECASE)
# Code presence. Ambiguous English words (public/private/select/update/insert/function/class/def/interface)
# are only matched with code context so prose like "Build in public" or "select your plan" does not flip
# a non-technical marketing post into a guide candidate.
CODE_RE = re.compile(r"(```|\b(?:curl|npm|pip|docker|kubectl|async|await|cargo|gradle|mvn|gcc)\b|class\s+\w+\s*[{:]|interface\s+\w+\s*[{:]|def\s+\w+\s*\(|function\s+\w+\s*\(|public\s+(?:class|static|interface|final|void|protected|override|readonly)|private\s+(?:class|static|final|void|protected|readonly)|\b(?:select|insert|update)\s+\w[^.\n]{0,80}?(?:from|into|set)\b)", re.IGNORECASE)
PROFANITY_RE = re.compile(r"\b(?:хуй|хуя|пизд|еба|ёба|бля|сука|shit|fuck)\w*\b", re.IGNORECASE)
FILLER_RE = re.compile(r"\b(?:лол|кек|ах+|хах+|ну|типа|короче|кнч|имхо|ок|спс|ага|угу|да|нет)\b", re.IGNORECASE)
EMOJI_RE = re.compile("[\U0001F300-\U0001FAFF\u2600-\u27BF]")
SHORT_ACK_RE = re.compile(r"^(?:да|нет|тоже|у меня тоже|работает|не работает|ок|ага|угу|спасибо|жду|печально|мощни|лол|кек|\+1|\+)$", re.IGNORECASE)
OFFICIAL_DOMAIN_RE = re.compile(r"(?:openai\.com|anthropic\.com|google\.com|ai\.google|mistral\.ai|github\.com|docs\.|documentation)", re.IGNORECASE)

# Technical domains that may anchor a single-message guide/reference.
# Generic e-commerce/shopping/content domains (rozetka, amiami, etc.) are NOT technical
# and must not by themselves qualify a shopping/recommendation list as a material guide.
TECHNICAL_DOMAINS = {
    "github.com", "gitlab.com", "stackoverflow.com", "npmjs.com", "pypi.org",
    "huggingface.co", "arxiv.org", "developer.mozilla.org", "kaggle.com",
    "docker.com", "docs.docker.com", "crates.io", "rubygems.org", "mvnrepository.com",
    "cve.org", "nvd.nist.gov", "kernel.org", "python.org",
}


def has_technical_entity(features: dict[str, Any]) -> bool:
    """True if the message carries a technical anchor: model/tool/api/code/api-term/strong-error/technical-domain.

    A bare e-commerce or content domain (shopping/news) does NOT count, so a single-source
    shopping/recommendation list cannot become a material guide just because it lists store URLs.
    """
    if features.get("model_names") or features.get("tools") or features.get("apis"):
        return True
    if features.get("has_code") or features.get("has_api_terms"):
        return True
    if features.get("error_codes") and features.get("has_error_code"):
        # error codes only anchor when there is real error context (checked at extract time);
        # require at least one non-generic technical signal alongside
        if features.get("has_code") or features.get("has_api_terms") or features.get("model_names") or features.get("tools"):
            return True
    domains = set(features.get("domains") or [])
    if domains & TECHNICAL_DOMAINS:
        return True
    return False

# Strong entities: specific enough to merge messages across conversations.
STRONG_MODEL_RE = re.compile(r"\b(?:gpt[-\s]?(?:4o|5|5\.5)|claude\s*code|codex|cursor|sonnet|opus|haiku|gemini|qwen|deepseek|llama|mistral|bge[-\s]?m3|o3|o4|hermes|droid|zcode)\b", re.IGNORECASE)
STRONG_TOOL_RE = re.compile(r"\b(?:claude\s*code|codex|cursor|zcode|hermes|droid|r[-\s]?api|vibemod|bynara|graph\s*api|responses[-\s]?api|messages[-\s]?api|openai[-\s]?compatible|codebase-memory-mcp|\w+-mcp|\bmcp\b|cline|clinepass|vimit|vibecraft|omnirouter)\b", re.IGNORECASE)
STRONG_API_RE = re.compile(r"\b(?:graph\s*api|openai[-\s]?compatible|responses[-\s]?api|messages[-\s]?api|webhook|oauth)\b", re.IGNORECASE)
# Weak entities: too generic to merge across conversations alone.
WEAK_ENTITY_RE = re.compile(r"\b(?:api|gpt|github|java|solid|faang|модель|инструмент|код|бот|токен|лимит|ошибка|сервис|сервер|база|данные|функция|класс)\b", re.IGNORECASE)

GUARD_RE = {
    "RULES_ONBOARDING": re.compile(r"(добро\s+пожаловать|приветствуем|ознакомьтесь\s+с\s+правилам|ознакомились\s+с\s+правилам|подтвердите.*правил|правила\s+(сообщества|чата|группы)|перед\s+тем\s+как\s+писать|путеводитель\s+по\s+сообществу|chatkeeper|lolsbot|помощник\s+о[мн])", re.IGNORECASE),
    "TEST_ARTIFACT": re.compile(r"\b(?:NIGTEST|NIGTOP|ZAUR)[-\s]*[A-Z]\d+\b|\[NIGTEST[^\]]*\]|\[NIGTOP[^\]]*\]", re.IGNORECASE),
    "MODERATION_BOT_EVENT": re.compile(r"\b(lols\s+ban|заблокировал|бан\b|разбан|mute|мут\b|предупреждени|кикнут)\b", re.IGNORECASE),
    "RISK_PROMO_REFERRAL": re.compile(r"(ref=|ref_|start=ref|реферал\w*|рефк\w*|инвайт\w*|партнерск\w*|бесконечн\w+\s+подп|получи\s+бесплатн\w*|бесплатн\w+\s+токен\w*|тестов\w+\s+токен\w*|100\s?\$\s+за\s+регистрац\w*|бесплатн\w+\s+100\s?\$|залетаем.*заявк\w*|senpi|hyperliquid.*100\s?\$|airdrop|presale|пресейл|мемкоин|discord\s+boost|no\s+card|temp\s*email|unlimited\s+accounts|free\s+access|бесплатн\w+\s+доступ\b|без\s+карты|халявн\w+\s+(?:токен|нейронк|api|подп|доступ|аккаунт)|бесплатн\w+\s+нейронк\w*|бесконечн\w+\s+(?:нейронк|токен|api|подп|аккаунт)|до\s+конца\s+жизни|полтора\s+доллар|доллар\w*\s+за\s+(?:труд|регистр|подарк)|(?:получи|даю|начисля)\s+\d+\s+кредит|получ\w+\s+\d+\s+кредит|\d+\s+кредитов\s+за\s+регистр|free\s+credits?|бесплатн\w+\s+кредит)", re.IGNORECASE),
    "ABUSE_OR_FRAUD": re.compile(r"\b(bin|cvv|carding|слив\s+карт|обход\s+лимит|фарм\s+аккаунт|sms\s*activation|free\s*trial\s*bypass|abuse\s+access|exploit\s+access|temp\s*email|temp\s*mail|temporam|10minutemail|guerrillamail|tempmail|временн\w+\s+(?:почт|email|mail)|одноразов\w+\s+(?:почт|email|mail)|no\s+card|unlimited\s+accounts)\b", re.IGNORECASE),
    "JOB_POST": re.compile(r"\b(ваканси\w*|ищем\w*|нанима\w*|резюме|hr\b|офер\w*|зарплат\w*|вилка\s+\d|#ваканси\w*|#работ\w*)", re.IGNORECASE),
    "EVENT_ANNOUNCEMENT": re.compile(r"\b(митап\w*|вебинар\w*|конференц\w*|доклад\w*|регистрация\s+на|анонс\s+встречи|мероприят\w*)\b", re.IGNORECASE),
}

CLAIM_INDICATOR_RE = re.compile(r"\b(бесплатн\w*|доступен|доступн\w*|free\s*access|claim|выпустил|запустил|релиз\w*|цена|стоимость|токеномик|пресейл|дропнул|drop|представил|анонс\w*|тариф\w*|прайс|подписк\w*)\b", re.IGNORECASE)
TECH_PROBLEM_RE = re.compile(r"\b(проблем\w*|не\s+работает|обрезает\w*|слетел\w*|сломал\w*|ломается|ошибк\w*|сталкивал\w*|баг\w*|отвалил\w*|не\s+коннект|реконнект\w*|упал\w*|зависает|тормозит|не\s+отвечает|вшив\w*|спайвар|подлог)\b", re.IGNORECASE)
QUESTION_RE = re.compile(r"\?|\b(почему|зачем|как\s+понять|кто\s+знает|кто-нибудь|сталкивал|можно\s+ли)\b", re.IGNORECASE)
HOWTO_RE = re.compile(r"\b(как\s+(проверить|сделать|настроить|запустить|починить|перенести|подключить|использовать|получить|установить|развернуть|собрать|запустить)|инструкц\w*|чеклист|пошагов|гайд\b|tutorial|что\s+нужно\s+сделать|шаг\s*\d|step\s*\d)\b", re.IGNORECASE)
GUIDE_STRUCTURE_RE = re.compile(r"(###\s*\d|вариант\w*|способы\b|лучшие\b|проверенн\w*|надежн\w*|1\.\s|2\.\s|3\.\s|→\s|\bизучи\b|\bопредели\b|\bпроверь\b|\bустанови\b|\bнастрой\b|\bзапусти\b|\bскачай\b)", re.IGNORECASE)
# Non-material long-form content that must NOT become a guide candidate even if it has numbered structure.
NON_MATERIAL_LONG_RE = re.compile(r"(системн\w*\s+промпт|\bты\s+—\s+\w+|твоя\s+идентичн|твоя\s+задача|идентичность|игров\w+\s+сценар|roleplay|представь\s+что|метод\s+«|вот\s+тебе\s+(топ|три|3)|base64|jpeg\s+image|file\s+signatures|transcription\s+of\s+the|database\s+schema|atomic\s+numbers|химическ|осталось\s+[\d.,]+\s+час|эфир\s+№|запустили\s+серию|пресс-релиз|you\s+are\s+a|as\s+an\s+ai|i\s+don'?t\s+have\s+access\s+to\s+your|я\s+не\s+имею\s+доступа\s+к\s+вашем|я\s+не\s+могу\s+открыть|ключевые\s+темы\s+чата|короткий\s+обзор|по\s+(сохраненн|найденн)|обзор\s+по\s+(найденн|сохраненн)|о\s+чем\s+канал|инструкц\w*\s+для\s+новоприбывш|объявляем\s+конкурс|запустил\s+сервис|анонс\w*|дайджест|итоги\s+розыгрыш|таймкоды\s+со\s+всеми|обзор\s+сообщений|summary\s+за|саммари\s+дня|разбор\s+твоего|обобщающ\w+\s+пост|мысли\s+из\s+стат|не\s+дословно|обзор\s+стат|мотивационн\w+\s+(пост|стат)|plane\s+crashed|passengers?\s+survived|struggling\s+to\s+survive|survival\s+(scenario|situation|story|mode)|role\s*-?play\s+(scenario|story|game|mode)|how\s+to\s+make\s+(guns|weapons|drugs)|survivors?\s+come\s+together|cut\s+off\s+from\s+society|narrative\s+scenario|краткий\s+пересказ|если\s+коротко|коротко:\s|таймкоды:\s*\d|сожалею,\s+что\s+(?:текущ|работ|интеграц|возник)|я\s+не\s+могу\s+(?:помочь|выполнить|предоставить|сгенерировать|подсказать|дать)|краткий\s+обзор\s+(?:того|чата)|пересказ\s+(?:того|чата|сообщений))", re.IGNORECASE)

FIRST_PASS_ROUTE_MAP = {
    "HARD_NO_MATERIAL": "REJECT_SAFE",
    "NO_MATERIAL": "CONTEXT_ONLY",
    "RETAIN_CONTEXT_ONLY": "CONTEXT_ONLY",
    "NEEDS_LINK_ENRICHMENT": "NEEDS_ENRICHMENT",
    "SIGNAL_ONLY_RISK_REVIEW": "MANUAL_REVIEW",
    "MANUAL_REVIEW_ONLY": "MANUAL_REVIEW",
    "REVIEW_SIGNAL_OR_DISCUSSION": "REVIEW_HIGH_RECALL",
}

ROUTE_ORDER = ["REJECT_SAFE", "MANUAL_REVIEW", "NEEDS_ENRICHMENT", "AGGREGATE_ONLY", "SIGNAL_ONLY", "REVIEW_HIGH_RECALL", "CONTEXT_ONLY"]

ROUTE_FIELDS = [
    "lab_message_id", "snapshot_id", "raw_id", "dataset_message_id", "account_id", "telegram_message_id",
    "conversation_key", "chat_id", "chat_title", "thread_id", "topic_id", "topic_title",
    "reply_to_message_id", "has_reply", "reply_resolved", "sender_id", "sender_name", "sender_is_bot",
    "timestamp", "primary_class", "classifier_route", "route", "route_reason", "risk",
    "material_type_hint", "confidence", "marker_id", "strong_entities", "weak_entities",
    "domains", "model_names", "error_codes", "length_tokens", "has_link", "hidden_url_count", "has_api_terms", "has_code", "preview",
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


def extract_raw_text(row: dict[str, Any]) -> str:
    for key in ("text", "message", "preview", "caption", "body", "content"):
        value = row.get(key)
        if value not in (None, ""):
            return str(value)
    return ""


def conversation_key(row: dict[str, Any]) -> str:
    account = row.get("account_id") or ""
    chat = row.get("telegram_chat_id") or row.get("chat_id") or ""
    thread = row.get("message_thread_id") or row.get("telegram_topic_id") or row.get("topic_id") or "main"
    return f"{account}|{chat}|{thread}"


def display_text(raw_text: str) -> str:
    text = (raw_text or "").replace("\u00a0", " ").replace("\r\n", "\n").replace("\r", "\n")
    return re.sub(r"[ \t]+", " ", text).strip()


def normalized_text(raw_text: str) -> str:
    return re.sub(r"\s+", " ", display_text(raw_text).lower()).strip()


def feature_text(raw_text: str) -> str:
    text = normalized_text(raw_text)
    text = URL_RE.sub(" URL ", text)
    text = USERNAME_RE.sub(" USERNAME ", text)
    text = re.sub(r"[^a-zа-яё0-9_\-./:]+", " ", text, flags=re.IGNORECASE)
    return re.sub(r"\s+", " ", text).strip()


def root_domain(domain: str) -> str:
    parts = domain.lower().strip(".,)").split(".")
    if len(parts) >= 2:
        if parts[-2] in {"co", "com", "org", "net", "io", "ai", "ru", "pro", "me", "dev", "app"} and len(parts) >= 3:
            return ".".join(parts[-3:])
        return ".".join(parts[-2:])
    return domain.lower()


def extract_raw_json_urls(raw_json: Any) -> list[str]:
    urls: list[str] = []

    def walk(value: Any) -> None:
        if isinstance(value, dict):
            entity_type = value.get("type")
            if isinstance(entity_type, dict):
                url = entity_type.get("url")
                if isinstance(url, str) and url.startswith(("http://", "https://")):
                    urls.append(url)
            for child in value.values():
                walk(child)
        elif isinstance(value, list):
            for child in value:
                walk(child)

    walk(raw_json or {})
    return sorted(set(urls))


def extract_entities(raw_text: str, raw_json: Any | None = None) -> dict[str, Any]:
    raw_json_urls = extract_raw_json_urls(raw_json)
    urls = sorted(set(URL_RE.findall(raw_text or "") + raw_json_urls))
    domain_source = "\n".join([raw_text or "", *raw_json_urls])
    domains = sorted(set(root_domain(match.group(1)) for match in DOMAIN_RE.finditer(domain_source)))
    usernames = sorted(set(USERNAME_RE.findall(raw_text or "")))
    models = sorted(set(match.group(0).lower().replace(" ", "") for match in STRONG_MODEL_RE.finditer(raw_text or "")))
    tools = sorted(set(match.group(0).lower().replace(" ", "") for match in STRONG_TOOL_RE.finditer(raw_text or "")))
    apis = sorted(set(match.group(0).lower().replace(" ", "") for match in STRONG_API_RE.finditer(raw_text or "")))
    error_codes = sorted(set(match.group(0).upper() for match in ERROR_CODE_RE.finditer(raw_text or "")))
    has_error_context = bool(ERROR_CONTEXT_RE.search(raw_text or ""))
    strong_domains = domains
    strong_errors = error_codes if has_error_context else []
    strong = sorted(set(strong_domains + models + tools + apis + strong_errors))
    weak = sorted(set(match.group(0).lower() for match in WEAK_ENTITY_RE.finditer(raw_text or "")) | (set(error_codes) - set(strong_errors)))
    return {
        "urls": urls,
        "raw_json_urls": raw_json_urls,
        "domains": domains,
        "usernames": usernames,
        "model_names": models,
        "tools": tools,
        "apis": apis,
        "error_codes": error_codes,
        "strong": strong,
        "weak": weak,
    }


def compute_features(row: dict[str, Any]) -> dict[str, Any]:
    raw_text = row.get("raw_text") or ""
    norm = normalized_text(raw_text)
    tokens = tokenize(norm)
    entities = extract_entities(raw_text, row.get("raw_json") or {})
    profanity = PROFANITY_RE.findall(norm)
    filler = FILLER_RE.findall(norm)
    emoji = EMOJI_RE.findall(raw_text)
    punct_noise = len(re.findall(r"[!?.,:;\-_=+*#]{2,}", raw_text or ""))
    sender = " ".join(str(row.get(key) or "") for key in ("sender_name", "chat_title", "content_type"))
    return {
        "length_chars": len(raw_text),
        "length_tokens": len(tokens),
        "has_link": bool(entities["urls"]),
        "domains": entities["domains"],
        "raw_json_urls": entities["raw_json_urls"],
        "hidden_url_count": len(entities["raw_json_urls"]),
        "usernames": entities["usernames"],
        "has_code": bool(CODE_RE.search(raw_text or "")),
        "has_error_code": bool(entities["error_codes"]),
        "error_codes": entities["error_codes"],
        "has_price": bool(PRICE_RE.search(raw_text or "")),
        "has_model_name": bool(entities["model_names"]),
        "model_names": entities["model_names"],
        "has_api_terms": bool(entities["apis"]) or bool(API_TERMS_RE.search(raw_text or "")) if (API_TERMS_RE := re.compile(r"\b(?:api|endpoint|sdk|json|oauth|token|rate\s*limit|docker|postgres|redis|http|webhook|openai-compatible)\b", re.IGNORECASE)) else False,
        "has_profanity": bool(profanity),
        "profanity_density": round(len(profanity) / max(1, len(tokens)), 4),
        "filler_density": round(len(filler) / max(1, len(tokens)), 4),
        "emoji_density": round(len(emoji) / max(1, len(raw_text)), 4),
        "punctuation_noise": round(punct_noise / max(1, len(raw_text)), 4),
        "sender_is_bot": bool(re.search(r"\b(bot|бот|chatkeeper|lolsbot)\b", sender, re.IGNORECASE)),
        "strong_entities": entities["strong"],
        "weak_entities": entities["weak"],
        "tools": entities["tools"],
        "apis": entities["apis"],
    }


def prepare_row(row: dict[str, Any], index: int, snapshot_id: str) -> dict[str, Any]:
    raw_id = str(row.get("id") or f"synthetic-{index}-{stable_hash(extract_raw_text(row))}")
    return {
        "lab_message_id": f"msg-{index:05d}",
        "snapshot_id": snapshot_id,
        "raw_id": raw_id,
        "dataset_message_id": raw_id,
        "account_id": row.get("account_id") or "",
        "telegram_message_id": row.get("telegram_message_id") or "",
        "telegram_chat_id": row.get("telegram_chat_id") or row.get("chat_id") or "",
        "chat_id": row.get("telegram_chat_id") or row.get("chat_id") or "",
        "chat_title": row.get("chat_title") or row.get("chatTitle") or "",
        "message_thread_id": row.get("message_thread_id") or "",
        "thread_id": row.get("message_thread_id") or row.get("telegram_topic_id") or "",
        "telegram_topic_id": row.get("telegram_topic_id") or "",
        "topic_id": row.get("telegram_topic_id") or row.get("message_thread_id") or "",
        "topic_title": row.get("topic_title") or row.get("topicTitle") or "",
        "reply_to_message_id": row.get("reply_to_message_id") or "",
        "sender_id": row.get("sender_id") or "",
        "sender_name": row.get("sender_name") or "",
        "content_type": row.get("content_type") or "",
        "message_date": row.get("message_date") or "",
        "ingested_at": row.get("ingested_at") or "",
        "timestamp": row.get("message_date") or row.get("ingested_at") or "",
        "raw_text": extract_raw_text(row),
        "raw_json": row.get("raw_json") or {},
        "conversation_key": "",
    }


def normalize_one(row: dict[str, Any]) -> dict[str, Any]:
    raw_text = row.get("raw_text") or ""
    features = compute_features(row)
    return {
        **row,
        "display_text": display_text(raw_text),
        "normalized_text": normalized_text(raw_text),
        "feature_text": feature_text(raw_text),
        "features": features,
    }


def marker_for(row: dict[str, Any], primary_class: str, route: str) -> str:
    features = row.get("features") or compute_features(row)
    text = row.get("normalized_text") or core_normalize_text(row.get("raw_text") or "")
    if primary_class in {"RULES_ONBOARDING", "MODERATION_BOT_EVENT"} or GUARD_RE["RULES_ONBOARDING"].search(text) or GUARD_RE["MODERATION_BOT_EVENT"].search(text):
        return "rules_onboarding_marker"
    if route == "MANUAL_REVIEW" or GUARD_RE["RISK_PROMO_REFERRAL"].search(text) or GUARD_RE["ABUSE_OR_FRAUD"].search(text):
        return "risk_referral_marker"
    if route == "NEEDS_ENRICHMENT" or (features.get("has_link") and features.get("length_tokens", 0) <= 8):
        return "link_only_marker"
    if GUARD_RE["JOB_POST"].search(text) or GUARD_RE["EVENT_ANNOUNCEMENT"].search(text) or primary_class in {"CAREER_JOB_POST", "EVENT_ANNOUNCEMENT"}:
        return "job_event_marker"
    if features.get("has_model_name") and (features.get("has_price") or GUARD_RE["RISK_PROMO_REFERRAL"].search(text) or CLAIM_INDICATOR_RE.search(text)) or features.get("has_price") or primary_class == "MODEL_RUMOR_OR_PRICING_CLAIM":
        return "model_pricing_marker"
    if "как " in text or "инструк" in text or "чеклист" in text:
        return "workflow_howto_marker"
    if features.get("has_error_code") or primary_class == "OUTAGE_STATUS":
        return "outage_status_marker"
    if features.get("has_api_terms") or features.get("has_code"):
        return "api_troubleshooting_marker"
    if features.get("domains") or primary_class in {"RESOURCE_LINK", "TECH_SIGNAL", "DIGEST_NEWS"}:
        return "tool_resource_marker"
    if features.get("length_tokens", 0) <= 3 or SHORT_ACK_RE.match(text):
        return "short_chatter_marker"
    return "unknown_potential_signal_marker" if route in {"REVIEW_HIGH_RECALL", "SIGNAL_ONLY"} else "short_chatter_marker"


def route_one(row: dict[str, Any]) -> dict[str, Any]:
    text = row.get("raw_text") or ""
    norm = row.get("normalized_text") or normalized_text(text)
    classification = classify_text(text, chat=str(row.get("chat_title") or ""), topic=str(row.get("topic_title") or ""))
    primary = classification.primary_class
    lab_route = FIRST_PASS_ROUTE_MAP.get(classification.material_route, "CONTEXT_ONLY")
    features = row.get("features") or compute_features(row)
    reasons = list(classification.reasons)
    has_claim_indicator = bool(CLAIM_INDICATOR_RE.search(norm))
    is_model_pricing_claim = (features.get("has_model_name") and has_claim_indicator) or primary == "MODEL_RUMOR_OR_PRICING_CLAIM" or (features.get("has_price") and features.get("has_model_name"))
    official_source = any(OFFICIAL_DOMAIN_RE.search(domain) for domain in features.get("domains", []) or [])
    all_urls = (features.get("urls", []) or []) + (features.get("raw_json_urls", []) or [])
    has_referral_url = any(("ref=" in u.lower() or "start=ref" in u.lower() or "ref_" in u.lower()) for u in all_urls)
    link_thin = features.get("has_link") and features.get("length_tokens", 0) <= 8
    strong = features.get("strong_entities", []) or []
    has_tech_signal = bool(strong) and (bool(TECH_PROBLEM_RE.search(norm)) or bool(QUESTION_RE.search(norm)) or bool(HOWTO_RE.search(norm)) or features.get("has_api_terms") or features.get("has_code"))
    has_guide_structure = features.get("length_tokens", 0) >= 20 and bool(GUIDE_STRUCTURE_RE.search(norm))

    looks_like_chat_rules = GUARD_RE["RULES_ONBOARDING"].search(norm) or GUARD_RE["MODERATION_BOT_EVENT"].search(norm)
    primary_rules = primary in {"RULES_ONBOARDING", "MODERATION_BOT_EVENT"}
    technical_override = (has_guide_structure or has_tech_signal) and bool(strong)
    if GUARD_RE["TEST_ARTIFACT"].search(norm):
        lab_route = "REJECT_SAFE"
        reasons.append("test_artifact_controlled_run_message_never_material")
    elif (looks_like_chat_rules or primary_rules) and not technical_override:
        lab_route = "REJECT_SAFE"
        reasons.append("hard_rule_never_material_rules_onboarding_or_moderation")
    elif GUARD_RE["ABUSE_OR_FRAUD"].search(norm) or primary == "ABUSE_OR_CIRCUMVENTION":
        lab_route = "MANUAL_REVIEW"
        reasons.append("abuse_or_fraud_never_auto_material")
    elif has_referral_url or GUARD_RE["RISK_PROMO_REFERRAL"].search(norm) or primary in {"RISK_PROMO_REFERRAL", "REFERRAL_OR_INVITE_LINK"}:
        lab_route = "MANUAL_REVIEW"
        reasons.append("risk_referral_or_promo_never_auto_material")
    elif GUARD_RE["JOB_POST"].search(norm) or primary == "CAREER_JOB_POST":
        lab_route = "AGGREGATE_ONLY"
        reasons.append("job_post_single_source_not_material")
    elif GUARD_RE["EVENT_ANNOUNCEMENT"].search(norm) or primary == "EVENT_ANNOUNCEMENT":
        lab_route = "AGGREGATE_ONLY"
        reasons.append("event_announcement_single_source_not_material")
    elif is_model_pricing_claim and not official_source:
        lab_route = "SIGNAL_ONLY"
        reasons.append("unverified_model_pricing_or_provider_claim")
    elif link_thin:
        lab_route = "NEEDS_ENRICHMENT"
        reasons.append("link_thin_message_requires_enrichment_first")
    elif NON_MATERIAL_LONG_RE.search(norm):
        lab_route = "CONTEXT_ONLY"
        reasons.append("non_material_long_form_roleplay_or_system_prompt_or_disclaimer_or_fiction")
    elif features.get("length_tokens", 0) <= 3 and not strong and not row.get("reply_to_message_id"):
        lab_route = "CONTEXT_ONLY"
        reasons.append("short_message_without_entities_link_or_reply")
    elif classification.material_route == "REVIEW_SIGNAL_OR_DISCUSSION" and not NON_MATERIAL_LONG_RE.search(norm):
        lab_route = "REVIEW_HIGH_RECALL"
        reasons.append("technical_or_resource_candidate_retained_for_context_evidence_review")
    elif has_tech_signal and not link_thin and not NON_MATERIAL_LONG_RE.search(norm):
        lab_route = "REVIEW_HIGH_RECALL"
        reasons.append("technical_troubleshooting_or_resource_with_strong_entity")
    elif has_guide_structure and not link_thin and not NON_MATERIAL_LONG_RE.search(norm):
        lab_route = "REVIEW_HIGH_RECALL"
        reasons.append("guide_or_howto_structure_with_substance")

    return {
        "lab_message_id": row.get("lab_message_id"),
        "snapshot_id": row.get("snapshot_id"),
        "raw_id": row.get("raw_id"),
        "dataset_message_id": row.get("dataset_message_id"),
        "account_id": row.get("account_id"),
        "telegram_message_id": row.get("telegram_message_id"),
        "conversation_key": row.get("conversation_key"),
        "chat_id": row.get("chat_id"),
        "chat_title": row.get("chat_title"),
        "thread_id": row.get("thread_id"),
        "topic_id": row.get("topic_id"),
        "topic_title": row.get("topic_title"),
        "reply_to_message_id": row.get("reply_to_message_id"),
        "has_reply": str(bool(row.get("reply_to_message_id"))).lower(),
        "reply_resolved": "",
        "sender_id": row.get("sender_id"),
        "sender_name": row.get("sender_name"),
        "sender_is_bot": str(bool((row.get("features") or {}).get("sender_is_bot"))).lower(),
        "timestamp": row.get("timestamp"),
        "primary_class": primary,
        "classifier_route": classification.material_route,
        "route": lab_route,
        "route_reason": ";".join(dict.fromkeys(reasons)),
        "risk": classification.risk,
        "material_type_hint": classification.material_type,
        "confidence": f"{classification.confidence:.3f}",
        "marker_id": marker_for(row, primary, lab_route),
        "strong_entities": ",".join(features.get("strong_entities", []) or []),
        "weak_entities": ",".join(features.get("weak_entities", []) or []),
        "domains": ",".join(features.get("domains", []) or []),
        "model_names": ",".join(features.get("model_names", []) or []),
        "error_codes": ",".join(features.get("error_codes", []) or []),
        "length_tokens": features.get("length_tokens", 0),
        "has_link": str(bool(features.get("has_link"))).lower(),
        "hidden_url_count": features.get("hidden_url_count", 0),
        "has_api_terms": str(bool(features.get("has_api_terms"))).lower(),
        "has_code": str(bool(features.get("has_code"))).lower(),
        "preview": text_preview(text, 280),
    }


def resolve_reply_chains(rows: list[dict[str, Any]]) -> dict[str, str]:
    """Map lab_message_id -> resolved parent lab_message_id within same conversation."""
    by_tg_msg: dict[tuple[str, str], dict[str, Any]] = {}
    for row in rows:
        acct = str(row.get("account_id") or "")
        tg_id = str(row.get("telegram_message_id") or "")
        if acct and tg_id:
            by_tg_msg[(acct, tg_id)] = row
    resolved: dict[str, str] = {}
    for row in rows:
        parent_tg = str(row.get("reply_to_message_id") or "")
        acct = str(row.get("account_id") or "")
        if parent_tg and (acct, parent_tg) in by_tg_msg:
            parent = by_tg_msg[(acct, parent_tg)]
            if parent.get("conversation_key") == row.get("conversation_key"):
                resolved[row["lab_message_id"]] = parent["lab_message_id"]
    return resolved


class UnionFind:
    def __init__(self, keys: list[str]) -> None:
        self.parent = {k: k for k in keys}

    def find(self, x: str) -> str:
        while self.parent[x] != x:
            self.parent[x] = self.parent[self.parent[x]]
            x = self.parent[x]
        return x

    def union(self, a: str, b: str) -> None:
        ra, rb = self.find(a), self.find(b)
        if ra != rb:
            self.parent[rb] = ra


def strong_entity_set(row: dict[str, Any]) -> set[str]:
    features = row.get("features") or {}
    return set(features.get("strong_entities", []) or [])


CANDIDATE_ROUTES = {"REVIEW_HIGH_RECALL", "SIGNAL_ONLY", "NEEDS_ENRICHMENT", "MANUAL_REVIEW", "AGGREGATE_ONLY"}


def build_conversation_clusters(rows: list[dict[str, Any]], routed: dict[str, dict[str, str]], resolved_replies: dict[str, str], time_window_min: int, time_window_msgs: int) -> tuple[list[list[dict[str, Any]]], dict[str, list[dict[str, Any]]]]:
    """Phase 1: conversation-local clusters of CANDIDATE messages only.

    Union by reply chain OR strong-entity overlap. Time window is NOT a merge
    condition (it caused whole-thread dumps). Non-candidate nearby messages are
    attached as context for display, but are not cluster members and do not drive
    the decision.
    Returns (candidate_clusters, context_map: candidate_mid -> nearby non-candidate rows).
    """
    candidate_ids = {row["lab_message_id"] for row in rows if routed.get(row["lab_message_id"], {}).get("route") in CANDIDATE_ROUTES}
    by_conv: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        by_conv[row.get("conversation_key") or "unknown"].append(row)
    for scoped in by_conv.values():
        scoped.sort(key=lambda item: parse_time(item.get("timestamp")) or datetime.min.replace(tzinfo=timezone.utc))

    uf = UnionFind([mid for mid in candidate_ids])
    for conv_rows in by_conv.values():
        for index, row in enumerate(conv_rows):
            mid = row["lab_message_id"]
            if mid not in candidate_ids:
                continue
            row_strong = strong_entity_set(row)
            left = max(0, index - time_window_msgs)
            right = min(len(conv_rows), index + time_window_msgs + 1)
            for neighbor in conv_rows[left:right]:
                nmid = neighbor["lab_message_id"]
                if nmid == mid or nmid not in candidate_ids:
                    continue
                reply_link = resolved_replies.get(mid) == nmid or resolved_replies.get(nmid) == mid
                entity_overlap = bool(row_strong and row_strong & strong_entity_set(neighbor))
                if reply_link or entity_overlap:
                    uf.union(mid, nmid)

    groups: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for mid in candidate_ids:
        groups[uf.find(mid)].append(next(row for row in rows if row["lab_message_id"] == mid))

    # Context attachment: non-candidate messages near each candidate (time window).
    context_map: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for conv_rows in by_conv.values():
        for index, row in enumerate(conv_rows):
            mid = row["lab_message_id"]
            if mid not in candidate_ids:
                continue
            created_at = parse_time(row.get("timestamp"))
            left = max(0, index - time_window_msgs)
            right = min(len(conv_rows), index + time_window_msgs + 1)
            for neighbor in conv_rows[left:right]:
                nmid = neighbor["lab_message_id"]
                if nmid == mid or nmid in candidate_ids:
                    continue
                neighbor_time = parse_time(neighbor.get("timestamp"))
                if created_at and neighbor_time and abs(created_at - neighbor_time) <= timedelta(minutes=time_window_min):
                    context_map[mid].append(neighbor)
    return list(groups.values()), context_map


GENERIC_MERGE_DOMAINS = {
    "github.com", "youtu.be", "youtube.com", "t.me", "telegram.me", "telegram.dog",
    "x.com", "twitter.com", "vk.com", "reddit.com", "instagram.com", "tiktok.com",
    "medium.com", "habr.com", "teletype.in",
    "google.com", "play.google.com", "docs.google.com", "forms.gle",
}


def merge_eligible_entities(row: dict[str, Any]) -> set[str]:
    """Entities specific enough to merge messages ACROSS different conversations.

    Excludes generic model/tool names (opus, claude, codex, ...) and generic
    platforms (github.com, youtu.be, t.me, ...) which appear in many unrelated
    threads and caused giant transitive components.
    """
    features = row.get("features") or {}
    domains = {d for d in features.get("domains", []) if d not in GENERIC_MERGE_DOMAINS}
    apis = set(features.get("apis", []) or [])
    errors = set(features.get("error_codes", []) or [])
    return domains | apis | errors


def merge_cross_conversation(local_clusters: list[list[dict[str, Any]]]) -> list[list[dict[str, Any]]]:
    """Phase 2: merge clusters across conversations ONLY by a specific shared
    merge-eligible entity (non-generic domain / specific API / error code).

    Generic model/tool names are NOT merge-eligible to avoid giant transitive
    components. Clusters already in the same conversation stay separate here.
    """
    entity_to_clusters: dict[str, list[int]] = defaultdict(list)
    for index, cluster in enumerate(local_clusters):
        shared = set.intersection(*[merge_eligible_entities(row) for row in cluster]) if cluster else set()
        for entity in shared:
            entity_to_clusters[entity].append(index)
    uf = UnionFind([str(i) for i in range(len(local_clusters))])
    for entity, cluster_indices in entity_to_clusters.items():
        conversations = {local_clusters[i][0].get("conversation_key") for i in cluster_indices}
        if len(conversations) >= 2 and len(cluster_indices) >= 2:
            for i in range(1, len(cluster_indices)):
                uf.union(str(cluster_indices[0]), str(cluster_indices[i]))
    merged: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for i, cluster in enumerate(local_clusters):
        merged[uf.find(str(i))].extend(cluster)
    # Safety cap: never let a cross-conversation merged cluster exceed 20 members.
    capped: list[list[dict[str, Any]]] = []
    for members in merged.values():
        if len(members) <= 20:
            capped.append(members)
        else:
            for i in range(0, len(members), 20):
                capped.append(members[i:i + 20])
    return capped


def shared_strong_entities(cluster: list[dict[str, Any]]) -> set[str]:
    if not cluster:
        return set()
    sets = [strong_entity_set(row) for row in cluster]
    return set.intersection(*sets) if all(sets) else set()


def cluster_decision(cluster: list[dict[str, Any]], routed: dict[str, dict[str, str]]) -> str:
    size = len(cluster)
    routes = Counter(routed.get(row["lab_message_id"], {}).get("route", "UNKNOWN") for row in cluster)
    classes = Counter(routed.get(row["lab_message_id"], {}).get("primary_class", "UNKNOWN") for row in cluster)
    risks = Counter(routed.get(row["lab_message_id"], {}).get("risk", "LOW") for row in cluster)
    top_class = classes.most_common(1)[0][0] if classes else "UNKNOWN"
    senders = {row.get("sender_id") for row in cluster if row.get("sender_id")}
    shared = shared_strong_entities(cluster)
    conversations = {row.get("conversation_key") for row in cluster}
    has_risk = risks.get("HIGH") or risks.get("MEDIUM")
    short_acks = sum(1 for row in cluster if (row.get("features") or {}).get("length_tokens", 0) <= 3)

    reject_safe = routes.get("REJECT_SAFE", 0)
    if reject_safe >= max(2, size // 2) or top_class in {"RULES_ONBOARDING", "MODERATION_BOT_EVENT"}:
        return "BLOCKED"
    if routes.get("MANUAL_REVIEW"):
        return "MANUAL_REVIEW"
    if routes.get("NEEDS_ENRICHMENT"):
        return "LINK_ENRICHMENT_FIRST"
    if size == 1:
        route = routed.get(cluster[0]["lab_message_id"], {}).get("route", "UNKNOWN")
        if route not in {"REVIEW_HIGH_RECALL"}:
            return "REVIEW_SINGLE_SIGNAL" if route in {"SIGNAL_ONLY"} else "CONTEXT_ONLY"
        row = cluster[0]
        features = row.get("features") or {}
        norm = row.get("normalized_text") or ""
        tokens = features.get("length_tokens", 0)
        has_guide_struct = bool(GUIDE_STRUCTURE_RE.search(norm)) or bool(HOWTO_RE.search(norm))
        has_tech = bool(features.get("strong_entities")) and (bool(TECH_PROBLEM_RE.search(norm)) or features.get("has_api_terms") or features.get("has_code") or bool(QUESTION_RE.search(norm)))
        has_link = features.get("has_link")
        link_thin = has_link and tokens <= 8
        has_strong_entity = bool(features.get("strong_entities"))
        is_non_material = bool(NON_MATERIAL_LONG_RE.search(norm))
        tech_entity = has_technical_entity(features)
        # Substantive self-contained single-message technical guide -> eligible for LLM Judge.
        # Requires a TECHNICAL entity (tool/api/code/model/dev-domain) so shopping lists,
        # roleplay, fiction, disclaimers, and article digests do not leak in.
        if tokens >= 60 and has_guide_struct and tech_entity and not link_thin and not has_risk and not is_non_material:
            return "SINGLE_MESSAGE_GUIDE_CANDIDATE"
        # Substantive single-message technical reference/resource with a technical entity.
        if tokens >= 60 and tech_entity and (features.get("has_code") or features.get("has_api_terms")) and not link_thin and not has_risk and not is_non_material:
            return "SINGLE_MESSAGE_REFERENCE_CANDIDATE"
        return "REVIEW_SINGLE_SIGNAL"
    if size == 2:
        return "SIGNAL_PAIR" if shared else "WEAK_PAIR"
    if size >= 3 and len(senders) >= 2 and shared and routes.get("REVIEW_HIGH_RECALL") and not has_risk and short_acks < size:
        return "EVIDENCE_GROUP_REVIEW"
    if shared:
        return "SIGNAL_GROUP"
    return "CONTEXT_GROUP"


DECISION_RU = {
    "EVIDENCE_GROUP_REVIEW": "группа доказательств на проверку материала",
    "SINGLE_MESSAGE_GUIDE_CANDIDATE": "содержательный одиночный гайд на проверку материала",
    "SINGLE_MESSAGE_REFERENCE_CANDIDATE": "одиночный технический справочник на проверку",
    "SIGNAL_GROUP": "группа сигналов (недостаточно источников)",
    "SIGNAL_PAIR": "пара сигналов по strong-сущности",
    "WEAK_PAIR": "слабая пара (нет strong-сущности)",
    "REVIEW_SINGLE_SIGNAL": "одиночный сигнал на проверку",
    "CONTEXT_ONLY": "только контекст",
    "CONTEXT_GROUP": "группа контекста (нет strong-сущности)",
    "LINK_ENRICHMENT_FIRST": "сначала обогащение ссылки",
    "MANUAL_REVIEW": "ручная проверка (риск)",
    "BLOCKED": "заблокировано",
}

ELIGIBLE_DECISIONS = {
    "EVIDENCE_GROUP_REVIEW",
    "SINGLE_MESSAGE_GUIDE_CANDIDATE",
    "SINGLE_MESSAGE_REFERENCE_CANDIDATE",
}

CLASS_RU = {
    "LOW_VALUE_CHATTER": "короткий чат / шум",
    "RULES_ONBOARDING": "правила и онбординг",
    "WEAK_SIGNAL": "слабый сигнал",
    "TECH_SIGNAL": "технический сигнал",
    "CAREER_JOB_POST": "вакансия / карьера",
    "MODERATION_BOT_EVENT": "событие модерации / бот",
    "MODEL_RUMOR_OR_PRICING_CLAIM": "модель / цена / провайдерский claim",
    "SOCIAL_MEDIA_LINK": "социальная ссылка",
    "LINK_SHARE": "тонкая ссылка",
    "INTERNAL_TELEGRAM_LINK": "telegram-ссылка",
    "OUTAGE_STATUS": "сбой / статус / ошибка",
    "EVENT_ANNOUNCEMENT": "анонс события",
    "RISK_PROMO_REFERRAL": "риск / реферал / промо",
    "RESOURCE_LINK": "ресурс / документация / repo",
    "REFERRAL_OR_INVITE_LINK": "реферальная или invite-ссылка",
    "DIGEST_NEWS": "дайджест / новости",
    "POTENTIAL_DISCUSSION_SIGNAL": "потенциальное обсуждение",
    "ABUSE_OR_CIRCUMVENTION": "абуз / обход ограничений",
    "MEDIA_ONLY_OR_EMPTY": "медиа / пусто",
}


def name_cluster_ru(cluster: list[dict[str, Any]], routed: dict[str, dict[str, str]]) -> str:
    shared = shared_strong_entities(cluster)
    routes = Counter(routed.get(row["lab_message_id"], {}).get("route", "UNKNOWN") for row in cluster)
    classes = Counter(routed.get(row["lab_message_id"], {}).get("primary_class", "UNKNOWN") for row in cluster)
    top_class = classes.most_common(1)[0][0] if classes else "UNKNOWN"
    size = len(cluster)
    if routes.get("MANUAL_REVIEW"):
        return "Риск / рефералы / абуз — ручная проверка"
    if routes.get("NEEDS_ENRICHMENT"):
        return "Ссылки требуют обогащения"
    if routes.get("REJECT_SAFE") or top_class in {"RULES_ONBOARDING", "MODERATION_BOT_EVENT"}:
        return "Правила / онбординг / модерация"
    if routes.get("AGGREGATE_ONLY") or top_class in {"CAREER_JOB_POST", "EVENT_ANNOUNCEMENT"}:
        return "Вакансии / события — агрегат только"
    if shared:
        entity = sorted(shared)[0]
        if any(e in {"401", "403", "404", "429", "500", "502", "503", "504", "522", "524"} for e in shared):
            return f"Сбои / ошибки: {entity}"
        if top_class == "OUTAGE_STATUS":
            return f"Сбои / статус: {entity}"
        if top_class in {"TECH_SIGNAL"}:
            return f"Техническое обсуждение: {entity}"
        return f"Тема: {entity}"
    if size == 2:
        return "Слабая техническая пара"
    if top_class == "LOW_VALUE_CHATTER":
        return "Короткий чат / контекст беседы"
    return "Смешанный технический контекст"


def summarize_cluster(index: int, cluster: list[dict[str, Any]], context: list[dict[str, Any]], routed: dict[str, dict[str, str]], resolved_replies: dict[str, str]) -> dict[str, Any]:
    routes = Counter(routed.get(row["lab_message_id"], {}).get("route", "UNKNOWN") for row in cluster)
    classes = Counter(routed.get(row["lab_message_id"], {}).get("primary_class", "UNKNOWN") for row in cluster)
    risks = Counter(routed.get(row["lab_message_id"], {}).get("risk", "LOW") for row in cluster)
    senders = Counter(str(row.get("sender_id") or "unknown") for row in cluster)
    conversations = Counter(row.get("conversation_key") or "unknown" for row in cluster)
    chats = Counter(str(row.get("chat_title") or row.get("chat_id") or "unknown") for row in cluster)
    topics = Counter(str(row.get("topic_title") or row.get("thread_id") or "main") for row in cluster)
    shared = shared_strong_entities(cluster)
    times = [parse_time(row.get("timestamp")) for row in cluster]
    times = [t for t in times if t]
    reply_count = sum(1 for row in cluster if resolved_replies.get(row["lab_message_id"]))
    decision = cluster_decision(cluster, routed)
    independent_sources = len(senders)
    return {
        "cluster_id": f"v2c-{index:05d}",
        "ru_name": name_cluster_ru(cluster, routed),
        "size": len(cluster),
        "context_count": len(context),
        "decision": decision,
        "decision_ru": DECISION_RU.get(decision, decision),
        "independent_source_count": independent_sources,
        "conversation_count": len(conversations),
        "shared_strong_entities": sorted(shared),
        "classes": dict(classes.most_common()),
        "routes": dict(routes.most_common()),
        "risks": dict(risks.most_common()),
        "chats": dict(chats.most_common(5)),
        "topics": dict(topics.most_common(5)),
        "reply_chain_count": reply_count,
        "time_span": {"from": min(times).isoformat() if times else "", "to": max(times).isoformat() if times else ""},
        "sample_message_ids": [row["lab_message_id"] for row in cluster[:30]],
        "samples": [
            {
                "lab_message_id": row["lab_message_id"],
                "raw_id": row["raw_id"],
                "dataset_message_id": row["dataset_message_id"],
                "conversation_key": row.get("conversation_key"),
                "chat_title": row.get("chat_title"),
                "topic_title": row.get("topic_title"),
                "sender_id": row.get("sender_id"),
                "sender_name": row.get("sender_name"),
                "timestamp": row.get("timestamp"),
                "reply_to": row.get("reply_to_message_id"),
                "reply_resolved": resolved_replies.get(row["lab_message_id"], ""),
                "class": routed.get(row["lab_message_id"], {}).get("primary_class", "UNKNOWN"),
                "route": routed.get(row["lab_message_id"], {}).get("route", "UNKNOWN"),
                "strong_entities": sorted(strong_entity_set(row)),
                "preview": text_preview(row.get("raw_text") or "", 220),
            }
            for row in cluster[:20]
        ],
        "context_samples": [
            {
                "lab_message_id": row["lab_message_id"],
                "raw_id": row["raw_id"],
                "class": routed.get(row["lab_message_id"], {}).get("primary_class", "UNKNOWN"),
                "route": routed.get(row["lab_message_id"], {}).get("route", "UNKNOWN"),
                "sender_name": row.get("sender_name"),
                "timestamp": row.get("timestamp"),
                "preview": text_preview(row.get("raw_text") or "", 160),
            }
            for row in context[:15]
        ],
    }


def command_prepare(args: argparse.Namespace, paths: LabPaths) -> None:
    input_path = Path(args.input).resolve()
    rows_raw = read_jsonl(input_path)
    snapshot_id = f"snapshot-{stable_hash(str(input_path) + ':' + str(input_path.stat().st_mtime_ns))}"
    rows = []
    for index, raw in enumerate(rows_raw, start=1):
        row = prepare_row(raw, index, snapshot_id)
        row["conversation_key"] = conversation_key(row)
        rows.append(row)
    write_jsonl(paths.p("data/raw_messages.jsonl"), rows)
    manifest = {
        "snapshot_id": snapshot_id,
        "created_at_utc": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        "input_path": str(input_path),
        "message_count": len(rows),
        "mode": "offline_read_only",
        "fields": ["reply_to_message_id", "sender_id", "sender_name", "telegram_message_id", "raw_json", "account_id"],
        "constraints": ["no_production_writes", "no_deploy", "no_generation", "raw_text_and_raw_json_preserved"],
    }
    safe_write_text(paths.p("data/snapshot_manifest.json"), json.dumps(manifest, ensure_ascii=False, indent=2))
    safe_write_text(paths.docs / "README.md", "# Material Selection Lab v2\n\nConversation-first clustering + strong-entity merge. Offline/read-only.\n")
    print(f"prepared {len(rows)} messages -> {paths.p('data/raw_messages.jsonl')}")


def command_normalize(args: argparse.Namespace, paths: LabPaths) -> None:
    rows = read_jsonl(paths.p("data/raw_messages.jsonl"))
    normalized = [normalize_one(row) for row in rows]
    write_jsonl(paths.p("data/messages_normalized.jsonl"), normalized)
    feature_rows = []
    for row in normalized:
        f = row["features"]
        feature_rows.append({
            "lab_message_id": row["lab_message_id"], "raw_id": row["raw_id"], "conversation_key": row["conversation_key"],
            "length_chars": f["length_chars"], "length_tokens": f["length_tokens"], "has_link": f["has_link"], "hidden_url_count": f["hidden_url_count"],
            "strong_entities": ",".join(f["strong_entities"]), "weak_entities": ",".join(f["weak_entities"]),
            "domains": ",".join(f["domains"]), "model_names": ",".join(f["model_names"]), "error_codes": ",".join(f["error_codes"]),
            "has_code": f["has_code"], "has_error_code": f["has_error_code"], "has_price": f["has_price"],
            "has_model_name": f["has_model_name"], "has_api_terms": f["has_api_terms"], "has_profanity": f["has_profanity"],
            "sender_id": row["sender_id"], "sender_name": row["sender_name"], "reply_to": row["reply_to_message_id"],
            "preview": text_preview(row["raw_text"], 160),
        })
    write_csv(paths.p("out/normalization/message_features.csv"), feature_rows, list(feature_rows[0].keys()) if feature_rows else [])
    print(f"normalized {len(normalized)} messages -> {paths.p('data/messages_normalized.jsonl')}")


def command_route(args: argparse.Namespace, paths: LabPaths) -> None:
    rows = read_jsonl(paths.p("data/messages_normalized.jsonl"))
    routed = [route_one(row) for row in rows]
    write_csv(paths.p("out/routes/messages_routed.csv"), routed, ROUTE_FIELDS)
    counts = Counter(r["route"] for r in routed)
    md = ["# Route Summary v2", "", f"Messages: `{len(routed)}`", "", "## Routes", ""]
    for name in ROUTE_ORDER:
        md.append(f"- `{name}`: `{counts.get(name, 0)}`")
    md.extend(["", "Every routed row has `route_reason`; `REJECT_SAFE` is auditable and does not delete data.", ""])
    safe_write_text(paths.p("out/routes/route_summary.md"), "\n".join(md))
    print(f"routed {len(routed)} messages -> {paths.p('out/routes/messages_routed.csv')}")


def command_cluster(args: argparse.Namespace, paths: LabPaths) -> None:
    rows = read_jsonl(paths.p("data/messages_normalized.jsonl"))
    routed = {row["lab_message_id"]: row for row in read_csv(paths.p("out/routes/messages_routed.csv"))}
    resolved = resolve_reply_chains(rows)
    local, context_map = build_conversation_clusters(rows, routed, resolved, args.time_window_min, args.time_window_msgs)
    merged = merge_cross_conversation(local)
    clusters = []
    for i, members in enumerate(merged, start=1):
        ctx_by_id = {}
        for m in members:
            for ctx in context_map.get(m["lab_message_id"], []):
                ctx_by_id[ctx["lab_message_id"]] = ctx
        clusters.append(summarize_cluster(i, members, list(ctx_by_id.values()), routed, resolved))
    clusters.sort(key=lambda c: (-c["size"], -len(c["shared_strong_entities"]), c["cluster_id"]))
    write_jsonl(paths.p("out/clusters/clusters.jsonl"), clusters)
    review = [c for c in clusters if c["decision"] in {"EVIDENCE_GROUP_REVIEW", "SINGLE_MESSAGE_GUIDE_CANDIDATE", "SINGLE_MESSAGE_REFERENCE_CANDIDATE", "SIGNAL_GROUP", "SIGNAL_PAIR", "REVIEW_SINGLE_SIGNAL", "LINK_ENRICHMENT_FIRST", "MANUAL_REVIEW"}]
    md = ["# Clusters v2", "", f"Total clusters: `{len(clusters)}`", f"Review clusters: `{len(review)}`", "", "## Decisions", ""]
    for decision, count in Counter(c["decision"] for c in clusters).most_common():
        md.append(f"- `{decision}` / {DECISION_RU.get(decision, decision)}: `{count}`")
    md.extend(["", "## Top review clusters", ""])
    for c in review[:40]:
        md.extend([f"### {c['ru_name']}", "", f"- cluster_id: `{c['cluster_id']}`", f"- candidates: `{c['size']}`; context: `{c['context_count']}`; решение: `{c['decision']}` / {c['decision_ru']}", f"- independent sources: `{c['independent_source_count']}`; conversations: `{c['conversation_count']}`; reply chains: `{c['reply_chain_count']}`", f"- shared strong entities: `{c['shared_strong_entities']}`", ""])
    safe_write_text(paths.p("out/clusters/cluster_summary.md"), "\n".join(md))
    print(f"built {len(clusters)} clusters ({len(review)} review) -> {paths.p('out/clusters/clusters.jsonl')}")


def command_name(args: argparse.Namespace, paths: LabPaths) -> None:
    clusters = read_jsonl(paths.p("out/clusters/clusters.jsonl"))
    review = [c for c in clusters if c["decision"] in {"EVIDENCE_GROUP_REVIEW", "SINGLE_MESSAGE_GUIDE_CANDIDATE", "SINGLE_MESSAGE_REFERENCE_CANDIDATE", "SIGNAL_GROUP", "SIGNAL_PAIR", "REVIEW_SINGLE_SIGNAL", "LINK_ENRICHMENT_FIRST", "MANUAL_REVIEW"}]
    review = sorted(review, key=lambda c: (-c["size"], -len(c["shared_strong_entities"])))[: args.top_clusters]
    md = ["# Русские имена кластеров v2", "", "Кластер = группа связанных сообщений. Класс = тип одного сообщения. Связь: беседа (chat+thread), reply-chain, время, strong-сущность.", ""]
    csv_rows = []
    for c in review:
        md.extend([f"## {c['ru_name']}", "", f"- cluster_id: `{c['cluster_id']}`", f"- размер: `{c['size']}`", f"- решение: `{c['decision']}` / {c['decision_ru']}", f"- independent sources: `{c['independent_source_count']}`; conversations: `{c['conversation_count']}`; reply chains: `{c['reply_chain_count']}`", f"- shared strong entities: `{c['shared_strong_entities']}`", f"- классы: `{c['classes']}`", "", f"Top-{args.top_messages} сообщений:", ""])
        csv_rows.append({
            "cluster_id": c["cluster_id"], "ru_name": c["ru_name"], "decision": c["decision"], "decision_ru": c["decision_ru"],
            "size": c["size"], "independent_source_count": c["independent_source_count"], "conversation_count": c["conversation_count"],
            "shared_strong_entities": ",".join(c["shared_strong_entities"]), "top_20_ids": ",".join(s["lab_message_id"] for s in c["samples"]),
            "top_20_classes": ",".join(s["class"] for s in c["samples"]),
        })
        for i, s in enumerate(c["samples"][: args.top_messages], start=1):
            cls = s["class"]
            conv = f"chat={s.get('chat_title') or c['chats']}; thread/topic={s.get('topic_title') or 'main'}; sender={s.get('sender_name')}; time={s.get('timestamp')}; reply_to={s.get('reply_to') or '-'}"
            md.append(f"{i}. `{s['lab_message_id']}` (raw `{s['raw_id']}`) класс `{cls}` / {CLASS_RU.get(cls, cls)}; route `{s['route']}`; conversation {conv}; strong `{s['strong_entities']}`; {s['preview']}")
        md.append("")
    safe_write_text(paths.p("out/clusters/russian_cluster_summary.md"), "\n".join(md))
    write_csv(paths.p("out/clusters/russian_cluster_summary.csv"), csv_rows, list(csv_rows[0].keys()) if csv_rows else [])
    print(f"named {len(review)} review clusters -> {paths.p('out/clusters/russian_cluster_summary.md')}")


def command_evidence(args: argparse.Namespace, paths: LabPaths) -> None:
    clusters = read_jsonl(paths.p("out/clusters/clusters.jsonl"))
    routed = {row["lab_message_id"]: row for row in read_csv(paths.p("out/routes/messages_routed.csv"))}
    rows = read_jsonl(paths.p("data/messages_normalized.jsonl"))
    by_id = {row["lab_message_id"]: row for row in rows}
    groups = []
    for c in clusters:
        if c["decision"] not in {"EVIDENCE_GROUP_REVIEW", "SINGLE_MESSAGE_GUIDE_CANDIDATE", "SINGLE_MESSAGE_REFERENCE_CANDIDATE", "SIGNAL_GROUP", "SIGNAL_PAIR", "LINK_ENRICHMENT_FIRST", "MANUAL_REVIEW"}:
            continue
        messages = [by_id[mid] for mid in c["sample_message_ids"] if mid in by_id]
        senders = Counter(str(r.get("sender_id") or "unknown") for r in messages)
        domains = sorted(set(d for r in messages for d in (r.get("features") or {}).get("domains", []) or []))
        risks = Counter(routed.get(r["lab_message_id"], {}).get("risk", "LOW") for r in messages)
        routes = Counter(routed.get(r["lab_message_id"], {}).get("route", "UNKNOWN") for r in messages)
        hashes = [stable_hash(r.get("feature_text") or r.get("raw_text") or "", 8) for r in messages]
        dup_ratio = 1.0 - (len(set(hashes)) / len(hashes)) if hashes else 0.0
        groups.append({
            "group_id": c["cluster_id"], "ru_name": c["ru_name"], "decision": c["decision"], "decision_ru": c["decision_ru"],
            "source_count": len(messages), "independent_source_count": len(senders), "conversation_count": c["conversation_count"],
            "shared_strong_entities": c["shared_strong_entities"], "links_domains": domains, "risk_state": risks.most_common(1)[0][0] if risks else "LOW",
            "link_enrichment_state": "NEEDS_LINK_ENRICHMENT" if routes.get("NEEDS_ENRICHMENT") else "NOT_REQUIRED",
            "duplicate_status": "POSSIBLE_DUPLICATES" if dup_ratio > 0.35 else "NOT_COLLAPSED", "duplicate_ratio": round(dup_ratio, 3),
            "time_span": c["time_span"], "raw_ids": ",".join(str(r["raw_id"]) for r in messages[:30]),
        })
    write_jsonl(paths.p("out/evidence/evidence_groups.jsonl"), groups)
    if groups:
        write_csv(paths.p("out/evidence/evidence_group_summary.csv"), groups, list(groups[0].keys()))
    print(f"created {len(groups)} evidence groups -> {paths.p('out/evidence/evidence_groups.jsonl')}")


def command_gold_template(args: argparse.Namespace, paths: LabPaths) -> None:
    routed = read_csv(paths.p("out/routes/messages_routed.csv"))
    selected = []
    for route_name in ["REJECT_SAFE", "CONTEXT_ONLY", "SIGNAL_ONLY", "NEEDS_ENRICHMENT", "MANUAL_REVIEW", "AGGREGATE_ONLY", "REVIEW_HIGH_RECALL"]:
        selected.extend(stable_sample([r for r in routed if r.get("route") == route_name], min(args.per_route, len([r for r in routed if r.get("route") == route_name]))))
    fields = ["lab_message_id", "raw_id", "dataset_message_id", "conversation_key", "route", "route_reason", "primary_class", "important", "not_important", "topic", "correct_route", "should_be_signal", "should_be_context", "should_be_enrichment", "should_be_manual_review", "should_be_aggregate", "eligible_material", "false_positive", "false_negative", "notes", "preview"]
    rows = []
    for r in selected:
        rows.append({**{k: r.get(k, "") for k in ["lab_message_id", "raw_id", "dataset_message_id", "conversation_key", "route", "route_reason", "primary_class", "preview"]}, "important": "", "not_important": "", "topic": "", "correct_route": "", "should_be_signal": "", "should_be_context": "", "should_be_enrichment": "", "should_be_manual_review": "", "should_be_aggregate": "", "eligible_material": "", "false_positive": "", "false_negative": "", "notes": ""})
    write_csv(paths.p("out/gold/gold_labels_template.csv"), rows, fields)
    safe_write_text(paths.p("out/gold/gold_labeling_instructions.md"), "# Gold Labeling v2\n\nВажное не должно в REJECT_SAFE. Мусор не должен в MATERIAL_CANDIDATE. Сомнительное -> REVIEW_HIGH_RECALL.\n")
    print(f"created gold template with {len(rows)} rows -> {paths.p('out/gold/gold_labels_template.csv')}")


def command_evaluate(args: argparse.Namespace, paths: LabPaths) -> None:
    routed = {row["lab_message_id"]: row for row in read_csv(paths.p("out/routes/messages_routed.csv"))}
    gold_path = Path(args.gold) if args.gold else paths.p("out/gold/gold_labels_template.csv")
    gold = read_csv(gold_path) if gold_path.exists() else []
    labeled = [row for row in gold if any((row.get(f) or "").strip() for f in ["important", "not_important", "correct_route", "eligible_material", "false_positive", "false_negative"])]
    safe_write_text(paths.p("out/eval/evaluation_report.md"), f"# Evaluation v2\n\nLabeled rows: `{len(labeled)}`.\nFill gold_labels_template.csv then re-run `evaluate`.\n")
    safe_write_text(paths.p("out/final/material_eligibility_policy.md"), "# Draft Material Eligibility Policy v2\n\nStates: REJECT_NEVER_MATERIAL, CONTEXT_ONLY, SIGNAL_ONLY, NEEDS_LINK_ENRICHMENT, MANUAL_REVIEW, AGGREGATE_ONLY, DUPLICATE_OR_UPDATE, ELIGIBLE_MATERIAL.\n\nDefaults: single-message not material; single-source not material; job/event single-source not material; rules/onboarding never material; risk/referral never material; link-only never material before enrichment; model/pricing claims need official/corroborating source.\n")
    safe_write_text(paths.p("out/final/next_automation_plan.md"), "# Next Automation Plan v2\n\n1. Finish gold labeling. 2. Tune routing until 0 important false negatives in REJECT_SAFE. 3. Port hard routes + evidence sufficiency gate + MaterialEligibilityGate to backend. 4. Do not port exploratory cluster names or generation changes.\n")
    print(f"evaluated {len(labeled)} labeled rows -> {paths.p('out/eval/evaluation_report.md')}")


def command_all(args: argparse.Namespace, paths: LabPaths) -> None:
    command_prepare(args, paths)
    command_normalize(args, paths)
    command_route(args, paths)
    command_cluster(args, paths)
    command_name(args, paths)
    command_evidence(args, paths)
    command_gold_template(args, paths)
    command_evaluate(args, paths)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Offline Material Selection Lab v2 (conversation-first + strong-entity merge)")
    parser.add_argument("command", choices=["prepare", "normalize", "route", "cluster", "name", "evidence", "gold-template", "evaluate", "all"])
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT, help="Rich local snapshot path")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT, help="Output root under reports/material-selection-lab-v2")
    parser.add_argument("--docs", type=Path, default=DEFAULT_DOCS, help="Lab docs directory")
    parser.add_argument("--time-window-min", type=int, default=10, help="Conversation time window in minutes")
    parser.add_argument("--time-window-msgs", type=int, default=10, help="Conversation message window size")
    parser.add_argument("--top-clusters", type=int, default=150, help="Top review clusters for Russian naming")
    parser.add_argument("--top-messages", type=int, default=20, help="Top messages per cluster in summary")
    parser.add_argument("--per-route", type=int, default=150, help="Gold template sample size per route")
    parser.add_argument("--gold", type=Path, default=None, help="Completed gold labels CSV for evaluation")
    return parser


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()
    paths = LabPaths(args.out.resolve(), args.docs.resolve())
    if not str(paths.out).startswith(str((PROJECT_ROOT / "reports" / "material-selection-lab-v2").resolve())):
        raise SystemExit("Refusing to write outside reports/material-selection-lab-v2")
    dispatch = {"prepare": command_prepare, "normalize": command_normalize, "route": command_route, "cluster": command_cluster, "name": command_name, "evidence": command_evidence, "gold-template": command_gold_template, "evaluate": command_evaluate, "all": command_all}
    dispatch[args.command](args, paths)


if __name__ == "__main__":
    main()
