# Clusters v2

Total clusters: `535`
Review clusters: `416`

## Decisions

- `REVIEW_SINGLE_SIGNAL` / одиночный сигнал на проверку: `187`
- `LINK_ENRICHMENT_FIRST` / сначала обогащение ссылки: `116`
- `CONTEXT_ONLY` / только контекст: `93`
- `MANUAL_REVIEW` / ручная проверка (риск): `78`
- `SIGNAL_PAIR` / пара сигналов по strong-сущности: `22`
- `CONTEXT_GROUP` / группа контекста (нет strong-сущности): `14`
- `WEAK_PAIR` / слабая пара (нет strong-сущности): `8`
- `SINGLE_MESSAGE_GUIDE_CANDIDATE` / содержательный одиночный гайд на проверку материала: `6`
- `SIGNAL_GROUP` / группа сигналов (недостаточно источников): `5`
- `BLOCKED` / заблокировано: `4`
- `EVIDENCE_GROUP_REVIEW` / группа доказательств на проверку материала: `1`
- `SINGLE_MESSAGE_REFERENCE_CANDIDATE` / одиночный технический справочник на проверку: `1`

## Top review clusters

### Риск / рефералы / абуз — ручная проверка

- cluster_id: `v2c-00010`
- candidates: `18`; context: `2`; решение: `MANUAL_REVIEW` / ручная проверка (риск)
- independent sources: `1`; conversations: `1`; reply chains: `0`
- shared strong entities: `['t.me']`

### Риск / рефералы / абуз — ручная проверка

- cluster_id: `v2c-00012`
- candidates: `15`; context: `2`; решение: `MANUAL_REVIEW` / ручная проверка (риск)
- independent sources: `3`; conversations: `1`; reply chains: `0`
- shared strong entities: `[]`

### Риск / рефералы / абуз — ручная проверка

- cluster_id: `v2c-00033`
- candidates: `12`; context: `8`; решение: `MANUAL_REVIEW` / ручная проверка (риск)
- independent sources: `7`; conversations: `10`; reply chains: `3`
- shared strong entities: `[]`

### Ссылки требуют обогащения

- cluster_id: `v2c-00231`
- candidates: `10`; context: `10`; решение: `LINK_ENRICHMENT_FIRST` / сначала обогащение ссылки
- independent sources: `1`; conversations: `1`; reply chains: `0`
- shared strong entities: `['kkinst.com']`

### Риск / рефералы / абуз — ручная проверка

- cluster_id: `v2c-00067`
- candidates: `9`; context: `18`; решение: `MANUAL_REVIEW` / ручная проверка (риск)
- independent sources: `3`; conversations: `4`; reply chains: `0`
- shared strong entities: `[]`

### Вакансии / события — агрегат только

- cluster_id: `v2c-00096`
- candidates: `6`; context: `0`; решение: `SIGNAL_GROUP` / группа сигналов (недостаточно источников)
- independent sources: `1`; conversations: `1`; reply chains: `0`
- shared strong entities: `['codex.sale', 't.me']`

### Ссылки требуют обогащения

- cluster_id: `v2c-00022`
- candidates: `5`; context: `0`; решение: `LINK_ENRICHMENT_FIRST` / сначала обогащение ссылки
- independent sources: `1`; conversations: `1`; reply chains: `0`
- shared strong entities: `['youtube.com']`

### Риск / рефералы / абуз — ручная проверка

- cluster_id: `v2c-00201`
- candidates: `5`; context: `4`; решение: `MANUAL_REVIEW` / ручная проверка (риск)
- independent sources: `5`; conversations: `1`; reply chains: `5`
- shared strong entities: `['senpis.xyz']`

### Тема: sonnet

- cluster_id: `v2c-00216`
- candidates: `5`; context: `17`; решение: `SIGNAL_GROUP` / группа сигналов (недостаточно источников)
- independent sources: `2`; conversations: `1`; reply chains: `2`
- shared strong entities: `['sonnet']`

### Риск / рефералы / абуз — ручная проверка

- cluster_id: `v2c-00021`
- candidates: `5`; context: `4`; решение: `MANUAL_REVIEW` / ручная проверка (риск)
- independent sources: `5`; conversations: `1`; reply chains: `4`
- shared strong entities: `[]`

### Тема: sonnet

- cluster_id: `v2c-00100`
- candidates: `4`; context: `4`; решение: `SIGNAL_GROUP` / группа сигналов (недостаточно источников)
- independent sources: `2`; conversations: `1`; reply chains: `1`
- shared strong entities: `['sonnet']`

