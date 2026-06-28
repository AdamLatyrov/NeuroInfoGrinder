# P0 Overnight Audit - 2026-06-26

## 1. Ночное окно анализа

- window_from: `2026-06-26T01:00:00+03:00`
- window_to: `2026-06-26T10:00:00+03:00`
- timezone: `Europe/Moscow (UTC+03)`
- SQL filter: `raw_messages.message_date >= from AND < to`

## 2-4. Raw Ingest Summary

- total raw_messages: 919
- enabled processable: 919
- display-only/out-of-scope: 0
- MessageText/text: 848
- media/no-text: 71
- latest ingested_at: 2026-06-26T06:59:58.40388+00:00
- latest message_date: 2026-06-26T06:59:58+00:00
- content_type counts: {"MessageText":815,"MessagePhoto":51,"MessageVideo":8,"MessageVideoNote":8,"MessageSticker":4,"MessageAnimation":6,"MessageAnimatedEmoji":3,"MessageChatJoinByRequest":6,"MessageVoiceNote":10,"MessageChatJoinByLink":4,"MessageDocument":2,"MessageChatAddMembers":1,"MessagePoll":1}
- topic/thread distinct count: 454

Top chats:

| chat | activeDialog | explicitAuto | processingState | raw_total | raw_text | latest_message | latest_ingested |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Опричнина. | true | false | ENABLED_PROCESSABLE | 614 | 575 | 2026-06-26T06:59:54+00:00 | 2026-06-26T06:59:58.40388+00:00 |
| Vibemode | true | true | ENABLED_PROCESSABLE | 82 | 80 | 2026-06-26T06:59:58+00:00 | 2026-06-26T06:59:58.355056+00:00 |
| Vibe GIG Мастерская | true | true | ENABLED_PROCESSABLE | 57 | 53 | 2026-06-26T06:55:43+00:00 | 2026-06-26T06:55:43.994308+00:00 |
| Vibe Dev | true | true | ENABLED_PROCESSABLE | 39 | 32 | 2026-06-26T06:52:06+00:00 | 2026-06-26T06:53:07.728111+00:00 |
| Vibecoder Chat [Public] | true | true | ENABLED_PROCESSABLE | 35 | 33 | 2026-06-26T06:59:11+00:00 | 2026-06-26T06:59:15.433718+00:00 |
| ОМ: Биохакинг | true | false | ENABLED_PROCESSABLE | 16 | 15 | 2026-06-26T06:55:07+00:00 | 2026-06-26T06:55:07.424258+00:00 |
| API SUPPORT / ModelHub | true | false | ENABLED_PROCESSABLE | 15 | 10 | 2026-06-26T06:18:13+00:00 | 2026-06-26T06:18:13.9827+00:00 |
| Asati Chat | true | false | ENABLED_PROCESSABLE | 13 | 12 | 2026-06-26T06:20:22+00:00 | 2026-06-26T06:20:28.758548+00:00 |
| ОМ: Полезные обсуждения | true | false | ENABLED_PROCESSABLE | 9 | 8 | 2026-06-26T06:58:01+00:00 | 2026-06-26T06:58:02.402596+00:00 |
| ОМ: Резюме | true | true | ENABLED_PROCESSABLE | 7 | 6 | 2026-06-26T06:14:45+00:00 | 2026-06-26T06:15:30.03964+00:00 |
| founderStack / Запуск продуктов и стартапов | true | false | ENABLED_PROCESSABLE | 5 | 5 | 2026-06-26T01:14:13+00:00 | 2026-06-26T01:14:14.069359+00:00 |
| ОМ: Бэкенд | true | false | ENABLED_PROCESSABLE | 4 | 4 | 2026-06-26T06:24:30+00:00 | 2026-06-26T06:24:34.359307+00:00 |
| AI / Data Science / Machine learning | true | false | ENABLED_PROCESSABLE | 4 | 4 | 2026-06-26T06:59:51+00:00 | 2026-06-26T06:59:51.961685+00:00 |
| Паша | true | true | ENABLED_PROCESSABLE | 3 | 2 | 2026-06-26T06:43:06+00:00 | 2026-06-26T06:43:06.848617+00:00 |
| Потребители | true | true | ENABLED_PROCESSABLE | 3 | 2 | 2026-06-26T05:04:33+00:00 | 2026-06-26T05:04:33.859249+00:00 |
| Сектовый чатик | true | false | ENABLED_PROCESSABLE | 2 | 1 | 2026-06-25T22:02:37+00:00 | 2026-06-25T22:02:38.079179+00:00 |
| ОМ: Полезное | true | false | ENABLED_PROCESSABLE | 2 | 0 | 2026-06-26T06:28:23+00:00 | 2026-06-26T06:29:15.099179+00:00 |
| ОМ: Флудилка | true | false | ENABLED_PROCESSABLE | 2 | 2 | 2026-06-26T06:40:52+00:00 | 2026-06-26T06:41:09.301188+00:00 |
| founderStack / Общение, знакомства | true | true | ENABLED_PROCESSABLE | 1 | 0 | 2026-06-25T22:03:41+00:00 | 2026-06-25T22:03:42.189457+00:00 |
| ОМ: Собеседования | true | true | ENABLED_PROCESSABLE | 1 | 0 | 2026-06-25T23:23:44+00:00 | 2026-06-25T23:24:00.13451+00:00 |
| ОМ: Волчонок | true | false | ENABLED_PROCESSABLE | 1 | 0 | 2026-06-26T03:11:13+00:00 | 2026-06-26T03:11:39.557688+00:00 |
| Codex.Sale | true | false | ENABLED_PROCESSABLE | 1 | 1 | 2026-06-26T05:00:27+00:00 | 2026-06-26T05:01:24.704285+00:00 |
| ЯИ-шка | true | false | ENABLED_PROCESSABLE | 1 | 1 | 2026-06-26T05:04:29+00:00 | 2026-06-26T05:04:30.590825+00:00 |
| SMM в России | true | false | ENABLED_PROCESSABLE | 1 | 1 | 2026-06-26T06:54:12+00:00 | 2026-06-26T06:54:32.307734+00:00 |
| Маркетинг Хаб / Вакансии и фриланс | true | false | ENABLED_PROCESSABLE | 1 | 1 | 2026-06-26T06:55:13+00:00 | 2026-06-26T06:55:16.225281+00:00 |

