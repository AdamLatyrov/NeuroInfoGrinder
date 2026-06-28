# Pipeline Tuning Observability

Этот документ описывает преддеплойный слой диагностических логов для настройки правил, классификаторов, линейных моделей и промптов.

## Operability Requirements

| Priority | Requirement | Owner |
|---|---|---|
| Must | Каждое решение pipeline должно иметь trace с `stage`, `status`, `score`, `reason` и ссылками на `messageId`, `classifierId`, `promptId`, `providerId` или `ruleId`. | Backend |
| Must | Trace для классификатора и генерации должен хранить `configSnapshotJson`, чтобы старые решения можно было анализировать после изменения конфигов. | Backend |
| Must | Агент должен получать пачку спорных кейсов через JSONL export без прямого доступа к БД. | Backend |
| Must | Trace-таблица должна иметь retention, чтобы диагностика не росла бесконечно на VPS. | Backend/Ops |
| Should | Спорные кейсы должны содержать исходный текст сообщения, сохраненные `ruleResultJson`, `signalBreakdown`, `classifierResultJson`, `messageIntelligenceJson`. | Backend |
| Should | UI мониторинга может поверх API показывать отдельную вкладку tuning cases. | Frontend |

## Endpoints

Последние спорные кейсы:

```bash
curl -s "http://127.0.0.1:8080/api/v1/traces/tuning-cases?problemOnly=true&page=0&size=50"
```

JSONL export для агента:

```bash
curl -s "http://127.0.0.1:8080/api/v1/traces/tuning-cases/export?problemOnly=true&limit=200" > tuning-cases.jsonl
```

Фильтры:

```text
from, to, stage, status, classifierId, promptId, ruleId, groupId, problemOnly, page, size, limit
```

Примеры:

```bash
curl -s "http://127.0.0.1:8080/api/v1/traces/tuning-cases?stage=CLASSIFICATION&classifierId=3&problemOnly=true&page=0&size=100"
curl -s "http://127.0.0.1:8080/api/v1/traces/tuning-cases/export?stage=GUIDE_GENERATION&status=FAILED&limit=100"
```

## What To Inspect

| Goal | Fields |
|---|---|
| Править правила | `stage=RULES`, `ruleResultJson`, `ruleId`, `conditionsJson`, `tuningHint`, `messageText` |
| Править keyword/regex classifier | `stage=CLASSIFICATION`, `configSnapshotJson.classifier.keywords`, `regexPattern`, `score`, `reason`, `messageText` |
| Править linear model | `configSnapshotJson.classifier.modelConfigJson`, `outputData`, `score`, `agentFocus`, `classifierResultJson` |
| Править LLM prompt | `configSnapshotJson.prompt.content`, `outputData`, `reason`, `evidenceMessageIds`, `messageText` |
| Править generation prompt | `stage=GUIDE_GENERATION`, `configSnapshotJson.prompt.content`, `confidence`, `errorMessage`, `outputData` |
| Искать шум | `spamScore`, `processingStatus=SKIPPED`, `reason`, `signalBreakdown` |

## Runbook

1. Открыть tuning cases за последние 7 дней с `problemOnly=true`.
2. Сгруппировать по `stage`, `status`, `entityName`.
3. Для false negative смотреть `messageText`, `signalBreakdown`, `classifierResultJson`, `configSnapshotJson`.
4. Для false positive смотреть `spamScore`, `matched labels`, `ruleResultJson`, `keywords/regex/model weights`.
5. Перед правкой промпта сохранить текущую версию через поле `version`; после правки поднять версию.
6. После правки прогнать несколько сообщений вручную и сравнить новые traces со старыми.
7. Если `GUIDE_GENERATION` часто `FAILED`, сначала проверить provider health и raw model output, потом ужесточать prompt JSON constraints.

## Alert Matrix

| Alert | Trigger | Severity | Action |
|---|---|---|---|
| Trace write failures | backend logs contain trace persistence errors | High | Check Postgres health, disk, migrations |
| High failed generation | many `GUIDE_GENERATION/FAILED` in tuning cases | Medium | Inspect provider/model, prompt snapshot, raw output |
| Classifier drift | sudden spike in `CLASSIFICATION/SKIPPED` or borderline scores | Medium | Review changed classifier/prompt versions |
| Rule overblocking | many useful messages with `RULES/REJECTED` | Medium | Inspect `ruleId` and conditions snapshot |
| Trace storage growth | disk or DB grows faster than expected | Medium | Lower `PIPELINE_TRACE_RETENTION_DAYS` or export/archive first |

## Support Handoff

System overview:

- Backend writes decision traces to `pipeline_traces`.
- Trace rows now include configuration snapshots and tuning hints.
- `tuning-cases` joins traces with message intelligence fields for analysis.
- JSONL export is intended for agent-assisted tuning.

Failure modes:

- Postgres unavailable: traces and pipeline persistence fail.
- Provider unavailable: generation/classification traces show provider errors.
- Bad classifier/prompt config: traces show low scores, malformed output, or skipped decisions.
- Disk pressure: lower trace retention or export/delete old traces.

Retention:

```env
PIPELINE_TRACE_RETENTION_DAYS=45
PIPELINE_TRACE_RETENTION_CLEANUP_CRON=0 20 3 * * *
```
