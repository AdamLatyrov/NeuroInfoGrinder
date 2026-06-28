# Enabled Message Usefulness Audit

Generated: 20260625

Scope: enabled `telegram_chats.is_enabled = true` only. This is a read-only audit of raw messages and existing pipeline/material evidence. It does not requeue, reprocess, generate materials, or change thresholds.

## Executive Summary

| Metric | Value |
|---|---:|
| Total enabled raw messages | 3354 |
| Potentially useful messages | 424 (12.6%) |
| Guide-ready messages | 107 (3.2%) |
| Discussion/context candidates | 173 (5.2%) |
| Already materialized messages | 10 (0.3%) |
| Missed useful messages | 416 (98.1% of useful) |
| Possible false-positive materials | 2 |
| Estimated recall on useful | 1.9% |
| Estimated material precision | 80.0% |

## Audit Class Counts

| Class | Count |
|---|---:|
| NOT_USEFUL | 2874 |
| GUIDE_READY | 107 |
| DISCUSSION_SEGMENT | 100 |
| USEFUL_SIGNAL | 80 |
| UNKNOWN_NEEDS_CONTEXT | 73 |
| ANSWER_OR_EXPLANATION | 53 |
| TROUBLESHOOTING | 35 |
| TOOL_OR_RELEASE | 23 |
| PROMPT_OR_TEMPLATE | 9 |

## System Result Counts

| Result | Count |
|---|---:|
| INTAKE_PENDING | 1857 |
| INTAKE_PROCESSED | 432 |
| REPLAYED_NO_EMBEDDING | 361 |
| CANDIDATE_ONLY | 347 |
| EMBEDDED_NO_MATERIAL | 178 |
| INTAKE_SKIPPED | 167 |
| MATERIALIZED | 10 |
| CLUSTERED_NO_MATERIAL | 2 |

## Miss Reason Counts

| Reason | Count |
|---|---:|
| GOOD_REJECTION | 2872 |
| PIPELINE_GAP | 226 |
| MISSED_DISCUSSION_CONTEXT | 166 |
| LOW_SCORE_OR_SINGLE_MESSAGE_FALSE_NEGATIVE | 49 |
| MISSED_SINGLE_MESSAGE | 24 |
| ALREADY_MATERIALIZED | 8 |
| LINK_CONTEXT_LOSS | 4 |
| MEDIA_CONTEXT_LOSS | 3 |
| POSSIBLE_FALSE_POSITIVE | 2 |

## Missed Useful By Chat

| Chat | Missed useful |
|---|---:|
| ОМ: Полезное | 41 |
| Vibecoder Chat [Public] | 38 |
| Vibemode | 38 |
| ОМ: Резюме | 36 |
| Vibe GIG Мастерская | 30 |
| Нейродвиж | 26 |
| Vibe Dev | 22 |
| founderStack / Общение, знакомства | 19 |
| ОМ: Флудилка | 17 |
| [UNCRN.me] Коридорки и кастдев | 16 |
| Local chat 999999000001 | 16 |
| Solo Founders | 14 |
| ОМ: Бэкенд | 14 |
| API SUPPORT | ModelHub | 12 |
| ОМ: Полезные обсуждения | 9 |
| GIG AI | 6 |
| Паша | 4 |
| Потребители | 3 |
| обкашляем вопросик | 3 |
| Сектовый чатик | 3 |
| Platega Support | 3 |
| Сделка с Ангелом | Kолмакова | 2 |
| Shchetnikov's AGI | 2 |
| ЯИ-шка | 2 |
| Asati Privatka Bot | 2 |
| Codex.Sale | 2 |
| Осознанная Меркантильность | Антон Назаров | 2 |
| SMM в России | 2 |
| ОМ: Собеседования | 2 |
| . | 2 |
| Miloslavski | 2 |
| Astana Hub | Events | 2 |
| утюг лох | 2 |
| AraDash | 1 |
| Трехсторонняя модель партнерства ООПТ-Наука-Волонтеры (Республика Ингушетия) | 1 |
| founderStack / Запуск продуктов и стартапов | 1 |
| Токены Claude 💶 / Tessera | 1 |
| Рефералки в IT | Вакансии и резюме | 1 |
| ОМ: Биохакинг | 1 |
| GLT Важные объявления | 1 |
| GLT Важные объявления Chat | 1 |
| Стартап-экосистема МАИ | 1 |
| Dsight | 1 |
| Pavlenko / Solo Founder | 1 |
| Павел Сорокин | Java | 1 |
| HSE FEST ACCELERATOR 2026 | 1 |
| ЧАТ Ивенты | Unicorns’ Room | 1 |
| XOR | 1 |
| hwmdt | 1 |
| Хайзов | fsind | 1 |
| Unicorns' Room Bot | 1 |
| Анастасия Сергеевна | 1 |
| ОМ: Бизнес | 1 |
| ㅤЗаур | 1 |
| Asati Chat | 1 |