### Ссылки требуют обогащения

- cluster_id: `v2c-00116`
- candidates: `4`; context: `9`; решение: `LINK_ENRICHMENT_FIRST` / сначала обогащение ссылки
- independent sources: `3`; conversations: `3`; reply chains: `0`
- shared strong entities: `['discord.gg']`

### Риск / рефералы / абуз — ручная проверка

- cluster_id: `v2c-00143`
- candidates: `4`; context: `20`; решение: `MANUAL_REVIEW` / ручная проверка (риск)
- independent sources: `3`; conversations: `1`; reply chains: `2`
- shared strong entities: `['t.me']`

### Ссылки требуют обогащения

- cluster_id: `v2c-00202`
- candidates: `4`; context: `4`; решение: `LINK_ENRICHMENT_FIRST` / сначала обогащение ссылки
- independent sources: `3`; conversations: `3`; reply chains: `0`
- shared strong entities: `['telegra.ph']`

### Риск / рефералы / абуз — ручная проверка

- cluster_id: `v2c-00219`
- candidates: `4`; context: `6`; решение: `MANUAL_REVIEW` / ручная проверка (риск)
- independent sources: `1`; conversations: `1`; reply chains: `0`
- shared strong entities: `['opus']`

### Ссылки требуют обогащения

- cluster_id: `v2c-00092`
- candidates: `4`; context: `4`; решение: `LINK_ENRICHMENT_FIRST` / сначала обогащение ссылки
- independent sources: `3`; conversations: `1`; reply chains: `1`
- shared strong entities: `[]`

### Риск / рефералы / абуз — ручная проверка

- cluster_id: `v2c-00107`
- candidates: `4`; context: `11`; решение: `MANUAL_REVIEW` / ручная проверка (риск)
- independent sources: `2`; conversations: `1`; reply chains: `4`
- shared strong entities: `[]`

### Риск / рефералы / абуз — ручная проверка

- cluster_id: `v2c-00050`
- candidates: `3`; context: `10`; решение: `MANUAL_REVIEW` / ручная проверка (риск)
- independent sources: `2`; conversations: `3`; reply chains: `0`
- shared strong entities: `['github.com', 'github.io']`

### Техническое обсуждение: r-api

- cluster_id: `v2c-00041`
- candidates: `3`; context: `3`; решение: `EVIDENCE_GROUP_REVIEW` / группа доказательств на проверку материала
- independent sources: `2`; conversations: `1`; reply chains: `3`
- shared strong entities: `['r-api']`

### Риск / рефералы / абуз — ручная проверка

- cluster_id: `v2c-00161`
- candidates: `3`; context: `9`; решение: `MANUAL_REVIEW` / ручная проверка (риск)
- independent sources: `3`; conversations: `2`; reply chains: `1`
- shared strong entities: `['senpi.one']`

### Тема: opus

- cluster_id: `v2c-00189`
- candidates: `3`; context: `15`; решение: `SIGNAL_GROUP` / группа сигналов (недостаточно источников)
- independent sources: `1`; conversations: `1`; reply chains: `0`
- shared strong entities: `['opus']`

### Ссылки требуют обогащения

- cluster_id: `v2c-00235`
- candidates: `3`; context: `23`; решение: `LINK_ENRICHMENT_FIRST` / сначала обогащение ссылки
- independent sources: `1`; conversations: `1`; reply chains: `0`
- shared strong entities: `['t.me']`

### Техническое обсуждение: deepseek

- cluster_id: `v2c-00335`
- candidates: `3`; context: `3`; решение: `SIGNAL_GROUP` / группа сигналов (недостаточно источников)
- independent sources: `1`; conversations: `1`; reply chains: `0`
- shared strong entities: `['deepseek']`

### Ссылки требуют обогащения

- cluster_id: `v2c-00017`
- candidates: `3`; context: `7`; решение: `LINK_ENRICHMENT_FIRST` / сначала обогащение ссылки
- independent sources: `3`; conversations: `1`; reply chains: `2`
- shared strong entities: `[]`

### Ссылки требуют обогащения

- cluster_id: `v2c-00083`
- candidates: `3`; context: `2`; решение: `LINK_ENRICHMENT_FIRST` / сначала обогащение ссылки
- independent sources: `2`; conversations: `1`; reply chains: `0`
- shared strong entities: `[]`

### Риск / рефералы / абуз — ручная проверка