## 5. Funnel raw -> material

| stage | input | processed | skipped | rejected | failed | pending | pass % | top reasons |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| raw_messages | 919 | 919 | 0 | 0 | 0 | 0 | 100.0% | processed:919 |
| pipeline intake | 919 | 919 | 0 | 0 | 0 | 0 | 100.0% | processed:919 |
| queue | 919 | 919 | 0 | 0 | 0 | 0 | 100.0% | processed:919 |
| batch | 919 | 919 | 0 | 0 | 0 | 0 | 100.0% | processed:919 |
| run | 919 | 919 | 0 | 0 | 0 | 0 | 100.0% | processed:919 |
| replay_run_messages | 919 | 919 | 0 | 0 | 0 | 0 | 100.0% | processed:919 |
| embeddings | 919 | 146 | 0 | 0 | 0 | 0 | 15.9% | no embedding:773, processed:146 |
| clustering | 919 | 919 | 0 | 0 | 0 | 0 | 100.0% | processed:919 |
| single-message detection | 919 | 919 | 0 | 0 | 0 | 0 | 100.0% | processed:919 |
| LLM judge/provider calls | 919 | 2 | 0 | 0 | 0 | 0 | 0.2% | no provider call:917, processed:2 |
| material generation | 919 | 919 | 0 | 0 | 0 | 0 | 100.0% | processed:919 |
| knowledge_items/materials | 919 | 0 | 0 | 0 | 0 | 0 | 0.0% | no material:919 |

Key counts: raw to intake `919`, queue `919`, run `919`, replay `919`, embeddings `146`, single-message evaluated `919`, candidates `2`, LLM/provider `2`, LLM rejected `1`, material generation persisted `0`, materials created `0`.

Important traceability note: `replay_run_messages.raw_message_id` is NULL for these live rows; correct linkage is `raw_messages -> dataset_messages -> replay_run_messages.dataset_message_id`. This is a reporting/traceability bug, not the runtime break.

## 6. Reason Counts And Percentages

| reason | count | percent | examples | expected/bug | recommendation |
| --- | --- | --- | --- | --- | --- |
| replay exists but no embedding | 702 | 76.4% | 5951 Vibemode: Если бы не ваш апи, я наверное на клод код обратно вернулся<br>5953 Vibemode: а там щас тоже беда<br>5954 Vibemode: мы раньше в двоем max x20 не выжигали, а щас прям в притых<br>5955 Vibemode: Ну значит свой апи такой развернул))<br>5956 Опричнина.: Ухахахаха | expected/quality-gate | Expected for low-signal/noise rows after single-message evaluation; inspect only if useful examples recur. |
| single_message_rejected_TOO_SHORT | 78 | 8.5% | 5952 Опричнина.: Ща хуйню какую-нибудь скину и спать<br>5958 API SUPPORT / ModelHub: так вроде недавно 5.2 вышла?<br>5979 Vibe Dev: никаких проблем абсолютно не было<br>5982 Asati Chat: Если у проекта 0 юзеров, это большой минус?<br>5984 Опричнина.: Я же вам лайфхак суки скидывал как эквилибриума хуя достичь | expected/quality-gate | No action without sample review or explicit reprocess approval. |
| no text/media only | 71 | 7.7% | 5964 Опричнина.: <br>5966 Сектовый чатик: <br>5969 Опричнина.: <br>5970 Опричнина.: <br>5973 API SUPPORT / ModelHub:  | expected | Expected media/no-caption exclusion. |
| single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | 66 | 7.2% | 5965 Vibemode: Да я верю. Я полтора года сидел на макс 5 в клоде и там конечно заканчивались лимиты, но э<br>5981 Vibemode: Я уже Максу писал) <br>Верните жирный тариф, чтоб был. Даже для успокоения)))<br>Пусть он стоит <br>5986 API SUPPORT / ModelHub: А кто нибудь китайскую дичь тестировал? <br><br>Аля 0.5% от фембоя 5, в открытом доступе? <br><br>Qwyt<br>6011 founderStack / Запуск продуктов и стартапов: ⛔ mas shaar, тебя заблокировали (Lols Ban)<br><br>Пользователь отмечен к глобальной блокировке в<br>6028 ОМ: Бэкенд: Добро пожаловать в стаю, @therearerealyou!<br><br>Помощник ОМ — это твой путеводитель по сообщес | expected/quality-gate | No action without sample review or explicit reprocess approval. |
| LLM_REJECTED_ALL | 1 | 0.1% | 6716 ОМ: Полезные обсуждения: Всем привет!<br>Может кто-нибудь, пожалуйста, подсказать актуальный гайд по поднятию резюме в | expected | Expected: LLM judged message as topic/question, not standalone material. |
| MATERIAL_GENERATION_FAILURE | 1 | 0.1% | 6861 ОМ: Полезные обсуждения: Актуального гайда никто не даст, актуальность постоянно меняется, плюс гарантированности с | bug-fixed | Fixed locally: judge approval whitelist now accepts SINGLE_MESSAGE_MATERIAL_CANDIDATE/DIRECT_MATERIAL_READY; deploy backend only. |

