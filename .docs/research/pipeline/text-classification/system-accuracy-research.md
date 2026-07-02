<!-- markdownlint-disable-file -->
# Task Research Notes: точность обработки и классификации текста

## Research Executed

### File Analysis
- `AGENTS.md`, `_agent/PROJECT_MEMORY.md`
  - Зафиксированы ограничения: только read-only production-проверки, без reprocess/backlog, изменения prompt/provider/threshold и write-запросов в production DB.
- `backend/2.0/src/main/java/com/larbcorp/neuroinfogrinder2/replay/ReplayV2Service.java`
  - Прослежен основной путь: normalization/rules → worker classification → embeddings → exact dedupe → semantic graph/microclusters → macroclusters → scoring → single-message/discussion candidates → route gate → LLM judge → generation → DRAFT knowledge item.
- `backend/2.0/model-worker/app.py` и Dockerfile-ы worker-а
  - `/classify` является regex bootstrap-классификатором, несмотря на имя `BOOTSTRAP_BERT_CLASSIFIER`.
  - При отсутствии `sentence-transformers` `/embed-batch` использует `DEGRADED_HASH_VECTOR`.
  - Standard fast image ставит только `requirements.txt`, где нет `sentence-transformers`, `scikit-learn` и `joblib`; heavy image ставит `requirements.heavy.txt`.
- `MessageUsefulnessClassifier.java`, `DefaultRouteIntelligenceEngine.java`, `DefaultFeatureVectorBuilder.java`
  - Usefulness — детерминированное keyword/regex-дерево; route intelligence — rules-first слой поверх worker stage predictions.
  - Classical-ML message trace запускается до основного worker classification, поэтому `classifierLabels` в этот момент ещё отсутствуют.
  - Worker fallback ожидает top-level `contentClass`, `candidateRoute`, `proposedMaterialType`, `rejectReason`, но `DefaultFeatureVectorBuilder` их не записывает.
- Исторические отчёты `reports/*20260625..20260629*`
  - Разделены эвристические оценки, ручная ground truth, контролируемые сценарии и provider spot-checks.

### Code Search Results
- `BOOTSTRAP_BERT_CLASSIFIER`
  - Имя сохраняется в model metadata/DB, но реализация в `classify_one` — regex по error/link/price/API/question/risk/release/guide/promo/news.
- `DEGRADED_HASH_VECTOR`
  - Backend принимает degraded embeddings как обработанный режим и продолжает semantic clustering.
- `classicalMlShadowEnabled`
  - Слой называется shadow и пишет trace, но его `blocked` policy flags реально используются для pre-LLM hard block risk/duplicate/promo/abuse/final-gate случаев.

### External Research
- Не выполнялось: выводы относятся к фактической реализации и данным проекта; внешние источники не нужны для установления текущей точности.

### Project Conventions
- Standards referenced: `AGENTS.md`, `_agent/PROJECT_MEMORY.md`, task-research skill.
- Instructions followed: исходники/config не изменяются; production только read-only; исследовательский артефакт хранится под `.docs/research/`.

## Key Discoveries

### Project Structure
- Есть три параллельно влияющих классификационных слоя: bootstrap worker labels, internal usefulness rules и classical-ML/route rules; затем LLM judge.
- Semantic clustering зависит от типа реально выданных embeddings, а не только от зарегистрированного имени модели `BAAI/bge-m3`.
- Single-message и discussion-segment маршруты решают разные задачи; ручной аудит показал, что большая доля полезного знания находится на уровне диалога.

