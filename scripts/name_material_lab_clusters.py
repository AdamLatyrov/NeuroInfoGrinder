#!/usr/bin/env python3
"""Name Material Selection Lab clusters in Russian.

Offline-only helper. It reads previously generated lab cluster JSONL files and
writes human-readable Russian cluster summaries with top messages and classes.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
from collections import Counter
from pathlib import Path
from typing import Any, Iterable


PROJECT_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_LAB_ROOT = PROJECT_ROOT / "reports" / "material-selection-lab"
DEFAULT_OUTPUT_MD = DEFAULT_LAB_ROOT / "out" / "cluster_review" / "russian_cluster_summary.md"
DEFAULT_OUTPUT_CSV = DEFAULT_LAB_ROOT / "out" / "cluster_review" / "russian_cluster_summary.csv"

DOMAIN_RE = re.compile(r"https?://(?:www\.)?([^/\s?#]+)", re.IGNORECASE)
MODEL_RE = re.compile(r"\b(gpt[-\s]?(?:4|4o|5|5\.5)|claude|sonnet|opus|gemini|qwen|deepseek|llama|mistral|bge[-\s]?m3|openai|anthropic)\b", re.IGNORECASE)
ERROR_RE = re.compile(r"\b(400|401|403|404|408|409|422|429|500|502|503|504|timeout|таймаут|rate\s*limit|лимит|не\s+работает|упал|ошибка)\b", re.IGNORECASE)
API_RE = re.compile(r"\b(api|endpoint|sdk|json|oauth|token|docker|postgres|redis|webhook|openai-compatible|r-api|router)\b", re.IGNORECASE)
JOB_RE = re.compile(r"\b(ваканси|ищем|работ|резюме|hr|офер|зарплат|собеседован)\b", re.IGNORECASE)
EVENT_RE = re.compile(r"\b(митап|вебинар|конференц|доклад|регистрация|мероприят|анонс\s+встречи)\b", re.IGNORECASE)
RISK_RE = re.compile(r"\b(ref=|реферал|инвайт|абуз|bypass|cvv|bin|кардинг|бесплатн\w+\s+токен|discord\s+boost)\b", re.IGNORECASE)
RULES_RE = re.compile(r"\b(правила|добро\s+пожаловать|приветствуем|ознакомьтесь|подтвердите|chatkeeper|lolsbot|бан|mute)\b", re.IGNORECASE)
HOWTO_RE = re.compile(r"\b(как\s+(проверить|сделать|настроить|запустить|починить)|инструкц|чеклист|пошагов)\b", re.IGNORECASE)

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
}

ROUTE_RU = {
    "REVIEW_CLUSTER_FOR_MATERIAL": "кластер на проверку материала",
    "REVIEW_SINGLE_SIGNAL": "одиночный сигнал на проверку",
    "LINK_ENRICHMENT_FIRST": "сначала обогащение ссылки",
    "RETAIN_CONTEXT": "оставить как контекст",
    "NO_MATERIAL": "не материал",
    "BLOCKED": "заблокировано",
}


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    with path.open("r", encoding="utf-8") as handle:
        return [json.loads(line) for line in handle if line.strip()]


def load_message_lookup(lab_root: Path) -> dict[str, dict[str, Any]]:
    normalized_path = lab_root / "data" / "messages_normalized.jsonl"
    if not normalized_path.exists():
        return {}
    return {row.get("lab_message_id", ""): row for row in read_jsonl(normalized_path)}


def write_csv(path: Path, rows: Iterable[dict[str, Any]], fields: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def top_counter_key(values: dict[str, Any]) -> str:
    return next(iter(values.keys()), "UNKNOWN") if values else "UNKNOWN"


def sample_text(cluster: dict[str, Any]) -> str:
    return "\n".join(str(sample.get("preview") or "") for sample in cluster.get("samples", [])[:20])


def extract_domains(text: str) -> list[str]:
    return sorted(set(match.group(1).lower().strip(".,)") for match in DOMAIN_RE.finditer(text or "")))


def infer_ru_name(cluster: dict[str, Any]) -> tuple[str, str]:
    text = sample_text(cluster)
    top_class = top_counter_key(cluster.get("classes", {}))
    domains = extract_domains(text)
    models = sorted(set(match.group(1).lower() for match in MODEL_RE.finditer(text)))

    if RULES_RE.search(text) or top_class in {"RULES_ONBOARDING", "MODERATION_BOT_EVENT"}:
        return "Правила, онбординг и события модерации", "rules_or_moderation"
    if RISK_RE.search(text) or top_class in {"RISK_PROMO_REFERRAL", "REFERRAL_OR_INVITE_LINK", "ABUSE_OR_CIRCUMVENTION"}:
        return "Риск, рефералки, промо или обход ограничений", "risk_referral_abuse"
    if JOB_RE.search(text) or top_class == "CAREER_JOB_POST":
        return "Вакансии, HR и карьерные сообщения", "jobs_career"
    if EVENT_RE.search(text) or top_class == "EVENT_ANNOUNCEMENT":
        return "Анонсы мероприятий и встреч", "events"
    if ERROR_RE.search(text) or top_class == "OUTAGE_STATUS":
        entity = models[0] if models else (domains[0] if domains else "сервисы/API")
        return f"Сбои, лимиты и ошибки: {entity}", "outage_status"
    if HOWTO_RE.search(text):
        entity = models[0] if models else (domains[0] if domains else "технический workflow")
        return f"How-to / инструкция: {entity}", "workflow_howto"
    if API_RE.search(text) or top_class == "TECH_SIGNAL":
        entity = models[0] if models else (domains[0] if domains else "API и инструменты")
        return f"API, инструменты и техническое обсуждение: {entity}", "api_tools"
    if domains:
        return f"Ссылки и ресурсы: {domains[0]}", "links_resources"
    if top_class == "MODEL_RUMOR_OR_PRICING_CLAIM" or models:
        entity = models[0] if models else "модели/провайдеры"
        return f"Claims про модели, цены и провайдеров: {entity}", "model_provider_claims"
    if top_class == "LOW_VALUE_CHATTER":
        return "Короткий чат и низкоценный контекст", "low_value_chatter"
    return f"Смешанный кластер: {CLASS_RU.get(top_class, top_class)}", "mixed"


def row_for_cluster(algorithm: str, cluster: dict[str, Any]) -> dict[str, Any]:
    ru_name, name_kind = infer_ru_name(cluster)
    top_class = top_counter_key(cluster.get("classes", {}))
    top_route = top_counter_key(cluster.get("routes", {}))
    samples = cluster.get("samples", [])[:20]
    return {
        "algorithm": algorithm,
        "cluster_id": cluster.get("id"),
        "ru_name": ru_name,
        "name_kind": name_kind,
        "size": cluster.get("size"),
        "decision": cluster.get("decision"),
        "decision_ru": ROUTE_RU.get(str(cluster.get("decision")), str(cluster.get("decision"))),
        "quality_score": cluster.get("qualityScore"),
        "top_class": top_class,
        "top_class_ru": CLASS_RU.get(top_class, top_class),
        "top_route": top_route,
        "classes": json.dumps(cluster.get("classes", {}), ensure_ascii=False),
        "routes": json.dumps(cluster.get("routes", {}), ensure_ascii=False),
        "top_20_ids": ",".join(str(sample.get("id")) for sample in samples),
        "top_20_classes": ",".join(str(sample.get("class")) for sample in samples),
    }


def conversation_ref(sample: dict[str, Any], lookup: dict[str, dict[str, Any]]) -> str:
    row = lookup.get(str(sample.get("id")), {})
    raw_id = row.get("raw_id") or sample.get("id") or ""
    dataset_message_id = row.get("dataset_message_id") or ""
    chat_id = row.get("chat_id") or sample.get("chat") or ""
    thread_id = row.get("thread_id") or row.get("topic_id") or sample.get("topic") or "main"
    timestamp = row.get("timestamp") or sample.get("createdAt") or ""
    chat_title = row.get("chat_title") or ""
    topic_title = row.get("topic_title") or ""
    title_part = ""
    if chat_title or topic_title:
        title_part = f"; title={chat_title}/{topic_title}"
    return f"raw_id={raw_id}; dataset_message_id={dataset_message_id}; chat={chat_id}; thread={thread_id}; time={timestamp}{title_part}"


def render_markdown(rows: list[dict[str, Any]], clusters_by_key: dict[tuple[str, str], dict[str, Any]], top_messages: int, lookup: dict[str, dict[str, Any]]) -> str:
    lines = [
        "# Русские имена кластеров Material Selection Lab",
        "",
        "Кластер - это группа связанных сообщений. Класс - это тип одного сообщения внутри кластера.",
        "",
    ]
    by_algorithm: dict[str, list[dict[str, Any]]] = {}
    for row in rows:
        by_algorithm.setdefault(str(row["algorithm"]), []).append(row)
    for algorithm, algorithm_rows in by_algorithm.items():
        lines.extend([f"## Алгоритм: {algorithm}", ""])
        for row in algorithm_rows:
            cluster = clusters_by_key[(str(row["algorithm"]), str(row["cluster_id"]))]
            lines.extend([
                f"### {row['ru_name']}",
                "",
                f"- cluster_id: `{row['cluster_id']}`",
                f"- размер: `{row['size']}`",
                f"- решение: `{row['decision']}` / {row['decision_ru']}",
                f"- главный класс: `{row['top_class']}` / {row['top_class_ru']}",
                f"- quality_score: `{row['quality_score']}`",
                f"- классы в кластере: `{row['classes']}`",
                "",
                f"Top-{top_messages} сообщений:",
                "",
            ])
            for index, sample in enumerate(cluster.get("samples", [])[:top_messages], start=1):
                sample_class = str(sample.get("class") or "UNKNOWN")
                sample_route = str(sample.get("route") or "UNKNOWN")
                lines.append(f"{index}. `{sample.get('id')}` класс `{sample_class}` / {CLASS_RU.get(sample_class, sample_class)}; route `{sample_route}`; conversation `{conversation_ref(sample, lookup)}`; {sample.get('preview')}")
            lines.append("")
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description="Give Russian names to offline lab clusters")
    parser.add_argument("--lab-root", type=Path, default=DEFAULT_LAB_ROOT)
    parser.add_argument("--output-md", type=Path, default=DEFAULT_OUTPUT_MD)
    parser.add_argument("--output-csv", type=Path, default=DEFAULT_OUTPUT_CSV)
    parser.add_argument("--top-per-algorithm", type=int, default=25)
    parser.add_argument("--top-messages", type=int, default=20)
    parser.add_argument("--only-review", action="store_true", help="Include only clusters with review-like decisions")
    args = parser.parse_args()

    algorithms_dir = args.lab_root / "out" / "algorithms"
    cluster_files = sorted(algorithms_dir.glob("clusters_*.jsonl"))
    rows: list[dict[str, Any]] = []
    clusters_by_key: dict[tuple[str, str], dict[str, Any]] = {}
    lookup = load_message_lookup(args.lab_root)
    for cluster_file in cluster_files:
        algorithm = cluster_file.stem.removeprefix("clusters_")
        clusters = read_jsonl(cluster_file)
        if args.only_review:
            clusters = [cluster for cluster in clusters if cluster.get("decision") in {"REVIEW_CLUSTER_FOR_MATERIAL", "REVIEW_SINGLE_SIGNAL", "LINK_ENRICHMENT_FIRST"}]
        clusters = sorted(clusters, key=lambda item: (-float(item.get("qualityScore") or 0), -int(item.get("size") or 0)))[: args.top_per_algorithm]
        for cluster in clusters:
            row = row_for_cluster(algorithm, cluster)
            rows.append(row)
            clusters_by_key[(algorithm, str(cluster.get("id")))] = cluster

    fields = ["algorithm", "cluster_id", "ru_name", "name_kind", "size", "decision", "decision_ru", "quality_score", "top_class", "top_class_ru", "top_route", "classes", "routes", "top_20_ids", "top_20_classes"]
    write_csv(args.output_csv, rows, fields)
    args.output_md.parent.mkdir(parents=True, exist_ok=True)
    args.output_md.write_text(render_markdown(rows, clusters_by_key, args.top_messages, lookup), encoding="utf-8")
    kind_counts = Counter(row["name_kind"] for row in rows)
    print(f"named {len(rows)} clusters -> {args.output_md}")
    for kind, count in kind_counts.most_common():
        print(f"{kind}: {count}")


if __name__ == "__main__":
    main()