## 7-9. Top 30 Potentially Useful Messages

| raw_id | chat/topic | time | preview | signals | score | decision | rejectionReason | pipelineStage | shouldBecomeGuide? | reason |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 6861 | ОМ: Полезные обсуждения | 2026-06-26T06:58:01+00:00 | Актуального гайда никто не даст, актуальность постоянно меняется, плюс гарантированности советов нет ни у кого кроме разрабов hh. В целом такие вопросы часто обсуждаются в чате ОМ: Резюме Если кратко: 1) Для поднятия резюме в топе: как можно больше писать в ча | GUIDE_OR_HOWTO, DIAGNOSIS_MAPPING, HARD_SIGNAL, LONG_USEFUL_TEXT | 0.62 | SINGLE_MESSAGE_MATERIAL_CANDIDATE |  | MATERIAL_GENERATION_FAILURE | SHOULD_BE_GUIDE | Fixed locally: judge approval whitelist now accepts SINGLE_MESSAGE_MATERIAL_CANDIDATE/DIRECT_MATERIAL_READY; deploy backend only. |
| 6544 | ЯИ-шка | 2026-06-26T05:04:29+00:00 | Дайджест нейросетей — главное за 25 июня 2026 == МОДЕЛИ == 🤖 Утечка превью GPT-5.6 В коде сайта OpenAI обнаружен идентификатор gpt-5.6-preview. Часть пользователей Pro-версии уже получила к нему доступ 🤖 Глобальный сбой ChatGPT Из-за серверной ошибки тысячи  | ERROR_SIGNAL, API_OR_STATUS_SIGNAL, HARD_SIGNAL, LONG_USEFUL_TEXT | 0.5 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | NEEDS_DISCUSSION_SEGMENT | No action without sample review or explicit reprocess approval. |
| 6545 | Потребители | 2026-06-26T05:04:33+00:00 | Дайджест нейросетей — главное за 25 июня 2026 == МОДЕЛИ == 🤖 Утечка превью GPT-5.6 В коде сайта OpenAI обнаружен идентификатор gpt-5.6-preview. Часть пользователей Pro-версии уже получила к нему доступ 🤖 Глобальный сбой ChatGPT Из-за серверной ошибки тысячи  | ERROR_SIGNAL, API_OR_STATUS_SIGNAL, HARD_SIGNAL, LONG_USEFUL_TEXT | 0.5 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | NEEDS_DISCUSSION_SEGMENT | No action without sample review or explicit reprocess approval. |
| 6306 | Vibecoder Chat [Public] / Ваши проекты | 2026-06-26T00:36:32+00:00 | 🚀 Мы не как другие хостинги. Пока большинство проектов используют готовые панели и стандартные решения, мы решили пойти по более сложному пути. За последние 3 месяца мы вложили огромное количество времени, сил, нервов и денег в разработку собственной платформ | GUIDE_OR_HOWTO, SOLUTION_SIGNAL, HARD_SIGNAL, LONG_USEFUL_TEXT | 0.34 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | NEEDS_DISCUSSION_SEGMENT | No action without sample review or explicit reprocess approval. |
| 6680 | ОМ: Полезные обсуждения | 2026-06-26T06:28:40+00:00 | Ну он тебе два раза намекнул - отъебись и дал самое ценное - самому определить то что тебе интересно. Забудь про книжки. Нахуй это не нужно никому, даже тебе. Тебе нужны твёрдые поинты, по результатам которых ты покажешь свою синьерность и даш им понять, что н | SOLUTION_SIGNAL, DIAGNOSIS_MAPPING, HARD_SIGNAL, LONG_USEFUL_TEXT | 0.3 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | NEEDS_DISCUSSION_SEGMENT | No action without sample review or explicit reprocess approval. |
| 6542 | Codex.Sale | 2026-06-26T05:00:27+00:00 | 📹Rutube запустил ИИ-агентов на GigaChat Rutube добавил двух ИИ-агентов на базе 😎GigaChat. Оба решения разработал портал «Рамблер». Первый агент помогает разбираться в дополнительной информации к видео: уточняет факты о фильмах и сериалах, подсказывает контек | SOLUTION_SIGNAL, API_OR_STATUS_SIGNAL, HARD_SIGNAL, LONG_USEFUL_TEXT | 0.22 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | NEEDS_DISCUSSION_SEGMENT | No action without sample review or explicit reprocess approval. |
| 6419 | Vibe Dev / Новости | 2026-06-26T03:37:26+00:00 | Правительство США будет утверждать доступ к GPT-5.6 для каждого клиента: разбор беспрецедентного решения Администрация Трампа потребовала от OpenAI выпустить GPT-5.6 только в ограниченном превью с одобрением каждого клиента федеральным правительством. Разбирае | GUIDE_OR_HOWTO, SOLUTION_SIGNAL, API_OR_STATUS_SIGNAL, LONG_USEFUL_TEXT | 0.5114 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | NEEDS_DISCUSSION_SEGMENT | No action without sample review or explicit reprocess approval. |
| 6224 | Vibe GIG Мастерская / Флудилка | 2026-06-25T23:30:54+00:00 | актуальный уже существующий список: 1. кривой ии-слопный фронт 2. объяснение агенту простых очевидных вещей которых он не знает, отчего ты вынужден тратить время и токены на инференс 3. отсутствие фри моделей в агенте 4. непонятный вырвиглазный больничный слож | GUIDE_OR_HOWTO, ERROR_SIGNAL, LONG_USEFUL_TEXT | 0.48 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | NEEDS_DISCUSSION_SEGMENT | No action without sample review or explicit reprocess approval. |
| 6543 | ОМ: Биохакинг | 2026-06-26T05:03:23+00:00 | У меня есть знакомые (4 человек), которые ретритили. После ретритов их проблемы не решались, они чаще уходили в "нужно жить в гармонии и по кайфу ваще". У всех после этого появились проблемы с целеполаганием типа "у самурая есть только путь". Моё мнение - это  | SOLUTION_SIGNAL, DIAGNOSIS_MAPPING, LONG_USEFUL_TEXT | 0.44 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | NEEDS_DISCUSSION_SEGMENT | No action without sample review or explicit reprocess approval. |
| 6416 | ОМ: Флудилка | 2026-06-26T03:34:28+00:00 | Граждане, доброго дня! Подскажите, а где-нибудь была информация про то, как крутить опыт для валютной удалёнки? За бугром же в 99% случаев пользуются LinkedIn, а в нём можно связаться с HR-ом компании, в которой ты якобы работал, как нехер делать. А если у тво | GUIDE_OR_HOWTO, DIAGNOSIS_MAPPING, LONG_USEFUL_TEXT | 0.5294 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | NEEDS_DISCUSSION_SEGMENT | No action without sample review or explicit reprocess approval. |
| 6765 | Vibecoder Chat [Public] / Claude Code | 2026-06-26T06:49:55+00:00 | почитайте теорию как работает кеширование в антропике. В подписке оно тоже используется, как и в api. Именно на первых запросах в сессии основная часть падает в кеш на запись, а лимиты в подписке считай что деньги в апи, потому что все измеряется в нагрузке на | GUIDE_OR_HOWTO, API_OR_STATUS_SIGNAL, LONG_USEFUL_TEXT | 0.4878 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | NEEDS_DISCUSSION_SEGMENT | No action without sample review or explicit reprocess approval. |
| 6107 | Vibe GIG Мастерская / Флудилка | 2026-06-25T22:17:36+00:00 | если что само приложение не глм делал, за основу взят https://github.com/truelockmc/streambert . В нём русской озвучки фильмов не было, я сказал ему спиздить озвучки и сами фильмы с http://lampa.mx/ | API_OR_STATUS_SIGNAL, DIAGNOSIS_MAPPING, LINK_CONTEXT | 0.4496 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | NEEDS_DISCUSSION_SEGMENT | No action without sample review or explicit reprocess approval. |
| 6119 | Vibe GIG Мастерская / Полезные ссылки | 2026-06-25T22:21:45+00:00 | если что само приложение не глм делал, за основу взят https://github.com/truelockmc/streambert . В нём русской озвучки фильмов не было, я сказал ему спиздить озвучки и сами фильмы с http://lampa.mx/ | API_OR_STATUS_SIGNAL, DIAGNOSIS_MAPPING, LINK_CONTEXT | 0.4496 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | NEEDS_DISCUSSION_SEGMENT | No action without sample review or explicit reprocess approval. |
| 6208 | Vibe GIG Мастерская / Флудилка | 2026-06-25T23:28:23+00:00 | кароче, я хуй знает работает ли оно вообще с гита, как на винде работает, бутаться на винду мне лень, но на арче всё с кайфом летает. Вот репо https://github.com/kroch228/lampa-stream дайте старок пжпжп. Можете скинуть ии с промтом установи пжпж | GUIDE_OR_HOWTO, API_OR_STATUS_SIGNAL, LINK_CONTEXT | 0.379 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | NEEDS_DISCUSSION_SEGMENT | No action without sample review or explicit reprocess approval. |
| 6730 | Vibe GIG Мастерская / Полезные ссылки | 2026-06-26T06:46:35+00:00 | кароче, я хуй знает работает ли оно вообще с гита, как на винде работает, бутаться на винду мне лень, но на арче всё с кайфом летает. Вот репо https://github.com/kroch228/lampa-stream дайте старок пжпжп. Можете скинуть ии с промтом установи пжпж | GUIDE_OR_HOWTO, API_OR_STATUS_SIGNAL, LINK_CONTEXT | 0.379 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | NEEDS_DISCUSSION_SEGMENT | No action without sample review or explicit reprocess approval. |
| 6803 | Vibecoder Chat [Public] / Claude Code | 2026-06-26T06:54:07+00:00 | Ну так мы про это и возмущались что ХЗ как он там БЕРЕТ бывает элементарнейший хапрос сжирает тонну лимитов (лично было что 15% за 1 текстовый запрос на 1 А4 лист текста тупо! ) а бывает бл пол дня работает в коде правит 2000 строк и сжирает дай бог 10% он не  | GUIDE_OR_HOWTO, HARD_SIGNAL, LONG_USEFUL_TEXT | 0.3576 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | NEEDS_DISCUSSION_SEGMENT | No action without sample review or explicit reprocess approval. |
| 6688 | ОМ: Биохакинг | 2026-06-26T06:36:19+00:00 | Конечно Там у людей прилипает всякое дезадаптивное говно вперемешку с религией и шизотерикой Глубоко там копать на самом деле часто не нужно: за парой-тройкой вопросов вглубь как в известном меме с шахтёром находится бриллиантовая залежь выгребная яма. Когда р | GUIDE_OR_HOWTO, SOLUTION_SIGNAL, LONG_USEFUL_TEXT | 0.3024 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | NEEDS_DISCUSSION_SEGMENT | No action without sample review or explicit reprocess approval. |
| 6716 | ОМ: Полезные обсуждения | 2026-06-26T06:44:53+00:00 | Всем привет! Может кто-нибудь, пожалуйста, подсказать актуальный гайд по поднятию резюме в топ выдачи на hh.ru (с целью прохождения ai-фильтров)? Слышал сам про такое (как пример): 1) что если по твоей специальности закончились вакансии, то можно откликаться в | GUIDE_OR_HOWTO, API_OR_STATUS_SIGNAL, DIAGNOSIS_MAPPING, HARD_SIGNAL, LONG_USEFUL_TEXT | 0.6048 | SINGLE_MESSAGE_MATERIAL_CANDIDATE |  | LLM_REJECTED_ALL | CORRECT_REJECT | Expected: LLM judged message as topic/question, not standalone material. |
| 6683 | Vibe GIG Мастерская / Полезные ссылки | 2026-06-26T06:31:51+00:00 | 🎬 Качаем видео с YouTube вплоть до 8K без ограничений Нашли удобную тулзу для загрузки роликов и плейлистов с YouTube в пару кликов. ➖ Скачивает как отдельные видео, так и целые плейлисты; ➖ Поддерживает качество от 144p до 8K; ➖ Сохраняет видео в MP4 и аудио | GUIDE_OR_HOWTO, LONG_USEFUL_TEXT | 0.4208 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | CORRECT_REJECT | No action without sample review or explicit reprocess approval. |
| 6776 | Vibecoder Chat [Public] / Claude Code | 2026-06-26T06:51:33+00:00 | ну как по человечески. Кешироввние, в api на запись есть бабки. Дорого. Зато следующие токены вытаскиваются из кеша. Поэтому дешево. Так в апи. В подписке то же самое, только вы не видите этого, оно под капотом | GUIDE_OR_HOWTO, API_OR_STATUS_SIGNAL | 0.402 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | CORRECT_REJECT | No action without sample review or explicit reprocess approval. |
| 6105 | Vibemode / Баги и вопросы по API | 2026-06-25T22:17:29+00:00 | Статус openai, тг чат, проверять свой vpn, проверять настройки самого инструмента, проверять автосжатие, в лк смотреть и ловить запросы, переключаться на другие модели, создавать новые чаты | GUIDE_OR_HOWTO, API_OR_STATUS_SIGNAL | 0.3578 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | CORRECT_REJECT | No action without sample review or explicit reprocess approval. |
| 6042 | Vibemode / Баги и вопросы по API | 2026-06-25T22:12:32+00:00 | Не знаю, проблема у меня или нет, но на r-api порой вообще отваливается. Hermes тормозит, Droid на домашнем сервере тоже (хотя там скорее проблема в настройках маршрутизации и Fake IP). | GUIDE_OR_HOWTO, API_OR_STATUS_SIGNAL | 0.357 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | CORRECT_REJECT | No action without sample review or explicit reprocess approval. |
| 6536 | ОМ: Резюме / Резюме | 2026-06-26T04:24:47+00:00 | "GUI и USB-транспорт стали отдельными модулями" - не стали сами, а ты сделал их отдельными "интерфейс не зависает" - не сам не зависает, а ты сделал чтобы интерфейс не зависал "Установка на новой машине Astra заняла одну команду" - не установка сама заняла, а  | GUIDE_OR_HOWTO, LONG_USEFUL_TEXT | 0.3368 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | CORRECT_REJECT | No action without sample review or explicit reprocess approval. |
| 6069 | Vibemode / Баги и вопросы по API | 2026-06-25T22:14:43+00:00 | причем тут лимиты то? эти 2 дня у меня кодекс с обычно подпиской работает как всегда обычно. а апи провайдер думает ооочень долго. Я не наговариваю, я факт говорю | GUIDE_OR_HOWTO, HARD_SIGNAL | 0.3324 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | CORRECT_REJECT | No action without sample review or explicit reprocess approval. |
| 6313 | founderStack / Запуск продуктов и стартапов | 2026-06-26T01:02:19+00:00 | привет! насчет актуальности идеи для бизнеса - это вечный вопрос. часто бывает, что ты делаешь что-то для себя, а потом оказывается, что таких как ты много. про кинопоиск и рустор – это, конечно, амбициозно, но для портфолио, думаю, подойдет любой продукт, кот | GUIDE_OR_HOWTO, LONG_USEFUL_TEXT | 0.3256 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | CORRECT_REJECT | No action without sample review or explicit reprocess approval. |
| 6553 | Опричнина. | 2026-06-26T05:20:25+00:00 | Четкий Дэвид как обычно кидает скрины где он делает +100500 долларов за пару часов и вообще криптотриллиардер а как стрельнуть у него пару сотен баксов так сразу сливается. Почему так? | GUIDE_OR_HOWTO, ERROR_SIGNAL | 0.2768 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | CORRECT_REJECT | No action without sample review or explicit reprocess approval. |
| 6291 | Опричнина. | 2026-06-26T00:22:12+00:00 | Завтра день х как писал мистер 69, имени его незнаю. И ещё там посмотреть надо крч, и будет ясно | GUIDE_OR_HOWTO, SOLUTION_SIGNAL | 0.2592 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | CORRECT_REJECT | No action without sample review or explicit reprocess approval. |
| 6754 | Опричнина. | 2026-06-26T06:49:21+00:00 | Тебе реально отдохнуть надо бро, с новыми силами. Щас гоняться за деньгами, будет хуже как будто | GUIDE_OR_HOWTO, SOLUTION_SIGNAL | 0.2592 | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | CORRECT_REJECT | No action without sample review or explicit reprocess approval. |
| 5951 | Vibemode / Баги и вопросы по API | 2026-06-25T22:00:17+00:00 | Если бы не ваш апи, я наверное на клод код обратно вернулся | DIAGNOSIS_MAPPING, HARD_SIGNAL | 0 | REJECTED_SINGLE_MESSAGE | CHAT_CONTEXT_ONLY | replay exists but no embedding | CORRECT_REJECT | Expected for low-signal/noise rows after single-message evaluation; inspect only if useful examples recur. |
| 6121 | Vibe GIG Мастерская / Флудилка | 2026-06-25T22:23:40+00:00 | я могу его залить на гит если кому надо, вроде норм работает | SOLUTION_SIGNAL, DIAGNOSIS_MAPPING | 0 | REJECTED_SINGLE_MESSAGE | CHAT_CONTEXT_ONLY | replay exists but no embedding | CORRECT_REJECT | Expected for low-signal/noise rows after single-message evaluation; inspect only if useful examples recur. |