### Verified Quality Evidence
- Актуальный локальный backend suite: `117/117` тестов успешно, `mvn test`, 2026-06-29. Это regression/contract coverage, не оценка статистической точности.
- Python worker прошёл только syntax-check `python -m py_compile app.py`; Python unit/integration tests и CI workflow в workspace не найдены.
- Ручной overnight audit: 919 processable сообщений; минимум 5 явно описанных single-message false negatives; 6 из 8 полезных ожидаемых материалов требовали discussion-segment сборки.
- Старый широкий heuristic audit: 3 354 сообщений, estimated recall `0.0189`, estimated material precision `0.8`; сам отчёт помечает классификацию как heuristic triage, поэтому значения нельзя считать современной ground truth метрикой.
- Controlled 10-scenario check до type-gate fix: 4 pass, 2 partial, 3 type errors, 1 misclustered case.
- Controlled artifact-type gate после fix: 6/6 сценариев прошли, включая GUIDE/GENERATION/ANSWER/SUMMARY/REFERENCE и duplicate suppression; это узкий сценарный тест, не population accuracy.
- Classical golden batch: stage labels 8/8 ожидаемые, но все 8 rows abstained; вход — evidence snapshots, а не полный raw-text validation set.
- Message-usefulness LLM dry-run: 2 успешных positive accepts, 1 pre-gate negative reject, 4 HTTP 502 inconclusive; выборка недостаточна для precision/recall.
- Discussion fixture check: 4 из 5 ожидаемых positive discussion fixtures обнаружены, 2 из 2 negative fixtures отклонены. Это даёт fixture-level recall `80%` и specificity `100%` только на семи заранее выбранных случаях; population precision неизвестна, поскольку scorer принял 17 сегментов, а fixture positives среди них — 4.
- Offline повтор актуального `MessageUsefulnessClassifier` на пяти задокументированных natural false negatives дал 4 `LOW_VALUE/REJECT`; только raw `6765` стал `STATUS_OUTAGE/SUMMARY`, хотя ручная цель была discussion-level GUIDE. Значит, synthetic unit coverage не устранила natural-language recall gap.
- Историческая ручная проверка 17 материалов до последних safety/type fixes: 9 `GOOD`, 3 `LOW_VALUE`, 2 `SHOULD_NOT_EXIST`, 2 `OK_BUT_WRONG_TYPE`, 1 `REVIEW`. Строгая доля `GOOD` — `52.9%`; это исторический baseline, не текущая precision. Поздняя контролируемая проверка трёх discussion-материалов дала 2 `GOOD` и 1 `OK_NEEDS_MINOR_PROMPT_TWEAK`.

### Current Production Read-only Snapshot
- Все сервисы healthy; public URL HTTP 200. На момент проверки: `15 590` raw rows, `12 946` processed, fresh raw gap `1`; fresh ingest работает.
- Model-worker действительно загрузил `BAAI/bge-m3`, status `OK`, dimension `1024`; production image содержит `sentence-transformers 5.6.0`, `scikit-learn 1.9.0`, `joblib 1.5.3`.
- Узкий BGE sanity-check: две перефразировки про API 429/quota имели cosine `0.688` и прошли production threshold `0.62`; несвязанные пары были `0.4018–0.4590`. Это подтверждает работающий semantic signal, но не cluster precision.
- Trained classical artifacts отсутствуют: `/app/artifacts` пуст, `/api/v2/classical-ml/models` вернул `trainedRegistry={}`.
- Training state: `11 833` examples, все split `UNASSIGNED`; `11 724` weak, `109` sourced from accepted knowledge item, `0` strong и `0` human-reviewed. `readyForClassifierV1=false`.
- Classical message traces: `1 308` total, `998` abstained (`76.3%`), `223` blocked (`17.0%`), только `13` require Judge (`1.0%`).
- Unique configured provider requests: LLM route judge `115/152` succeeded (`75.7%`), generation `53/54` (`98.1%`), discussion judge `11/11`; judge availability остаётся заметным источником false negatives.
- Production materials на момент проверки: `38` historical rows, все soft-deleted; active materials `0`. Поэтому текущую end-to-end material precision нельзя измерить на активной выдаче.

### Implementation Patterns
- Exact duplicate detection использует hash полностью нормализованного текста; near-duplicate semantic suppression не является надёжно обученным dedupe-моделем.
- Macrocluster grouping использует `artifactType + firstWord(title)`, то есть topic purity зависит от первой лексемы заголовка, а не от полноценного clustering objective.
- Topic discovery — частота токенов длиной >3; это не topic model.
- Cluster scores вычисляются формулами от размера кластера; поля pain/WTP/publishability/novelty выглядят как эвристические значения, не калиброванные вероятности.
- LLM judge остаётся критическим quality gate, но provider errors делают часть решений inconclusive.
- `fallback_model_id` читается из route, но не используется в `callJson`; retries повторяют primary provider/model.
- После успешного Judge и ошибки generation single-message path может создать lightweight DRAFT fallback. Это повышает availability, но требует отдельной маркировки и quality metric.

