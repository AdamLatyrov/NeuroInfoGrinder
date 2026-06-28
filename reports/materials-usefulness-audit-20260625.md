# Current Materials Usefulness Audit - 2026-06-25

Scope: read-only production `/api/v1/materials` list plus detail fetches. No DB writes, no requeue, no scoring changes.

## Summary

- Total materials: 13
- Detail opens: 13/13
- Broken detail endpoints: 0
- Draft materials: 13/13
- Average backend quality score: 90.8/100
- Types: GUIDE=5, CLUSTER_SUMMARY=1, GENERATION=6, ANSWER=1

## Row Review

| ID | Opens | Type | Quality | Sources | Calls | Cost USD | Usefulness note | Title |
|---:|:---:|---|---:|---:|---:|---:|---|---|
| 14 | yes | GUIDE | 78 | 1 | 2 | 0.03578 | Useful but needs human review | Единая точка переключения нескольких ChatGPT Plus аккаунтов для VPS, Codex CLI и Desktop |
| 13 | yes | CLUSTER_SUMMARY | 98 | 4 | 2 | 0.01788 | Questionable as material type; reads like meta-summary | Приветственное сообщение для новых участников сообщества |
| 12 | yes | GUIDE | 82 | 1 | 3 | 0.02599 | Good single-message draft; verify source context | Как оптимизировать резюме для AI-фильтров и HR на hh |
| 11 | yes | GENERATION | 93 | 2 | 3 | 0.02537 | Useful technical note | Подборки полезных промтов для ChatGPT |
| 10 | yes | GUIDE | 96 | 1 | 2 | 0.01366 | Good single-message draft; verify source context | Мини-гайд по проверке OpenAI-compatible API в Cursor |
| 9 | yes | GENERATION | 93 | 2 | 3 | 0.01188 | Low immediate product relevance unless career tracking is desired | Anthropic Careers Overview |
| 8 | yes | ANSWER | 95 | 2 | 11 | 0.10018 | Useful technical note | Требования BGE-M3: RAM и размерность эмбеддингов |
| 7 | yes | GENERATION | 86 | 5 | 11 | 0.10018 | Useful technical note | Краткая справка по Hugging Face и инфраструктуре для ML/RAG-проекта |
| 6 | yes | GUIDE | 91 | 5 | 11 | 0.10018 | Useful technical note | Fixing Docker Build Failures for PyTorch CPU-Only Python Images |
| 5 | yes | GUIDE | 93 | 2 | 11 | 0.10018 | Useful technical note | FastAPI: запуск и production deployment |
| 4 | yes | GENERATION | 94 | 2 | 11 | 0.10018 | Useful technical note | Ошибка импорта SentenceTransformer из sentence_transformers |
| 3 | yes | GENERATION | 95 | 3 | 4 | 0.08333 | Useful technical note | Scoped Acceptance Run Budget Guidance for ModelHub and Telegram Backfill |
| 2 | yes | GENERATION | 86 | 5 | 4 | 0.08333 | Useful technical note | Spring Pipeline Operations: Timeouts, Health Checks, Provider Limits, and Trace-Based Debugging |

## Main Findings

- The material set is small but not junk: most items are practical dev/AI ops notes and several are directly actionable.
- The biggest user-facing issue is not only content quality; it is navigation and trust. Users cannot quickly see what is a guide, what is a generated summary, what has sources, and what is safe to open.
- All detail endpoints opened on the repeated UTF-8 audit; the list still needs to surface trust signals instead of presenting all drafts as equally ready.
- All materials are drafts, so publication state is not helping prioritization yet.
- `CLUSTER_SUMMARY` appears in production data but the frontend type/filter model did not explicitly support it.
