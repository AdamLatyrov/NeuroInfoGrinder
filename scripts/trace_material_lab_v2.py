#!/usr/bin/env python3
"""Trace 10 realistic conversations through the full Material Lab v2 pipeline step by step.

Offline-only. Shows exactly what happens at each stage:
  prepare -> normalize -> features -> route -> conversation cluster -> cluster decision -> evidence -> final eligibility
"""
import json
import sys
from collections import Counter
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))
import material_lab_v2 as v2


SCENARIOS = [
    {
        "id": "S1-multi-source-technical-material",
        "title": "Обсуждение r-api балансировщика (3 сообщения, 3 разных автора, одна тема)",
        "messages": [
            {"id": "s1-1", "account_id": 1, "telegram_chat_id": -1003919536687, "message_thread_id": 1302, "sender_id": 1001, "sender_name": "dev", "chat_title": "Vibemode", "topic_title": "Баги и вопросы по API", "text": "Да мы просто переведем r-api на api, потому что на прямом api кеширование работает.", "raw_json": {}},
            {"id": "s1-2", "account_id": 1, "telegram_chat_id": -1003919536687, "message_thread_id": 1302, "sender_id": 1002, "sender_name": "dev2", "chat_title": "Vibemode", "topic_title": "Баги и вопросы по API", "text": "r-api?", "raw_json": {}},
            {"id": "s1-3", "account_id": 1, "telegram_chat_id": -1003919536687, "message_thread_id": 1302, "sender_id": 1003, "sender_name": "dev3", "chat_title": "Vibemode", "topic_title": "Баги и вопросы по API", "text": "а может вы сделаете балансировщик автоматический с r-api на api и наоборот?", "raw_json": {}},
        ],
    },
    {
        "id": "S2-single-source-tool-problem",
        "title": "Жалобы на droid от одного автора (не independent sources)",
        "messages": [
            {"id": "s2-1", "account_id": 1, "telegram_chat_id": -100111, "message_thread_id": 5, "sender_id": 2001, "sender_name": "userA", "chat_title": "Tools", "topic_title": "main", "text": "Кто-нибудь сталкивался с тем, что droid обрезает часть ответа?", "raw_json": {}},
            {"id": "s2-2", "account_id": 1, "telegram_chat_id": -100111, "message_thread_id": 5, "sender_id": 2001, "sender_name": "userA", "chat_title": "Tools", "topic_title": "main", "text": "у меня такая проблема встречалась в droid desktop", "raw_json": {}},
            {"id": "s2-3", "account_id": 1, "telegram_chat_id": -100111, "message_thread_id": 5, "sender_id": 2001, "sender_name": "userA", "chat_title": "Tools", "topic_title": "main", "text": "такое бывает и не только в droid, проблема воспроизводится стабильно", "raw_json": {}},
        ],
    },
    {
        "id": "S3-promo-airdrop-multi-source",
        "title": "Senpi free-token кампания от разных авторов (promo, не материал)",
        "messages": [
            {"id": "s3-1", "account_id": 1, "telegram_chat_id": -100222, "message_thread_id": 1, "sender_id": 3001, "sender_name": "dropper", "chat_title": "Crypto", "topic_title": "main", "text": "Senpi senpis.xyz — новый проект, 100$ за регистрацию на Hyperliquid", "raw_json": {}},
            {"id": "s3-2", "account_id": 1, "telegram_chat_id": -100222, "message_thread_id": 1, "sender_id": 3002, "sender_name": "dropper2", "chat_title": "Crypto", "topic_title": "main", "text": "Senpi senpis.xyz: залетаем оставлять заявку и получать бесплатные 100$ в тестовых токенах", "raw_json": {}},
        ],
    },
    {
        "id": "S4-rules-onboarding",
        "title": "Rules-бот приветствие (никогда не материал)",
        "messages": [
            {"id": "s4-1", "account_id": 1, "telegram_chat_id": -100333, "message_thread_id": 1, "sender_id": 4001, "sender_name": "Vibecoder Rules", "chat_title": "Vibecoders", "topic_title": "Основной", "text": "misha, прежде чем писать в этом чате, подтвердите, что ознакомились с правилами.", "raw_json": {"sender_is_bot": True}},
            {"id": "s4-2", "account_id": 1, "telegram_chat_id": -100333, "message_thread_id": 1, "sender_id": 4001, "sender_name": "Бот-Помощник ОМ", "chat_title": "Vibecoders", "topic_title": "Основной", "text": "Добро пожаловать! Прочитайте правила сообщества и базу знаний.", "raw_json": {"sender_is_bot": True}},
        ],
    },
    {
        "id": "S5-hidden-referral-link",
        "title": "Скрытая реферальная ссылка в raw_json caption entity",
        "messages": [
            {"id": "s5-1", "account_id": 1, "telegram_chat_id": -100444, "message_thread_id": 1, "sender_id": 5001, "sender_name": "anon", "chat_title": "Free", "topic_title": "main", "text": "Залетайте на бесплатный сервис", "raw_json": {"content": {"text": {"text": "Залетайте на бесплатный сервис", "entities": [{"type": {"url": "https://router.bynara.id/register?ref=5RWD9UQV", "@type": "textEntityTypeTextUrl"}, "@type": "textEntity", "length": 19, "offset": 10}]}}}},
        ],
    },
    {
        "id": "S6-link-only-needs-enrichment",
        "title": "Только ссылка без контекста (нужно обогащение)",
        "messages": [
            {"id": "s6-1", "account_id": 1, "telegram_chat_id": -100555, "message_thread_id": 1, "sender_id": 6001, "sender_name": "a", "chat_title": "Links", "topic_title": "main", "text": "https://github.com/larbcorp/example", "raw_json": {}},
            {"id": "s6-2", "account_id": 1, "telegram_chat_id": -100555, "message_thread_id": 1, "sender_id": 6002, "sender_name": "b", "chat_title": "Links", "topic_title": "main", "text": "https://github.com/larbcorp/another", "raw_json": {}},
        ],
    },
    {
        "id": "S7-unverified-model-claim",
        "title": "Unverified model/pricing claim (signal, не материал)",
        "messages": [
            {"id": "s7-1", "account_id": 1, "telegram_chat_id": -100666, "message_thread_id": 1, "sender_id": 7001, "sender_name": "anon", "chat_title": "AI News", "topic_title": "main", "text": "Бесплатный Opus 4.8 доступен, Fable 5 тоже, интеграция с Visual Studio. Быстрый абузик на Opus 4.8 + GPT 5.5.", "raw_json": {}},
        ],
    },
    {
        "id": "S8-short-chatter-no-context",
        "title": "Короткий chatter без сущностей (context only)",
        "messages": [
            {"id": "s8-1", "account_id": 1, "telegram_chat_id": -100777, "message_thread_id": 1, "sender_id": 8001, "sender_name": "anon", "chat_title": "Флудилка", "topic_title": "main", "text": "мощни", "raw_json": {}},
            {"id": "s8-2", "account_id": 1, "telegram_chat_id": -100777, "message_thread_id": 1, "sender_id": 8002, "sender_name": "anon2", "chat_title": "Флудилка", "topic_title": "main", "text": "да", "raw_json": {}},
        ],
    },
    {
        "id": "S9-howto-checklist",
        "title": "How-to / чеклист (technical, single message)",
        "messages": [
            {"id": "s9-1", "account_id": 1, "telegram_chat_id": -100888, "message_thread_id": 1, "sender_id": 9001, "sender_name": "dev", "chat_title": "HowTo", "topic_title": "main", "text": "Как проверить OpenAI-compatible API: чеклист — получить ключ, вызвать GET /models, проверить ответ 200, сверить список моделей.", "raw_json": {}},
        ],
    },
    {
        "id": "S10-weak-pair-generic-keywords",
        "title": "Слабая пара по generic tech-словам (не материал)",
        "messages": [
            {"id": "s10-1", "account_id": 1, "telegram_chat_id": -100999, "message_thread_id": 1, "sender_id": 10001, "sender_name": "a", "chat_title": "Флудилка", "topic_title": "main", "text": "MVVM OZON SOLID", "raw_json": {}},
            {"id": "s10-2", "account_id": 1, "telegram_chat_id": -100999, "message_thread_id": 1, "sender_id": 10002, "sender_name": "b", "chat_title": "Флудилка", "topic_title": "main", "text": "FAANG HTTP JAVA", "raw_json": {}},
        ],
    },
]


