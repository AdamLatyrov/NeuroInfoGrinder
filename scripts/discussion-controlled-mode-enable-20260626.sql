WITH enable_time AS (
  SELECT to_char(clock_timestamp() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') AS value
), desired(setting_key, setting_value, setting_type, description, safe_min, safe_max, default_value, category) AS (
  VALUES
    ('discussionSegmentGenerationEnabled', '1', 'INTEGER', 'Enables discussion-segment material generation only when controlled mode gates also pass.', '0', '1', '0', 'discussion_segment'),
    ('discussionSegmentGenerationMode', 'CONTROLLED', 'STRING', 'Discussion-segment generation mode. Only CONTROLLED is allowed for automatic generation.', NULL, NULL, 'OFF', 'discussion_segment'),
    ('discussionSegmentMaxMaterialsPerDay', '3', 'INTEGER', 'Maximum DISCUSSION_SEGMENT DRAFT materials created per UTC day in controlled mode.', '0', '20', '3', 'discussion_segment'),
    ('discussionSegmentMaxMaterialsPerChatTopicPerDay', '1', 'INTEGER', 'Maximum DISCUSSION_SEGMENT DRAFT materials per chat/topic/thread per UTC day in controlled mode.', '0', '10', '1', 'discussion_segment'),
    ('discussionSegmentRequireLlmAccepted', '1', 'INTEGER', 'Requires accepted DISCUSSION_SEGMENT_JUDGE decision before generation.', '0', '1', '1', 'discussion_segment'),
    ('discussionSegmentDraftOnly', '1', 'INTEGER', 'Forces generated discussion-segment materials to remain DRAFT.', '0', '1', '1', 'discussion_segment'),
    ('discussionSegmentSkipRiskSensitive', '1', 'INTEGER', 'Skips automatic generation for risk-sensitive discussion segments.', '0', '1', '1', 'discussion_segment'),
    ('discussionSegmentFreshOnly', '1', 'INTEGER', 'Allows only discussion segments ending after discussionSegmentControlledEnableTime.', '0', '1', '1', 'discussion_segment'),
    ('discussionSegmentStopOnProviderError', '1', 'INTEGER', 'Stops controlled generation loop on judge provider errors.', '0', '1', '1', 'discussion_segment'),
    ('discussionSegmentStopOnGenerationError', '1', 'INTEGER', 'Stops controlled generation loop on generation provider errors.', '0', '1', '1', 'discussion_segment')
), upserted AS (
  INSERT INTO pipeline_settings (setting_key, setting_value, setting_type, description, safe_min, safe_max, default_value, category)
  SELECT setting_key, setting_value, setting_type, description, safe_min, safe_max, default_value, category
  FROM desired
  ON CONFLICT (setting_key) DO UPDATE SET setting_value=EXCLUDED.setting_value, setting_type=EXCLUDED.setting_type, description=EXCLUDED.description, safe_min=EXCLUDED.safe_min, safe_max=EXCLUDED.safe_max, default_value=EXCLUDED.default_value, category=EXCLUDED.category, updated_at=now()
  RETURNING setting_key
), enabled_time_upsert AS (
  INSERT INTO pipeline_settings (setting_key, setting_value, setting_type, description, default_value, category)
  SELECT 'discussionSegmentControlledEnableTime', value, 'STRING', 'UTC ISO timestamp from which fresh/live discussion-segment generation may begin.', '', 'discussion_segment'
  FROM enable_time
  ON CONFLICT (setting_key) DO UPDATE SET setting_value=EXCLUDED.setting_value, updated_at=now()
  RETURNING setting_value
)
SELECT jsonb_pretty(jsonb_build_object(
  'enableTime', (SELECT setting_value FROM enabled_time_upsert),
  'settings', (
    SELECT jsonb_object_agg(setting_key, setting_value ORDER BY setting_key)
    FROM pipeline_settings
    WHERE setting_key LIKE 'discussionSegment%'
  )
));
