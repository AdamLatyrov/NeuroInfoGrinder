-- V19: Pipeline settings (thresholds) + Prompt templates with versioning

-- 1. pipeline_settings: key-value store for thresholds and pipeline config
CREATE TABLE IF NOT EXISTS pipeline_settings (
    id BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(255) NOT NULL UNIQUE,
    setting_value TEXT NOT NULL,
    setting_type VARCHAR(50) NOT NULL DEFAULT 'DOUBLE',
    description TEXT,
    safe_min TEXT,
    safe_max TEXT,
    default_value TEXT,
    category VARCHAR(100) DEFAULT 'threshold',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed default thresholds
INSERT INTO pipeline_settings (setting_key, setting_value, setting_type, description, safe_min, safe_max, default_value, category) VALUES
('classificationConfidenceThreshold', '0.65', 'DOUBLE', 'Минимальная уверенность BERT/bootstrap классификатора', '0.0', '1.0', '0.65', 'threshold'),
('noiseSuppressThreshold', '0.75', 'DOUBLE', 'Порог подавления шума', '0.0', '1.0', '0.75', 'threshold'),
('clusterCandidateThreshold', '0.55', 'DOUBLE', 'Минимальный score кластера для кандидата', '0.0', '1.0', '0.55', 'threshold'),
('singleMessageCandidateThreshold', '0.55', 'DOUBLE', 'Минимальный score single-message для кандидата', '0.0', '1.0', '0.55', 'threshold'),
('directMaterialReadyThreshold', '0.72', 'DOUBLE', 'Порог для DIRECT_MATERIAL_READY', '0.0', '1.0', '0.72', 'threshold'),
('semanticSimilarityThreshold', '0.62', 'DOUBLE', 'Порог косинусной близости для семантического поиска', '0.0', '1.0', '0.62', 'threshold'),
('minMicroclusterSize', '2', 'INTEGER', 'Минимальный размер микрокластера', '1', '100', '2', 'threshold'),
('minMacroclusterSize', '2', 'INTEGER', 'Минимальный размер макрокластера', '1', '100', '2', 'threshold'),
('clusterGenerationThreshold', '0.60', 'DOUBLE', 'Минимальный score кластера для отправки в LLM', '0.0', '1.0', '0.60', 'threshold'),
('maxNoiseRatio', '0.35', 'DOUBLE', 'Максимальная доля шума в кластере', '0.0', '1.0', '0.35', 'threshold'),
('minSingleMessageTextLength', '500', 'INTEGER', 'Минимальная длина текста для single-message детекции', '100', '5000', '500', 'threshold'),
('maxProviderCallsPerRun', '30', 'INTEGER', 'Максимум вызовов провайдера за run', '1', '500', '30', 'budget'),
('maxCostUsdPerRun', '2.0', 'DOUBLE', 'Максимальная стоимость за run в USD', '0.1', '50.0', '2.0', 'budget')
ON CONFLICT (setting_key) DO UPDATE SET
    setting_value = EXCLUDED.setting_value,
    description = EXCLUDED.description,
    safe_min = EXCLUDED.safe_min,
    safe_max = EXCLUDED.safe_max,
    default_value = EXCLUDED.default_value,
    category = EXCLUDED.category;

-- 2. prompt_templates: LLM prompt templates with versioning
CREATE TABLE IF NOT EXISTS prompt_templates (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    stage VARCHAR(100) NOT NULL DEFAULT 'LLM_CLUSTER_JUDGE_AND_ROUTING',
    supported_modes_json JSONB NOT NULL DEFAULT '["CLUSTER","SINGLE_MESSAGE"]'::jsonb,
    system_prompt TEXT NOT NULL DEFAULT '',
    user_prompt_template TEXT NOT NULL DEFAULT '',
    output_schema_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    provider_route VARCHAR(100) DEFAULT 'ModelHub',
    model_name VARCHAR(100) DEFAULT 'gpt-5.5',
    fallback_model VARCHAR(100) DEFAULT 'claude-sonnet-4-6',
    version INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT true,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    variables_json JSONB DEFAULT '[]'::jsonb,
    metadata_json JSONB DEFAULT '{}'::jsonb,
    created_by VARCHAR(255) DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed default prompt templates
INSERT INTO prompt_templates (code, name, description, stage, supported_modes_json, system_prompt, user_prompt_template, output_schema_json, provider_route, model_name, fallback_model, version, is_active, status, variables_json)
VALUES
('LLM_CLUSTER_JUDGE_AND_ROUTING', 'LLM Judge & Routing',
 'Оценивает кластер или single message и решает, нужно ли генерировать material',
 'LLM_CLUSTER_JUDGE_AND_ROUTING',
 '["CLUSTER","SINGLE_MESSAGE"]'::jsonb,
 'Ты — эксперт по анализу сообщений из Telegram-чатов. Оцени, содержит ли предоставленный контекст полезную информацию для создания базы знаний (knowledge base).',
 '{{text}}
---
Контекст:
- Тип источника: {{sourceType}}
- Количество сообщений: {{messageCount}}
- Язык: {{language}}
- Похожие темы: {{topics}}
---
Прими решение: нужно ли создать материал (GENERATE) или пропустить (SKIP).',
 '{"decision": "GENERATE|SKIP", "artifactType": "GUIDE|NOTE|TROUBLESHOOTING_NOTE|COMPARISON_INSIGHT|PRICE_ACCESS_CARD|RESOURCE_CARD|RISK_NOTE|NEWS_SIGNAL|TREND_CLUSTER|NONE", "confidence": 0.0, "reason": "", "recommendedTitle": "", "generationInstructions": ""}'::jsonb,
 'ModelHub', 'gpt-5.5', 'claude-sonnet-4-6', 1, true, 'ACTIVE',
 '["text","sourceType","messageCount","language","topics"]'::jsonb),

('KNOWLEDGE_GENERATION', 'Генерация материалов',
 'Создаёт material из approved cluster/single message',
 'KNOWLEDGE_GENERATION',
 '["CLUSTER","SINGLE_MESSAGE"]'::jsonb,
 'Ты — ассистент по созданию базы знаний. Создай структурированный материал на основе предоставленного контекста.',
 '{{text}}
---
Контекст:
- Тип: {{sourceType}}
- Количество источников: {{messageCount}}
- Язык: {{language}}
- Дополнительные инструкции: {{generationInstructions}}
---
Создай материал в указанном формате.',
 '{"title": "", "artifactType": "GUIDE|NOTE|TROUBLESHOOTING_NOTE|COMPARISON_INSIGHT|PRICE_ACCESS_CARD|RESOURCE_CARD|RISK_NOTE|NEWS_SIGNAL|TREND_CLUSTER", "summary": "", "bodyMarkdown": "", "sourceMessageIds": [], "confidence": 0.0, "limitations": [], "tags": []}'::jsonb,
 'ModelHub', 'gpt-5.5', 'claude-sonnet-4-6', 1, true, 'ACTIVE',
 '["text","sourceType","messageCount","language","generationInstructions"]'::jsonb)

ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    stage = EXCLUDED.stage,
    supported_modes_json = EXCLUDED.supported_modes_json,
    system_prompt = EXCLUDED.system_prompt,
    user_prompt_template = EXCLUDED.user_prompt_template,
    output_schema_json = EXCLUDED.output_schema_json,
    provider_route = EXCLUDED.provider_route,
    model_name = EXCLUDED.model_name,
    fallback_model = EXCLUDED.fallback_model,
    variables_json = EXCLUDED.variables_json;

-- 3. prompt_template_versions: versioning history for prompt templates
CREATE TABLE IF NOT EXISTS prompt_template_versions (
    id BIGSERIAL PRIMARY KEY,
    prompt_template_id BIGINT NOT NULL REFERENCES prompt_templates(id) ON DELETE CASCADE,
    version INT NOT NULL,
    snapshot_json JSONB NOT NULL,
    change_reason TEXT,
    created_by VARCHAR(255) DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (prompt_template_id, version)
);

-- 4. Update provider_calls to link to prompt template version and mode
ALTER TABLE provider_calls ADD COLUMN IF NOT EXISTS prompt_template_id BIGINT REFERENCES prompt_templates(id);
ALTER TABLE provider_calls ADD COLUMN IF NOT EXISTS prompt_version INT;
ALTER TABLE provider_calls ADD COLUMN IF NOT EXISTS prompt_mode VARCHAR(50);
