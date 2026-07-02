CREATE TABLE IF NOT EXISTS knowledge_topics (
    id BIGSERIAL PRIMARY KEY,
    slug TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    description TEXT,
    parent_topic_id BIGINT REFERENCES knowledge_topics(id),
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    source TEXT NOT NULL DEFAULT 'SYSTEM',
    sort_order INTEGER NOT NULL DEFAULT 100,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS knowledge_signals (
    id BIGSERIAL PRIMARY KEY,
    raw_message_id BIGINT REFERENCES raw_messages(id),
    dataset_message_id BIGINT REFERENCES dataset_messages(id),
    replay_run_id BIGINT REFERENCES replay_runs(id),
    replay_run_message_id BIGINT REFERENCES replay_run_messages(id),
    source_chat_id BIGINT,
    source_topic_id BIGINT,
    title TEXT NOT NULL,
    summary TEXT,
    signal_type TEXT NOT NULL,
    status TEXT NOT NULL,
    readiness TEXT NOT NULL DEFAULT 'NOT_MATERIAL_READY',
    reason TEXT,
    risk_flags_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    evidence_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    confidence NUMERIC(5,4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (raw_message_id),
    UNIQUE (dataset_message_id, replay_run_id)
);

CREATE TABLE IF NOT EXISTS knowledge_signal_topics (
    signal_id BIGINT NOT NULL REFERENCES knowledge_signals(id) ON DELETE CASCADE,
    topic_id BIGINT NOT NULL REFERENCES knowledge_topics(id),
    confidence NUMERIC(5,4) NOT NULL DEFAULT 0.5,
    source TEXT NOT NULL DEFAULT 'RULE',
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (signal_id, topic_id)
);

CREATE TABLE IF NOT EXISTS knowledge_item_topics (
    knowledge_item_id BIGINT NOT NULL REFERENCES knowledge_items(id) ON DELETE CASCADE,
    topic_id BIGINT NOT NULL REFERENCES knowledge_topics(id),
    confidence NUMERIC(5,4) NOT NULL DEFAULT 0.5,
    source TEXT NOT NULL DEFAULT 'RULE',
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (knowledge_item_id, topic_id)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_signals_status ON knowledge_signals(status);
CREATE INDEX IF NOT EXISTS idx_knowledge_signals_type ON knowledge_signals(signal_type);
CREATE INDEX IF NOT EXISTS idx_knowledge_signal_topics_topic ON knowledge_signal_topics(topic_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_item_topics_topic ON knowledge_item_topics(topic_id);

INSERT INTO knowledge_topics (slug, name, description, source, sort_order)
VALUES
    ('models-releases', 'Модели и релизы', 'Новые модели, доступность, benchmarks и product updates.', 'SYSTEM', 10),
    ('free-tokens-quotas', 'Бесплатные токены и квоты', 'Free-tier, лимиты, бонусы, бесплатные модели и daily quota.', 'SYSTEM', 20),
    ('providers-routers', 'Провайдеры и роутеры', 'Model providers, API routers, proxy endpoints и model routing.', 'SYSTEM', 30),
    ('api-integrations', 'API и интеграции', 'Endpoints, OpenAI-compatible API, keys, SDK, auth и config.', 'SYSTEM', 40),
    ('outages-limits', 'Сбои, лимиты и fallback', 'Outage, quota exhausted, degraded service, fallback и диагностика.', 'SYSTEM', 50),
    ('tools-repos', 'Инструменты и GitHub', 'Open-source tools, repositories, libraries и frameworks.', 'SYSTEM', 60),
    ('agents-prompts', 'Агенты и промпты', 'Prompt engineering, agents, Codex, Cursor и Claude Code workflows.', 'SYSTEM', 70),
    ('abuse-risk', 'Абьюзы и риск', 'Рефералки, обходы, накрутки, loopholes и другие risk-сигналы.', 'SYSTEM', 80),
    ('security-privacy', 'Безопасность и приватность', 'Credentials, tokens, proxy privacy, logs, account и data risks.', 'SYSTEM', 90),
    ('pricing-costs', 'Деньги, pricing и cache cost', 'Стоимость, billing, cache cost, тарифы и token spend.', 'SYSTEM', 100)
ON CONFLICT (slug) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    sort_order = EXCLUDED.sort_order,
    updated_at = now();