## 10. Provider / LLM Path Summary

| provider_call_id | raw_id/run_id | stage | model | status | duration | tokens | error | output_summary |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 69 | 6716/1847 | LLM_CLUSTER_JUDGE_AND_ROUTING | gpt-5.5 | SUCCESS | 17745 | 755 |  | {"title":"Запрос на актуальный гайд по продвижению резюме на hh.ru","reason":"Сообщение является вопросом и не содержит самостоятельного проверенного гайда или достаточного набора инструкций. Упомянутые действия представлены как слухи и примеры, без подтверждения, контекста, результатов или пошагово |
| 70 | 6861/1954 | LLM_CLUSTER_JUDGE_AND_ROUTING | gpt-5.5 | SUCCESS | 19343 | 925 |  | {"title":"Как повысить видимость резюме на hh: практические сигналы активности и ограничения","reason":"Сообщение имеет явную структуру, содержит пошаговые рекомендации и диагностическое разделение: что делать для поднятия резюме и когда это не сработает из-за AI-фильтров. Несмотря на неофициальный  |

Provider interpretation: raw `6716` was correctly rejected by LLM as a question/topic, not a standalone guide. Raw `6861` was approved by LLM as `SINGLE_MESSAGE_MATERIAL_CANDIDATE`, but backend treated that decision as rejected and skipped `KNOWLEDGE_GENERATION`; this caused zero material despite a positive judge response.

## 11. Duplicate Guard Summary

No duplicate skip caused the zero-material result. Candidate runs `1847` and `1954` both show `DEDUPLICATION.duplicate_count=0`; `dedupe_group_id=null`; no existing material was linked.

## 12. Cluster Path Summary

No overnight clusters were created: microcluster output `0`, macrocluster output `0`, `replay_clusters` count `0`. The two useful paths were single-message candidates, not cluster candidates. No suspicious one-message cluster summary was found.

## 13. Discussion / Context Evidence

| sequence_id | chat/topic | raw_ids | messages_count | time_window | combined_signals | proposed_type | recommendation | combined preview |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 34 | Vibe GIG Мастерская / Флудилка | 6220,6221,6222,6223,6224,6225 | 6 | 2026-06-25T23:30:54+00:00 .. 2026-06-25T23:30:54+00:00 | GUIDE_OR_HOWTO, ERROR_SIGNAL, SOLUTION_SIGNAL, DIAGNOSIS_MAPPING, HARD_SIGNAL, LONG_USEFUL_TEXT | GUIDE | DISCUSSION_SEGMENT evidence only; not implemented. | собираю ОС по кли на каком принципе мы основываемся: сделать работу простой, удобной и эффективной без ебли с кучей скиллов, плагинов и установок - интуитивно понятно, решая главную проблему вайбкодеров ГОРЕНИЕ ЖОПЫ поэтому мне нужно знать - от чего у тебя чаще всего горит жопа в ВК актуальный уже существующий список: 1. кривой ии-слопный фронт 2. объяснение агенту простых очевидных вещей которых он не знает, отчего ты вынужден тратить время и токены на инференс 3. отсутствие фри моделей в агент |
| 35 | Vibe GIG Мастерская / Флудилка | 6221,6222,6223,6224,6225,6226 | 6 | 2026-06-25T23:30:54+00:00 .. 2026-06-25T23:31:01+00:00 | GUIDE_OR_HOWTO, ERROR_SIGNAL, SOLUTION_SIGNAL, DIAGNOSIS_MAPPING, HARD_SIGNAL, LONG_USEFUL_TEXT | GUIDE | DISCUSSION_SEGMENT evidence only; not implemented. | на каком принципе мы основываемся: сделать работу простой, удобной и эффективной без ебли с кучей скиллов, плагинов и установок - интуитивно понятно, решая главную проблему вайбкодеров ГОРЕНИЕ ЖОПЫ поэтому мне нужно знать - от чего у тебя чаще всего горит жопа в ВК актуальный уже существующий список: 1. кривой ии-слопный фронт 2. объяснение агенту простых очевидных вещей которых он не знает, отчего ты вынужден тратить время и токены на инференс 3. отсутствие фри моделей в агенте 4. непонятный вы |
| 18 | Vibemode / Баги и вопросы по API | 6105,6111,6112,6113,6114,6117 | 6 | 2026-06-25T22:17:29+00:00 .. 2026-06-25T22:21:37+00:00 | GUIDE_OR_HOWTO, SOLUTION_SIGNAL, API_OR_STATUS_SIGNAL, DIAGNOSIS_MAPPING, LONG_USEFUL_TEXT | ANSWER_OR_NOTE | DISCUSSION_SEGMENT evidence only; not implemented. | Статус openai, тг чат, проверять свой vpn, проверять настройки самого инструмента, проверять автосжатие, в лк смотреть и ловить запросы, переключаться на другие модели, создавать новые чаты Большое спасибо за вашу работу! Если хоть чем-то можем помочь, только дайте знать Деньгами)) Покупай подписки)) Предложения и фидбеки, вот что самое важное сейчас Покупаю, друзей зову покупать) Пока сомневаются, боятся, но рано или поздно прибегут) Особенно с моделями Claude - сразу прискочат да нам щас нужно |
| 29 | Vibe GIG Мастерская / Флудилка | 6107,6109,6110,6115,6116,6121 | 6 | 2026-06-25T22:17:36+00:00 .. 2026-06-25T22:23:40+00:00 | SOLUTION_SIGNAL, API_OR_STATUS_SIGNAL, DIAGNOSIS_MAPPING, LINK_CONTEXT, LONG_USEFUL_TEXT | ANSWER_OR_NOTE | DISCUSSION_SEGMENT evidence only; not implemented. | если что само приложение не глм делал, за основу взят https://github.com/truelockmc/streambert . В нём русской озвучки фильмов не было, я сказал ему спиздить озвучки и сами фильмы с http://lampa.mx/ и он сделал плеер отдельный не прям вау, просто прикольненько Ага Хорош я могу его залить на гит если кому надо, вроде норм работает |
| 33 | Vibe GIG Мастерская / Флудилка | 6219,6220,6221,6222,6223,6224 | 6 | 2026-06-25T23:30:35+00:00 .. 2026-06-25T23:30:54+00:00 | GUIDE_OR_HOWTO, ERROR_SIGNAL, SOLUTION_SIGNAL, HARD_SIGNAL, LONG_USEFUL_TEXT | GUIDE | DISCUSSION_SEGMENT evidence only; not implemented. | дадите ОС? собираю ОС по кли на каком принципе мы основываемся: сделать работу простой, удобной и эффективной без ебли с кучей скиллов, плагинов и установок - интуитивно понятно, решая главную проблему вайбкодеров ГОРЕНИЕ ЖОПЫ поэтому мне нужно знать - от чего у тебя чаще всего горит жопа в ВК актуальный уже существующий список: 1. кривой ии-слопный фронт 2. объяснение агенту простых очевидных вещей которых он не знает, отчего ты вынужден тратить время и токены на инференс 3. отсутствие фри моде |
| 36 | Vibe GIG Мастерская / Флудилка | 6222,6223,6224,6225,6226,6258 | 6 | 2026-06-25T23:30:54+00:00 .. 2026-06-25T23:48:38+00:00 | GUIDE_OR_HOWTO, ERROR_SIGNAL, SOLUTION_SIGNAL, DIAGNOSIS_MAPPING, LONG_USEFUL_TEXT | GUIDE | DISCUSSION_SEGMENT evidence only; not implemented. | ГОРЕНИЕ ЖОПЫ поэтому мне нужно знать - от чего у тебя чаще всего горит жопа в ВК актуальный уже существующий список: 1. кривой ии-слопный фронт 2. объяснение агенту простых очевидных вещей которых он не знает, отчего ты вынужден тратить время и токены на инференс 3. отсутствие фри моделей в агенте 4. непонятный вырвиглазный больничный сложный интерфейс и огромное количество мусорного функционала 5. отсутствие опенсурс фактора 6. неумение агентов работать с узкими сферами на приличном уровне: пар |
| 37 | Vibe GIG Мастерская / Флудилка | 6223,6224,6225,6226,6258,6259 | 6 | 2026-06-25T23:30:54+00:00 .. 2026-06-25T23:48:51+00:00 | GUIDE_OR_HOWTO, ERROR_SIGNAL, SOLUTION_SIGNAL, DIAGNOSIS_MAPPING, LONG_USEFUL_TEXT | GUIDE | DISCUSSION_SEGMENT evidence only; not implemented. | поэтому мне нужно знать - от чего у тебя чаще всего горит жопа в ВК актуальный уже существующий список: 1. кривой ии-слопный фронт 2. объяснение агенту простых очевидных вещей которых он не знает, отчего ты вынужден тратить время и токены на инференс 3. отсутствие фри моделей в агенте 4. непонятный вырвиглазный больничный сложный интерфейс и огромное количество мусорного функционала 5. отсутствие опенсурс фактора 6. неумение агентов работать с узкими сферами на приличном уровне: парсинг, отказ р |
| 70 | ОМ: Полезные обсуждения | 6716,6861 | 2 | 2026-06-26T06:44:53+00:00 .. 2026-06-26T06:58:01+00:00 | GUIDE_OR_HOWTO, API_OR_STATUS_SIGNAL, DIAGNOSIS_MAPPING, HARD_SIGNAL, LONG_USEFUL_TEXT | ANSWER_OR_NOTE | DISCUSSION_SEGMENT evidence only; not implemented. | Всем привет! Может кто-нибудь, пожалуйста, подсказать актуальный гайд по поднятию резюме в топ выдачи на hh.ru (с целью прохождения ai-фильтров)? Слышал сам про такое (как пример): 1) что если по твоей специальности закончились вакансии, то можно откликаться в другой, чтобы держать активность 2) что можно постоянно обновлять резюме путем внесения минорных изменений: добавить точку, сохранить, убрать точку, сохранить и тд Актуального гайда никто не даст, актуальность постоянно меняется, плюс гара |
| 7 | Vibemode / Баги и вопросы по API | 6047,6048,6052,6062,6064,6069 | 6 | 2026-06-25T22:12:58+00:00 .. 2026-06-25T22:14:43+00:00 | GUIDE_OR_HOWTO, API_OR_STATUS_SIGNAL, HARD_SIGNAL, LONG_USEFUL_TEXT | ANSWER_OR_NOTE | DISCUSSION_SEGMENT evidence only; not implemented. | 100%, ру апи для РФ айпишников он по этому и r-api Очень хотелось бы страницу мониторинга, чтобы знать наверняка, какой API и какая модель работает, в идеале бы ещё по запросу списка моделей получать информацию о рабочих или не рабочих моделях Чтобы в Hermes и подобных агентах fallback на другие модели делать нормально постараемся причем тут лимиты то? эти 2 дня у меня кодекс с обычно подпиской работает как всегда обычно. а апи провайдер думает ооочень долго. Я не наговариваю, я факт говорю |
| 8 | Vibemode / Баги и вопросы по API | 6048,6052,6062,6064,6069,6070 | 6 | 2026-06-25T22:13:05+00:00 .. 2026-06-25T22:14:46+00:00 | GUIDE_OR_HOWTO, API_OR_STATUS_SIGNAL, HARD_SIGNAL, LONG_USEFUL_TEXT | ANSWER_OR_NOTE | DISCUSSION_SEGMENT evidence only; not implemented. | он по этому и r-api Очень хотелось бы страницу мониторинга, чтобы знать наверняка, какой API и какая модель работает, в идеале бы ещё по запросу списка моделей получать информацию о рабочих или не рабочих моделях Чтобы в Hermes и подобных агентах fallback на другие модели делать нормально постараемся причем тут лимиты то? эти 2 дня у меня кодекс с обычно подпиской работает как всегда обычно. а апи провайдер думает ооочень долго. Я не наговариваю, я факт говорю но чаще всего зависит не от нас, а  |

