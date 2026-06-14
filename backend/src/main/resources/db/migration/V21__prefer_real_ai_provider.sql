-- V21: Prefer real AI providers over local mock providers.

UPDATE classifiers
SET provider_id = replacement.id,
    updated_at = CURRENT_TIMESTAMP
FROM (
    SELECT id
    FROM ai_providers
    WHERE status = 'ACTIVE'
      AND protocol <> 'MOCK'
    ORDER BY id
    LIMIT 1
) replacement
WHERE type = 'LLM'
  AND provider_id IN (
      SELECT id
      FROM ai_providers
      WHERE protocol = 'MOCK'
  );

UPDATE ai_providers
SET status = 'DISABLED',
    updated_at = CURRENT_TIMESTAMP
WHERE protocol = 'MOCK'
  AND status = 'ACTIVE'
  AND EXISTS (
      SELECT 1
      FROM ai_providers real_provider
      WHERE real_provider.status = 'ACTIVE'
        AND real_provider.protocol <> 'MOCK'
  );