## Top Missed Useful Candidates

| raw_id | chat | class | score | system | miss reason | preview |
|---:|---|---|---:|---|---|---|
| 395 | ОМ: Полезное | TROUBLESHOOTING | 100 | INTAKE_PENDING | PIPELINE_GAP | Рост из айтишника в предпринимателя / Вот тебе пальто, носи и мечтай о великом Как эго мешает программисту двигаться вперед, почему все таки придется дрочить софтскиллы и учиться... |
| 2254 | founderStack / Общение, знакомства | GUIDE_READY | 100 | INTAKE_PROCESSED | PIPELINE_GAP | 📝 🤔 Вайбкодинг, деньги и смысл работы Обсуждали, что мотивирует людей заниматься продуктами и кодом: деньги, интерес к созданию нового, польза, известность или просто сам процесс.... |
| 422 | ОМ: Полезное | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | Дайджест сообщества за апрель 📱 Ролики - Выпуск №3 для олдов: Сообщество, бабки, пострадавшие от Ульянова - МОК-интервью по System Design / Frontend-разработчик проектирует Market... |
| 305 | founderStack / Общение, знакомства | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | 📝 ⚖️ Юрисдикции, договоры и спорные сделки Обсуждали, как международные нормы по интеллектуальной собственности и банкротству работают на практике: где можно судиться, что реально... |
| 424 | ОМ: Полезное | PROMPT_OR_TEMPLATE | 100 | INTAKE_PENDING | PIPELINE_GAP | Как наладить жизнь до 30 (отчет миллионера) / Презираю бесцельных людей (скидка 15%) В настолке попался вопрос: "каких людей ты презираешь?". Я эмпат, поэтому над ответом пришлось... |
| 803 | Рефералки в IT | Вакансии и резюме | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | Область и стек: Devops Должность: Platform engineer Компания: 40 Acres Зарплатная вилка: до €75.000 в год Формат работы: Удаленка Страна работы: United Arab Emirates Platform engi... |
| 3725 | Platega Support | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | Здравствуйте, служба поддержки ТОО “ОнлиПэй”. Обращаюсь к вам с официальной жалобой на мошеннические действия со стороны мерчанта, который использует вашу инфраструктуру для приём... |
| 2222 | ОМ: Резюме | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | 1) "Готов к редким командировкам" заменить на "готов к командировкам", режет конверсию. Лучше отказаться от оффера если условия подходить не будут, чем HR не позовет на вакансию б... |
| 3254 | Miloslavski | GUIDE_READY | 100 | EMBEDDED_NO_MATERIAL | LOW_SCORE_OR_SINGLE_MESSAGE_FALSE_NEGATIVE | С небольшим опозданием, но все же, открываем поток на Июльский набор обучения. Цены стандартные : Групповое : 150 000. Личное : 350 000. Но! Первые 10 "групповых" оплат : 125 000... |
| 2194 | ОМ: Резюме | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | 9) Достижения в опыте работы оформить согласно правилам: 9.1) Вообще убрать то что относится к штатным обязанностям по твоей профессии (и так понятно что точно делал если занимал... |
| 4408 | GIG AI | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | 🤩Быстрая настройка MiMo MCP за 1 секунду и перенос с других CLI Отдаем ИИ агенту на слабой модели и просим перенести ваши mcp из других cli, типо omp, opencode и других. Кормим ИИ... |
| 4406 | GIG AI | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | MiMo Code Я в сильном приятном шоке. Лучшего я еще не видел. Достойный аналог Droid. Очень похоже на наконец то прокаченный и мощный Opencode. То чего нам не хватало! На полном се... |
| 409 | ОМ: Полезное | PROMPT_OR_TEMPLATE | 100 | INTAKE_PENDING | PIPELINE_GAP | Зарубежный валютный доход в IT — что нужно знать резиденту РФ в 2026 году Тг спикера Тг канал спикера На созвоне разобрали: - Работаете как ИП по контракту с зарубежным заказчиком... |
| 2755 | Потребители | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | Дайджест нейросетей — главное за 24 июня 2026 == МОДЕЛИ == 🤖 Claude Fable 5 вернулся в AWS Модель снова доступна на платформе AWS с поддержкой бессерверного и кросс-регионального... |
| 2756 | ЯИ-шка | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | Дайджест нейросетей — главное за 24 июня 2026 == МОДЕЛИ == 🤖 Claude Fable 5 вернулся в AWS Модель снова доступна на платформе AWS с поддержкой бессерверного и кросс-регионального... |
| 507 | Потребители | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | Дайджест нейросетей — главное за 23 июня 2026 == МОДЕЛИ == 🤖 Релиз GPT-5.6 отложен Вопреки вчерашним данным, выход GPT-5.6 перенесен на середину июля. Релиз Gemini 3.5 Pro также о... |
| 536 | ЯИ-шка | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | Дайджест нейросетей — главное за 23 июня 2026 == МОДЕЛИ == 🤖 Релиз GPT-5.6 отложен Вопреки вчерашним данным, выход GPT-5.6 перенесен на середину июля. Релиз Gemini 3.5 Pro также о... |
| 2664 | Vibecoder Chat [Public] | TROUBLESHOOTING | 100 | INTAKE_PENDING | PIPELINE_GAP | #opensource Всем привет! 👋 Откликаюсь на призыв поддержать опенсорс и хочу познакомить вас со своим проектом - 7/24 IDE. Это десктопная среда разработки, созданная с нуля для авто... |
| 2215 | ОМ: Резюме | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | 10) Достижения в опыте работы оформить согласно правилам: 10.1) Вообще убрать то что относится к штатным обязанностям по твоей профессии, типа код-ревью, документации и т.д. (и та... |
| 3892 | Codex.Sale | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | ✴️ Cursor на Compile 26: агенты, SDK и новая модель на 1.5 трлн параметров Cursor выложил запись с конференции Compile 26. Главное: редактор окончательно превратился в экосистему,... |
| 2192 | Павел Сорокин | Java | TROUBLESHOOTING | 100 | INTAKE_PENDING | PIPELINE_GAP | Самая дорогая ошибка разработчика - перепутать комфорт с ростом Я уже рассказывал о знакомом из Ижевска, перед которым стоял выбор его дальнейшей карьеры. В комментах у многих нач... |
| 402 | ОМ: Полезное | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | Краткий гайд по менторству: как помогать людям и выстраивать сильную практику Тг спикера На созвоне разобрали: - Как построить свою программу обучения: от цели ученика, а не от на... |
| 2193 | ОМ: Резюме | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | 1) Возраст должен быть указан и попадать в диапазон 26-35 лет, HR могут предвзято относится к остальным. Нужно подкрутить даты обучения и опыта работы чтобы укладывалось в этот ди... |
| 4294 | SMM в России | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | Кулуарные новости: что обсуждают маркетологи в июне 2026 🤩 Что делать дальше с Телегой. Во-первых, с базой подписчиков — куда её переводить, пока есть возможность (видели даже упо... |
| 392 | ОМ: Полезное | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | Разговор об английском, который поможет изменить твою карьеру! Начать учить английский Курс Английского языка по валютной удаленке Обсудили три главные темы: 1. Роль английского в... |
| 414 | ОМ: Полезное | TROUBLESHOOTING | 100 | INTAKE_PENDING | PIPELINE_GAP | Карьера в Big Tech: переезд в Германию, Amazon и Google Тг спикера Тг канал спикера LinkedIn спикера На созвоне разобрали: - История переезда в Германию, адаптации и обучения за г... |
| 394 | ОМ: Полезное | TROUBLESHOOTING | 100 | INTAKE_PENDING | PIPELINE_GAP | Когнитивные способности мозга и спорт: как движение меняет мышление Тг спикера Тг канал спикера На созвоне разобрали: - Как физическая активность усиливает нейропластичность и пом... |
| 398 | ОМ: Полезное | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | Как работать 3–4 часа в глубоком фокусе и делать больше: личный опыт построения Harness Framework в Big Tech Тг спикера Ютуб канал спикера На созвоне разобрали: - Что такое harnes... |
| 413 | ОМ: Полезное | TROUBLESHOOTING | 100 | INTAKE_PENDING | PIPELINE_GAP | Курс по Python: Тестирование Полная расшифровка курса и дополнительные материалы собраны в миниаппе в направлении Python Полный курс: - Pydantic, ООП и декораторы - уже на YouTube... |
| 423 | ОМ: Полезное | TROUBLESHOOTING | 100 | INTAKE_PENDING | PIPELINE_GAP | Коммуникация как основной инструмент реализации в жизни Тг спикера Тг канал спикера Чекап мужского гормонального фона Сегодня недостаточно быть просто сильным специалистом. Решает... |
| 415 | ОМ: Полезное | TROUBLESHOOTING | 100 | INTAKE_PENDING | PIPELINE_GAP | Enterprise IT Sales: как устроены большие сделки в IT Тг канал спикера Ютуб спикера Boosty спикера На созвоне разобрали: - почему одни IT-продажи закрываются за неделю, а другие м... |
| 408 | ОМ: Полезное | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | Какие IT специальности останутся актуальными / Карьерный свитч на синьор уровне https://youtu.be/sMgo4sIJpik https://youtu.be/sMgo4sIJpik https://youtu.be/sMgo4sIJpik Кисулечки мо... |
| 425 | ОМ: Полезное | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | Крути деревья, разбирай кучи / Гайд по алгоритмам для устройства в Яндекс Какому-то сумрачному гению пришла мысль "знание алгоритмов нельзя накрутить, это признак настоящего прогр... |
| 3775 | [UNCRN.me] Коридорки и кастдев | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | Исследование для бренд-менеджеров и аналитиков рынка FMCG / Non-Food Приветствуем! Мы развиваем аналитические инструменты для партнеров и хотим понять, как бренды сегодня работают... |
| 4400 | GIG AI | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | Новый Claude Code. Ну чё, пацаны. Нашёл я имбу. Модели Opencode GO прямо в Droid Называется Factory Droid. Тот же CC по духу[форк Claude Code] — только симпатичнее. MCP работают к... |
| 379 | ОМ: Полезное | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | Зависшие задачи: почему они воруют вашу энергию и как вернуть контроль Тг спикера На созвоне разобрали: - Что такое эффект Зейгарник и почему незавершённые дела создают постоянное... |
| 427 | ОМ: Полезное | PROMPT_OR_TEMPLATE | 100 | INTAKE_PENDING | PIPELINE_GAP | Если нужно найти специалиста не из IT, у нас есть «Сервисы». Там собраны эксперты, с которыми мы работаем сами и уверены в их профессионализме. Вот, кого можно подобрать в Сервиса... |
| 2958 | Нейродвиж | GUIDE_READY | 100 | EMBEDDED_NO_MATERIAL | LOW_SCORE_OR_SINGLE_MESSAGE_FALSE_NEGATIVE | 📎Дайджест подкастов про ИИ от команды Яндекс Браузера Снова напоминаем вам о наших подкастах с экспертами Яндекса! Вас ждёт много нового о: ⏺️Поиске работы с помощью ИИ ⏺️Создании... |
| 4402 | GIG AI | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | ➖➖➖ AGENTS.md теперь виден в Factory Droid Проблема была в том же что со скиллами — симлинк вместо реального файла. Droid TUI симлинки не переваривает. Файл теперь в двух местах:... |
| 428 | ОМ: Полезное | GUIDE_READY | 100 | INTAKE_PENDING | PIPELINE_GAP | Новая серия курса по процессам в бигтехе / Переход от UX/UI-дизайна к тестировванию идеи Рассказываем про тестирование гипотез при разработке новой функциональности. Обсудим, как... |

## Top Already Materialized Candidates

| raw_id | chat | class | score | system | miss reason | preview |
|---:|---|---|---:|---|---|---|
| 2947 | Нейродвиж | GUIDE_READY | 100 | MATERIALIZED | ALREADY_MATERIALIZED | Бустим свою ЗП за один промт — нашли подсказку, которая поможет аргументированно получить прибавку к зарплате. Логика простая: ChatGPT расспросит вас о ваших достижениях и поможет... |
| 2902 | Паша | GUIDE_READY | 100 | MATERIALIZED | ALREADY_MATERIALIZED | Мини-гайд по проверке API в Cursor: если внешний OpenAI-compatible endpoint не подключается, сначала проверь base URL — он должен заканчиваться на /v1. Потом проверь, что ключ пер... |
| 2926 | Нейродвиж | TROUBLESHOOTING | 69 | MATERIALIZED | ALREADY_MATERIALIZED | Нашли 7 рабочих промтов для ChatGPT, которые помогут разобраться в себе и найти причину беспокойства. Не замена врачам, но полезно: Ищем проблему: Спроси меня наводящие вопросы, ч... |
| 2662 | ОМ: Резюме | USEFUL_SIGNAL | 62 | MATERIALIZED | ALREADY_MATERIALIZED | Summary за сутки: Обсуждались подробные рекомендации по оптимизации резюме для прохождения AI-фильтров и HR. Были даны советы по структурированию достижений с использованием глаго... |
| 2813 | ОМ: Резюме | ANSWER_OR_EXPLANATION | 58 | MATERIALIZED | ALREADY_MATERIALIZED | Добро пожаловать в стаю, @backEnd_deve1oper! Помощник ОМ — это твой путеводитель по сообществу. Внутри ты найдешь: - базу знаний - список всех доступных чатов и ресурсов - контакт... |
| 2440 | ОМ: Резюме | ANSWER_OR_EXPLANATION | 58 | MATERIALIZED | ALREADY_MATERIALIZED | Добро пожаловать в стаю, пользователь! Помощник ОМ — это твой путеводитель по сообществу. Внутри ты найдешь: - базу знаний - список всех доступных чатов и ресурсов - контакты подд... |
| 4174 | ОМ: Резюме | ANSWER_OR_EXPLANATION | 58 | MATERIALIZED | ALREADY_MATERIALIZED | Добро пожаловать в стаю, @crisnine! Помощник ОМ — это твой путеводитель по сообществу. Внутри ты найдешь: - базу знаний - список всех доступных чатов и ресурсов - контакты поддерж... |
| 3240 | ОМ: Резюме | ANSWER_OR_EXPLANATION | 58 | MATERIALIZED | ALREADY_MATERIALIZED | Добро пожаловать в стаю, @htmngr! Помощник ОМ — это твой путеводитель по сообществу. Внутри ты найдешь: - базу знаний - список всех доступных чатов и ресурсов - контакты поддержки... |
| 2678 | Vibe Dev | NOT_USEFUL | 16 | MATERIALIZED | POSSIBLE_FALSE_POSITIVE | https://www.anthropic.com/careers/jobs |
| 2672 | Vibe Dev | NOT_USEFUL | 16 | MATERIALIZED | POSSIBLE_FALSE_POSITIVE | https://www.anthropic.com/careers |

## Possible False Positive Materials

| raw_id | chat | class | score | system | miss reason | preview |
|---:|---|---|---:|---|---|---|
| 2678 | Vibe Dev | NOT_USEFUL | 16 | MATERIALIZED | POSSIBLE_FALSE_POSITIVE | https://www.anthropic.com/careers/jobs |
| 2672 | Vibe Dev | NOT_USEFUL | 16 | MATERIALIZED | POSSIBLE_FALSE_POSITIVE | https://www.anthropic.com/careers |

## Interpretation Notes

- `MATERIALIZED` means the raw message can be linked to an existing `knowledge_item` through `knowledge_item_sources` and `dataset_messages` evidence.
- `EMBEDDED_NO_MATERIAL` usually means the message reached semantic processing but did not become a material. For single strong messages this is a likely false negative in scoring/single-message handling.
- `PIPELINE_GAP` means the raw message looks useful in this independent audit but did not reach material-producing evidence.
- `MISSED_DISCUSSION_CONTEXT` means the message is likely only useful with neighbor/reply/thread context.
- Scores are heuristic and meant for triage, not final human labeling. Use the CSV for row-level review.

## Output Files

- CSV: `enabled-message-usefulness-audit-20260625.csv`
- Metrics JSON: `enabled-message-usefulness-audit-20260625.json`