## 14. Root Cause Classification

- Primary: `MATERIAL_GENERATION_FAILURE` for raw `6861` / run `1954`: LLM judge approved `SINGLE_MESSAGE_MATERIAL_CANDIDATE`, but `approvedJudgeDecision` did not accept that value, so generation was skipped and no `knowledge_items` row was written.
- Secondary: `LLM_REJECTED_ALL` for raw `6716` / run `1847`, expected because the message was a request/topic rather than standalone material.
- Not root cause: ingest, intake, queue, batch, run, replay, provider availability, duplicate guard, proxy, TDLib.

## 15. Safe Bugfix Applied

- Added failing regression test `singleMessageMaterialCandidateJudgeDecisionIsApproved`.
- Confirmed RED failure: helper returned false for `SINGLE_MESSAGE_MATERIAL_CANDIDATE`.
- Minimal fix: `approvedJudgeDecision` now accepts `SINGLE_MESSAGE_MATERIAL_CANDIDATE` and `DIRECT_MATERIAL_READY`.
- Targeted test passed. Full backend suite passed: `49/49`.

## 16. What Was Not Touched

- No scoring thresholds changed.
- No mass backlog/requeue/reprocess.
- No DISCUSSION_SEGMENT implementation.
- No prompt rewrite.
- No provider config change.
- No Telegram logout/session/auth/proxy change.
- No public discovery guard change.
- No generated materials batch.

## 17. Recommended Next Step

1. Backend-only safe parser fix has been deployed and health-checked.
2. Do not reprocess backlog yet. If Adam wants to materialize the missed positive candidate, run a controlled single raw-id reprocess for raw `6861` only after explicit approval.
3. Separately consider improving trace persistence: fill `replay_run_messages.raw_message_id` for live runs or update all reports to join through `dataset_messages`.
4. Separately review discussion/context sequences before approving any `DISCUSSION_SEGMENT` work.

## Fresh Live Samples

| raw_id | intake | queue | batch | run | replay | embedding | decision | providerCalls | material |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 6877 | PROCESSED | PROCESSED | 1994 | 1967 | true | true | REJECTED_SINGLE_MESSAGE | 0 | false |
| 6879 | PROCESSED | PROCESSED | 1996 | 1969 | true | false | REJECTED_SINGLE_MESSAGE | 0 | false |
| 6878 | PROCESSED | PROCESSED | 1995 | 1968 | true | false | REJECTED_SINGLE_MESSAGE | 0 | false |
