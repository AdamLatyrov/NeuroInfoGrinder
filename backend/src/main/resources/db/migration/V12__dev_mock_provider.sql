INSERT INTO ai_providers (
    id, created_at, updated_at, name, protocol, endpoint_url,
    api_key_encrypted, model, status, last_tested_at, last_test_result
)
SELECT
    3001,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'Local Mock Provider',
    'MOCK',
    'mock://local',
    'mock-api-key',
    'mock-guide-model',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    'OK'
WHERE NOT EXISTS (
    SELECT 1 FROM ai_providers WHERE name = 'Local Mock Provider'
);

UPDATE classifiers
SET
    provider_id = (SELECT id FROM ai_providers WHERE name = 'Local Mock Provider' ORDER BY id LIMIT 1),
    prompt_id = (SELECT id FROM prompts WHERE type = 'CLASSIFICATION' AND status = 'ACTIVE' ORDER BY id LIMIT 1),
    status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP
WHERE name = 'AI Useful Content LLM';
