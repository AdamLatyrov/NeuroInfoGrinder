UPDATE classifiers
SET
    name = 'Словарь AI-сигналов',
    status = 'DISABLED',
    updated_at = CURRENT_TIMESTAMP
WHERE name = 'AI Useful Content Keywords';

UPDATE classifiers
SET
    name = 'LLM: полезность AI-контента',
    updated_at = CURRENT_TIMESTAMP
WHERE name = 'AI Useful Content LLM';

UPDATE classifiers
SET
    name = 'Линейная модель полезности',
    updated_at = CURRENT_TIMESTAMP
WHERE name = 'AI Useful Content Linear';

UPDATE prompts
SET
    name = 'Классификация: полезность сообщения',
    type = 'CLASSIFICATION',
    updated_at = CURRENT_TIMESTAMP
WHERE name = 'Starter AI Classification Prompt';

UPDATE prompts
SET
    name = 'Генерация: черновик гайда',
    type = 'GENERATION',
    updated_at = CURRENT_TIMESTAMP
WHERE name = 'Starter Guide Generation Prompt';
