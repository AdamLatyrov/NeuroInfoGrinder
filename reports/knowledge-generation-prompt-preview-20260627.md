# Knowledge Generation Prompt Preview - 2026-06-27

## Scope

TASK 11 prepares a safer `KNOWLEDGE_GENERATION` prompt for review only. No production prompt template was changed, no provider settings were changed, and no material was generated or published by this preview.

## Current Production Prompt

System prompt:

```text
Ты — ассистент по созданию базы знаний. Создай структурированный материал на основе предоставленного контекста.
```

User prompt template:

```text
{{text}}
---
Контекст:
- Тип: {{sourceType}}
- Количество источников: {{messageCount}}
- Язык: {{language}}
- Дополнительные инструкции: {{generationInstructions}}
---
Создай материал в указанном формате.
```

## Proposed System Prompt

```text
Ты — эксперт по созданию прикладных материалов базы знаний NeuroInfoGrinder.

Твоя задача — превратить подтвержденный источник или группу источников в самостоятельный полезный материал, а не пересказать чат.

Правила:
1. Пиши как готовую справку, инструкцию, ответ, обзор или карточку ресурса для читателя, который не видел исходный чат.
2. Не используй мета-формулировки вроде "в обсуждении", "участники чата", "из сообщений следует", если это не критично для смысла.
3. Сохраняй проверяемые факты из источников. Не добавляй неподтвержденные возможности, цены, лимиты, версии, юридические выводы или технические гарантии.
4. Если источник касается внешнего API, провайдера, модели, тарифа, лимитов, cache или доступности модели, добавь ограничение: "Проверьте актуальную документацию провайдера: лимиты, тарифы, поведение cache и доступность моделей могут меняться."
5. Если источник касается репозитория, библиотеки, инструмента или ссылки на ресурс, добавь ограничение: "Перед использованием проверьте репозиторий, лицензию, активность, issues и требования безопасности."
6. Если материал может быть риск-sensitive, обходом ограничений, злоупотреблением, фродом или промо без практического контекста, не превращай его в инструкцию по действию. Верни безопасное предупреждение или укажи ограничение.
7. Для одиночных новостей и обновлений предпочитай краткий REFERENCE/NEWS_SIGNAL/SUMMARY, а не длинную инструкцию.
8. Для link-only источников без контекста явно укажи, что данных недостаточно для полноценного материала.
9. Возвращай только JSON по заданной схеме. Markdown используй только внутри поля bodyMarkdown.
```

## Proposed User Prompt Template

```text
Источник:
{{text}}

---
Контекст генерации:
- Тип источника: {{sourceType}}
- Количество источников: {{messageCount}}
- Язык материала: {{language}}
- Инструкции judge/router: {{generationInstructions}}

---
Собери материал по схеме JSON:
- title: короткий конкретный заголовок без кликбейта;
- artifactType: тип, совместимый с инструкциями judge/router;
- summary: 1-3 предложения о пользе материала;
- bodyMarkdown: самостоятельный материал с практической структурой;
- sourceMessageIds: ids исходных сообщений, если они явно доступны в источнике;
- confidence: уверенность 0.0-1.0;
- limitations: проверяемые ограничения, caveats и недостающий контекст;
- tags: 3-7 коротких тегов.

Не добавляй материалы, которых нет в источнике. Не переписывай рискованный контент как инструкцию по обходу ограничений.
```

## Old vs New

| Area | Current | Proposed |
| --- | --- | --- |
| Standalone style | Generic material request | Explicit standalone knowledge-base artifact |
| Meta-chat phrasing | Not forbidden | Explicitly forbidden except when essential |
| Provider/API caveats | Not enforced | Required for API/provider/model/pricing/limit/cache claims |
| Repo/resource caveats | Not enforced | Required for repos/libraries/tools/resource links |
| Risk-sensitive content | Not specified | Must not become bypass/abuse instructions |
| Link-only content | Not specified | Must mark insufficient context |
| JSON schema | Existing schema | Preserved exactly |
| Production activation | Active | Preview only; not activated |

## Example Input

```text
Источник:
OpenMontage: open-source tool for creating montage videos from scenes. GitHub: https://github.com/example/openmontage

---
Контекст генерации:
- Тип источника: SINGLE_MESSAGE
- Количество источников: 1
- Язык материала: ru
- Инструкции judge/router: Create a RESOURCE_REFERENCE material for a developer audience.
```

## Example Output Preview

```json
{
  "title": "OpenMontage: ресурс для сборки монтажных видео",
  "artifactType": "RESOURCE_CARD",
  "summary": "Краткая карточка ресурса для разработчиков, которые оценивают инструмент для создания монтажных видео из сцен.",
  "bodyMarkdown": "## Что это\nOpenMontage описан как open-source инструмент для создания монтажных видео из сцен.\n\n## Когда может быть полезен\n- Быстро оценить подход к автоматизации видеомонтажа.\n- Найти пример реализации или библиотеку для собственного pipeline.\n\n## Что проверить перед использованием\n- Поддерживаемые форматы входных и выходных файлов.\n- Требования к окружению и зависимостям.\n- Активность репозитория и качество issues/PR.\n- Лицензию и ограничения для коммерческого использования.",
  "sourceMessageIds": [],
  "confidence": 0.72,
  "limitations": [
    "Перед использованием проверьте репозиторий, лицензию, активность, issues и требования безопасности.",
    "В источнике нет деталей о поддерживаемых форматах, производительности и лицензии."
  ],
  "tags": ["open-source", "video", "tooling", "resource"]
}
```

## Acceptance Notes

- Preserves the existing `KNOWLEDGE_GENERATION` output schema.
- Does not require backend code changes.
- Does not alter provider routing or selected model.
- Needs explicit approval before production DB prompt update.
- Suggested verification before activation: run prompt test using a non-null synthetic run or fix `PromptTemplateService.testPrompt` null-run handling first.
