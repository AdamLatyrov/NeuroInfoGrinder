import hashlib
import json
import os
import subprocess
import time
import urllib.error
import urllib.request

BASE_DIR = "/srv/neuroinfogrinder/app"
INPUT_PATH = "/tmp/discussion-segment-controlled-selected-20260626.json"
OUTPUT_PATH = "/tmp/discussion-segment-controlled-materialization-20260626.json"
PROVIDER_ID = 10
MODEL_ID = 27
MODEL_NAME = "gpt-5.5"
STAGE = "KNOWLEDGE_GENERATION"
ALLOWED = ["S0024", "S0269", "S0132"]


def run_psql(sql: str) -> str:
    proc = subprocess.run(
        [
            "bash",
            "-lc",
            "cd /srv/neuroinfogrinder/app && docker compose -f docker-compose.prod.yml -f docker-compose.fast.yml --env-file .env exec -T postgres psql -q -U postgres -d neuroinfogrinder2_prod_clean -t -A",
        ],
        input=sql,
        text=True,
        capture_output=True,
        check=True,
    )
    return proc.stdout


def sql_quote(value):
    if value is None:
        return "NULL"
    return "'" + str(value).replace("'", "''") + "'"


def env_value(key: str):
    with open(os.path.join(BASE_DIR, ".env"), "r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            name, value = line.split("=", 1)
            if name == key:
                return value.strip().strip('"').strip("'")
    return None


def query_scalar(sql):
    lines = [line for line in run_psql(sql).splitlines() if line.strip()]
    for line in lines:
        value = line.strip()
        if value and not value.startswith(("INSERT ", "UPDATE ", "DELETE ")):
            return value
    return None


def prompt(segment):
    payload = {
        "segment_id": segment["segment_id"],
        "fixture": segment["fixture_or_new"],
        "artifact_type": segment["llm_type"],
        "title_from_judge": segment["title"],
        "judge_reason": segment["reason"],
        "judge_outline": segment["suggested_outline"],
        "raw_ids": segment["raw_ids"],
        "ordered_source_messages": segment["sources"],
    }
    return (
        "Return strict JSON only. Mode=KNOWLEDGE_GENERATION for a DISCUSSION_SEGMENT. "
        "Use only ordered_source_messages and judge_outline. Do not invent provider, API, medical, legal, or pricing facts. "
        "Every claim must be supported by the sources or clearly framed as participants' observations. "
        "Output schema: {artifactType:string,title:string,summary:string,body:string,sources:[{raw_id:number,role:string,quote:string}],qualityNotes:[string]}. "
        "Status will be DRAFT. Keep output in Russian. For SUMMARY, summarize feedback; for GUIDE, produce concise checklist/troubleshooting guidance with caveats.\n"
        + json.dumps(payload, ensure_ascii=False)
    )


def call_model(api_key, base_url, prompt_text):
    body = json.dumps(
        {
            "model": MODEL_NAME,
            "messages": [
                {"role": "system", "content": "You generate source-grounded DRAFT materials from Telegram discussion segments."},
                {"role": "user", "content": prompt_text},
            ],
            "temperature": 0,
            "response_format": {"type": "json_object"},
        },
        ensure_ascii=False,
    ).encode("utf-8")
    req = urllib.request.Request(
        base_url.rstrip("/") + "/chat/completions",
        data=body,
        method="POST",
        headers={"Authorization": "Bearer " + api_key, "Content-Type": "application/json"},
    )
    started = time.time()
    try:
        with urllib.request.urlopen(req, timeout=120) as response:
            raw = response.read().decode("utf-8")
            latency = int((time.time() - started) * 1000)
            return response.status, latency, json.loads(raw), None
    except urllib.error.HTTPError as exc:
        latency = int((time.time() - started) * 1000)
        error_body = exc.read().decode("utf-8", errors="replace")
        return exc.code, latency, {"error": error_body}, error_body[:500]
    except Exception as exc:
        latency = int((time.time() - started) * 1000)
        return None, latency, {"error": str(exc)}, str(exc)


def parse_response(response_json):
    try:
        content = response_json["choices"][0]["message"]["content"]
        return json.loads(content)
    except Exception:
        return {"artifactType": "ERROR", "title": "Generation parse error", "summary": "Could not parse model JSON content", "body": "", "sources": [], "qualityNotes": [json.dumps(response_json, ensure_ascii=False)[:1000]]}


def ensure_run(segment):
    existing = query_scalar(f"""
SELECT id
FROM replay_runs
WHERE pipeline_version = 'controlled-discussion-materialization-20260626'
  AND run_name = {sql_quote('Controlled discussion materialization ' + segment['segment_id'])}
ORDER BY id
LIMIT 1;
""")
    if existing:
        return int(existing)
    sql = f"""
INSERT INTO replay_runs (dataset_id, run_name, mode, pipeline_version, config_snapshot_json, status, started_at, finished_at, total_messages, processed_messages, provider_calls_total, estimated_cost_usd, error)
VALUES (1, {sql_quote('Controlled discussion materialization ' + segment['segment_id'])}, 'CONTROLLED_DISCUSSION_MATERIALIZATION', 'controlled-discussion-materialization-20260626', {sql_quote(json.dumps({'segment_id': segment['segment_id'], 'raw_ids': segment['raw_ids']}, ensure_ascii=False))}::jsonb, 'COMPLETED', now(), now(), {len(segment['sources'])}, {len(segment['sources'])}, 0, 0, NULL)
RETURNING id;
"""
    return int(query_scalar(sql))


def insert_discussion_segment(run_id, segment):
    existing = query_scalar(f"SELECT id FROM discussion_segments WHERE run_id = {run_id} ORDER BY id LIMIT 1;")
    if existing:
        return int(existing)
    first = segment["sources"][0]
    last = segment["sources"][-1]
    signals = json.dumps(["CONTROLLED_MATERIALIZATION", segment["llm_type"], segment["fixture_or_new"]], ensure_ascii=False)
    segment_text = "\n".join([f"{idx + 1}. [raw {src['raw_id']}] {src['text']}" for idx, src in enumerate(segment["sources"])])
    sql = f"""
WITH scope AS (
  SELECT account_id, telegram_chat_id, telegram_topic_id AS forum_topic_id, message_thread_id
  FROM raw_messages
  WHERE id = {int(first['raw_id'])}
), inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id, candidate_id)
  SELECT account_id, telegram_chat_id, forum_topic_id, message_thread_id, {sql_quote(first['message_date'])}::timestamptz, {sql_quote(last['message_date'])}::timestamptz, {len(segment['sources'])}, {float(segment['confidence'])}, {sql_quote(segment['llm_type'])}, 'DISCUSSION_SEGMENT_MATERIAL_CANDIDATE', NULL, {sql_quote(signals)}::jsonb, '[]'::jsonb, {sql_quote(segment_text)}, {run_id}, NULL
  FROM scope
  RETURNING id
)
SELECT id FROM inserted;
"""
    segment_db_id = int(query_scalar(sql))
    values = []
    for index, source in enumerate(segment["sources"]):
        values.append(f"({segment_db_id}, {int(source['raw_id'])}, {int(source['dataset_message_id'])}, NULL, {index}, 'context', {sql_quote(source['text'][:500])}, {sql_quote(source['message_date'])}::timestamptz)")
    run_psql("INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date) VALUES " + ",\n".join(values) + ";")
    return segment_db_id


def insert_provider_call(run_id, segment, prompt_text, response_json, parsed, status, http_status, latency_ms, error):
    usage = response_json.get("usage", {}) if isinstance(response_json, dict) else {}
    input_tokens = int(usage.get("prompt_tokens") or 0)
    output_tokens = int(usage.get("completion_tokens") or 0)
    request_hash = hashlib.sha256((STAGE + segment["segment_id"] + prompt_text).encode("utf-8")).hexdigest()
    response_payload = {"segment_id": segment["segment_id"], "parsed": parsed, "raw_ids": segment["raw_ids"]}
    sql = f"""
INSERT INTO provider_calls (run_id, stage, provider_id, model_id, model_name, attempt_number, request_hash, request_preview, response_preview, response_json, status, input_tokens, output_tokens, cached_tokens, estimated_cost_usd, latency_ms, http_status, error_code, error_message, prompt_mode)
VALUES ({run_id}, {sql_quote(STAGE)}, {PROVIDER_ID}, {MODEL_ID}, {sql_quote(MODEL_NAME)}, 1, {sql_quote(request_hash)}, {sql_quote(prompt_text[:2000])}, {sql_quote(json.dumps(parsed, ensure_ascii=False)[:2000])}, {sql_quote(json.dumps(response_payload, ensure_ascii=False))}::jsonb, {sql_quote(status)}, {input_tokens}, {output_tokens}, 0, 0, {latency_ms if latency_ms is not None else 'NULL'}, {http_status if http_status is not None else 'NULL'}, {sql_quote('HTTP_OR_PARSE_ERROR' if error or parsed.get('artifactType') == 'ERROR' else None)}, {sql_quote(error)}, 'DISCUSSION_SEGMENT');
SELECT currval('provider_calls_id_seq');
"""
    return int(query_scalar(sql))


def existing_success_generation(run_id):
    sql = f"""
SELECT json_build_object('id', id, 'parsed', response_json->'parsed')::text
FROM provider_calls
WHERE run_id = {run_id}
  AND stage = 'KNOWLEDGE_GENERATION'
  AND status = 'SUCCESS'
ORDER BY id
LIMIT 1;
"""
    value = query_scalar(sql)
    return json.loads(value) if value else None


def insert_stage(run_id, stage, status, input_count, output_count, skipped_count, provider_calls, metrics, error=None):
    sql = f"""
INSERT INTO replay_run_stages (run_id, stage, status, started_at, finished_at, input_count, output_count, skipped_count, error_count, provider_call_count, local_model_call_count, latency_ms, metrics_json, error)
VALUES ({run_id}, {sql_quote(stage)}, {sql_quote(status)}, now(), now(), {input_count}, {output_count}, {skipped_count}, {1 if error else 0}, {provider_calls}, 0, 0, {sql_quote(json.dumps(metrics, ensure_ascii=False))}::jsonb, {sql_quote(error)});
"""
    run_psql(sql)


def insert_material(run_id, segment_db_id, provider_call_id, segment, generated):
    existing = query_scalar(f"SELECT id FROM knowledge_items WHERE run_id = {run_id} AND source_cluster_type = 'DISCUSSION_SEGMENT' AND deleted_at IS NULL ORDER BY id LIMIT 1;")
    if existing:
        return int(existing), segment["llm_type"], generated.get("title") or segment["title"]
    artifact_type = generated.get("artifactType") or segment["llm_type"]
    if artifact_type not in ["GUIDE", "SUMMARY", "ANSWER", "REFERENCE"]:
        artifact_type = segment["llm_type"]
    title = generated.get("title") or segment["title"]
    summary = generated.get("summary") or segment["reason"][:500]
    body_json = json.dumps(generated, ensure_ascii=False)
    source_ids = json.dumps([int(source["dataset_message_id"]) for source in segment["sources"]])
    sql = f"""
INSERT INTO knowledge_items (run_id, cluster_id, item_type, title, summary, content_json, confidence, source_message_ids, provider_call_id, source_cluster_type, source_cluster_id, artifact_type, vertical, body_json, usefulness_score, publishability_score, knowledge_value_score, status)
VALUES ({run_id}, NULL, {sql_quote(artifact_type)}, {sql_quote(title)}, {sql_quote(summary)}, {sql_quote(body_json)}::jsonb, {float(segment['confidence'])}, {sql_quote(source_ids)}::jsonb, {provider_call_id}, 'DISCUSSION_SEGMENT', {segment_db_id}, {sql_quote(artifact_type)}, 'telegram-intelligence', {sql_quote(body_json)}::jsonb, 0.90, 0.82, 0.90, 'DRAFT')
RETURNING id;
"""
    material_id = int(query_scalar(sql))
    values = []
    for source in segment["sources"]:
        values.append(f"({material_id}, {int(source['dataset_message_id'])}, 'EVIDENCE', {sql_quote(source['text'][:1000])}, {float(segment['confidence'])})")
    if values:
        run_psql("INSERT INTO knowledge_item_sources (knowledge_item_id, dataset_message_id, source_role, quote, confidence) VALUES " + ",\n".join(values) + " ON CONFLICT DO NOTHING;")
    return material_id, artifact_type, title


def insert_trace(run_id, segment_db_id, material_id, segment, provider_call_id):
    stages = [
        ("discussion_segment_window_formed", "DISCUSSION_SEGMENT_WINDOW_FORMED", {"segmentId": segment["segment_id"], "sourceCount": len(segment["sources"])}),
        ("discussion_segment_scoring", "DISCUSSION_SEGMENT_SCORING", {"segmentId": segment["segment_id"], "score": segment["confidence"], "type": segment["llm_type"]}),
        ("discussion_segment_judge", "DISCUSSION_SEGMENT_JUDGE", {"segmentId": segment["segment_id"], "decision": segment["llm_decision"]}),
        ("discussion_segment_dedupe", "DISCUSSION_SEGMENT_DEDUPE", {"segmentId": segment["segment_id"], "duplicate": False}),
        ("knowledge_generation", "KNOWLEDGE_GENERATION", {"segmentId": segment["segment_id"], "providerCallId": provider_call_id}),
        ("material_created", "MATERIAL_CREATED", {"segmentId": segment["segment_id"], "materialId": material_id, "discussionSegmentId": segment_db_id}),
    ]
    for source in segment["sources"]:
        for stage_id, stage_name, output in stages:
            sql = f"""
INSERT INTO pipeline_message_trace (raw_message_id, replay_run_id, stage_id, stage_name, status, input_json, output_json, error_code, error_message, started_at, finished_at, duration_ms, created_at, updated_at)
VALUES ({int(source['raw_id'])}, {run_id}, {sql_quote(stage_id)}, {sql_quote(stage_name)}, 'PROCESSED', {sql_quote(json.dumps({'rawId': source['raw_id'], 'segmentId': segment['segment_id']}, ensure_ascii=False))}::jsonb, {sql_quote(json.dumps(output, ensure_ascii=False))}::jsonb, NULL, NULL, now(), now(), 0, now(), now())
ON CONFLICT (raw_message_id, stage_id) DO UPDATE SET replay_run_id = EXCLUDED.replay_run_id, status = EXCLUDED.status, output_json = EXCLUDED.output_json, updated_at = now();
"""
            run_psql(sql)


def main():
    api_key = env_value("MODELHUB_API_KEY")
    if not api_key:
        raise SystemExit("MODELHUB_API_KEY is not configured")
    with open(INPUT_PATH, "r", encoding="utf-8") as handle:
        selected = json.load(handle)
    if [item["segment_id"] for item in selected] != ALLOWED:
        raise SystemExit("selected segments do not match allowed exact scope")
    flag = query_scalar("SELECT COALESCE(max(setting_value), '0') FROM pipeline_settings WHERE setting_key = 'discussionSegmentGenerationEnabled';")
    if flag != "0":
        raise SystemExit("discussionSegmentGenerationEnabled must remain 0")
    results = []
    for segment in selected:
        duplicate_sql = """
WITH selected(raw_ids) AS (VALUES (ARRAY[%s]::bigint[])), existing AS (
  SELECT ki.id, array_agg(rm.id ORDER BY rm.id) FILTER (WHERE rm.id IS NOT NULL) AS raw_ids
  FROM knowledge_items ki
  JOIN knowledge_item_sources kis ON kis.knowledge_item_id = ki.id
  JOIN dataset_messages dm ON dm.id = kis.dataset_message_id
  LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
  WHERE ki.deleted_at IS NULL
  GROUP BY ki.id
)
SELECT COALESCE(max((cardinality(ARRAY(SELECT unnest(selected.raw_ids) INTERSECT SELECT unnest(COALESCE(existing.raw_ids, ARRAY[]::bigint[]))))::numeric / cardinality(selected.raw_ids)::numeric)), 0)
FROM selected CROSS JOIN existing;
""" % ",".join(str(raw_id) for raw_id in segment["raw_ids"])
        overlap = float(query_scalar(duplicate_sql) or 0)
        if overlap >= 0.8:
            results.append({"segment_id": segment["segment_id"], "generation_status": "SKIPPED_DUPLICATE", "source_overlap": overlap})
            continue
        run_id = ensure_run(segment)
        segment_db_id = insert_discussion_segment(run_id, segment)
        for stage_name in ["DISCUSSION_SEGMENT_WINDOW_FORMED", "DISCUSSION_SEGMENT_SCORING", "DISCUSSION_SEGMENT_JUDGE", "DISCUSSION_SEGMENT_DEDUPE"]:
            insert_stage(run_id, stage_name, "COMPLETED", len(segment["sources"]), 1, 0, 0, {"segmentId": segment["segment_id"], "discussionSegmentId": segment_db_id})
        existing_generation = existing_success_generation(run_id)
        if existing_generation:
            provider_call_id = int(existing_generation["id"])
            generated = existing_generation["parsed"]
            status = "SUCCESS"
            error = None
        else:
            prompt_text = prompt(segment)
            http_status, latency_ms, response_json, error = call_model(api_key, "https://modelhub.my/v1", prompt_text)
            generated = parse_response(response_json)
            status = "SUCCESS" if not error and generated.get("artifactType") != "ERROR" else "ERROR"
            provider_call_id = insert_provider_call(run_id, segment, prompt_text, response_json, generated, status, http_status, latency_ms, error)
        if status != "SUCCESS":
            insert_stage(run_id, "KNOWLEDGE_GENERATION", "FAILED", 1, 0, 0, 1, {"segmentId": segment["segment_id"], "providerCallId": provider_call_id}, error or "PARSE_ERROR")
            run_psql(f"UPDATE replay_runs SET status='FAILED', error={sql_quote(error or 'PARSE_ERROR')}, provider_calls_total=(SELECT count(*) FROM provider_calls WHERE run_id={run_id}) WHERE id={run_id};")
            results.append({"segment_id": segment["segment_id"], "run_id": run_id, "discussion_segment_id": segment_db_id, "provider_call_id": provider_call_id, "generation_status": "FAILED", "error": error or "PARSE_ERROR"})
            break
        material_id, artifact_type, title = insert_material(run_id, segment_db_id, provider_call_id, segment, generated)
        insert_stage(run_id, "KNOWLEDGE_GENERATION", "COMPLETED", 1, 1, 0, 1, {"segmentId": segment["segment_id"], "providerCallId": provider_call_id, "materialId": material_id})
        insert_stage(run_id, "MATERIAL_CREATED", "COMPLETED", 1, 1, 0, 0, {"segmentId": segment["segment_id"], "materialId": material_id})
        insert_trace(run_id, segment_db_id, material_id, segment, provider_call_id)
        run_psql(f"UPDATE replay_runs SET provider_calls_total=(SELECT count(*) FROM provider_calls WHERE run_id={run_id}), estimated_cost_usd=(SELECT COALESCE(sum(estimated_cost_usd),0) FROM provider_calls WHERE run_id={run_id}) WHERE id={run_id};")
        results.append({"segment_id": segment["segment_id"], "run_id": run_id, "discussion_segment_id": segment_db_id, "provider_call_id": provider_call_id, "material_id": material_id, "type": artifact_type, "title": title, "status": "DRAFT", "source_count": len(segment["sources"]), "raw_ids": segment["raw_ids"], "generation_status": "CREATED"})
    with open(OUTPUT_PATH, "w", encoding="utf-8") as handle:
        json.dump(results, handle, ensure_ascii=False, indent=2)
    print(json.dumps({"processed": [item.get("segment_id") for item in results], "created": [item.get("material_id") for item in results if item.get("material_id")]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