### Accuracy by Layer

| Слой | Проверенная оценка | Вывод |
| --- | --- | --- |
| Ingest/scope | fresh raw gap `1`, services healthy, regression tests pass | operationally healthy; это не content accuracy |
| Text extraction | text → caption fallback, hidden links из entities/raw JSON | базовый путь хороший; raw JSON не является fallback-текстом, media context ограничен |
| Bootstrap classification | regex labels с фиксированными confidence; holdout отсутствует | статистическая точность неизвестна, confidence не калиброван |
| Usefulness rules | 14/14 synthetic tests; 4/5 natural documented misses всё ещё reject | хороши как safety/routing rules, недостаточны как recall classifier |
| BGE embeddings | real BGE-M3, sanity pair `0.688` vs unrelated `<0.46` | модель работает; cluster quality не измерена |
| Micro/macro clustering | connected components + `artifactType:firstWord(title)` | основной риск смешивания/разрыва тем; accuracy неизвестна |
| Discussion retrieval | fixture positives `4/5`, negatives `2/2` | перспективно, но выборка мала и population precision неизвестна |
| Classical ML | no trained registry, 0 strong/human labels, abstain `76.3%` | это heuristic scaffold, не валидированная ML-модель |
| LLM Judge | selected reviews выглядят grounded; unique request success `75.7%` | quality promising on biased samples, availability and general accuracy insufficient |
| Artifact type gate | controlled `6/6` | текущий узкий gate точен на сценариях |
| Final material quality | historical strict GOOD `9/17`; current active `0` | текущая population precision не измерима |

### Critical Correctness and Governance Findings

1. **Reported ML metrics are not measured.** При пустом registry `/classical-ml/metrics` возвращает hard-coded `macroF1` (`0.58`, `0.55`, `0.52`, `0.49`) со status `SUCCESS`; `/models` перечисляет `xgboost-placeholder`, `hdbscan-signals` и другие предполагаемые компоненты, хотя trained artifacts отсутствуют.
2. **`shadow` реально влияет на материализацию.** Settings description обещает trace-only поведение, но `routeIntelligence.blocked` используется для pre-LLM hard block по risk/duplicate/promo/abuse/final gate.
3. **Displayed/configured thresholds не всегда являются effective runtime values.** `semanticSimilarityThreshold=0.62` показан в settings, но live stages выполняются с `0.66`, потому что `ReplayRequest` использует hard-coded default. Ряд settings (`classificationConfidenceThreshold`, `noiseSuppressThreshold`, `clusterCandidateThreshold`, `minMicroclusterSize`, `minMacroclusterSize`, `clusterGenerationThreshold`, `maxNoiseRatio`) в processing code не используется.
4. **Live run budget snapshot игнорируется ReplayRequest.** Live config snapshot показывает `maxProviderCalls=10`, `maxCostUsd=1`, но `runExistingLiveAutoRun` создаёт request с null и получает defaults `50` и `2`. Фактически ни один проверенный run не превысил 4 calls, поэтому это latent control bug, а не зафиксированный перерасход.
5. **Bootstrap model name misleading.** `BOOTSTRAP_BERT_CLASSIFIER` — regex classifier; сохранённые confidence в основном фиксированы (`0.72`, `0.64`, `0.74` и т.д.), а не являются вероятностями модели.
6. **Macrocluster score не является quality probability.** Он растёт в основном от количества members, а macro grouping опирается на первую лексему title.
7. **Production active output пуст.** Все 38 knowledge items soft-deleted; UI/API без authenticated active set не даёт текущей выборки для material-level acceptance.

### API and Schema Documentation
- Основные evidence tables: `message_intelligence`, `message_classifications`, `message_embeddings`, `semantic_neighbors`, `microclusters`, `macroclusters`, `cluster_scores`, `discussion_segments`, `provider_calls`, `knowledge_items`, `knowledge_item_sources`.
- Trace хранит worker/classical/usefulness решения, но названия моделей и режимы degraded могут создавать ложное впечатление о фактически применённой модели.