def trace_scenario(scenario: dict) -> list[str]:
    out = []
    out.append("=" * 80)
    out.append(f"СЦЕНАРИЙ: {scenario['title']}")
    out.append(f"id: {scenario['id']}")
    out.append("=" * 80)
    snapshot_id = "trace"

    # Stage 0: prepare
    rows = []
    for i, msg in enumerate(scenario["messages"], start=1):
        row = v2.prepare_row(msg, i, snapshot_id)
        row["conversation_key"] = v2.conversation_key(row)
        rows.append(row)
    out.append("")
    out.append(">>> ШАГ 0 — prepare (фиксация raw, conversation_key)")
    for r in rows:
        out.append(f"  {r['lab_message_id']} raw_id={r['raw_id']} conv_key={r['conversation_key']} reply_to={r.get('reply_to_message_id') or '-'} sender={r.get('sender_name')}({r.get('sender_id')})")

    # Stage 1: normalize + features
    rows = [v2.normalize_one(r) for r in rows]
    out.append("")
    out.append(">>> ШАГ 1 — normalize + features (display/normalized/feature text + strong/weak entities)")
    for r in rows:
        f = r["features"]
        out.append(f"  {r['lab_message_id']} len_tokens={f['length_tokens']} has_link={f['has_link']} hidden_url_count={f.get('hidden_url_count',0)}")
        out.append(f"     strong_entities={f['strong_entities']}")
        out.append(f"     weak_entities={f['weak_entities']}")
        out.append(f"     domains={f['domains']} model_names={f['model_names']} error_codes={f['error_codes']}")

    # Stage 2: route
    routed = {r["lab_message_id"]: v2.route_one(r) for r in rows}
    out.append("")
    out.append(">>> ШАГ 2 — route (первичная воронка, не финальный material eligibility)")
    for mid, r in routed.items():
        out.append(f"  {mid} route={r['route']} marker={r['marker_id']} risk={r['risk']} reason={r['route_reason']}")

    # Stage 3: conversation reconstruction + reply chains
    resolved = v2.resolve_reply_chains(rows)
    out.append("")
    out.append(">>> ШАГ 3 — conversation reconstruction (reply chain resolve)")
    for r in rows:
        mid = r["lab_message_id"]
        out.append(f"  {mid} reply_resolved={resolved.get(mid, '-')}")

    # Stage 4: conversation-local clustering
    local, context_map = v2.build_conversation_clusters(rows, routed, resolved, 10, 10)
    out.append("")
    out.append(">>> ШАГ 4 — conversation-local clustering (union по reply-chain OR strong-entity; время = context)")
    for ci, members in enumerate(local):
        out.append(f"  local-cluster {ci}: {[m['lab_message_id'] for m in members]}")
    if context_map:
        out.append(f"  context attachments (non-candidate nearby): {sum(len(v) for v in context_map.values())}")

    # Stage 5: cross-conversation merge (merge-eligible entities only)
    merged = v2.merge_cross_conversation(local)
    out.append("")
    out.append(">>> ШАГ 5 — cross-conversation merge (только по merge-eligible сущности, generic исключены)")
    for ci, members in enumerate(merged):
        convs = {m.get("conversation_key") for m in members}
        shared = v2.shared_strong_entities(members)
        out.append(f"  merged-cluster {ci}: size={len(members)} conversations={len(convs)} shared={sorted(shared)} ids={[m['lab_message_id'] for m in members]}")

    # Stage 6: cluster decision
    out.append("")
    out.append(">>> ШАГ 6 — cluster decision (material eligibility gate)")
    for members in merged:
        decision = v2.cluster_decision(members, routed)
        senders = {m.get("sender_id") for m in members if m.get("sender_id")}
        routes_in = Counter(routed.get(m["lab_message_id"], {}).get("route", "?") for m in members)
        out.append(f"  decision={decision} ({v2.DECISION_RU.get(decision, decision)})")
        out.append(f"    size={len(members)} independent_sources={len(senders)} routes={dict(routes_in)}")
        out.append(f"    shared_strong={sorted(v2.shared_strong_entities(members))}")

    # Stage 7: final material eligibility
    out.append("")
    out.append(">>> ШАГ 7 — финальный MaterialEligibilityGate (допуск к LLM)")
    for members in merged:
        decision = v2.cluster_decision(members, routed)
        senders = {m.get("sender_id") for m in members if m.get("sender_id")}
        shared = v2.shared_strong_entities(members)
        routes_in = Counter(routed.get(m["lab_message_id"], {}).get("route", "?") for m in members)
        if decision == "EVIDENCE_GROUP_REVIEW":
            verdict = "ELIGIBLE_FOR_LLM_JUDGE — кластер допущен к LLM Judge (мульти-источник + strong entity + REVIEW_HIGH_RECALL)"
        elif decision == "SINGLE_MESSAGE_GUIDE_CANDIDATE":
            verdict = "ELIGIBLE_FOR_LLM_JUDGE — содержательный одиночный гайд (self-contained, >=80 токенов, структура, не risk) — durable knowledge из одного источника"
        elif decision == "SINGLE_MESSAGE_REFERENCE_CANDIDATE":
            verdict = "ELIGIBLE_FOR_LLM_JUDGE — одиночный технический справочник (strong entity + code/api, не risk)"
        elif decision in ("SIGNAL_GROUP", "SIGNAL_PAIR", "REVIEW_SINGLE_SIGNAL"):
            verdict = "NOT_ELIGIBLE — остаётся сигналом/контекстом, до LLM не доходит"
        elif decision == "MANUAL_REVIEW":
            verdict = "NOT_ELIGIBLE — риск/promo, ручная проверка, до LLM не доходит"
        elif decision == "LINK_ENRICHMENT_FIRST":
            verdict = "NOT_ELIGIBLE — сначала обогащение ссылок, потом пересмотр"
        elif decision in ("WEAK_PAIR", "CONTEXT_GROUP", "CONTEXT_ONLY"):
            verdict = "NOT_ELIGIBLE — слабая связность/контекст, до LLM не доходит"
        else:
            verdict = f"NOT_ELIGIBLE — {decision}"
        out.append(f"  decision={decision} -> {verdict}")
    out.append("")
    return out


def main():
    out = []
    for scenario in SCENARIOS:
        out.extend(trace_scenario(scenario))
    report = PROJECT_ROOT / "reports" / "material-selection-lab-v2" / "out" / "final" / "scenario_trace_10.md"
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text("\n".join(out), encoding="utf-8")
    print(f"wrote {len(SCENARIOS)} scenario traces -> {report}")


if __name__ == "__main__":
    main()
