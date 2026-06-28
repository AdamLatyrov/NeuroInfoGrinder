WITH searches AS (
  SELECT * FROM (VALUES
    ('M01', ARRAY['GPT-5.6','Sol','Terra','Luna','Fable','OpenAI']::text[]),
    ('M02', ARRAY['PlusVibe','PlusVibeAPI','Claude Opus','GPT-5.5','Gemini','DeepSeek','Nano Banana','Veo 3','Seedance','token balance','bonus']::text[]),
    ('M03', ARRAY['proxy','proxies','прокси','подмен','подмена','model substitution','Gemini-2.5','медицин','benchmark','бенчмарк','логи','logs','training','трениров']::text[]),
    ('M04', ARRAY['Figma','Config 2026','canvas','канвас','workspace','код','animation','shaders','agents']::text[]),
    ('M05', ARRAY['Codex','quota','quotas','квот','outage','сбой','status','статус','GitHub','service components']::text[]),
    ('M06', ARRAY['government approval','гос','правительство','одобр','approval','GPT-5.6','restricted access','limited preview']::text[]),
    ('M07', ARRAY['Claude','Fable','iOS','Claude Code','loophole','баг','уязв','обход','временн','remote-control']::text[]),
    ('M08', ARRAY['referral','реферал','реф','бот','bots','500','8 руб','рублей']::text[]),
    ('M09', ARRAY['OpenMontage','video-agent','video agent','Kling','Runway','FLUX','ElevenLabs','Suno','52 tools','500 skills']::text[]),
    ('M10', ARRAY['sensors_discord_bot','discord_bot']::text[]),
    ('M11', ARRAY['lolz.live','Почитайте','почитайте','полезно']::text[]),
    ('M12', ARRAY['очень полезный гайд','полезный гайд','по итогу']::text[])
  ) AS x(manual_id, terms)
), raw_day AS (
  SELECT rm.*, coalesce(rm.text, rm.caption, '') AS body
  FROM raw_messages rm
  WHERE rm.message_date >= TIMESTAMPTZ '2026-06-27 00:00Z'
    AND rm.message_date < TIMESTAMPTZ '2026-06-28 00:00Z'
), scored AS (
  SELECT
    s.manual_id,
    rd.id AS raw_id,
    rd.telegram_chat_id,
    rd.telegram_topic_id,
    rd.message_thread_id,
    rd.telegram_message_id,
    rd.chat_title,
    rd.topic_title,
    rd.message_date,
    rd.ingested_at,
    rd.content_type,
    (
      SELECT count(*) FROM unnest(s.terms) term WHERE lower(rd.body) LIKE '%' || lower(term) || '%'
    ) AS hit_count,
    (
      SELECT jsonb_agg(term) FROM unnest(s.terms) term WHERE lower(rd.body) LIKE '%' || lower(term) || '%'
    ) AS matched_terms,
    left(rd.body, 1800) AS text_preview
  FROM searches s
  JOIN raw_day rd ON EXISTS (
    SELECT 1 FROM unnest(s.terms) term WHERE lower(rd.body) LIKE '%' || lower(term) || '%'
  )
), ranked AS (
  SELECT *, row_number() OVER (PARTITION BY manual_id ORDER BY hit_count DESC, message_date, raw_id) AS rn
  FROM scored
)
SELECT jsonb_pretty(jsonb_build_object(
  'capturedAt', now(),
  'matches', COALESCE((SELECT jsonb_agg(to_jsonb(ranked) ORDER BY manual_id, rn) FROM ranked WHERE rn <= 12), '[]'::jsonb),
  'topMatches', COALESCE((SELECT jsonb_agg(to_jsonb(ranked) ORDER BY manual_id, rn) FROM ranked WHERE rn = 1), '[]'::jsonb),
  'unmatchedManualIds', COALESCE((SELECT jsonb_agg(manual_id ORDER BY manual_id) FROM searches s WHERE NOT EXISTS (SELECT 1 FROM scored WHERE scored.manual_id = s.manual_id)), '[]'::jsonb)
));