### Technical Requirements
- Нужен versioned human-labeled evaluation set с raw text, chat/thread context, expected route, material type, safety decision и duplicate group.
- Метрики должны считаться отдельно по stage и end-to-end: coverage, candidate precision/recall, route/type macro-F1, safety false-negative rate, duplicate leakage, discussion retrieval, judge availability/accuracy, final material groundedness.
- Production dashboard должен различать real BGE-M3 vs degraded hash embeddings и trained model vs heuristic fallback.
- Accuracy endpoints не должны возвращать synthetic defaults как measured metrics; при отсутствии holdout/registry нужен status `NOT_EVALUATED`.
- Effective settings snapshot должен формироваться из тех же значений, которые реально использует execution path.

## Recommended Approach

Выбранный рекомендуемый путь: **measurement-first calibration**. Не менять глобальные thresholds и не обучать модель на текущих weak labels как на истине. Сначала сделать telemetry честной, собрать versioned human-labeled benchmark, затем калибровать rules/classical ML/LLM на одной и той же holdout выборке.

### Alternatives and Trade-offs

- **A. Measurement-first calibration — рекомендуется.** Сначала исправить model/metrics/settings truthfulness и собрать 500+ human-reviewed примеров. Медленнее до первого «улучшения», но единственный путь получить настоящие precision/recall и не переобучиться на собственных ошибках pipeline.
- **B. Сразу расширять regex/usefulness rules.** Быстро закроет известные raw-like misses, но увеличит overlap правил и будет оптимизацией под несколько примеров без контроля false positives.
- **C. Сразу обучить sklearn stages на 11 833 weak examples.** Технически возможно, но модель воспроизведёт bootstrap/LLM biases; все splits сейчас `UNASSIGNED`, human labels `0`, поэтому опубликованные F1 будут ненадёжны.

## Implementation Guidance
- **Objectives**: получить измеримую, воспроизводимую точность end-to-end и убрать silent degraded/heuristic modes.
- **P0 — truthfulness/control**:
  1. Возвращать `NOT_TRAINED/NOT_EVALUATED` вместо hard-coded models/F1 при пустом registry.
  2. Либо сделать classical layer действительно trace-only, либо переименовать setting и явно показывать, что он hard-blocking.
  3. Связать live `ReplayRequest` с effective settings/config snapshot; удалить или реализовать dead settings.
  4. Добавить тесты, доказывающие совпадение UI setting, run snapshot и stage actual value.
- **P1 — evaluation**:
  1. Собрать 500–1 000 independently reviewed messages/segments с минимум 50 examples на rare class и split по времени+чату без leakage.
  2. Зафиксировать labels: material/no-material, content class, route/type, needs-context, safety, duplicate group, final groundedness.
  3. Считать confusion matrices, macro-F1, per-class precision/recall, calibration ECE, abstain coverage и end-to-end material precision/recall.
  4. Добавить Python tests для worker inference/training/artifact loading и CI gate.
- **P2 — model quality**:
  1. Улучшать natural-language recall после benchmark: resume before/after, compact checklist, technical explanation, status vs guide.
  2. Заменить `firstWord(title)` macro grouping на embedding/entity-aware clustering с cluster-purity evaluation.
  3. Реализовать provider fallback model и маркировать lightweight fallback-created materials.
- **Dependencies**: полный raw-text evaluation export, независимая ручная разметка, стабильный worker/provider, model/version telemetry.
- **Success Criteria**:
  - no synthetic metrics presented as measured;
  - 100% effective-setting parity between snapshot and executed stage;
  - holdout material-candidate precision ≥ `0.85`, recall ≥ `0.70` как первый рабочий target;
  - safety false-negative rate `0` на reviewed safety set;
  - duplicate leakage ≤ `0.02`;
  - Judge unique-request success ≥ `0.95` с fallback;
  - active DRAFT material audit ≥ `0.85` grounded/useful before any broader generation.
