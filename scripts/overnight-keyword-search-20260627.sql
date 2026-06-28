WITH keywords AS (
  SELECT * FROM (VALUES
    ('M01', ARRAY['GPT-5.6','Sol','Terra','Luna','Fable']::text[]),
    ('M02', ARRAY['PlusVibe','PlusVibeAPI','GPT-5.5','Nano Banana','Veo 3','Seedance']::text[]),
    ('M03', ARRAY['17 proxy','17 proxies','Gemini-2.5','benchmark','logs','training','подмен']::text[]),
    ('M04', ARRAY['Figma','Config 2026','canvas','shaders']::text[]),
    ('M05', ARRAY['Codex','quota','outage','GitHub issues','service components']::text[]),
    ('M06', ARRAY['government approval','GPT-5.6','limited preview']::text[]),
    ('M07', ARRAY['Claude','Fable','iOS','Claude Code','loophole']::text[]),
    ('M08', ARRAY['referral','500 bots','500 ботов','8 руб']::text[]),
    ('M09', ARRAY['OpenMontage','Kling','Runway','FLUX','ElevenLabs','Suno']::text[]),
    ('M10', ARRAY['sensors_discord_bot']::text[]),
    ('M11', ARRAY['lolz.live','Почитайте','полезно']::text[]),
    ('M12', ARRAY['очень полезный гайд','полезный гайд']::text[])
  ) AS x(manual_id, terms)
), raw_day AS (
  SELECT rm.*, coalesce(rm.text, rm.caption, '') AS body
  FROM raw_messages rm
  WHERE rm.message_date >= TIMESTAMPTZ '2026-06-27 00:00Z'
    AND rm.message_date < TIMESTAMPTZ '2026-06-28 00:00Z'
), matches AS (
  SELECT
    k.manual_id,
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
      SELECT jsonb_agg(term)
      FROM unnest(k.terms) term
      WHERE lower(rd.body) LIKE '%' || lower(term) || '%'
    ) AS matched_terms,
    left(rd.body, 1200) AS text_preview
  FROM keywords k
  JOIN raw_day rd ON EXISTS (
    SELECT 1 FROM unnest(k.terms) term
    WHERE lower(rd.body) LIKE '%' || lower(term) || '%'
  )
)
SELECT jsonb_pretty(jsonb_build_object(
  'capturedAt', now(),
  'searchDayUtc', '2026-06-27',
  'matches', COALESCE((SELECT jsonb_agg(to_jsonb(matches) ORDER BY manual_id, message_date, raw_id) FROM matches), '[]'::jsonb),
  'unmatchedManualIds', COALESCE((
    SELECT jsonb_agg(k.manual_id ORDER BY k.manual_id)
    FROM keywords k
    WHERE NOT EXISTS (SELECT 1 FROM matches m WHERE m.manual_id = k.manual_id)
  ), '[]'::jsonb)
));
