import hashlib
import json
import os
import subprocess
import time
import urllib.error
import urllib.request

SEGMENT_IDS = [86, 87, 50, 88, 89]
BASE_DIR = "/srv/neuroinfogrinder/app"
PROVIDER_ID = 10
MODEL_ID = 27
MODEL_NAME = "gpt-5.5"
STAGE = "DISCUSSION_SEGMENT_JUDGE"


def run_psql(sql: str) -> str:
    proc = subprocess.run(
        [
            "bash",
            "-lc",
            "cd /srv/neuroinfogrinder/app && docker compose -f docker-compose.prod.yml -f docker-compose.fast.yml --env-file .env exec -T postgres psql -U postgres -d neuroinfogrinder2_prod_clean -t -A",
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


def load_segments():
    sql = """
SELECT row_to_json(t)::text
FROM (
  SELECT ds.id, ds.source_count, ds.combined_score, ds.proposed_material_type, ds.decision, ds.signals_json,
         ds.telegram_chat_id, ds.forum_topic_id, ds.message_thread_id,
         jsonb_agg(jsonb_build_object(
           'order', dss.order_index,
           'raw_id', dss.raw_message_id,
           'dataset_message_id', dss.dataset_message_id,
           'role', dss.role,
           'message_date', dss.message_date,
           'text', coalesce(rm.text, rm.caption, dm.text, dm.caption, dss.text_preview, '')
         ) ORDER BY dss.order_index) AS sources
  FROM discussion_segments ds
  JOIN discussion_segment_sources dss ON dss.discussion_segment_id=ds.id
  JOIN dataset_messages dm ON dm.id=dss.dataset_message_id
  LEFT JOIN raw_messages rm ON rm.id=dss.raw_message_id
  WHERE ds.id IN (86,87,50,88,89)
  GROUP BY ds.id
  ORDER BY ds.id
) t;
"""
    return [json.loads(line) for line in run_psql(sql).splitlines() if line.strip()]


def prompt(segment):
    payload = {
        "segment_id": segment["id"],
        "chat_metadata": {
            "telegram_chat_id": segment["telegram_chat_id"],
            "forum_topic_id": segment["forum_topic_id"],
            "message_thread_id": segment["message_thread_id"],
        },
        "combined_score": float(segment["combined_score"]),
        "signal_families": segment["signals_json"],
        "proposed_material_type": segment["proposed_material_type"],
        "ordered_source_messages": segment["sources"],
    }
    return (
        "Return strict JSON only. Mode=DISCUSSION_SEGMENT_JUDGE. "
        "Use only the ordered source messages. Do not hallucinate. Output language follows source language. "
        "Schema: {accepted:boolean, decision:string, material_type:string, title:string, reason:string, confidence:number, "
        "source_message_roles:[{raw_id:number, role:string}], suggested_outline:[string], rejection_reason:string|null}. "
        "Allowed positive decisions: DISCUSSION_SEGMENT_MATERIAL_CANDIDATE, DIRECT_MATERIAL_READY. "
        "Allowed reject decisions: REJECTED_LOW_VALUE, REJECTED_NEEDS_MORE_CONTEXT, REJECTED_DUPLICATE, REJECTED_UNSAFE_OR_UNSUPPORTED, REJECTED_PROMO_OR_NOISE. "
        "For health, employment, security, or risk topics, accept only with careful source-aware wording and no unsupported claims.\n"
        + json.dumps(payload, ensure_ascii=False)
    )


def call_model(api_key, base_url, prompt_text):
    body = json.dumps(
        {
            "model": MODEL_NAME,
            "messages": [
                {"role": "system", "content": "You are a strict source-grounded judge for discussion-segment material candidates."},
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
        with urllib.request.urlopen(req, timeout=90) as response:
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
        return {"accepted": False, "decision": "PARSE_ERROR", "reason": "Could not parse model JSON content", "raw": response_json}


def insert_provider_call(segment_id, prompt_text, response_json, parsed, status, http_status, latency_ms, error):
    usage = response_json.get("usage", {}) if isinstance(response_json, dict) else {}
    input_tokens = int(usage.get("prompt_tokens") or 0)
    output_tokens = int(usage.get("completion_tokens") or 0)
    request_hash = hashlib.sha256((STAGE + prompt_text).encode("utf-8")).hexdigest()
    request_preview = prompt_text[:2000]
    response_preview = json.dumps(parsed, ensure_ascii=False)[:2000]
    sql = f"""
WITH inserted AS (
  INSERT INTO provider_calls (run_id, stage, provider_id, model_id, model_name, attempt_number, request_hash, request_preview, response_preview, response_json, status, input_tokens, output_tokens, cached_tokens, estimated_cost_usd, latency_ms, http_status, error_code, error_message, prompt_mode)
  VALUES (NULL, {sql_quote(STAGE)}, {PROVIDER_ID}, {MODEL_ID}, {sql_quote(MODEL_NAME)}, 1, {sql_quote(request_hash)}, {sql_quote(request_preview)}, {sql_quote(response_preview)}, {sql_quote(json.dumps(parsed, ensure_ascii=False))}::jsonb, {sql_quote(status)}, {input_tokens}, {output_tokens}, 0, 0, {latency_ms if latency_ms is not None else 'NULL'}, {http_status if http_status is not None else 'NULL'}, {sql_quote('HTTP_OR_PARSE_ERROR' if error else None)}, {sql_quote(error)}, 'DISCUSSION_SEGMENT')
  RETURNING id
)
UPDATE discussion_segments
SET decision = coalesce({sql_quote(parsed.get('decision'))}, decision),
    rejection_reason = CASE WHEN {sql_quote(parsed.get('decision'))} IN ('DISCUSSION_SEGMENT_MATERIAL_CANDIDATE','DIRECT_MATERIAL_READY') THEN 'DRY_RUN_GENERATION_DISABLED' ELSE coalesce({sql_quote(parsed.get('rejection_reason'))}, {sql_quote(parsed.get('reason'))}, rejection_reason) END,
    updated_at = now()
WHERE id = {segment_id};
SELECT currval('provider_calls_id_seq');
"""
    output = run_psql(sql)
    ids = [line.strip() for line in output.splitlines() if line.strip().isdigit()]
    return int(ids[-1]) if ids else None


def main():
    api_key = env_value("MODELHUB_API_KEY")
    if not api_key:
        raise SystemExit("MODELHUB_API_KEY is not configured")
    base_url = "https://modelhub.my/v1"
    results = []
    for segment in load_segments():
        prompt_text = prompt(segment)
        http_status, latency_ms, response_json, error = call_model(api_key, base_url, prompt_text)
        parsed = parse_response(response_json)
        status = "SUCCESS" if not error and parsed.get("decision") != "PARSE_ERROR" else "ERROR"
        call_id = insert_provider_call(segment["id"], prompt_text, response_json, parsed, status, http_status, latency_ms, error)
        results.append({
            "segment_id": segment["id"],
            "provider_call_id": call_id,
            "http_status": http_status,
            "latency_ms": latency_ms,
            "status": status,
            "parsed": parsed,
            "raw_ids": [source.get("raw_id") for source in segment["sources"]],
            "source_count": segment["source_count"],
            "proposed_type": segment["proposed_material_type"],
        })
    with open("/tmp/discussion-segment-llm-judge-results-20260626.json", "w", encoding="utf-8") as handle:
        json.dump(results, handle, ensure_ascii=False, indent=2)
    print(json.dumps({"segments": SEGMENT_IDS, "provider_calls": len(results)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