- cluster_id: `v2c-00232`
- candidates: `3`; context: `1`; решение: `MANUAL_REVIEW` / ручная проверка (риск)
- independent sources: `2`; conversations: `1`; reply chains: `2`
- shared strong entities: `[]`

### Тема: deepseek

- cluster_id: `v2c-00253`
- candidates: `2`; context: `0`; решение: `SIGNAL_PAIR` / пара сигналов по strong-сущности
- independent sources: `1`; conversations: `1`; reply chains: `0`
- shared strong entities: `['deepseek', 'gpt-5', 'opus', 't.me']`

### Тема: claudecode

- cluster_id: `v2c-00472`
- candidates: `2`; context: `0`; решение: `SIGNAL_PAIR` / пара сигналов по strong-сущности
- independent sources: `1`; conversations: `2`; reply chains: `0`
- shared strong entities: `['claudecode', 'github.com', 'githubusercontent.com', 'mcp']`

### Вакансии / события — агрегат только

- cluster_id: `v2c-00065`
- candidates: `2`; context: `0`; решение: `SIGNAL_PAIR` / пара сигналов по strong-сущности
- independent sources: `1`; conversations: `1`; reply chains: `0`
- shared strong entities: `['instagram.com', 't.me', 'youtube.com']`

### Ссылки требуют обогащения

- cluster_id: `v2c-00031`
- candidates: `2`; context: `12`; решение: `LINK_ENRICHMENT_FIRST` / сначала обогащение ссылки
- independent sources: `2`; conversations: `2`; reply chains: `0`
- shared strong entities: `['vibemod', 'vibemod.pro']`

### Тема: popolama.com

- cluster_id: `v2c-00068`
- candidates: `2`; context: `0`; решение: `SIGNAL_PAIR` / пара сигналов по strong-сущности
- independent sources: `1`; conversations: `2`; reply chains: `0`
- shared strong entities: `['popolama.com', 't.me']`

### Тема: codex

- cluster_id: `v2c-00195`
- candidates: `2`; context: `20`; решение: `SIGNAL_PAIR` / пара сигналов по strong-сущности
- independent sources: `1`; conversations: `1`; reply chains: `1`
- shared strong entities: `['codex', 'gpt5']`

### Риск / рефералы / абуз — ручная проверка

- cluster_id: `v2c-00268`
- candidates: `2`; context: `1`; решение: `MANUAL_REVIEW` / ручная проверка (риск)
- independent sources: `1`; conversations: `1`; reply chains: `0`
- shared strong entities: `['gemini', 't.me']`

### Ссылки требуют обогащения

- cluster_id: `v2c-00014`
- candidates: `2`; context: `16`; решение: `LINK_ENRICHMENT_FIRST` / сначала обогащение ссылки
- independent sources: `2`; conversations: `2`; reply chains: `0`
- shared strong entities: `['anthropic.com']`

### Ссылки требуют обогащения

- cluster_id: `v2c-00018`
- candidates: `2`; context: `5`; решение: `LINK_ENRICHMENT_FIRST` / сначала обогащение ссылки
- independent sources: `1`; conversations: `1`; reply chains: `0`
- shared strong entities: `['tiktok.com']`

### Тема: droid

- cluster_id: `v2c-00020`
- candidates: `2`; context: `27`; решение: `SIGNAL_PAIR` / пара сигналов по strong-сущности
- independent sources: `1`; conversations: `1`; reply chains: `0`
- shared strong entities: `['droid']`

### Техническое обсуждение: github.com

- cluster_id: `v2c-00024`
- candidates: `2`; context: `4`; решение: `SIGNAL_PAIR` / пара сигналов по strong-сущности
- independent sources: `2`; conversations: `1`; reply chains: `0`
- shared strong entities: `['github.com']`

### Вакансии / события — агрегат только

- cluster_id: `v2c-00043`
- candidates: `2`; context: `0`; решение: `SIGNAL_PAIR` / пара сигналов по strong-сущности
- independent sources: `1`; conversations: `1`; reply chains: `0`
- shared strong entities: `['mts-link.ru']`

### Тема: droid

- cluster_id: `v2c-00052`
- candidates: `2`; context: `22`; решение: `SIGNAL_PAIR` / пара сигналов по strong-сущности
- independent sources: `1`; conversations: `1`; reply chains: `2`
- shared strong entities: `['droid']`

### Тема: mcp

- cluster_id: `v2c-00057`
- candidates: `2`; context: `9`; решение: `SIGNAL_PAIR` / пара сигналов по strong-сущности
- independent sources: `1`; conversations: `1`; reply chains: `1`
- shared strong entities: `['mcp']`
