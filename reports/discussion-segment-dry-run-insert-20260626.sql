BEGIN;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:00:29+00:00', '2026-06-25T22:02:08+00:00', 6, 0.7, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 5953] а там щас тоже беда
2. [raw 5954] мы раньше в двоем max x20 не выжигали, а щас прям в притых
3. [raw 5955] Ну значит свой апи такой развернул))
4. [raw 5957] Это вообще треш
5. [raw 5959] Сразу уточню, что объем работы не поменялся
6. [raw 5965] Да я верю. Я полтора года сидел на макс 5 в клоде и там конечно заканчивались лимиты, но это даже не сопоставимо с гпт х20', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5012, 'context', 'а там щас тоже беда', '2026-06-25T22:00:29+00:00'),
  (1, 5013, 'context', 'мы раньше в двоем max x20 не выжигали, а щас прям в притых', '2026-06-25T22:00:43+00:00'),
  (2, 5014, 'context', 'Ну значит свой апи такой развернул))', '2026-06-25T22:00:57+00:00'),
  (3, 5015, 'context', 'Это вообще треш', '2026-06-25T22:01:02+00:00'),
  (4, 5019, 'context', 'Сразу уточню, что объем работы не поменялся', '2026-06-25T22:01:19+00:00'),
  (5, 5024, 'context', 'Да я верю. Я полтора года сидел на макс 5 в клоде и там конечно заканчивались лимиты, но это даже не сопоставимо с гпт х20', '2026-06-25T22:02:08+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:07:43+00:00', '2026-06-25T22:09:00+00:00', 6, 0.58, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6012] на подписке работает сильно быстрее, 3-10 сек на ответ, на прокси 20-140сек на ответ
2. [raw 6014] последние 3 часа на подписке
3. [raw 6016] мы смотрим почему могло у кого-то замедлиться, а у кого-то нет
4. [raw 6018] а какой у тебя поинт стоит подскажи
5. [raw 6019] что за поинт
6. [raw 6021] Хз, не замечал такого', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5062, 'context', 'на подписке работает сильно быстрее, 3-10 сек на ответ, на прокси 20-140сек на ответ', '2026-06-25T22:07:43+00:00'),
  (1, 5063, 'context', 'последние 3 часа на подписке', '2026-06-25T22:07:52+00:00'),
  (2, 5064, 'context', 'мы смотрим почему могло у кого-то замедлиться, а у кого-то нет', '2026-06-25T22:08:11+00:00'),
  (3, 5067, 'context', 'а какой у тебя поинт стоит подскажи', '2026-06-25T22:08:18+00:00'),
  (4, 5068, 'context', 'что за поинт', '2026-06-25T22:08:31+00:00'),
  (5, 5070, 'context', 'Хз, не замечал такого', '2026-06-25T22:09:00+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:08:18+00:00', '2026-06-25T22:10:40+00:00', 6, 0.6, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6018] а какой у тебя поинт стоит подскажи
2. [raw 6019] что за поинт
3. [raw 6021] Хз, не замечал такого
4. [raw 6024] base_url
5. [raw 6025] да у меня тоже летает
6. [raw 6026] api.', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5067, 'context', 'а какой у тебя поинт стоит подскажи', '2026-06-25T22:08:18+00:00'),
  (1, 5068, 'context', 'что за поинт', '2026-06-25T22:08:31+00:00'),
  (2, 5070, 'context', 'Хз, не замечал такого', '2026-06-25T22:09:00+00:00'),
  (3, 5073, 'context', 'base_url', '2026-06-25T22:10:27+00:00'),
  (4, 5074, 'context', 'да у меня тоже летает', '2026-06-25T22:10:32+00:00'),
  (5, 5075, 'context', 'api.', '2026-06-25T22:10:40+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:08:31+00:00', '2026-06-25T22:10:45+00:00', 6, 0.6, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6019] что за поинт
2. [raw 6021] Хз, не замечал такого
3. [raw 6024] base_url
4. [raw 6025] да у меня тоже летает
5. [raw 6026] api.
6. [raw 6030] не рф который', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5068, 'context', 'что за поинт', '2026-06-25T22:08:31+00:00'),
  (1, 5070, 'context', 'Хз, не замечал такого', '2026-06-25T22:09:00+00:00'),
  (2, 5073, 'context', 'base_url', '2026-06-25T22:10:27+00:00'),
  (3, 5074, 'context', 'да у меня тоже летает', '2026-06-25T22:10:32+00:00'),
  (4, 5075, 'context', 'api.', '2026-06-25T22:10:40+00:00'),
  (5, 5076, 'context', 'не рф который', '2026-06-25T22:10:45+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:09:00+00:00', '2026-06-25T22:11:26+00:00', 6, 0.6, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6021] Хз, не замечал такого
2. [raw 6024] base_url
3. [raw 6025] да у меня тоже летает
4. [raw 6026] api.
5. [raw 6030] не рф который
6. [raw 6034] а, переключись на рф, не рф пока не стабилен изза усиленного CF', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5070, 'context', 'Хз, не замечал такого', '2026-06-25T22:09:00+00:00'),
  (1, 5073, 'context', 'base_url', '2026-06-25T22:10:27+00:00'),
  (2, 5074, 'context', 'да у меня тоже летает', '2026-06-25T22:10:32+00:00'),
  (3, 5075, 'context', 'api.', '2026-06-25T22:10:40+00:00'),
  (4, 5076, 'context', 'не рф который', '2026-06-25T22:10:45+00:00'),
  (5, 5085, 'context', 'а, переключись на рф, не рф пока не стабилен изза усиленного CF', '2026-06-25T22:11:26+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:10:27+00:00', '2026-06-25T22:12:32+00:00', 6, 0.6, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6024] base_url
2. [raw 6025] да у меня тоже летает
3. [raw 6026] api.
4. [raw 6030] не рф который
5. [raw 6034] а, переключись на рф, не рф пока не стабилен изза усиленного CF
6. [raw 6042] Не знаю, проблема у меня или нет, но на r-api порой вообще отваливается. Hermes тормозит, Droid на домашнем сервере тоже (хотя там скорее проблема в настройках маршрутизации и Fake IP).', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5073, 'context', 'base_url', '2026-06-25T22:10:27+00:00'),
  (1, 5074, 'context', 'да у меня тоже летает', '2026-06-25T22:10:32+00:00'),
  (2, 5075, 'context', 'api.', '2026-06-25T22:10:40+00:00'),
  (3, 5076, 'context', 'не рф который', '2026-06-25T22:10:45+00:00'),
  (4, 5085, 'context', 'а, переключись на рф, не рф пока не стабилен изза усиленного CF', '2026-06-25T22:11:26+00:00'),
  (5, 5091, 'context', 'Не знаю, проблема у меня или нет, но на r-api порой вообще отваливается. Hermes тормозит, Droid на домашнем сервере тоже (хотя там скорее проблема в настройках маршрутизации и Fake IP).', '2026-06-25T22:12:32+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:10:32+00:00', '2026-06-25T22:12:58+00:00', 6, 0.6, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6025] да у меня тоже летает
2. [raw 6026] api.
3. [raw 6030] не рф который
4. [raw 6034] а, переключись на рф, не рф пока не стабилен изза усиленного CF
5. [raw 6042] Не знаю, проблема у меня или нет, но на r-api порой вообще отваливается. Hermes тормозит, Droid на домашнем сервере тоже (хотя там скорее проблема в настройках маршрутизации и Fake IP).
6. [raw 6047] 100%, ру апи для РФ айпишников', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5074, 'context', 'да у меня тоже летает', '2026-06-25T22:10:32+00:00'),
  (1, 5075, 'context', 'api.', '2026-06-25T22:10:40+00:00'),
  (2, 5076, 'context', 'не рф который', '2026-06-25T22:10:45+00:00'),
  (3, 5085, 'context', 'а, переключись на рф, не рф пока не стабилен изза усиленного CF', '2026-06-25T22:11:26+00:00'),
  (4, 5091, 'context', 'Не знаю, проблема у меня или нет, но на r-api порой вообще отваливается. Hermes тормозит, Droid на домашнем сервере тоже (хотя там скорее проблема в настройках маршрутизации и Fake IP).', '2026-06-25T22:12:32+00:00'),
  (5, 5092, 'context', '100%, ру апи для РФ айпишников', '2026-06-25T22:12:58+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:10:40+00:00', '2026-06-25T22:13:05+00:00', 6, 0.6, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6026] api.
2. [raw 6030] не рф который
3. [raw 6034] а, переключись на рф, не рф пока не стабилен изза усиленного CF
4. [raw 6042] Не знаю, проблема у меня или нет, но на r-api порой вообще отваливается. Hermes тормозит, Droid на домашнем сервере тоже (хотя там скорее проблема в настройках маршрутизации и Fake IP).
5. [raw 6047] 100%, ру апи для РФ айпишников
6. [raw 6048] он по этому и r-api', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5075, 'context', 'api.', '2026-06-25T22:10:40+00:00'),
  (1, 5076, 'context', 'не рф который', '2026-06-25T22:10:45+00:00'),
  (2, 5085, 'context', 'а, переключись на рф, не рф пока не стабилен изза усиленного CF', '2026-06-25T22:11:26+00:00'),
  (3, 5091, 'context', 'Не знаю, проблема у меня или нет, но на r-api порой вообще отваливается. Hermes тормозит, Droid на домашнем сервере тоже (хотя там скорее проблема в настройках маршрутизации и Fake IP).', '2026-06-25T22:12:32+00:00'),
  (4, 5092, 'context', '100%, ру апи для РФ айпишников', '2026-06-25T22:12:58+00:00'),
  (5, 5093, 'context', 'он по этому и r-api', '2026-06-25T22:13:05+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:10:45+00:00', '2026-06-25T22:13:38+00:00', 6, 0.6, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6030] не рф который
2. [raw 6034] а, переключись на рф, не рф пока не стабилен изза усиленного CF
3. [raw 6042] Не знаю, проблема у меня или нет, но на r-api порой вообще отваливается. Hermes тормозит, Droid на домашнем сервере тоже (хотя там скорее проблема в настройках маршрутизации и Fake IP).
4. [raw 6047] 100%, ру апи для РФ айпишников
5. [raw 6048] он по этому и r-api
6. [raw 6052] Очень хотелось бы страницу мониторинга, чтобы знать наверняка, какой API и какая модель работает, в идеале бы ещё по запросу списка моделей получать информацию о рабочих или не рабочих моделях', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5076, 'context', 'не рф который', '2026-06-25T22:10:45+00:00'),
  (1, 5085, 'context', 'а, переключись на рф, не рф пока не стабилен изза усиленного CF', '2026-06-25T22:11:26+00:00'),
  (2, 5091, 'context', 'Не знаю, проблема у меня или нет, но на r-api порой вообще отваливается. Hermes тормозит, Droid на домашнем сервере тоже (хотя там скорее проблема в настройках маршрутизации и Fake IP).', '2026-06-25T22:12:32+00:00'),
  (3, 5092, 'context', '100%, ру апи для РФ айпишников', '2026-06-25T22:12:58+00:00'),
  (4, 5093, 'context', 'он по этому и r-api', '2026-06-25T22:13:05+00:00'),
  (5, 5102, 'context', 'Очень хотелось бы страницу мониторинга, чтобы знать наверняка, какой API и какая модель работает, в идеале бы ещё по запросу списка моделей получать информацию о рабочих или не рабочих моделях', '2026-06-25T22:13:38+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:11:26+00:00', '2026-06-25T22:14:10+00:00', 6, 0.6, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6034] а, переключись на рф, не рф пока не стабилен изза усиленного CF
2. [raw 6042] Не знаю, проблема у меня или нет, но на r-api порой вообще отваливается. Hermes тормозит, Droid на домашнем сервере тоже (хотя там скорее проблема в настройках маршрутизации и Fake IP).
3. [raw 6047] 100%, ру апи для РФ айпишников
4. [raw 6048] он по этому и r-api
5. [raw 6052] Очень хотелось бы страницу мониторинга, чтобы знать наверняка, какой API и какая модель работает, в идеале бы ещё по запросу списка моделей получать информацию о рабочих или не рабочих моделях
6. [raw 6062] Чтобы в Hermes и подобных агентах fallback на другие модели делать нормально', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5085, 'context', 'а, переключись на рф, не рф пока не стабилен изза усиленного CF', '2026-06-25T22:11:26+00:00'),
  (1, 5091, 'context', 'Не знаю, проблема у меня или нет, но на r-api порой вообще отваливается. Hermes тормозит, Droid на домашнем сервере тоже (хотя там скорее проблема в настройках маршрутизации и Fake IP).', '2026-06-25T22:12:32+00:00'),
  (2, 5092, 'context', '100%, ру апи для РФ айпишников', '2026-06-25T22:12:58+00:00'),
  (3, 5093, 'context', 'он по этому и r-api', '2026-06-25T22:13:05+00:00'),
  (4, 5102, 'context', 'Очень хотелось бы страницу мониторинга, чтобы знать наверняка, какой API и какая модель работает, в идеале бы ещё по запросу списка моделей получать информацию о рабочих или не рабочих моделях', '2026-06-25T22:13:38+00:00'),
  (5, 5103, 'context', 'Чтобы в Hermes и подобных агентах fallback на другие модели делать нормально', '2026-06-25T22:14:10+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:12:32+00:00', '2026-06-25T22:14:19+00:00', 6, 0.6, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6042] Не знаю, проблема у меня или нет, но на r-api порой вообще отваливается. Hermes тормозит, Droid на домашнем сервере тоже (хотя там скорее проблема в настройках маршрутизации и Fake IP).
2. [raw 6047] 100%, ру апи для РФ айпишников
3. [raw 6048] он по этому и r-api
4. [raw 6052] Очень хотелось бы страницу мониторинга, чтобы знать наверняка, какой API и какая модель работает, в идеале бы ещё по запросу списка моделей получать информацию о рабочих или не рабочих моделях
5. [raw 6062] Чтобы в Hermes и подобных агентах fallback на другие модели делать нормально
6. [raw 6064] постараемся', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5091, 'context', 'Не знаю, проблема у меня или нет, но на r-api порой вообще отваливается. Hermes тормозит, Droid на домашнем сервере тоже (хотя там скорее проблема в настройках маршрутизации и Fake IP).', '2026-06-25T22:12:32+00:00'),
  (1, 5092, 'context', '100%, ру апи для РФ айпишников', '2026-06-25T22:12:58+00:00'),
  (2, 5093, 'context', 'он по этому и r-api', '2026-06-25T22:13:05+00:00'),
  (3, 5102, 'context', 'Очень хотелось бы страницу мониторинга, чтобы знать наверняка, какой API и какая модель работает, в идеале бы ещё по запросу списка моделей получать информацию о рабочих или не рабочих моделях', '2026-06-25T22:13:38+00:00'),
  (4, 5103, 'context', 'Чтобы в Hermes и подобных агентах fallback на другие модели делать нормально', '2026-06-25T22:14:10+00:00'),
  (5, 5113, 'context', 'постараемся', '2026-06-25T22:14:19+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:12:58+00:00', '2026-06-25T22:14:43+00:00', 6, 0.98, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6047] 100%, ру апи для РФ айпишников
2. [raw 6048] он по этому и r-api
3. [raw 6052] Очень хотелось бы страницу мониторинга, чтобы знать наверняка, какой API и какая модель работает, в идеале бы ещё по запросу списка моделей получать информацию о рабочих или не рабочих моделях
4. [raw 6062] Чтобы в Hermes и подобных агентах fallback на другие модели делать нормально
5. [raw 6064] постараемся
6. [raw 6069] причем тут лимиты то? эти 2 дня у меня кодекс с обычно подпиской работает как всегда обычно. а апи провайдер думает ооочень долго. Я не наговариваю, я факт говорю', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5092, 'context', '100%, ру апи для РФ айпишников', '2026-06-25T22:12:58+00:00'),
  (1, 5093, 'context', 'он по этому и r-api', '2026-06-25T22:13:05+00:00'),
  (2, 5102, 'context', 'Очень хотелось бы страницу мониторинга, чтобы знать наверняка, какой API и какая модель работает, в идеале бы ещё по запросу списка моделей получать информацию о рабочих или не рабочих моделях', '2026-06-25T22:13:38+00:00'),
  (3, 5103, 'context', 'Чтобы в Hermes и подобных агентах fallback на другие модели делать нормально', '2026-06-25T22:14:10+00:00'),
  (4, 5113, 'context', 'постараемся', '2026-06-25T22:14:19+00:00'),
  (5, 5114, 'context', 'причем тут лимиты то? эти 2 дня у меня кодекс с обычно подпиской работает как всегда обычно. а апи провайдер думает ооочень долго. Я не наговариваю, я факт говорю', '2026-06-25T22:14:43+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:13:05+00:00', '2026-06-25T22:14:46+00:00', 6, 0.98, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6048] он по этому и r-api
2. [raw 6052] Очень хотелось бы страницу мониторинга, чтобы знать наверняка, какой API и какая модель работает, в идеале бы ещё по запросу списка моделей получать информацию о рабочих или не рабочих моделях
3. [raw 6062] Чтобы в Hermes и подобных агентах fallback на другие модели делать нормально
4. [raw 6064] постараемся
5. [raw 6069] причем тут лимиты то? эти 2 дня у меня кодекс с обычно подпиской работает как всегда обычно. а апи провайдер думает ооочень долго. Я не наговариваю, я факт говорю
6. [raw 6070] но чаще всего зависит не от нас, а чаще всего все сбои на стороне кого то, но мы обязательно подумаем над этим! Спасибо!', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5093, 'context', 'он по этому и r-api', '2026-06-25T22:13:05+00:00'),
  (1, 5102, 'context', 'Очень хотелось бы страницу мониторинга, чтобы знать наверняка, какой API и какая модель работает, в идеале бы ещё по запросу списка моделей получать информацию о рабочих или не рабочих моделях', '2026-06-25T22:13:38+00:00'),
  (2, 5103, 'context', 'Чтобы в Hermes и подобных агентах fallback на другие модели делать нормально', '2026-06-25T22:14:10+00:00'),
  (3, 5113, 'context', 'постараемся', '2026-06-25T22:14:19+00:00'),
  (4, 5114, 'context', 'причем тут лимиты то? эти 2 дня у меня кодекс с обычно подпиской работает как всегда обычно. а апи провайдер думает ооочень долго. Я не наговариваю, я факт говорю', '2026-06-25T22:14:43+00:00'),
  (5, 5115, 'context', 'но чаще всего зависит не от нас, а чаще всего все сбои на стороне кого то, но мы обязательно подумаем над этим! Спасибо!', '2026-06-25T22:14:46+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:13:38+00:00', '2026-06-25T22:14:56+00:00', 6, 0.98, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6052] Очень хотелось бы страницу мониторинга, чтобы знать наверняка, какой API и какая модель работает, в идеале бы ещё по запросу списка моделей получать информацию о рабочих или не рабочих моделях
2. [raw 6062] Чтобы в Hermes и подобных агентах fallback на другие модели делать нормально
3. [raw 6064] постараемся
4. [raw 6069] причем тут лимиты то? эти 2 дня у меня кодекс с обычно подпиской работает как всегда обычно. а апи провайдер думает ооочень долго. Я не наговариваю, я факт говорю
5. [raw 6070] но чаще всего зависит не от нас, а чаще всего все сбои на стороне кого то, но мы обязательно подумаем над этим! Спасибо!
6. [raw 6071] поинт какой', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5102, 'context', 'Очень хотелось бы страницу мониторинга, чтобы знать наверняка, какой API и какая модель работает, в идеале бы ещё по запросу списка моделей получать информацию о рабочих или не рабочих моделях', '2026-06-25T22:13:38+00:00'),
  (1, 5103, 'context', 'Чтобы в Hermes и подобных агентах fallback на другие модели делать нормально', '2026-06-25T22:14:10+00:00'),
  (2, 5113, 'context', 'постараемся', '2026-06-25T22:14:19+00:00'),
  (3, 5114, 'context', 'причем тут лимиты то? эти 2 дня у меня кодекс с обычно подпиской работает как всегда обычно. а апи провайдер думает ооочень долго. Я не наговариваю, я факт говорю', '2026-06-25T22:14:43+00:00'),
  (4, 5115, 'context', 'но чаще всего зависит не от нас, а чаще всего все сбои на стороне кого то, но мы обязательно подумаем над этим! Спасибо!', '2026-06-25T22:14:46+00:00'),
  (5, 5120, 'context', 'поинт какой', '2026-06-25T22:14:56+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:14:10+00:00', '2026-06-25T22:15:32+00:00', 6, 0.98, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6062] Чтобы в Hermes и подобных агентах fallback на другие модели делать нормально
2. [raw 6064] постараемся
3. [raw 6069] причем тут лимиты то? эти 2 дня у меня кодекс с обычно подпиской работает как всегда обычно. а апи провайдер думает ооочень долго. Я не наговариваю, я факт говорю
4. [raw 6070] но чаще всего зависит не от нас, а чаще всего все сбои на стороне кого то, но мы обязательно подумаем над этим! Спасибо!
5. [raw 6071] поинт какой
6. [raw 6078] я уже добавил статус доступности моделей, пока в работе', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5103, 'context', 'Чтобы в Hermes и подобных агентах fallback на другие модели делать нормально', '2026-06-25T22:14:10+00:00'),
  (1, 5113, 'context', 'постараемся', '2026-06-25T22:14:19+00:00'),
  (2, 5114, 'context', 'причем тут лимиты то? эти 2 дня у меня кодекс с обычно подпиской работает как всегда обычно. а апи провайдер думает ооочень долго. Я не наговариваю, я факт говорю', '2026-06-25T22:14:43+00:00'),
  (3, 5115, 'context', 'но чаще всего зависит не от нас, а чаще всего все сбои на стороне кого то, но мы обязательно подумаем над этим! Спасибо!', '2026-06-25T22:14:46+00:00'),
  (4, 5120, 'context', 'поинт какой', '2026-06-25T22:14:56+00:00'),
  (5, 5129, 'context', 'я уже добавил статус доступности моделей, пока в работе', '2026-06-25T22:15:32+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:14:19+00:00', '2026-06-25T22:15:43+00:00', 6, 0.88, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6064] постараемся
2. [raw 6069] причем тут лимиты то? эти 2 дня у меня кодекс с обычно подпиской работает как всегда обычно. а апи провайдер думает ооочень долго. Я не наговариваю, я факт говорю
3. [raw 6070] но чаще всего зависит не от нас, а чаще всего все сбои на стороне кого то, но мы обязательно подумаем над этим! Спасибо!
4. [raw 6071] поинт какой
5. [raw 6078] я уже добавил статус доступности моделей, пока в работе
6. [raw 6079] Да, понимаю, потому и предлагаю. Ибо точек отказа достаточно много, потому любой реконнект уже вызывает панические атаки xD', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5113, 'context', 'постараемся', '2026-06-25T22:14:19+00:00'),
  (1, 5114, 'context', 'причем тут лимиты то? эти 2 дня у меня кодекс с обычно подпиской работает как всегда обычно. а апи провайдер думает ооочень долго. Я не наговариваю, я факт говорю', '2026-06-25T22:14:43+00:00'),
  (2, 5115, 'context', 'но чаще всего зависит не от нас, а чаще всего все сбои на стороне кого то, но мы обязательно подумаем над этим! Спасибо!', '2026-06-25T22:14:46+00:00'),
  (3, 5120, 'context', 'поинт какой', '2026-06-25T22:14:56+00:00'),
  (4, 5129, 'context', 'я уже добавил статус доступности моделей, пока в работе', '2026-06-25T22:15:32+00:00'),
  (5, 5130, 'context', 'Да, понимаю, потому и предлагаю. Ибо точек отказа достаточно много, потому любой реконнект уже вызывает панические атаки xD', '2026-06-25T22:15:43+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:14:43+00:00', '2026-06-25T22:16:54+00:00', 6, 0.88, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6069] причем тут лимиты то? эти 2 дня у меня кодекс с обычно подпиской работает как всегда обычно. а апи провайдер думает ооочень долго. Я не наговариваю, я факт говорю
2. [raw 6070] но чаще всего зависит не от нас, а чаще всего все сбои на стороне кого то, но мы обязательно подумаем над этим! Спасибо!
3. [raw 6071] поинт какой
4. [raw 6078] я уже добавил статус доступности моделей, пока в работе
5. [raw 6079] Да, понимаю, потому и предлагаю. Ибо точек отказа достаточно много, потому любой реконнект уже вызывает панические атаки xD
6. [raw 6094] у нас работы очень много в последние дни, стараемся всё исправить и улучшить, но хорошие новости тоже будут', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5114, 'context', 'причем тут лимиты то? эти 2 дня у меня кодекс с обычно подпиской работает как всегда обычно. а апи провайдер думает ооочень долго. Я не наговариваю, я факт говорю', '2026-06-25T22:14:43+00:00'),
  (1, 5115, 'context', 'но чаще всего зависит не от нас, а чаще всего все сбои на стороне кого то, но мы обязательно подумаем над этим! Спасибо!', '2026-06-25T22:14:46+00:00'),
  (2, 5120, 'context', 'поинт какой', '2026-06-25T22:14:56+00:00'),
  (3, 5129, 'context', 'я уже добавил статус доступности моделей, пока в работе', '2026-06-25T22:15:32+00:00'),
  (4, 5130, 'context', 'Да, понимаю, потому и предлагаю. Ибо точек отказа достаточно много, потому любой реконнект уже вызывает панические атаки xD', '2026-06-25T22:15:43+00:00'),
  (5, 5143, 'context', 'у нас работы очень много в последние дни, стараемся всё исправить и улучшить, но хорошие новости тоже будут', '2026-06-25T22:16:54+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:15:32+00:00', '2026-06-25T22:18:15+00:00', 6, 0.68, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","TECHNICAL_API_CACHE_COST","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6078] я уже добавил статус доступности моделей, пока в работе
2. [raw 6079] Да, понимаю, потому и предлагаю. Ибо точек отказа достаточно много, потому любой реконнект уже вызывает панические атаки xD
3. [raw 6094] у нас работы очень много в последние дни, стараемся всё исправить и улучшить, но хорошие новости тоже будут
4. [raw 6103] Это уже крик души)
Как в анекдоте - Как вспомню, въебать охота)))
5. [raw 6105] Статус openai, тг чат, проверять свой vpn, проверять настройки самого инструмента, проверять автосжатие, в лк смотреть и ловить запросы, переключаться на другие модели, создавать новые чаты
6. [raw 6111] Большое спасибо за вашу работу!

Если хоть чем-то можем помочь, только дайте знать', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5129, 'context', 'я уже добавил статус доступности моделей, пока в работе', '2026-06-25T22:15:32+00:00'),
  (1, 5130, 'context', 'Да, понимаю, потому и предлагаю. Ибо точек отказа достаточно много, потому любой реконнект уже вызывает панические атаки xD', '2026-06-25T22:15:43+00:00'),
  (2, 5143, 'context', 'у нас работы очень много в последние дни, стараемся всё исправить и улучшить, но хорошие новости тоже будут', '2026-06-25T22:16:54+00:00'),
  (3, 5144, 'context', 'Это уже крик души)
Как в анекдоте - Как вспомню, въебать охота)))', '2026-06-25T22:17:17+00:00'),
  (4, 5154, 'context', 'Статус openai, тг чат, проверять свой vpn, проверять настройки самого инструмента, проверять автосжатие, в лк смотреть и ловить запросы, переключаться на другие модели, создавать новые чаты', '2026-06-25T22:17:29+00:00'),
  (5, 5160, 'context', 'Большое спасибо за вашу работу!

Если хоть чем-то можем помочь, только дайте знать', '2026-06-25T22:18:15+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:15:43+00:00', '2026-06-25T22:18:49+00:00', 6, 0.68, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","TECHNICAL_API_CACHE_COST","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6079] Да, понимаю, потому и предлагаю. Ибо точек отказа достаточно много, потому любой реконнект уже вызывает панические атаки xD
2. [raw 6094] у нас работы очень много в последние дни, стараемся всё исправить и улучшить, но хорошие новости тоже будут
3. [raw 6103] Это уже крик души)
Как в анекдоте - Как вспомню, въебать охота)))
4. [raw 6105] Статус openai, тг чат, проверять свой vpn, проверять настройки самого инструмента, проверять автосжатие, в лк смотреть и ловить запросы, переключаться на другие модели, создавать новые чаты
5. [raw 6111] Большое спасибо за вашу работу!

Если хоть чем-то можем помочь, только дайте знать
6. [raw 6112] Деньгами))
Покупай подписки))', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5130, 'context', 'Да, понимаю, потому и предлагаю. Ибо точек отказа достаточно много, потому любой реконнект уже вызывает панические атаки xD', '2026-06-25T22:15:43+00:00'),
  (1, 5143, 'context', 'у нас работы очень много в последние дни, стараемся всё исправить и улучшить, но хорошие новости тоже будут', '2026-06-25T22:16:54+00:00'),
  (2, 5144, 'context', 'Это уже крик души)
Как в анекдоте - Как вспомню, въебать охота)))', '2026-06-25T22:17:17+00:00'),
  (3, 5154, 'context', 'Статус openai, тг чат, проверять свой vpn, проверять настройки самого инструмента, проверять автосжатие, в лк смотреть и ловить запросы, переключаться на другие модели, создавать новые чаты', '2026-06-25T22:17:29+00:00'),
  (4, 5160, 'context', 'Большое спасибо за вашу работу!

Если хоть чем-то можем помочь, только дайте знать', '2026-06-25T22:18:15+00:00'),
  (5, 5161, 'context', 'Деньгами))
Покупай подписки))', '2026-06-25T22:18:49+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:16:54+00:00', '2026-06-25T22:18:55+00:00', 6, 0.78, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","CHECKLIST_OR_LIST","TECHNICAL_API_CACHE_COST","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6094] у нас работы очень много в последние дни, стараемся всё исправить и улучшить, но хорошие новости тоже будут
2. [raw 6103] Это уже крик души)
Как в анекдоте - Как вспомню, въебать охота)))
3. [raw 6105] Статус openai, тг чат, проверять свой vpn, проверять настройки самого инструмента, проверять автосжатие, в лк смотреть и ловить запросы, переключаться на другие модели, создавать новые чаты
4. [raw 6111] Большое спасибо за вашу работу!

Если хоть чем-то можем помочь, только дайте знать
5. [raw 6112] Деньгами))
Покупай подписки))
6. [raw 6113] Предложения и фидбеки, вот что самое важное сейчас', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5143, 'context', 'у нас работы очень много в последние дни, стараемся всё исправить и улучшить, но хорошие новости тоже будут', '2026-06-25T22:16:54+00:00'),
  (1, 5144, 'context', 'Это уже крик души)
Как в анекдоте - Как вспомню, въебать охота)))', '2026-06-25T22:17:17+00:00'),
  (2, 5154, 'context', 'Статус openai, тг чат, проверять свой vpn, проверять настройки самого инструмента, проверять автосжатие, в лк смотреть и ловить запросы, переключаться на другие модели, создавать новые чаты', '2026-06-25T22:17:29+00:00'),
  (3, 5160, 'context', 'Большое спасибо за вашу работу!

Если хоть чем-то можем помочь, только дайте знать', '2026-06-25T22:18:15+00:00'),
  (4, 5161, 'context', 'Деньгами))
Покупай подписки))', '2026-06-25T22:18:49+00:00'),
  (5, 5162, 'context', 'Предложения и фидбеки, вот что самое важное сейчас', '2026-06-25T22:18:55+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:17:17+00:00', '2026-06-25T22:20:13+00:00', 6, 0.88, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","CHECKLIST_OR_LIST","TECHNICAL_API_CACHE_COST","RISK_OR_CAUTION_SIGNAL","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6103] Это уже крик души)
Как в анекдоте - Как вспомню, въебать охота)))
2. [raw 6105] Статус openai, тг чат, проверять свой vpn, проверять настройки самого инструмента, проверять автосжатие, в лк смотреть и ловить запросы, переключаться на другие модели, создавать новые чаты
3. [raw 6111] Большое спасибо за вашу работу!

Если хоть чем-то можем помочь, только дайте знать
4. [raw 6112] Деньгами))
Покупай подписки))
5. [raw 6113] Предложения и фидбеки, вот что самое важное сейчас
6. [raw 6114] Покупаю, друзей зову покупать)

Пока сомневаются, боятся, но рано или поздно прибегут)

Особенно с моделями Claude - сразу прискочат', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5144, 'context', 'Это уже крик души)
Как в анекдоте - Как вспомню, въебать охота)))', '2026-06-25T22:17:17+00:00'),
  (1, 5154, 'context', 'Статус openai, тг чат, проверять свой vpn, проверять настройки самого инструмента, проверять автосжатие, в лк смотреть и ловить запросы, переключаться на другие модели, создавать новые чаты', '2026-06-25T22:17:29+00:00'),
  (2, 5160, 'context', 'Большое спасибо за вашу работу!

Если хоть чем-то можем помочь, только дайте знать', '2026-06-25T22:18:15+00:00'),
  (3, 5161, 'context', 'Деньгами))
Покупай подписки))', '2026-06-25T22:18:49+00:00'),
  (4, 5162, 'context', 'Предложения и фидбеки, вот что самое важное сейчас', '2026-06-25T22:18:55+00:00'),
  (5, 5163, 'context', 'Покупаю, друзей зову покупать)

Пока сомневаются, боятся, но рано или поздно прибегут)

Особенно с моделями Claude - сразу прискочат', '2026-06-25T22:20:13+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:17:29+00:00', '2026-06-25T22:21:37+00:00', 6, 0.7, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["CHECKLIST_OR_LIST","TECHNICAL_API_CACHE_COST","RISK_OR_CAUTION_SIGNAL","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6105] Статус openai, тг чат, проверять свой vpn, проверять настройки самого инструмента, проверять автосжатие, в лк смотреть и ловить запросы, переключаться на другие модели, создавать новые чаты
2. [raw 6111] Большое спасибо за вашу работу!

Если хоть чем-то можем помочь, только дайте знать
3. [raw 6112] Деньгами))
Покупай подписки))
4. [raw 6113] Предложения и фидбеки, вот что самое важное сейчас
5. [raw 6114] Покупаю, друзей зову покупать)

Пока сомневаются, боятся, но рано или поздно прибегут)

Особенно с моделями Claude - сразу прискочат
6. [raw 6117] да нам щас нужно исправить все что есть', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5154, 'context', 'Статус openai, тг чат, проверять свой vpn, проверять настройки самого инструмента, проверять автосжатие, в лк смотреть и ловить запросы, переключаться на другие модели, создавать новые чаты', '2026-06-25T22:17:29+00:00'),
  (1, 5160, 'context', 'Большое спасибо за вашу работу!

Если хоть чем-то можем помочь, только дайте знать', '2026-06-25T22:18:15+00:00'),
  (2, 5161, 'context', 'Деньгами))
Покупай подписки))', '2026-06-25T22:18:49+00:00'),
  (3, 5162, 'context', 'Предложения и фидбеки, вот что самое важное сейчас', '2026-06-25T22:18:55+00:00'),
  (4, 5163, 'context', 'Покупаю, друзей зову покупать)

Пока сомневаются, боятся, но рано или поздно прибегут)

Особенно с моделями Claude - сразу прискочат', '2026-06-25T22:20:13+00:00'),
  (5, 5166, 'context', 'да нам щас нужно исправить все что есть', '2026-06-25T22:21:37+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:18:15+00:00', '2026-06-25T22:21:45+00:00', 6, 0.6, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["CHECKLIST_OR_LIST","RISK_OR_CAUTION_SIGNAL","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6111] Большое спасибо за вашу работу!

Если хоть чем-то можем помочь, только дайте знать
2. [raw 6112] Деньгами))
Покупай подписки))
3. [raw 6113] Предложения и фидбеки, вот что самое важное сейчас
4. [raw 6114] Покупаю, друзей зову покупать)

Пока сомневаются, боятся, но рано или поздно прибегут)

Особенно с моделями Claude - сразу прискочат
5. [raw 6117] да нам щас нужно исправить все что есть
6. [raw 6118] я тут еще с Gemini ковыряюсь', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5160, 'context', 'Большое спасибо за вашу работу!

Если хоть чем-то можем помочь, только дайте знать', '2026-06-25T22:18:15+00:00'),
  (1, 5161, 'context', 'Деньгами))
Покупай подписки))', '2026-06-25T22:18:49+00:00'),
  (2, 5162, 'context', 'Предложения и фидбеки, вот что самое важное сейчас', '2026-06-25T22:18:55+00:00'),
  (3, 5163, 'context', 'Покупаю, друзей зову покупать)

Пока сомневаются, боятся, но рано или поздно прибегут)

Особенно с моделями Claude - сразу прискочат', '2026-06-25T22:20:13+00:00'),
  (4, 5166, 'context', 'да нам щас нужно исправить все что есть', '2026-06-25T22:21:37+00:00'),
  (5, 5167, 'context', 'я тут еще с Gemini ковыряюсь', '2026-06-25T22:21:45+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:18:49+00:00', '2026-06-25T22:22:46+00:00', 6, 0.6, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["CHECKLIST_OR_LIST","RISK_OR_CAUTION_SIGNAL","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6112] Деньгами))
Покупай подписки))
2. [raw 6113] Предложения и фидбеки, вот что самое важное сейчас
3. [raw 6114] Покупаю, друзей зову покупать)

Пока сомневаются, боятся, но рано или поздно прибегут)

Особенно с моделями Claude - сразу прискочат
4. [raw 6117] да нам щас нужно исправить все что есть
5. [raw 6118] я тут еще с Gemini ковыряюсь
6. [raw 6120] О, если распознавание речи прикрутите - цены вам не будет', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5161, 'context', 'Деньгами))
Покупай подписки))', '2026-06-25T22:18:49+00:00'),
  (1, 5162, 'context', 'Предложения и фидбеки, вот что самое важное сейчас', '2026-06-25T22:18:55+00:00'),
  (2, 5163, 'context', 'Покупаю, друзей зову покупать)

Пока сомневаются, боятся, но рано или поздно прибегут)

Особенно с моделями Claude - сразу прискочат', '2026-06-25T22:20:13+00:00'),
  (3, 5166, 'context', 'да нам щас нужно исправить все что есть', '2026-06-25T22:21:37+00:00'),
  (4, 5167, 'context', 'я тут еще с Gemini ковыряюсь', '2026-06-25T22:21:45+00:00'),
  (5, 5169, 'context', 'О, если распознавание речи прикрутите - цены вам не будет', '2026-06-25T22:22:46+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:18:55+00:00', '2026-06-25T22:24:21+00:00', 6, 0.6, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["CHECKLIST_OR_LIST","RISK_OR_CAUTION_SIGNAL","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6113] Предложения и фидбеки, вот что самое важное сейчас
2. [raw 6114] Покупаю, друзей зову покупать)

Пока сомневаются, боятся, но рано или поздно прибегут)

Особенно с моделями Claude - сразу прискочат
3. [raw 6117] да нам щас нужно исправить все что есть
4. [raw 6118] я тут еще с Gemini ковыряюсь
5. [raw 6120] О, если распознавание речи прикрутите - цены вам не будет
6. [raw 6125] Тоже самое в лк 30-90сек на запрос. До обновления сегодняшнего было 10-30', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5162, 'context', 'Предложения и фидбеки, вот что самое важное сейчас', '2026-06-25T22:18:55+00:00'),
  (1, 5163, 'context', 'Покупаю, друзей зову покупать)

Пока сомневаются, боятся, но рано или поздно прибегут)

Особенно с моделями Claude - сразу прискочат', '2026-06-25T22:20:13+00:00'),
  (2, 5166, 'context', 'да нам щас нужно исправить все что есть', '2026-06-25T22:21:37+00:00'),
  (3, 5167, 'context', 'я тут еще с Gemini ковыряюсь', '2026-06-25T22:21:45+00:00'),
  (4, 5169, 'context', 'О, если распознавание речи прикрутите - цены вам не будет', '2026-06-25T22:22:46+00:00'),
  (5, 5174, 'context', 'Тоже самое в лк 30-90сек на запрос. До обновления сегодняшнего было 10-30', '2026-06-25T22:24:21+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:46:55+00:00', '2026-06-25T22:50:35+00:00', 6, 0.58, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6151] На улицу красных фонарей, делать бум бум
2. [raw 6152] главное бум бум с трансом не сделать
3. [raw 6153] хотя нет
4. [raw 6154] это же вьетнам
5. [raw 6155] а не таиланд
6. [raw 6159] Как говорится, если деньги уже уплочены, че поделать)))', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5200, 'context', 'На улицу красных фонарей, делать бум бум', '2026-06-25T22:46:55+00:00'),
  (1, 5201, 'context', 'главное бум бум с трансом не сделать', '2026-06-25T22:47:23+00:00'),
  (2, 5202, 'context', 'хотя нет', '2026-06-25T22:47:49+00:00'),
  (3, 5203, 'context', 'это же вьетнам', '2026-06-25T22:47:55+00:00'),
  (4, 5204, 'context', 'а не таиланд', '2026-06-25T22:47:59+00:00'),
  (5, 5209, 'context', 'Как говорится, если деньги уже уплочены, че поделать)))', '2026-06-25T22:50:35+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:47:23+00:00', '2026-06-25T22:53:11+00:00', 6, 0.58, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6152] главное бум бум с трансом не сделать
2. [raw 6153] хотя нет
3. [raw 6154] это же вьетнам
4. [raw 6155] а не таиланд
5. [raw 6159] Как говорится, если деньги уже уплочены, че поделать)))
6. [raw 6162] Дананг', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5201, 'context', 'главное бум бум с трансом не сделать', '2026-06-25T22:47:23+00:00'),
  (1, 5202, 'context', 'хотя нет', '2026-06-25T22:47:49+00:00'),
  (2, 5203, 'context', 'это же вьетнам', '2026-06-25T22:47:55+00:00'),
  (3, 5204, 'context', 'а не таиланд', '2026-06-25T22:47:59+00:00'),
  (4, 5209, 'context', 'Как говорится, если деньги уже уплочены, че поделать)))', '2026-06-25T22:50:35+00:00'),
  (5, 5211, 'context', 'Дананг', '2026-06-25T22:53:11+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:47:49+00:00', '2026-06-25T22:54:32+00:00', 6, 0.58, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6153] хотя нет
2. [raw 6154] это же вьетнам
3. [raw 6155] а не таиланд
4. [raw 6159] Как говорится, если деньги уже уплочены, че поделать)))
5. [raw 6162] Дананг
6. [raw 6164] А нам с женой не зашёл дня 4-5 потусили и уехали', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5202, 'context', 'хотя нет', '2026-06-25T22:47:49+00:00'),
  (1, 5203, 'context', 'это же вьетнам', '2026-06-25T22:47:55+00:00'),
  (2, 5204, 'context', 'а не таиланд', '2026-06-25T22:47:59+00:00'),
  (3, 5209, 'context', 'Как говорится, если деньги уже уплочены, че поделать)))', '2026-06-25T22:50:35+00:00'),
  (4, 5211, 'context', 'Дананг', '2026-06-25T22:53:11+00:00'),
  (5, 5213, 'context', 'А нам с женой не зашёл дня 4-5 потусили и уехали', '2026-06-25T22:54:32+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:47:55+00:00', '2026-06-25T22:55:30+00:00', 6, 0.58, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6154] это же вьетнам
2. [raw 6155] а не таиланд
3. [raw 6159] Как говорится, если деньги уже уплочены, че поделать)))
4. [raw 6162] Дананг
5. [raw 6164] А нам с женой не зашёл дня 4-5 потусили и уехали
6. [raw 6166] ну мы туда ЛЕТИМ', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5203, 'context', 'это же вьетнам', '2026-06-25T22:47:55+00:00'),
  (1, 5204, 'context', 'а не таиланд', '2026-06-25T22:47:59+00:00'),
  (2, 5209, 'context', 'Как говорится, если деньги уже уплочены, че поделать)))', '2026-06-25T22:50:35+00:00'),
  (3, 5211, 'context', 'Дананг', '2026-06-25T22:53:11+00:00'),
  (4, 5213, 'context', 'А нам с женой не зашёл дня 4-5 потусили и уехали', '2026-06-25T22:54:32+00:00'),
  (5, 5215, 'context', 'ну мы туда ЛЕТИМ', '2026-06-25T22:55:30+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:47:59+00:00', '2026-06-25T22:55:38+00:00', 6, 0.58, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6155] а не таиланд
2. [raw 6159] Как говорится, если деньги уже уплочены, че поделать)))
3. [raw 6162] Дананг
4. [raw 6164] А нам с женой не зашёл дня 4-5 потусили и уехали
5. [raw 6166] ну мы туда ЛЕТИМ
6. [raw 6167] а от туда уже куда угодно же', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5204, 'context', 'а не таиланд', '2026-06-25T22:47:59+00:00'),
  (1, 5209, 'context', 'Как говорится, если деньги уже уплочены, че поделать)))', '2026-06-25T22:50:35+00:00'),
  (2, 5211, 'context', 'Дананг', '2026-06-25T22:53:11+00:00'),
  (3, 5213, 'context', 'А нам с женой не зашёл дня 4-5 потусили и уехали', '2026-06-25T22:54:32+00:00'),
  (4, 5215, 'context', 'ну мы туда ЛЕТИМ', '2026-06-25T22:55:30+00:00'),
  (5, 5216, 'context', 'а от туда уже куда угодно же', '2026-06-25T22:55:38+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:50:35+00:00', '2026-06-25T22:56:49+00:00', 6, 0.58, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6159] Как говорится, если деньги уже уплочены, че поделать)))
2. [raw 6162] Дананг
3. [raw 6164] А нам с женой не зашёл дня 4-5 потусили и уехали
4. [raw 6166] ну мы туда ЛЕТИМ
5. [raw 6167] а от туда уже куда угодно же
6. [raw 6169] Ну, многим заходит. Там прям комьюнити)', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5209, 'context', 'Как говорится, если деньги уже уплочены, че поделать)))', '2026-06-25T22:50:35+00:00'),
  (1, 5211, 'context', 'Дананг', '2026-06-25T22:53:11+00:00'),
  (2, 5213, 'context', 'А нам с женой не зашёл дня 4-5 потусили и уехали', '2026-06-25T22:54:32+00:00'),
  (3, 5215, 'context', 'ну мы туда ЛЕТИМ', '2026-06-25T22:55:30+00:00'),
  (4, 5216, 'context', 'а от туда уже куда угодно же', '2026-06-25T22:55:38+00:00'),
  (5, 5218, 'context', 'Ну, многим заходит. Там прям комьюнити)', '2026-06-25T22:56:49+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:55:38+00:00', '2026-06-25T22:58:15+00:00', 6, 0.58, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6167] а от туда уже куда угодно же
2. [raw 6169] Ну, многим заходит. Там прям комьюнити)
3. [raw 6170] не
4. [raw 6171] я на оборот хочу прям в вайб, А не туда где таких как я сотни ходит
5. [raw 6173] Ну там прям сотни как ты)))
6. [raw 6175] Это хз куда надо в ебеня ехать)
Дананг сейчас топ направление', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5216, 'context', 'а от туда уже куда угодно же', '2026-06-25T22:55:38+00:00'),
  (1, 5218, 'context', 'Ну, многим заходит. Там прям комьюнити)', '2026-06-25T22:56:49+00:00'),
  (2, 5219, 'context', 'не', '2026-06-25T22:56:56+00:00'),
  (3, 5220, 'context', 'я на оборот хочу прям в вайб, А не туда где таких как я сотни ходит', '2026-06-25T22:57:14+00:00'),
  (4, 5223, 'context', 'Ну там прям сотни как ты)))', '2026-06-25T22:57:32+00:00'),
  (5, 5224, 'context', 'Это хз куда надо в ебеня ехать)
Дананг сейчас топ направление', '2026-06-25T22:58:15+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:56:49+00:00', '2026-06-25T22:58:32+00:00', 6, 0.58, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6169] Ну, многим заходит. Там прям комьюнити)
2. [raw 6170] не
3. [raw 6171] я на оборот хочу прям в вайб, А не туда где таких как я сотни ходит
4. [raw 6173] Ну там прям сотни как ты)))
5. [raw 6175] Это хз куда надо в ебеня ехать)
Дананг сейчас топ направление
6. [raw 6176] А ням ням?', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5218, 'context', 'Ну, многим заходит. Там прям комьюнити)', '2026-06-25T22:56:49+00:00'),
  (1, 5219, 'context', 'не', '2026-06-25T22:56:56+00:00'),
  (2, 5220, 'context', 'я на оборот хочу прям в вайб, А не туда где таких как я сотни ходит', '2026-06-25T22:57:14+00:00'),
  (3, 5223, 'context', 'Ну там прям сотни как ты)))', '2026-06-25T22:57:32+00:00'),
  (4, 5224, 'context', 'Это хз куда надо в ебеня ехать)
Дананг сейчас топ направление', '2026-06-25T22:58:15+00:00'),
  (5, 5225, 'context', 'А ням ням?', '2026-06-25T22:58:32+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:56:56+00:00', '2026-06-25T22:58:46+00:00', 6, 0.58, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6170] не
2. [raw 6171] я на оборот хочу прям в вайб, А не туда где таких как я сотни ходит
3. [raw 6173] Ну там прям сотни как ты)))
4. [raw 6175] Это хз куда надо в ебеня ехать)
Дананг сейчас топ направление
5. [raw 6176] А ням ням?
6. [raw 6177] Так на юге их тоже не мало)', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5219, 'context', 'не', '2026-06-25T22:56:56+00:00'),
  (1, 5220, 'context', 'я на оборот хочу прям в вайб, А не туда где таких как я сотни ходит', '2026-06-25T22:57:14+00:00'),
  (2, 5223, 'context', 'Ну там прям сотни как ты)))', '2026-06-25T22:57:32+00:00'),
  (3, 5224, 'context', 'Это хз куда надо в ебеня ехать)
Дананг сейчас топ направление', '2026-06-25T22:58:15+00:00'),
  (4, 5225, 'context', 'А ням ням?', '2026-06-25T22:58:32+00:00'),
  (5, 5226, 'context', 'Так на юге их тоже не мало)', '2026-06-25T22:58:46+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:57:14+00:00', '2026-06-25T22:58:55+00:00', 6, 0.58, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6171] я на оборот хочу прям в вайб, А не туда где таких как я сотни ходит
2. [raw 6173] Ну там прям сотни как ты)))
3. [raw 6175] Это хз куда надо в ебеня ехать)
Дананг сейчас топ направление
4. [raw 6176] А ням ням?
5. [raw 6177] Так на юге их тоже не мало)
6. [raw 6178] Я с тремя успел пересечься знакомыми, еще с кучей можно было)', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5220, 'context', 'я на оборот хочу прям в вайб, А не туда где таких как я сотни ходит', '2026-06-25T22:57:14+00:00'),
  (1, 5223, 'context', 'Ну там прям сотни как ты)))', '2026-06-25T22:57:32+00:00'),
  (2, 5224, 'context', 'Это хз куда надо в ебеня ехать)
Дананг сейчас топ направление', '2026-06-25T22:58:15+00:00'),
  (3, 5225, 'context', 'А ням ням?', '2026-06-25T22:58:32+00:00'),
  (4, 5226, 'context', 'Так на юге их тоже не мало)', '2026-06-25T22:58:46+00:00'),
  (5, 5227, 'context', 'Я с тремя успел пересечься знакомыми, еще с кучей можно было)', '2026-06-25T22:58:55+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:57:32+00:00', '2026-06-25T23:00:52+00:00', 6, 0.58, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6173] Ну там прям сотни как ты)))
2. [raw 6175] Это хз куда надо в ебеня ехать)
Дананг сейчас топ направление
3. [raw 6176] А ням ням?
4. [raw 6177] Так на юге их тоже не мало)
5. [raw 6178] Я с тремя успел пересечься знакомыми, еще с кучей можно было)
6. [raw 6180] А я только что взял билеты в Израиль))', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5223, 'context', 'Ну там прям сотни как ты)))', '2026-06-25T22:57:32+00:00'),
  (1, 5224, 'context', 'Это хз куда надо в ебеня ехать)
Дананг сейчас топ направление', '2026-06-25T22:58:15+00:00'),
  (2, 5225, 'context', 'А ням ням?', '2026-06-25T22:58:32+00:00'),
  (3, 5226, 'context', 'Так на юге их тоже не мало)', '2026-06-25T22:58:46+00:00'),
  (4, 5227, 'context', 'Я с тремя успел пересечься знакомыми, еще с кучей можно было)', '2026-06-25T22:58:55+00:00'),
  (5, 5229, 'context', 'А я только что взял билеты в Израиль))', '2026-06-25T23:00:52+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:58:15+00:00', '2026-06-25T23:00:52+00:00', 5, 0.56, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6175] Это хз куда надо в ебеня ехать)
Дананг сейчас топ направление
2. [raw 6176] А ням ням?
3. [raw 6177] Так на юге их тоже не мало)
4. [raw 6178] Я с тремя успел пересечься знакомыми, еще с кучей можно было)
5. [raw 6180] А я только что взял билеты в Израиль))', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5224, 'context', 'Это хз куда надо в ебеня ехать)
Дананг сейчас топ направление', '2026-06-25T22:58:15+00:00'),
  (1, 5225, 'context', 'А ням ням?', '2026-06-25T22:58:32+00:00'),
  (2, 5226, 'context', 'Так на юге их тоже не мало)', '2026-06-25T22:58:46+00:00'),
  (3, 5227, 'context', 'Я с тремя успел пересечься знакомыми, еще с кучей можно было)', '2026-06-25T22:58:55+00:00'),
  (4, 5229, 'context', 'А я только что взял билеты в Израиль))', '2026-06-25T23:00:52+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1002165514145, 763758641152, 763758641152, '2026-06-25T22:03:00+00:00', '2026-06-25T22:05:25+00:00', 4, 0.64, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","RESOURCE_LINK_WITH_EXPLANATION","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 5977] Лучше просто на облако ссылку, я на ПК такое качать не буду
2. [raw 5978] В Субботу 18:00 МСК, ссылка будет в канале
3. [raw 5982] Если у проекта 0 юзеров, это большой минус?
4. [raw 6001] Нет', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5036, 'context', 'Лучше просто на облако ссылку, я на ПК такое качать не буду', '2026-06-25T22:03:00+00:00'),
  (1, 5037, 'context', 'В Субботу 18:00 МСК, ссылка будет в канале', '2026-06-25T22:03:11+00:00'),
  (2, 5041, 'context', 'Если у проекта 0 юзеров, это большой минус?', '2026-06-25T22:03:53+00:00'),
  (3, 5050, 'context', 'Нет', '2026-06-25T22:05:25+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1002165514145, 763758641152, 763758641152, '2026-06-25T22:03:11+00:00', '2026-06-25T22:05:25+00:00', 3, 0.62, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","RESOURCE_LINK_WITH_EXPLANATION","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 5978] В Субботу 18:00 МСК, ссылка будет в канале
2. [raw 5982] Если у проекта 0 юзеров, это большой минус?
3. [raw 6001] Нет', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5037, 'context', 'В Субботу 18:00 МСК, ссылка будет в канале', '2026-06-25T22:03:11+00:00'),
  (1, 5041, 'context', 'Если у проекта 0 юзеров, это большой минус?', '2026-06-25T22:03:53+00:00'),
  (2, 5050, 'context', 'Нет', '2026-06-25T22:05:25+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003922856266, 142, 142, '2026-06-25T22:12:03+00:00', '2026-06-25T22:17:36+00:00', 6, 0.78, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","RESOURCE_LINK_WITH_EXPLANATION","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6038] как ахуенно
2. [raw 6040] Круто оч
3. [raw 6041] я чую сарказм
4. [raw 6072] Не, серьзено
5. [raw 6077] Это типо хдрезка?
6. [raw 6107] если что само приложение не глм делал, за основу взят https://github.com/truelockmc/streambert . В нём русской озвучки фильмов не было, я сказал ему спиздить озвучки и сами фильмы с http://lampa.mx/', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5087, 'context', 'как ахуенно', '2026-06-25T22:12:03+00:00'),
  (1, 5088, 'context', 'Круто оч', '2026-06-25T22:12:18+00:00'),
  (2, 5089, 'context', 'я чую сарказм', '2026-06-25T22:12:28+00:00'),
  (3, 5122, 'context', 'Не, серьзено', '2026-06-25T22:15:24+00:00'),
  (4, 5121, 'context', 'Это типо хдрезка?', '2026-06-25T22:15:30+00:00'),
  (5, 5156, 'context', 'если что само приложение не глм делал, за основу взят https://github.com/truelockmc/streambert . В нём русской озвучки фильмов не было, я сказал ему спиздить озвучки и сами фильмы с http://lampa.mx/', '2026-06-25T22:17:36+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003922856266, 142, 142, '2026-06-25T22:12:18+00:00', '2026-06-25T22:17:55+00:00', 6, 0.78, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","RESOURCE_LINK_WITH_EXPLANATION","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6040] Круто оч
2. [raw 6041] я чую сарказм
3. [raw 6072] Не, серьзено
4. [raw 6077] Это типо хдрезка?
5. [raw 6107] если что само приложение не глм делал, за основу взят https://github.com/truelockmc/streambert . В нём русской озвучки фильмов не было, я сказал ему спиздить озвучки и сами фильмы с http://lampa.mx/
6. [raw 6109] и он сделал плеер отдельный', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5088, 'context', 'Круто оч', '2026-06-25T22:12:18+00:00'),
  (1, 5089, 'context', 'я чую сарказм', '2026-06-25T22:12:28+00:00'),
  (2, 5122, 'context', 'Не, серьзено', '2026-06-25T22:15:24+00:00'),
  (3, 5121, 'context', 'Это типо хдрезка?', '2026-06-25T22:15:30+00:00'),
  (4, 5156, 'context', 'если что само приложение не глм делал, за основу взят https://github.com/truelockmc/streambert . В нём русской озвучки фильмов не было, я сказал ему спиздить озвучки и сами фильмы с http://lampa.mx/', '2026-06-25T22:17:36+00:00'),
  (5, 5157, 'context', 'и он сделал плеер отдельный', '2026-06-25T22:17:55+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003922856266, 142, 142, '2026-06-25T22:12:28+00:00', '2026-06-25T22:18:03+00:00', 6, 0.78, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","RESOURCE_LINK_WITH_EXPLANATION","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6041] я чую сарказм
2. [raw 6072] Не, серьзено
3. [raw 6077] Это типо хдрезка?
4. [raw 6107] если что само приложение не глм делал, за основу взят https://github.com/truelockmc/streambert . В нём русской озвучки фильмов не было, я сказал ему спиздить озвучки и сами фильмы с http://lampa.mx/
5. [raw 6109] и он сделал плеер отдельный
6. [raw 6110] не прям вау, просто прикольненько', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5089, 'context', 'я чую сарказм', '2026-06-25T22:12:28+00:00'),
  (1, 5122, 'context', 'Не, серьзено', '2026-06-25T22:15:24+00:00'),
  (2, 5121, 'context', 'Это типо хдрезка?', '2026-06-25T22:15:30+00:00'),
  (3, 5156, 'context', 'если что само приложение не глм делал, за основу взят https://github.com/truelockmc/streambert . В нём русской озвучки фильмов не было, я сказал ему спиздить озвучки и сами фильмы с http://lampa.mx/', '2026-06-25T22:17:36+00:00'),
  (4, 5157, 'context', 'и он сделал плеер отдельный', '2026-06-25T22:17:55+00:00'),
  (5, 5158, 'context', 'не прям вау, просто прикольненько', '2026-06-25T22:18:03+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003922856266, 142, 142, '2026-06-25T22:15:24+00:00', '2026-06-25T22:20:33+00:00', 6, 0.78, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","RESOURCE_LINK_WITH_EXPLANATION","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6072] Не, серьзено
2. [raw 6077] Это типо хдрезка?
3. [raw 6107] если что само приложение не глм делал, за основу взят https://github.com/truelockmc/streambert . В нём русской озвучки фильмов не было, я сказал ему спиздить озвучки и сами фильмы с http://lampa.mx/
4. [raw 6109] и он сделал плеер отдельный
5. [raw 6110] не прям вау, просто прикольненько
6. [raw 6115] Ага', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5122, 'context', 'Не, серьзено', '2026-06-25T22:15:24+00:00'),
  (1, 5121, 'context', 'Это типо хдрезка?', '2026-06-25T22:15:30+00:00'),
  (2, 5156, 'context', 'если что само приложение не глм делал, за основу взят https://github.com/truelockmc/streambert . В нём русской озвучки фильмов не было, я сказал ему спиздить озвучки и сами фильмы с http://lampa.mx/', '2026-06-25T22:17:36+00:00'),
  (3, 5157, 'context', 'и он сделал плеер отдельный', '2026-06-25T22:17:55+00:00'),
  (4, 5158, 'context', 'не прям вау, просто прикольненько', '2026-06-25T22:18:03+00:00'),
  (5, 5164, 'context', 'Ага', '2026-06-25T22:20:33+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003922856266, 142, 142, '2026-06-25T22:15:30+00:00', '2026-06-25T22:20:35+00:00', 6, 0.78, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","RESOURCE_LINK_WITH_EXPLANATION","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6077] Это типо хдрезка?
2. [raw 6107] если что само приложение не глм делал, за основу взят https://github.com/truelockmc/streambert . В нём русской озвучки фильмов не было, я сказал ему спиздить озвучки и сами фильмы с http://lampa.mx/
3. [raw 6109] и он сделал плеер отдельный
4. [raw 6110] не прям вау, просто прикольненько
5. [raw 6115] Ага
6. [raw 6116] Хорош', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5121, 'context', 'Это типо хдрезка?', '2026-06-25T22:15:30+00:00'),
  (1, 5156, 'context', 'если что само приложение не глм делал, за основу взят https://github.com/truelockmc/streambert . В нём русской озвучки фильмов не было, я сказал ему спиздить озвучки и сами фильмы с http://lampa.mx/', '2026-06-25T22:17:36+00:00'),
  (2, 5157, 'context', 'и он сделал плеер отдельный', '2026-06-25T22:17:55+00:00'),
  (3, 5158, 'context', 'не прям вау, просто прикольненько', '2026-06-25T22:18:03+00:00'),
  (4, 5164, 'context', 'Ага', '2026-06-25T22:20:33+00:00'),
  (5, 5165, 'context', 'Хорош', '2026-06-25T22:20:35+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003922856266, 142, 142, '2026-06-25T22:17:36+00:00', '2026-06-25T22:23:40+00:00', 6, 0.6, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["RESOURCE_LINK_WITH_EXPLANATION","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6107] если что само приложение не глм делал, за основу взят https://github.com/truelockmc/streambert . В нём русской озвучки фильмов не было, я сказал ему спиздить озвучки и сами фильмы с http://lampa.mx/
2. [raw 6109] и он сделал плеер отдельный
3. [raw 6110] не прям вау, просто прикольненько
4. [raw 6115] Ага
5. [raw 6116] Хорош
6. [raw 6121] я могу его залить на гит если кому надо, вроде норм работает', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5156, 'context', 'если что само приложение не глм делал, за основу взят https://github.com/truelockmc/streambert . В нём русской озвучки фильмов не было, я сказал ему спиздить озвучки и сами фильмы с http://lampa.mx/', '2026-06-25T22:17:36+00:00'),
  (1, 5157, 'context', 'и он сделал плеер отдельный', '2026-06-25T22:17:55+00:00'),
  (2, 5158, 'context', 'не прям вау, просто прикольненько', '2026-06-25T22:18:03+00:00'),
  (3, 5164, 'context', 'Ага', '2026-06-25T22:20:33+00:00'),
  (4, 5165, 'context', 'Хорош', '2026-06-25T22:20:35+00:00'),
  (5, 5170, 'context', 'я могу его залить на гит если кому надо, вроде норм работает', '2026-06-25T22:23:40+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003922856266, 142, 142, '2026-06-25T23:14:18+00:00', '2026-06-25T23:30:16+00:00', 6, 0.78, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","RESOURCE_LINK_WITH_EXPLANATION","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6192] Да
2. [raw 6208] кароче, я хуй знает работает ли оно вообще с гита, как на винде работает, бутаться на винду мне лень, но на арче всё с кайфом летает. Вот репо https://github.com/kroch228/lampa-stream дайте старок пжпжп. Можете скинуть ии с промтом установи пжпж
3. [raw 6210] тестите
4. [raw 6211] .
5. [raw 6212] .
6. [raw 6213] Ну так по проси агента пусть под  .exe сделает епта', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5241, 'context', 'Да', '2026-06-25T23:14:18+00:00'),
  (1, 5257, 'context', 'кароче, я хуй знает работает ли оно вообще с гита, как на винде работает, бутаться на винду мне лень, но на арче всё с кайфом летает. Вот репо https://github.com/kroch228/lampa-stream дайте старок пжпжп. Можете скинуть ии с промтом установи пжпж', '2026-06-25T23:28:23+00:00'),
  (2, 5259, 'context', 'тестите', '2026-06-25T23:29:01+00:00'),
  (3, 5260, 'context', '.', '2026-06-25T23:29:06+00:00'),
  (4, 5261, 'context', '.', '2026-06-25T23:29:13+00:00'),
  (5, 5262, 'context', 'Ну так по проси агента пусть под  .exe сделает епта', '2026-06-25T23:30:16+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003922856266, 142, 142, '2026-06-25T23:28:23+00:00', '2026-06-25T23:30:26+00:00', 6, 0.78, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","RESOURCE_LINK_WITH_EXPLANATION","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6208] кароче, я хуй знает работает ли оно вообще с гита, как на винде работает, бутаться на винду мне лень, но на арче всё с кайфом летает. Вот репо https://github.com/kroch228/lampa-stream дайте старок пжпжп. Можете скинуть ии с промтом установи пжпж
2. [raw 6210] тестите
3. [raw 6211] .
4. [raw 6212] .
5. [raw 6213] Ну так по проси агента пусть под  .exe сделает епта
6. [raw 6215] тяжело', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5257, 'context', 'кароче, я хуй знает работает ли оно вообще с гита, как на винде работает, бутаться на винду мне лень, но на арче всё с кайфом летает. Вот репо https://github.com/kroch228/lampa-stream дайте старок пжпжп. Можете скинуть ии с промтом установи пжпж', '2026-06-25T23:28:23+00:00'),
  (1, 5259, 'context', 'тестите', '2026-06-25T23:29:01+00:00'),
  (2, 5260, 'context', '.', '2026-06-25T23:29:06+00:00'),
  (3, 5261, 'context', '.', '2026-06-25T23:29:13+00:00'),
  (4, 5262, 'context', 'Ну так по проси агента пусть под  .exe сделает епта', '2026-06-25T23:30:16+00:00'),
  (5, 5263, 'context', 'тяжело', '2026-06-25T23:30:26+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003922856266, 142, 142, '2026-06-25T23:30:33+00:00', '2026-06-25T23:30:54+00:00', 6, 0.58, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6218] ща
2. [raw 6219] дадите ОС?
3. [raw 6220] собираю ОС по кли
4. [raw 6221] на каком принципе мы основываемся: сделать работу простой, удобной и эффективной без ебли с кучей скиллов, плагинов и установок - интуитивно понятно, решая главную проблему вайбкодеров
5. [raw 6222] ГОРЕНИЕ ЖОПЫ
6. [raw 6223] поэтому мне нужно знать - от чего у тебя чаще всего горит жопа в ВК', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5265, 'context', 'ща', '2026-06-25T23:30:33+00:00'),
  (1, 5266, 'context', 'дадите ОС?', '2026-06-25T23:30:35+00:00'),
  (2, 5269, 'context', 'собираю ОС по кли', '2026-06-25T23:30:54+00:00'),
  (3, 5270, 'context', 'на каком принципе мы основываемся: сделать работу простой, удобной и эффективной без ебли с кучей скиллов, плагинов и установок - интуитивно понятно, решая главную проблему вайбкодеров', '2026-06-25T23:30:54+00:00'),
  (4, 5271, 'context', 'ГОРЕНИЕ ЖОПЫ', '2026-06-25T23:30:54+00:00'),
  (5, 5272, 'context', 'поэтому мне нужно знать - от чего у тебя чаще всего горит жопа в ВК', '2026-06-25T23:30:54+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003922856266, 142, 142, '2026-06-25T23:30:35+00:00', '2026-06-25T23:30:54+00:00', 6, 0.98, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","CHECKLIST_OR_LIST","TECHNICAL_API_CACHE_COST","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6219] дадите ОС?
2. [raw 6220] собираю ОС по кли
3. [raw 6221] на каком принципе мы основываемся: сделать работу простой, удобной и эффективной без ебли с кучей скиллов, плагинов и установок - интуитивно понятно, решая главную проблему вайбкодеров
4. [raw 6222] ГОРЕНИЕ ЖОПЫ
5. [raw 6223] поэтому мне нужно знать - от чего у тебя чаще всего горит жопа в ВК
6. [raw 6224] актуальный уже существующий список:

1. кривой ии-слопный фронт
2. объяснение агенту простых очевидных вещей которых он не знает, отчего ты вынужден тратить время и токены на инференс
3. отсутствие фри моделей в агенте
4. непонятный вырвиглазный больничный сложный интерфейс и огромное количество мусорного функционала
5. отсутствие опенсурс фактора
6. неумение агентов работать с узкими сферами на приличном уровне: парсинг, отказ работы с кибербезом
7. типичные постоянно повторяющиесся ошибки деплоя
8. отсутствие осведомленности о готовых опенсурс источников для ассетов, иных типов файлов / документации
9. непомерные объемы траты токенов
10. отсутствие оптимизации - нагрузка на устройство
11. проблемы с работой с определенными моделями - сложности с настройкой тулл колс, деградация контекста', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5266, 'context', 'дадите ОС?', '2026-06-25T23:30:35+00:00'),
  (1, 5269, 'context', 'собираю ОС по кли', '2026-06-25T23:30:54+00:00'),
  (2, 5270, 'context', 'на каком принципе мы основываемся: сделать работу простой, удобной и эффективной без ебли с кучей скиллов, плагинов и установок - интуитивно понятно, решая главную проблему вайбкодеров', '2026-06-25T23:30:54+00:00'),
  (3, 5271, 'context', 'ГОРЕНИЕ ЖОПЫ', '2026-06-25T23:30:54+00:00'),
  (4, 5272, 'context', 'поэтому мне нужно знать - от чего у тебя чаще всего горит жопа в ВК', '2026-06-25T23:30:54+00:00'),
  (5, 5273, 'context', 'актуальный уже существующий список:

1. кривой ии-слопный фронт
2. объяснение агенту простых очевидных вещей которых он не знает, отчего ты вынужден тратить время и токены на инференс
3. отсутствие фри моделей в агенте
4. непонятный вырвиглазный больничный сложный интерфейс и огромное количество мусорного функционала
5. отсутствие опенсурс фактора
6. неумение агентов работать с узкими сферами на приличном уровне: парсинг, отказ работы с кибербезом
7. типичные постоянно повторяющиесся ошибки депл', '2026-06-25T23:30:54+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003922856266, 142, 142, '2026-06-25T23:30:54+00:00', '2026-06-25T23:30:54+00:00', 6, 0.9, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","CHECKLIST_OR_LIST","TECHNICAL_API_CACHE_COST","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6220] собираю ОС по кли
2. [raw 6221] на каком принципе мы основываемся: сделать работу простой, удобной и эффективной без ебли с кучей скиллов, плагинов и установок - интуитивно понятно, решая главную проблему вайбкодеров
3. [raw 6222] ГОРЕНИЕ ЖОПЫ
4. [raw 6223] поэтому мне нужно знать - от чего у тебя чаще всего горит жопа в ВК
5. [raw 6224] актуальный уже существующий список:

1. кривой ии-слопный фронт
2. объяснение агенту простых очевидных вещей которых он не знает, отчего ты вынужден тратить время и токены на инференс
3. отсутствие фри моделей в агенте
4. непонятный вырвиглазный больничный сложный интерфейс и огромное количество мусорного функционала
5. отсутствие опенсурс фактора
6. неумение агентов работать с узкими сферами на приличном уровне: парсинг, отказ работы с кибербезом
7. типичные постоянно повторяющиесся ошибки деплоя
8. отсутствие осведомленности о готовых опенсурс источников для ассетов, иных типов файлов / документации
9. непомерные объемы траты токенов
10. отсутствие оптимизации - нагрузка на устройство
11. проблемы с работой с определенными моделями - сложности с настройкой тулл колс, деградация контекста
6. [raw 6225] если есть какие-то идеи - буду рад ОС', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5269, 'context', 'собираю ОС по кли', '2026-06-25T23:30:54+00:00'),
  (1, 5270, 'context', 'на каком принципе мы основываемся: сделать работу простой, удобной и эффективной без ебли с кучей скиллов, плагинов и установок - интуитивно понятно, решая главную проблему вайбкодеров', '2026-06-25T23:30:54+00:00'),
  (2, 5271, 'context', 'ГОРЕНИЕ ЖОПЫ', '2026-06-25T23:30:54+00:00'),
  (3, 5272, 'context', 'поэтому мне нужно знать - от чего у тебя чаще всего горит жопа в ВК', '2026-06-25T23:30:54+00:00'),
  (4, 5273, 'context', 'актуальный уже существующий список:

1. кривой ии-слопный фронт
2. объяснение агенту простых очевидных вещей которых он не знает, отчего ты вынужден тратить время и токены на инференс
3. отсутствие фри моделей в агенте
4. непонятный вырвиглазный больничный сложный интерфейс и огромное количество мусорного функционала
5. отсутствие опенсурс фактора
6. неумение агентов работать с узкими сферами на приличном уровне: парсинг, отказ работы с кибербезом
7. типичные постоянно повторяющиесся ошибки депл', '2026-06-25T23:30:54+00:00'),
  (5, 5274, 'context', 'если есть какие-то идеи - буду рад ОС', '2026-06-25T23:30:54+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003922856266, 142, 142, '2026-06-25T23:30:54+00:00', '2026-06-25T23:31:01+00:00', 6, 0.8, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","CHECKLIST_OR_LIST","TECHNICAL_API_CACHE_COST","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6221] на каком принципе мы основываемся: сделать работу простой, удобной и эффективной без ебли с кучей скиллов, плагинов и установок - интуитивно понятно, решая главную проблему вайбкодеров
2. [raw 6222] ГОРЕНИЕ ЖОПЫ
3. [raw 6223] поэтому мне нужно знать - от чего у тебя чаще всего горит жопа в ВК
4. [raw 6224] актуальный уже существующий список:

1. кривой ии-слопный фронт
2. объяснение агенту простых очевидных вещей которых он не знает, отчего ты вынужден тратить время и токены на инференс
3. отсутствие фри моделей в агенте
4. непонятный вырвиглазный больничный сложный интерфейс и огромное количество мусорного функционала
5. отсутствие опенсурс фактора
6. неумение агентов работать с узкими сферами на приличном уровне: парсинг, отказ работы с кибербезом
7. типичные постоянно повторяющиесся ошибки деплоя
8. отсутствие осведомленности о готовых опенсурс источников для ассетов, иных типов файлов / документации
9. непомерные объемы траты токенов
10. отсутствие оптимизации - нагрузка на устройство
11. проблемы с работой с определенными моделями - сложности с настройкой тулл колс, деградация контекста
5. [raw 6225] если есть какие-то идеи - буду рад ОС
6. [raw 6226] это по "SINGULAR"', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5270, 'context', 'на каком принципе мы основываемся: сделать работу простой, удобной и эффективной без ебли с кучей скиллов, плагинов и установок - интуитивно понятно, решая главную проблему вайбкодеров', '2026-06-25T23:30:54+00:00'),
  (1, 5271, 'context', 'ГОРЕНИЕ ЖОПЫ', '2026-06-25T23:30:54+00:00'),
  (2, 5272, 'context', 'поэтому мне нужно знать - от чего у тебя чаще всего горит жопа в ВК', '2026-06-25T23:30:54+00:00'),
  (3, 5273, 'context', 'актуальный уже существующий список:

1. кривой ии-слопный фронт
2. объяснение агенту простых очевидных вещей которых он не знает, отчего ты вынужден тратить время и токены на инференс
3. отсутствие фри моделей в агенте
4. непонятный вырвиглазный больничный сложный интерфейс и огромное количество мусорного функционала
5. отсутствие опенсурс фактора
6. неумение агентов работать с узкими сферами на приличном уровне: парсинг, отказ работы с кибербезом
7. типичные постоянно повторяющиесся ошибки депл', '2026-06-25T23:30:54+00:00'),
  (4, 5274, 'context', 'если есть какие-то идеи - буду рад ОС', '2026-06-25T23:30:54+00:00'),
  (5, 5275, 'context', 'это по "SINGULAR"', '2026-06-25T23:31:01+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003922856266, 142, 142, '2026-06-25T23:30:54+00:00', '2026-06-25T23:48:38+00:00', 6, 0.8, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","CHECKLIST_OR_LIST","TECHNICAL_API_CACHE_COST","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6222] ГОРЕНИЕ ЖОПЫ
2. [raw 6223] поэтому мне нужно знать - от чего у тебя чаще всего горит жопа в ВК
3. [raw 6224] актуальный уже существующий список:

1. кривой ии-слопный фронт
2. объяснение агенту простых очевидных вещей которых он не знает, отчего ты вынужден тратить время и токены на инференс
3. отсутствие фри моделей в агенте
4. непонятный вырвиглазный больничный сложный интерфейс и огромное количество мусорного функционала
5. отсутствие опенсурс фактора
6. неумение агентов работать с узкими сферами на приличном уровне: парсинг, отказ работы с кибербезом
7. типичные постоянно повторяющиесся ошибки деплоя
8. отсутствие осведомленности о готовых опенсурс источников для ассетов, иных типов файлов / документации
9. непомерные объемы траты токенов
10. отсутствие оптимизации - нагрузка на устройство
11. проблемы с работой с определенными моделями - сложности с настройкой тулл колс, деградация контекста
4. [raw 6225] если есть какие-то идеи - буду рад ОС
5. [raw 6226] это по "SINGULAR"
6. [raw 6258] я надеюсь глм не решил туда раточку засунуть', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5271, 'context', 'ГОРЕНИЕ ЖОПЫ', '2026-06-25T23:30:54+00:00'),
  (1, 5272, 'context', 'поэтому мне нужно знать - от чего у тебя чаще всего горит жопа в ВК', '2026-06-25T23:30:54+00:00'),
  (2, 5273, 'context', 'актуальный уже существующий список:

1. кривой ии-слопный фронт
2. объяснение агенту простых очевидных вещей которых он не знает, отчего ты вынужден тратить время и токены на инференс
3. отсутствие фри моделей в агенте
4. непонятный вырвиглазный больничный сложный интерфейс и огромное количество мусорного функционала
5. отсутствие опенсурс фактора
6. неумение агентов работать с узкими сферами на приличном уровне: парсинг, отказ работы с кибербезом
7. типичные постоянно повторяющиесся ошибки депл', '2026-06-25T23:30:54+00:00'),
  (3, 5274, 'context', 'если есть какие-то идеи - буду рад ОС', '2026-06-25T23:30:54+00:00'),
  (4, 5275, 'context', 'это по "SINGULAR"', '2026-06-25T23:31:01+00:00'),
  (5, 5307, 'context', 'я надеюсь глм не решил туда раточку засунуть', '2026-06-25T23:48:38+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003922856266, 142, 142, '2026-06-25T23:30:54+00:00', '2026-06-25T23:48:51+00:00', 6, 0.8, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","CHECKLIST_OR_LIST","TECHNICAL_API_CACHE_COST","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6223] поэтому мне нужно знать - от чего у тебя чаще всего горит жопа в ВК
2. [raw 6224] актуальный уже существующий список:

1. кривой ии-слопный фронт
2. объяснение агенту простых очевидных вещей которых он не знает, отчего ты вынужден тратить время и токены на инференс
3. отсутствие фри моделей в агенте
4. непонятный вырвиглазный больничный сложный интерфейс и огромное количество мусорного функционала
5. отсутствие опенсурс фактора
6. неумение агентов работать с узкими сферами на приличном уровне: парсинг, отказ работы с кибербезом
7. типичные постоянно повторяющиесся ошибки деплоя
8. отсутствие осведомленности о готовых опенсурс источников для ассетов, иных типов файлов / документации
9. непомерные объемы траты токенов
10. отсутствие оптимизации - нагрузка на устройство
11. проблемы с работой с определенными моделями - сложности с настройкой тулл колс, деградация контекста
3. [raw 6225] если есть какие-то идеи - буду рад ОС
4. [raw 6226] это по "SINGULAR"
5. [raw 6258] я надеюсь глм не решил туда раточку засунуть
6. [raw 6259] он чёт там сделал вроде, затести', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5272, 'context', 'поэтому мне нужно знать - от чего у тебя чаще всего горит жопа в ВК', '2026-06-25T23:30:54+00:00'),
  (1, 5273, 'context', 'актуальный уже существующий список:

1. кривой ии-слопный фронт
2. объяснение агенту простых очевидных вещей которых он не знает, отчего ты вынужден тратить время и токены на инференс
3. отсутствие фри моделей в агенте
4. непонятный вырвиглазный больничный сложный интерфейс и огромное количество мусорного функционала
5. отсутствие опенсурс фактора
6. неумение агентов работать с узкими сферами на приличном уровне: парсинг, отказ работы с кибербезом
7. типичные постоянно повторяющиесся ошибки депл', '2026-06-25T23:30:54+00:00'),
  (2, 5274, 'context', 'если есть какие-то идеи - буду рад ОС', '2026-06-25T23:30:54+00:00'),
  (3, 5275, 'context', 'это по "SINGULAR"', '2026-06-25T23:31:01+00:00'),
  (4, 5307, 'context', 'я надеюсь глм не решил туда раточку засунуть', '2026-06-25T23:48:38+00:00'),
  (5, 5308, 'context', 'он чёт там сделал вроде, затести', '2026-06-25T23:48:51+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003922856266, 142, 142, '2026-06-25T23:30:54+00:00', '2026-06-25T23:48:51+00:00', 5, 0.78, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","CHECKLIST_OR_LIST","TECHNICAL_API_CACHE_COST","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6224] актуальный уже существующий список:

1. кривой ии-слопный фронт
2. объяснение агенту простых очевидных вещей которых он не знает, отчего ты вынужден тратить время и токены на инференс
3. отсутствие фри моделей в агенте
4. непонятный вырвиглазный больничный сложный интерфейс и огромное количество мусорного функционала
5. отсутствие опенсурс фактора
6. неумение агентов работать с узкими сферами на приличном уровне: парсинг, отказ работы с кибербезом
7. типичные постоянно повторяющиесся ошибки деплоя
8. отсутствие осведомленности о готовых опенсурс источников для ассетов, иных типов файлов / документации
9. непомерные объемы траты токенов
10. отсутствие оптимизации - нагрузка на устройство
11. проблемы с работой с определенными моделями - сложности с настройкой тулл колс, деградация контекста
2. [raw 6225] если есть какие-то идеи - буду рад ОС
3. [raw 6226] это по "SINGULAR"
4. [raw 6258] я надеюсь глм не решил туда раточку засунуть
5. [raw 6259] он чёт там сделал вроде, затести', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5273, 'context', 'актуальный уже существующий список:

1. кривой ии-слопный фронт
2. объяснение агенту простых очевидных вещей которых он не знает, отчего ты вынужден тратить время и токены на инференс
3. отсутствие фри моделей в агенте
4. непонятный вырвиглазный больничный сложный интерфейс и огромное количество мусорного функционала
5. отсутствие опенсурс фактора
6. неумение агентов работать с узкими сферами на приличном уровне: парсинг, отказ работы с кибербезом
7. типичные постоянно повторяющиесся ошибки депл', '2026-06-25T23:30:54+00:00'),
  (1, 5274, 'context', 'если есть какие-то идеи - буду рад ОС', '2026-06-25T23:30:54+00:00'),
  (2, 5275, 'context', 'это по "SINGULAR"', '2026-06-25T23:31:01+00:00'),
  (3, 5307, 'context', 'я надеюсь глм не решил туда раточку засунуть', '2026-06-25T23:48:38+00:00'),
  (4, 5308, 'context', 'он чёт там сделал вроде, затести', '2026-06-25T23:48:51+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003922856266, 116, 116, '2026-06-26T06:31:51+00:00', '2026-06-26T06:46:35+00:00', 2, 0.6, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","RESOURCE_LINK_WITH_EXPLANATION","REPEATED_ENTITY"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6683] 🎬 Качаем видео с YouTube вплоть до 8K без ограничений

Нашли удобную тулзу для загрузки роликов и плейлистов с YouTube в пару кликов.

➖ Скачивает как отдельные видео, так и целые плейлисты;
➖ Поддерживает качество от 144p до 8K;
➖ Сохраняет видео в MP4 и аудио в MP3;
➖ Запоминает выбранные настройки качества;
➖ Поддерживает массовую загрузку без потери скорости.

Забираем здесь.

✅ LOLZTEAM. Подписаться
2. [raw 6730] кароче, я хуй знает работает ли оно вообще с гита, как на винде работает, бутаться на винду мне лень, но на арче всё с кайфом летает. Вот репо https://github.com/kroch228/lampa-stream дайте старок пжпжп. Можете скинуть ии с промтом установи пжпж', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5732, 'context', '🎬 Качаем видео с YouTube вплоть до 8K без ограничений

Нашли удобную тулзу для загрузки роликов и плейлистов с YouTube в пару кликов.

➖ Скачивает как отдельные видео, так и целые плейлисты;
➖ Поддерживает качество от 144p до 8K;
➖ Сохраняет видео в MP4 и аудио в MP3;
➖ Запоминает выбранные настройки качества;
➖ Поддерживает массовую загрузку без потери скорости.

Забираем здесь.

✅ LOLZTEAM. Подписаться', '2026-06-26T06:31:51+00:00'),
  (1, 5779, 'context', 'кароче, я хуй знает работает ли оно вообще с гита, как на винде работает, бутаться на винду мне лень, но на арче всё с кайфом летает. Вот репо https://github.com/kroch228/lampa-stream дайте старок пжпжп. Можете скинуть ии с промтом установи пжпж', '2026-06-26T06:46:35+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1001843792932, 6411, 6411, '2026-06-26T02:35:20+00:00', '2026-06-26T05:03:23+00:00', 6, 0.78, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","CAREER_RESUME_SIGNAL","RISK_OR_CAUTION_SIGNAL","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6341] Здарова 
Ездил кто на психоделические ретриты? Хочу задать несколько вопросов
2. [raw 6360] Привет
Какая цель?
3. [raw 6538] Я собираюсь провести иследование на эту тему. От "решил что это нужно" до "последний интеграционный звонок от организации"
4. [raw 6539] Понял) 
А исследование в каком смысле?
5. [raw 6540] Как разные люди, имеющие разные опыты таких ретритов, видят весь процесс и какое мнение они теперь имеют на счет такой практики + какие бенефиты они приобрели. Вот)
6. [raw 6543] У меня есть знакомые (4 человек), которые ретритили.

После ретритов их проблемы не решались, они чаще уходили в "нужно жить в гармонии и по кайфу ваще". 

У всех после этого появились проблемы с целеполаганием типа "у самурая есть только путь". Моё  мнение - это плохо.

Плюсом они начинают погружаться в шизотерику и становятся ёбнутыми. Можешь послушать Мацкевича и Маркес, Саня Ильин и Декабрист делали на них реакт =)

Если хочешь какую-то болячку полечить / смысл жизни найти - ретрит это очень кривой способ достижения этих целей', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5390, 'context', 'Здарова 
Ездил кто на психоделические ретриты? Хочу задать несколько вопросов', '2026-06-26T02:35:20+00:00'),
  (1, 5409, 'context', 'Привет
Какая цель?', '2026-06-26T02:59:49+00:00'),
  (2, 5587, 'context', 'Я собираюсь провести иследование на эту тему. От "решил что это нужно" до "последний интеграционный звонок от организации"', '2026-06-26T04:37:15+00:00'),
  (3, 5588, 'context', 'Понял) 
А исследование в каком смысле?', '2026-06-26T04:38:22+00:00'),
  (4, 5589, 'context', 'Как разные люди, имеющие разные опыты таких ретритов, видят весь процесс и какое мнение они теперь имеют на счет такой практики + какие бенефиты они приобрели. Вот)', '2026-06-26T04:50:24+00:00'),
  (5, 5592, 'context', 'У меня есть знакомые (4 человек), которые ретритили.

После ретритов их проблемы не решались, они чаще уходили в "нужно жить в гармонии и по кайфу ваще". 

У всех после этого появились проблемы с целеполаганием типа "у самурая есть только путь". Моё  мнение - это плохо.

Плюсом они начинают погружаться в шизотерику и становятся ёбнутыми. Можешь послушать Мацкевича и Маркес, Саня Ильин и Декабрист делали на них реакт =)

Если хочешь какую-то болячку полечить / смысл жизни найти - ретрит это очень', '2026-06-26T05:03:23+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1001843792932, 6411, 6411, '2026-06-26T02:59:49+00:00', '2026-06-26T05:30:05+00:00', 6, 0.78, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","CAREER_RESUME_SIGNAL","RISK_OR_CAUTION_SIGNAL","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6360] Привет
Какая цель?
2. [raw 6538] Я собираюсь провести иследование на эту тему. От "решил что это нужно" до "последний интеграционный звонок от организации"
3. [raw 6539] Понял) 
А исследование в каком смысле?
4. [raw 6540] Как разные люди, имеющие разные опыты таких ретритов, видят весь процесс и какое мнение они теперь имеют на счет такой практики + какие бенефиты они приобрели. Вот)
5. [raw 6543] У меня есть знакомые (4 человек), которые ретритили.

После ретритов их проблемы не решались, они чаще уходили в "нужно жить в гармонии и по кайфу ваще". 

У всех после этого появились проблемы с целеполаганием типа "у самурая есть только путь". Моё  мнение - это плохо.

Плюсом они начинают погружаться в шизотерику и становятся ёбнутыми. Можешь послушать Мацкевича и Маркес, Саня Ильин и Декабрист делали на них реакт =)

Если хочешь какую-то болячку полечить / смысл жизни найти - ретрит это очень кривой способ достижения этих целей
6. [raw 6571] Если есть инфа какие ретриты они конкретно посещали, то буду рад знать', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5409, 'context', 'Привет
Какая цель?', '2026-06-26T02:59:49+00:00'),
  (1, 5587, 'context', 'Я собираюсь провести иследование на эту тему. От "решил что это нужно" до "последний интеграционный звонок от организации"', '2026-06-26T04:37:15+00:00'),
  (2, 5588, 'context', 'Понял) 
А исследование в каком смысле?', '2026-06-26T04:38:22+00:00'),
  (3, 5589, 'context', 'Как разные люди, имеющие разные опыты таких ретритов, видят весь процесс и какое мнение они теперь имеют на счет такой практики + какие бенефиты они приобрели. Вот)', '2026-06-26T04:50:24+00:00'),
  (4, 5592, 'context', 'У меня есть знакомые (4 человек), которые ретритили.

После ретритов их проблемы не решались, они чаще уходили в "нужно жить в гармонии и по кайфу ваще". 

У всех после этого появились проблемы с целеполаганием типа "у самурая есть только путь". Моё  мнение - это плохо.

Плюсом они начинают погружаться в шизотерику и становятся ёбнутыми. Можешь послушать Мацкевича и Маркес, Саня Ильин и Декабрист делали на них реакт =)

Если хочешь какую-то болячку полечить / смысл жизни найти - ретрит это очень', '2026-06-26T05:03:23+00:00'),
  (5, 5620, 'context', 'Если есть инфа какие ретриты они конкретно посещали, то буду рад знать', '2026-06-26T05:30:05+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1001843792932, 6411, 6411, '2026-06-26T04:37:15+00:00', '2026-06-26T05:30:05+00:00', 5, 0.66, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","CAREER_RESUME_SIGNAL","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6538] Я собираюсь провести иследование на эту тему. От "решил что это нужно" до "последний интеграционный звонок от организации"
2. [raw 6539] Понял) 
А исследование в каком смысле?
3. [raw 6540] Как разные люди, имеющие разные опыты таких ретритов, видят весь процесс и какое мнение они теперь имеют на счет такой практики + какие бенефиты они приобрели. Вот)
4. [raw 6543] У меня есть знакомые (4 человек), которые ретритили.

После ретритов их проблемы не решались, они чаще уходили в "нужно жить в гармонии и по кайфу ваще". 

У всех после этого появились проблемы с целеполаганием типа "у самурая есть только путь". Моё  мнение - это плохо.

Плюсом они начинают погружаться в шизотерику и становятся ёбнутыми. Можешь послушать Мацкевича и Маркес, Саня Ильин и Декабрист делали на них реакт =)

Если хочешь какую-то болячку полечить / смысл жизни найти - ретрит это очень кривой способ достижения этих целей
5. [raw 6571] Если есть инфа какие ретриты они конкретно посещали, то буду рад знать', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5587, 'context', 'Я собираюсь провести иследование на эту тему. От "решил что это нужно" до "последний интеграционный звонок от организации"', '2026-06-26T04:37:15+00:00'),
  (1, 5588, 'context', 'Понял) 
А исследование в каком смысле?', '2026-06-26T04:38:22+00:00'),
  (2, 5589, 'context', 'Как разные люди, имеющие разные опыты таких ретритов, видят весь процесс и какое мнение они теперь имеют на счет такой практики + какие бенефиты они приобрели. Вот)', '2026-06-26T04:50:24+00:00'),
  (3, 5592, 'context', 'У меня есть знакомые (4 человек), которые ретритили.

После ретритов их проблемы не решались, они чаще уходили в "нужно жить в гармонии и по кайфу ваще". 

У всех после этого появились проблемы с целеполаганием типа "у самурая есть только путь". Моё  мнение - это плохо.

Плюсом они начинают погружаться в шизотерику и становятся ёбнутыми. Можешь послушать Мацкевича и Маркес, Саня Ильин и Декабрист делали на них реакт =)

Если хочешь какую-то болячку полечить / смысл жизни найти - ретрит это очень', '2026-06-26T05:03:23+00:00'),
  (4, 5620, 'context', 'Если есть инфа какие ретриты они конкретно посещали, то буду рад знать', '2026-06-26T05:30:05+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1001843792932, 6411, 6411, '2026-06-26T04:38:22+00:00', '2026-06-26T05:30:05+00:00', 4, 0.64, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","CAREER_RESUME_SIGNAL","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6539] Понял) 
А исследование в каком смысле?
2. [raw 6540] Как разные люди, имеющие разные опыты таких ретритов, видят весь процесс и какое мнение они теперь имеют на счет такой практики + какие бенефиты они приобрели. Вот)
3. [raw 6543] У меня есть знакомые (4 человек), которые ретритили.

После ретритов их проблемы не решались, они чаще уходили в "нужно жить в гармонии и по кайфу ваще". 

У всех после этого появились проблемы с целеполаганием типа "у самурая есть только путь". Моё  мнение - это плохо.

Плюсом они начинают погружаться в шизотерику и становятся ёбнутыми. Можешь послушать Мацкевича и Маркес, Саня Ильин и Декабрист делали на них реакт =)

Если хочешь какую-то болячку полечить / смысл жизни найти - ретрит это очень кривой способ достижения этих целей
4. [raw 6571] Если есть инфа какие ретриты они конкретно посещали, то буду рад знать', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5588, 'context', 'Понял) 
А исследование в каком смысле?', '2026-06-26T04:38:22+00:00'),
  (1, 5589, 'context', 'Как разные люди, имеющие разные опыты таких ретритов, видят весь процесс и какое мнение они теперь имеют на счет такой практики + какие бенефиты они приобрели. Вот)', '2026-06-26T04:50:24+00:00'),
  (2, 5592, 'context', 'У меня есть знакомые (4 человек), которые ретритили.

После ретритов их проблемы не решались, они чаще уходили в "нужно жить в гармонии и по кайфу ваще". 

У всех после этого появились проблемы с целеполаганием типа "у самурая есть только путь". Моё  мнение - это плохо.

Плюсом они начинают погружаться в шизотерику и становятся ёбнутыми. Можешь послушать Мацкевича и Маркес, Саня Ильин и Декабрист делали на них реакт =)

Если хочешь какую-то болячку полечить / смысл жизни найти - ретрит это очень', '2026-06-26T05:03:23+00:00'),
  (3, 5620, 'context', 'Если есть инфа какие ретриты они конкретно посещали, то буду рад знать', '2026-06-26T05:30:05+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1001843792932, 6411, 6411, '2026-06-26T04:50:24+00:00', '2026-06-26T05:44:26+00:00', 6, 0.78, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","RESOURCE_LINK_WITH_EXPLANATION","CAREER_RESUME_SIGNAL","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6540] Как разные люди, имеющие разные опыты таких ретритов, видят весь процесс и какое мнение они теперь имеют на счет такой практики + какие бенефиты они приобрели. Вот)
2. [raw 6543] У меня есть знакомые (4 человек), которые ретритили.

После ретритов их проблемы не решались, они чаще уходили в "нужно жить в гармонии и по кайфу ваще". 

У всех после этого появились проблемы с целеполаганием типа "у самурая есть только путь". Моё  мнение - это плохо.

Плюсом они начинают погружаться в шизотерику и становятся ёбнутыми. Можешь послушать Мацкевича и Маркес, Саня Ильин и Декабрист делали на них реакт =)

Если хочешь какую-то болячку полечить / смысл жизни найти - ретрит это очень кривой способ достижения этих целей
3. [raw 6571] Если есть инфа какие ретриты они конкретно посещали, то буду рад знать
4. [raw 6574] Дай ссылки плиз, если не сложно
5. [raw 6575] https://www.youtube.com/live/IaFbBfD3IcA?si=7-MUzKNpUtqGikck
6. [raw 6591] Ты работал с такими людьми?', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5589, 'context', 'Как разные люди, имеющие разные опыты таких ретритов, видят весь процесс и какое мнение они теперь имеют на счет такой практики + какие бенефиты они приобрели. Вот)', '2026-06-26T04:50:24+00:00'),
  (1, 5592, 'context', 'У меня есть знакомые (4 человек), которые ретритили.

После ретритов их проблемы не решались, они чаще уходили в "нужно жить в гармонии и по кайфу ваще". 

У всех после этого появились проблемы с целеполаганием типа "у самурая есть только путь". Моё  мнение - это плохо.

Плюсом они начинают погружаться в шизотерику и становятся ёбнутыми. Можешь послушать Мацкевича и Маркес, Саня Ильин и Декабрист делали на них реакт =)

Если хочешь какую-то болячку полечить / смысл жизни найти - ретрит это очень', '2026-06-26T05:03:23+00:00'),
  (2, 5620, 'context', 'Если есть инфа какие ретриты они конкретно посещали, то буду рад знать', '2026-06-26T05:30:05+00:00'),
  (3, 5623, 'context', 'Дай ссылки плиз, если не сложно', '2026-06-26T05:30:48+00:00'),
  (4, 5624, 'context', 'https://www.youtube.com/live/IaFbBfD3IcA?si=7-MUzKNpUtqGikck', '2026-06-26T05:31:18+00:00'),
  (5, 5640, 'context', 'Ты работал с такими людьми?', '2026-06-26T05:44:26+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1001843792932, 6411, 6411, '2026-06-26T05:03:23+00:00', '2026-06-26T05:51:39+00:00', 6, 0.88, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","RESOURCE_LINK_WITH_EXPLANATION","CAREER_RESUME_SIGNAL","RISK_OR_CAUTION_SIGNAL","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6543] У меня есть знакомые (4 человек), которые ретритили.

После ретритов их проблемы не решались, они чаще уходили в "нужно жить в гармонии и по кайфу ваще". 

У всех после этого появились проблемы с целеполаганием типа "у самурая есть только путь". Моё  мнение - это плохо.

Плюсом они начинают погружаться в шизотерику и становятся ёбнутыми. Можешь послушать Мацкевича и Маркес, Саня Ильин и Декабрист делали на них реакт =)

Если хочешь какую-то болячку полечить / смысл жизни найти - ретрит это очень кривой способ достижения этих целей
2. [raw 6571] Если есть инфа какие ретриты они конкретно посещали, то буду рад знать
3. [raw 6574] Дай ссылки плиз, если не сложно
4. [raw 6575] https://www.youtube.com/live/IaFbBfD3IcA?si=7-MUzKNpUtqGikck
5. [raw 6591] Ты работал с такими людьми?
6. [raw 6592] Работал) 
Такие начинают бегать по потолку, когда оказывается, что можно формулировать и путь на 10/10, и цель на 10/10. Причём это делается за 40-60 минутов

Ещё интересное наблюдение - тревожность у некоторых людей отцепляется просто постановкой очень хорошей цели. Цели, в которую их тревожная голова с удовольствием погрузится. Появляется цель - и они начинают делать, а не гонять мысли по лобной доле 😁', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5592, 'context', 'У меня есть знакомые (4 человек), которые ретритили.

После ретритов их проблемы не решались, они чаще уходили в "нужно жить в гармонии и по кайфу ваще". 

У всех после этого появились проблемы с целеполаганием типа "у самурая есть только путь". Моё  мнение - это плохо.

Плюсом они начинают погружаться в шизотерику и становятся ёбнутыми. Можешь послушать Мацкевича и Маркес, Саня Ильин и Декабрист делали на них реакт =)

Если хочешь какую-то болячку полечить / смысл жизни найти - ретрит это очень', '2026-06-26T05:03:23+00:00'),
  (1, 5620, 'context', 'Если есть инфа какие ретриты они конкретно посещали, то буду рад знать', '2026-06-26T05:30:05+00:00'),
  (2, 5623, 'context', 'Дай ссылки плиз, если не сложно', '2026-06-26T05:30:48+00:00'),
  (3, 5624, 'context', 'https://www.youtube.com/live/IaFbBfD3IcA?si=7-MUzKNpUtqGikck', '2026-06-26T05:31:18+00:00'),
  (4, 5640, 'context', 'Ты работал с такими людьми?', '2026-06-26T05:44:26+00:00'),
  (5, 5641, 'context', 'Работал) 
Такие начинают бегать по потолку, когда оказывается, что можно формулировать и путь на 10/10, и цель на 10/10. Причём это делается за 40-60 минутов

Ещё интересное наблюдение - тревожность у некоторых людей отцепляется просто постановкой очень хорошей цели. Цели, в которую их тревожная голова с удовольствием погрузится. Появляется цель - и они начинают делать, а не гонять мысли по лобной доле 😁', '2026-06-26T05:51:39+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1001843792932, 6411, 6411, '2026-06-26T05:30:05+00:00', '2026-06-26T06:31:57+00:00', 6, 0.88, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","RESOURCE_LINK_WITH_EXPLANATION","CAREER_RESUME_SIGNAL","RISK_OR_CAUTION_SIGNAL","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6571] Если есть инфа какие ретриты они конкретно посещали, то буду рад знать
2. [raw 6574] Дай ссылки плиз, если не сложно
3. [raw 6575] https://www.youtube.com/live/IaFbBfD3IcA?si=7-MUzKNpUtqGikck
4. [raw 6591] Ты работал с такими людьми?
5. [raw 6592] Работал) 
Такие начинают бегать по потолку, когда оказывается, что можно формулировать и путь на 10/10, и цель на 10/10. Причём это делается за 40-60 минутов

Ещё интересное наблюдение - тревожность у некоторых людей отцепляется просто постановкой очень хорошей цели. Цели, в которую их тревожная голова с удовольствием погрузится. Появляется цель - и они начинают делать, а не гонять мысли по лобной доле 😁
6. [raw 6684] Приходилось ли переубеждать таких людей на счет убеждений с ретрита?', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5620, 'context', 'Если есть инфа какие ретриты они конкретно посещали, то буду рад знать', '2026-06-26T05:30:05+00:00'),
  (1, 5623, 'context', 'Дай ссылки плиз, если не сложно', '2026-06-26T05:30:48+00:00'),
  (2, 5624, 'context', 'https://www.youtube.com/live/IaFbBfD3IcA?si=7-MUzKNpUtqGikck', '2026-06-26T05:31:18+00:00'),
  (3, 5640, 'context', 'Ты работал с такими людьми?', '2026-06-26T05:44:26+00:00'),
  (4, 5641, 'context', 'Работал) 
Такие начинают бегать по потолку, когда оказывается, что можно формулировать и путь на 10/10, и цель на 10/10. Причём это делается за 40-60 минутов

Ещё интересное наблюдение - тревожность у некоторых людей отцепляется просто постановкой очень хорошей цели. Цели, в которую их тревожная голова с удовольствием погрузится. Появляется цель - и они начинают делать, а не гонять мысли по лобной доле 😁', '2026-06-26T05:51:39+00:00'),
  (5, 5733, 'context', 'Приходилось ли переубеждать таких людей на счет убеждений с ретрита?', '2026-06-26T06:31:57+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1001843792932, 6411, 6411, '2026-06-26T05:30:48+00:00', '2026-06-26T06:36:19+00:00', 6, 0.88, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","RESOURCE_LINK_WITH_EXPLANATION","CAREER_RESUME_SIGNAL","RISK_OR_CAUTION_SIGNAL","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6574] Дай ссылки плиз, если не сложно
2. [raw 6575] https://www.youtube.com/live/IaFbBfD3IcA?si=7-MUzKNpUtqGikck
3. [raw 6591] Ты работал с такими людьми?
4. [raw 6592] Работал) 
Такие начинают бегать по потолку, когда оказывается, что можно формулировать и путь на 10/10, и цель на 10/10. Причём это делается за 40-60 минутов

Ещё интересное наблюдение - тревожность у некоторых людей отцепляется просто постановкой очень хорошей цели. Цели, в которую их тревожная голова с удовольствием погрузится. Появляется цель - и они начинают делать, а не гонять мысли по лобной доле 😁
5. [raw 6684] Приходилось ли переубеждать таких людей на счет убеждений с ретрита?
6. [raw 6688] Конечно
Там у людей прилипает всякое дезадаптивное говно вперемешку с религией и шизотерикой

Глубоко там копать на самом деле часто не нужно: за парой-тройкой вопросов вглубь как в известном меме с шахтёром находится бриллиантовая залежь выгребная яма.

Когда ретритнутый это осознает, работа сильно ускоряется =)', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5623, 'context', 'Дай ссылки плиз, если не сложно', '2026-06-26T05:30:48+00:00'),
  (1, 5624, 'context', 'https://www.youtube.com/live/IaFbBfD3IcA?si=7-MUzKNpUtqGikck', '2026-06-26T05:31:18+00:00'),
  (2, 5640, 'context', 'Ты работал с такими людьми?', '2026-06-26T05:44:26+00:00'),
  (3, 5641, 'context', 'Работал) 
Такие начинают бегать по потолку, когда оказывается, что можно формулировать и путь на 10/10, и цель на 10/10. Причём это делается за 40-60 минутов

Ещё интересное наблюдение - тревожность у некоторых людей отцепляется просто постановкой очень хорошей цели. Цели, в которую их тревожная голова с удовольствием погрузится. Появляется цель - и они начинают делать, а не гонять мысли по лобной доле 😁', '2026-06-26T05:51:39+00:00'),
  (4, 5733, 'context', 'Приходилось ли переубеждать таких людей на счет убеждений с ретрита?', '2026-06-26T06:31:57+00:00'),
  (5, 5737, 'context', 'Конечно
Там у людей прилипает всякое дезадаптивное говно вперемешку с религией и шизотерикой

Глубоко там копать на самом деле часто не нужно: за парой-тройкой вопросов вглубь как в известном меме с шахтёром находится бриллиантовая залежь выгребная яма.

Когда ретритнутый это осознает, работа сильно ускоряется =)', '2026-06-26T06:36:19+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1001843792932, 6411, 6411, '2026-06-26T05:31:18+00:00', '2026-06-26T06:36:19+00:00', 5, 0.86, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","RESOURCE_LINK_WITH_EXPLANATION","CAREER_RESUME_SIGNAL","RISK_OR_CAUTION_SIGNAL","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6575] https://www.youtube.com/live/IaFbBfD3IcA?si=7-MUzKNpUtqGikck
2. [raw 6591] Ты работал с такими людьми?
3. [raw 6592] Работал) 
Такие начинают бегать по потолку, когда оказывается, что можно формулировать и путь на 10/10, и цель на 10/10. Причём это делается за 40-60 минутов

Ещё интересное наблюдение - тревожность у некоторых людей отцепляется просто постановкой очень хорошей цели. Цели, в которую их тревожная голова с удовольствием погрузится. Появляется цель - и они начинают делать, а не гонять мысли по лобной доле 😁
4. [raw 6684] Приходилось ли переубеждать таких людей на счет убеждений с ретрита?
5. [raw 6688] Конечно
Там у людей прилипает всякое дезадаптивное говно вперемешку с религией и шизотерикой

Глубоко там копать на самом деле часто не нужно: за парой-тройкой вопросов вглубь как в известном меме с шахтёром находится бриллиантовая залежь выгребная яма.

Когда ретритнутый это осознает, работа сильно ускоряется =)', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5624, 'context', 'https://www.youtube.com/live/IaFbBfD3IcA?si=7-MUzKNpUtqGikck', '2026-06-26T05:31:18+00:00'),
  (1, 5640, 'context', 'Ты работал с такими людьми?', '2026-06-26T05:44:26+00:00'),
  (2, 5641, 'context', 'Работал) 
Такие начинают бегать по потолку, когда оказывается, что можно формулировать и путь на 10/10, и цель на 10/10. Причём это делается за 40-60 минутов

Ещё интересное наблюдение - тревожность у некоторых людей отцепляется просто постановкой очень хорошей цели. Цели, в которую их тревожная голова с удовольствием погрузится. Появляется цель - и они начинают делать, а не гонять мысли по лобной доле 😁', '2026-06-26T05:51:39+00:00'),
  (3, 5733, 'context', 'Приходилось ли переубеждать таких людей на счет убеждений с ретрита?', '2026-06-26T06:31:57+00:00'),
  (4, 5737, 'context', 'Конечно
Там у людей прилипает всякое дезадаптивное говно вперемешку с религией и шизотерикой

Глубоко там копать на самом деле часто не нужно: за парой-тройкой вопросов вглубь как в известном меме с шахтёром находится бриллиантовая залежь выгребная яма.

Когда ретритнутый это осознает, работа сильно ускоряется =)', '2026-06-26T06:36:19+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1001843792932, 6411, 6411, '2026-06-26T05:44:26+00:00', '2026-06-26T06:36:19+00:00', 4, 0.74, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","CAREER_RESUME_SIGNAL","RISK_OR_CAUTION_SIGNAL","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6591] Ты работал с такими людьми?
2. [raw 6592] Работал) 
Такие начинают бегать по потолку, когда оказывается, что можно формулировать и путь на 10/10, и цель на 10/10. Причём это делается за 40-60 минутов

Ещё интересное наблюдение - тревожность у некоторых людей отцепляется просто постановкой очень хорошей цели. Цели, в которую их тревожная голова с удовольствием погрузится. Появляется цель - и они начинают делать, а не гонять мысли по лобной доле 😁
3. [raw 6684] Приходилось ли переубеждать таких людей на счет убеждений с ретрита?
4. [raw 6688] Конечно
Там у людей прилипает всякое дезадаптивное говно вперемешку с религией и шизотерикой

Глубоко там копать на самом деле часто не нужно: за парой-тройкой вопросов вглубь как в известном меме с шахтёром находится бриллиантовая залежь выгребная яма.

Когда ретритнутый это осознает, работа сильно ускоряется =)', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5640, 'context', 'Ты работал с такими людьми?', '2026-06-26T05:44:26+00:00'),
  (1, 5641, 'context', 'Работал) 
Такие начинают бегать по потолку, когда оказывается, что можно формулировать и путь на 10/10, и цель на 10/10. Причём это делается за 40-60 минутов

Ещё интересное наблюдение - тревожность у некоторых людей отцепляется просто постановкой очень хорошей цели. Цели, в которую их тревожная голова с удовольствием погрузится. Появляется цель - и они начинают делать, а не гонять мысли по лобной доле 😁', '2026-06-26T05:51:39+00:00'),
  (2, 5733, 'context', 'Приходилось ли переубеждать таких людей на счет убеждений с ретрита?', '2026-06-26T06:31:57+00:00'),
  (3, 5737, 'context', 'Конечно
Там у людей прилипает всякое дезадаптивное говно вперемешку с религией и шизотерикой

Глубоко там копать на самом деле часто не нужно: за парой-тройкой вопросов вглубь как в известном меме с шахтёром находится бриллиантовая залежь выгребная яма.

Когда ретритнутый это осознает, работа сильно ускоряется =)', '2026-06-26T06:36:19+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1001843792932, 6411, 6411, '2026-06-26T05:51:39+00:00', '2026-06-26T06:55:07+00:00', 4, 0.74, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","CAREER_RESUME_SIGNAL","RISK_OR_CAUTION_SIGNAL","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6592] Работал) 
Такие начинают бегать по потолку, когда оказывается, что можно формулировать и путь на 10/10, и цель на 10/10. Причём это делается за 40-60 минутов

Ещё интересное наблюдение - тревожность у некоторых людей отцепляется просто постановкой очень хорошей цели. Цели, в которую их тревожная голова с удовольствием погрузится. Появляется цель - и они начинают делать, а не гонять мысли по лобной доле 😁
2. [raw 6684] Приходилось ли переубеждать таких людей на счет убеждений с ретрита?
3. [raw 6688] Конечно
Там у людей прилипает всякое дезадаптивное говно вперемешку с религией и шизотерикой

Глубоко там копать на самом деле часто не нужно: за парой-тройкой вопросов вглубь как в известном меме с шахтёром находится бриллиантовая залежь выгребная яма.

Когда ретритнутый это осознает, работа сильно ускоряется =)
4. [raw 6820] А приходят изначально с выгоранием вообще :)', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5641, 'context', 'Работал) 
Такие начинают бегать по потолку, когда оказывается, что можно формулировать и путь на 10/10, и цель на 10/10. Причём это делается за 40-60 минутов

Ещё интересное наблюдение - тревожность у некоторых людей отцепляется просто постановкой очень хорошей цели. Цели, в которую их тревожная голова с удовольствием погрузится. Появляется цель - и они начинают делать, а не гонять мысли по лобной доле 😁', '2026-06-26T05:51:39+00:00'),
  (1, 5733, 'context', 'Приходилось ли переубеждать таких людей на счет убеждений с ретрита?', '2026-06-26T06:31:57+00:00'),
  (2, 5737, 'context', 'Конечно
Там у людей прилипает всякое дезадаптивное говно вперемешку с религией и шизотерикой

Глубоко там копать на самом деле часто не нужно: за парой-тройкой вопросов вглубь как в известном меме с шахтёром находится бриллиантовая залежь выгребная яма.

Когда ретритнутый это осознает, работа сильно ускоряется =)', '2026-06-26T06:36:19+00:00'),
  (3, 5873, 'context', 'А приходят изначально с выгоранием вообще :)', '2026-06-26T06:55:07+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1002922797592, 106, 106, '2026-06-26T06:40:47+00:00', '2026-06-26T06:53:47+00:00', 6, 0.98, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6691] Тони Монтана хуев
2. [raw 6765] почитайте теорию как работает кеширование в антропике. В подписке оно тоже используется, как и в api. Именно на первых запросах в сессии основная часть падает в кеш на запись, а лимиты в подписке считай что деньги в апи, потому что все измеряется в нагрузке на мощности. Потому так все выходит. 

Это не волшебство, а нагрузка на кеширование
3. [raw 6769] а по человечески? ))
4. [raw 6776] ну как по человечески. Кешироввние, в api на запись есть бабки. Дорого. Зато следующие токены вытаскиваются из кеша. Поэтому дешево. Так в апи.

В подписке то же самое, только вы не видите этого, оно под капотом
5. [raw 6778] потому выходит некоторые запросы жрут лимиты сразу, а некоторые нет (берутся из кеша)
6. [raw 6799] основные постулаты при кодинге
- не ходить в туалет, не жрать и не делать паузы более 5 минут между запросами, кеш сбрасывается. Есть 1 часовой кеш у МАкс тарифов на подписке. Там жрать и ссать можно, но кеш по идее дороже уже, поэтому тратится может быстрее лимиты', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5740, 'context', 'Тони Монтана хуев', '2026-06-26T06:40:47+00:00'),
  (1, 5816, 'context', 'почитайте теорию как работает кеширование в антропике. В подписке оно тоже используется, как и в api. Именно на первых запросах в сессии основная часть падает в кеш на запись, а лимиты в подписке считай что деньги в апи, потому что все измеряется в нагрузке на мощности. Потому так все выходит. 

Это не волшебство, а нагрузка на кеширование', '2026-06-26T06:49:55+00:00'),
  (2, 5815, 'context', 'а по человечески? ))', '2026-06-26T06:50:22+00:00'),
  (3, 5825, 'context', 'ну как по человечески. Кешироввние, в api на запись есть бабки. Дорого. Зато следующие токены вытаскиваются из кеша. Поэтому дешево. Так в апи.

В подписке то же самое, только вы не видите этого, оно под капотом', '2026-06-26T06:51:33+00:00'),
  (4, 5826, 'context', 'потому выходит некоторые запросы жрут лимиты сразу, а некоторые нет (берутся из кеша)', '2026-06-26T06:51:57+00:00'),
  (5, 5849, 'context', 'основные постулаты при кодинге
- не ходить в туалет, не жрать и не делать паузы более 5 минут между запросами, кеш сбрасывается. Есть 1 часовой кеш у МАкс тарифов на подписке. Там жрать и ссать можно, но кеш по идее дороже уже, поэтому тратится может быстрее лимиты', '2026-06-26T06:53:47+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1002922797592, 106, 106, '2026-06-26T06:49:55+00:00', '2026-06-26T06:54:07+00:00', 6, 0.98, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6765] почитайте теорию как работает кеширование в антропике. В подписке оно тоже используется, как и в api. Именно на первых запросах в сессии основная часть падает в кеш на запись, а лимиты в подписке считай что деньги в апи, потому что все измеряется в нагрузке на мощности. Потому так все выходит. 

Это не волшебство, а нагрузка на кеширование
2. [raw 6769] а по человечески? ))
3. [raw 6776] ну как по человечески. Кешироввние, в api на запись есть бабки. Дорого. Зато следующие токены вытаскиваются из кеша. Поэтому дешево. Так в апи.

В подписке то же самое, только вы не видите этого, оно под капотом
4. [raw 6778] потому выходит некоторые запросы жрут лимиты сразу, а некоторые нет (берутся из кеша)
5. [raw 6799] основные постулаты при кодинге
- не ходить в туалет, не жрать и не делать паузы более 5 минут между запросами, кеш сбрасывается. Есть 1 часовой кеш у МАкс тарифов на подписке. Там жрать и ссать можно, но кеш по идее дороже уже, поэтому тратится может быстрее лимиты
6. [raw 6803] Ну так мы про это и возмущались что ХЗ как он там БЕРЕТ бывает элементарнейший хапрос сжирает тонну лимитов (лично было что 15% за 1 текстовый запрос на 1 А4 лист текста тупо! ) а бывает бл пол дня работает в коде правит 2000 строк и сжирает дай бог 10% 

он не зря удалили ЛОГИ токенов же)', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5816, 'context', 'почитайте теорию как работает кеширование в антропике. В подписке оно тоже используется, как и в api. Именно на первых запросах в сессии основная часть падает в кеш на запись, а лимиты в подписке считай что деньги в апи, потому что все измеряется в нагрузке на мощности. Потому так все выходит. 

Это не волшебство, а нагрузка на кеширование', '2026-06-26T06:49:55+00:00'),
  (1, 5815, 'context', 'а по человечески? ))', '2026-06-26T06:50:22+00:00'),
  (2, 5825, 'context', 'ну как по человечески. Кешироввние, в api на запись есть бабки. Дорого. Зато следующие токены вытаскиваются из кеша. Поэтому дешево. Так в апи.

В подписке то же самое, только вы не видите этого, оно под капотом', '2026-06-26T06:51:33+00:00'),
  (3, 5826, 'context', 'потому выходит некоторые запросы жрут лимиты сразу, а некоторые нет (берутся из кеша)', '2026-06-26T06:51:57+00:00'),
  (4, 5849, 'context', 'основные постулаты при кодинге
- не ходить в туалет, не жрать и не делать паузы более 5 минут между запросами, кеш сбрасывается. Есть 1 часовой кеш у МАкс тарифов на подписке. Там жрать и ссать можно, но кеш по идее дороже уже, поэтому тратится может быстрее лимиты', '2026-06-26T06:53:47+00:00'),
  (5, 5852, 'context', 'Ну так мы про это и возмущались что ХЗ как он там БЕРЕТ бывает элементарнейший хапрос сжирает тонну лимитов (лично было что 15% за 1 текстовый запрос на 1 А4 лист текста тупо! ) а бывает бл пол дня работает в коде правит 2000 строк и сжирает дай бог 10% 

он не зря удалили ЛОГИ токенов же)', '2026-06-26T06:54:07+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1002922797592, 106, 106, '2026-06-26T06:50:22+00:00', '2026-06-26T06:54:19+00:00', 6, 0.98, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6769] а по человечески? ))
2. [raw 6776] ну как по человечески. Кешироввние, в api на запись есть бабки. Дорого. Зато следующие токены вытаскиваются из кеша. Поэтому дешево. Так в апи.

В подписке то же самое, только вы не видите этого, оно под капотом
3. [raw 6778] потому выходит некоторые запросы жрут лимиты сразу, а некоторые нет (берутся из кеша)
4. [raw 6799] основные постулаты при кодинге
- не ходить в туалет, не жрать и не делать паузы более 5 минут между запросами, кеш сбрасывается. Есть 1 часовой кеш у МАкс тарифов на подписке. Там жрать и ссать можно, но кеш по идее дороже уже, поэтому тратится может быстрее лимиты
5. [raw 6803] Ну так мы про это и возмущались что ХЗ как он там БЕРЕТ бывает элементарнейший хапрос сжирает тонну лимитов (лично было что 15% за 1 текстовый запрос на 1 А4 лист текста тупо! ) а бывает бл пол дня работает в коде правит 2000 строк и сжирает дай бог 10% 

он не зря удалили ЛОГИ токенов же)
6. [raw 6805] - не делать постоянно /new и /clear как советуют дебилы в ютубе', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5815, 'context', 'а по человечески? ))', '2026-06-26T06:50:22+00:00'),
  (1, 5825, 'context', 'ну как по человечески. Кешироввние, в api на запись есть бабки. Дорого. Зато следующие токены вытаскиваются из кеша. Поэтому дешево. Так в апи.

В подписке то же самое, только вы не видите этого, оно под капотом', '2026-06-26T06:51:33+00:00'),
  (2, 5826, 'context', 'потому выходит некоторые запросы жрут лимиты сразу, а некоторые нет (берутся из кеша)', '2026-06-26T06:51:57+00:00'),
  (3, 5849, 'context', 'основные постулаты при кодинге
- не ходить в туалет, не жрать и не делать паузы более 5 минут между запросами, кеш сбрасывается. Есть 1 часовой кеш у МАкс тарифов на подписке. Там жрать и ссать можно, но кеш по идее дороже уже, поэтому тратится может быстрее лимиты', '2026-06-26T06:53:47+00:00'),
  (4, 5852, 'context', 'Ну так мы про это и возмущались что ХЗ как он там БЕРЕТ бывает элементарнейший хапрос сжирает тонну лимитов (лично было что 15% за 1 текстовый запрос на 1 А4 лист текста тупо! ) а бывает бл пол дня работает в коде правит 2000 строк и сжирает дай бог 10% 

он не зря удалили ЛОГИ токенов же)', '2026-06-26T06:54:07+00:00'),
  (5, 5853, 'context', '- не делать постоянно /new и /clear как советуют дебилы в ютубе', '2026-06-26T06:54:19+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1002922797592, 106, 106, '2026-06-26T06:51:33+00:00', '2026-06-26T06:54:23+00:00', 6, 0.98, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6776] ну как по человечески. Кешироввние, в api на запись есть бабки. Дорого. Зато следующие токены вытаскиваются из кеша. Поэтому дешево. Так в апи.

В подписке то же самое, только вы не видите этого, оно под капотом
2. [raw 6778] потому выходит некоторые запросы жрут лимиты сразу, а некоторые нет (берутся из кеша)
3. [raw 6799] основные постулаты при кодинге
- не ходить в туалет, не жрать и не делать паузы более 5 минут между запросами, кеш сбрасывается. Есть 1 часовой кеш у МАкс тарифов на подписке. Там жрать и ссать можно, но кеш по идее дороже уже, поэтому тратится может быстрее лимиты
4. [raw 6803] Ну так мы про это и возмущались что ХЗ как он там БЕРЕТ бывает элементарнейший хапрос сжирает тонну лимитов (лично было что 15% за 1 текстовый запрос на 1 А4 лист текста тупо! ) а бывает бл пол дня работает в коде правит 2000 строк и сжирает дай бог 10% 

он не зря удалили ЛОГИ токенов же)
5. [raw 6805] - не делать постоянно /new и /clear как советуют дебилы в ютубе
6. [raw 6806] жесть', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5825, 'context', 'ну как по человечески. Кешироввние, в api на запись есть бабки. Дорого. Зато следующие токены вытаскиваются из кеша. Поэтому дешево. Так в апи.

В подписке то же самое, только вы не видите этого, оно под капотом', '2026-06-26T06:51:33+00:00'),
  (1, 5826, 'context', 'потому выходит некоторые запросы жрут лимиты сразу, а некоторые нет (берутся из кеша)', '2026-06-26T06:51:57+00:00'),
  (2, 5849, 'context', 'основные постулаты при кодинге
- не ходить в туалет, не жрать и не делать паузы более 5 минут между запросами, кеш сбрасывается. Есть 1 часовой кеш у МАкс тарифов на подписке. Там жрать и ссать можно, но кеш по идее дороже уже, поэтому тратится может быстрее лимиты', '2026-06-26T06:53:47+00:00'),
  (3, 5852, 'context', 'Ну так мы про это и возмущались что ХЗ как он там БЕРЕТ бывает элементарнейший хапрос сжирает тонну лимитов (лично было что 15% за 1 текстовый запрос на 1 А4 лист текста тупо! ) а бывает бл пол дня работает в коде правит 2000 строк и сжирает дай бог 10% 

он не зря удалили ЛОГИ токенов же)', '2026-06-26T06:54:07+00:00'),
  (4, 5853, 'context', '- не делать постоянно /new и /clear как советуют дебилы в ютубе', '2026-06-26T06:54:19+00:00'),
  (5, 5854, 'context', 'жесть', '2026-06-26T06:54:23+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1002922797592, 106, 106, '2026-06-26T06:51:57+00:00', '2026-06-26T06:54:59+00:00', 6, 0.98, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6778] потому выходит некоторые запросы жрут лимиты сразу, а некоторые нет (берутся из кеша)
2. [raw 6799] основные постулаты при кодинге
- не ходить в туалет, не жрать и не делать паузы более 5 минут между запросами, кеш сбрасывается. Есть 1 часовой кеш у МАкс тарифов на подписке. Там жрать и ссать можно, но кеш по идее дороже уже, поэтому тратится может быстрее лимиты
3. [raw 6803] Ну так мы про это и возмущались что ХЗ как он там БЕРЕТ бывает элементарнейший хапрос сжирает тонну лимитов (лично было что 15% за 1 текстовый запрос на 1 А4 лист текста тупо! ) а бывает бл пол дня работает в коде правит 2000 строк и сжирает дай бог 10% 

он не зря удалили ЛОГИ токенов же)
4. [raw 6805] - не делать постоянно /new и /clear как советуют дебилы в ютубе
5. [raw 6806] жесть
6. [raw 6817] потому что вы тем самым убиваете кеш в контекстном окне и он создается заново, что жрет лимиты (в апи бабки)', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5826, 'context', 'потому выходит некоторые запросы жрут лимиты сразу, а некоторые нет (берутся из кеша)', '2026-06-26T06:51:57+00:00'),
  (1, 5849, 'context', 'основные постулаты при кодинге
- не ходить в туалет, не жрать и не делать паузы более 5 минут между запросами, кеш сбрасывается. Есть 1 часовой кеш у МАкс тарифов на подписке. Там жрать и ссать можно, но кеш по идее дороже уже, поэтому тратится может быстрее лимиты', '2026-06-26T06:53:47+00:00'),
  (2, 5852, 'context', 'Ну так мы про это и возмущались что ХЗ как он там БЕРЕТ бывает элементарнейший хапрос сжирает тонну лимитов (лично было что 15% за 1 текстовый запрос на 1 А4 лист текста тупо! ) а бывает бл пол дня работает в коде правит 2000 строк и сжирает дай бог 10% 

он не зря удалили ЛОГИ токенов же)', '2026-06-26T06:54:07+00:00'),
  (3, 5853, 'context', '- не делать постоянно /new и /clear как советуют дебилы в ютубе', '2026-06-26T06:54:19+00:00'),
  (4, 5854, 'context', 'жесть', '2026-06-26T06:54:23+00:00'),
  (5, 5855, 'context', 'потому что вы тем самым убиваете кеш в контекстном окне и он создается заново, что жрет лимиты (в апи бабки)', '2026-06-26T06:54:59+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1002922797592, 106, 106, '2026-06-26T06:53:47+00:00', '2026-06-26T06:55:02+00:00', 6, 0.98, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6799] основные постулаты при кодинге
- не ходить в туалет, не жрать и не делать паузы более 5 минут между запросами, кеш сбрасывается. Есть 1 часовой кеш у МАкс тарифов на подписке. Там жрать и ссать можно, но кеш по идее дороже уже, поэтому тратится может быстрее лимиты
2. [raw 6803] Ну так мы про это и возмущались что ХЗ как он там БЕРЕТ бывает элементарнейший хапрос сжирает тонну лимитов (лично было что 15% за 1 текстовый запрос на 1 А4 лист текста тупо! ) а бывает бл пол дня работает в коде правит 2000 строк и сжирает дай бог 10% 

он не зря удалили ЛОГИ токенов же)
3. [raw 6805] - не делать постоянно /new и /clear как советуют дебилы в ютубе
4. [raw 6806] жесть
5. [raw 6817] потому что вы тем самым убиваете кеш в контекстном окне и он создается заново, что жрет лимиты (в апи бабки)
6. [raw 6819] Ну и контекстное окно тоже не стоит забивать, так ведь?', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5849, 'context', 'основные постулаты при кодинге
- не ходить в туалет, не жрать и не делать паузы более 5 минут между запросами, кеш сбрасывается. Есть 1 часовой кеш у МАкс тарифов на подписке. Там жрать и ссать можно, но кеш по идее дороже уже, поэтому тратится может быстрее лимиты', '2026-06-26T06:53:47+00:00'),
  (1, 5852, 'context', 'Ну так мы про это и возмущались что ХЗ как он там БЕРЕТ бывает элементарнейший хапрос сжирает тонну лимитов (лично было что 15% за 1 текстовый запрос на 1 А4 лист текста тупо! ) а бывает бл пол дня работает в коде правит 2000 строк и сжирает дай бог 10% 

он не зря удалили ЛОГИ токенов же)', '2026-06-26T06:54:07+00:00'),
  (2, 5853, 'context', '- не делать постоянно /new и /clear как советуют дебилы в ютубе', '2026-06-26T06:54:19+00:00'),
  (3, 5854, 'context', 'жесть', '2026-06-26T06:54:23+00:00'),
  (4, 5855, 'context', 'потому что вы тем самым убиваете кеш в контекстном окне и он создается заново, что жрет лимиты (в апи бабки)', '2026-06-26T06:54:59+00:00'),
  (5, 5871, 'context', 'Ну и контекстное окно тоже не стоит забивать, так ведь?', '2026-06-26T06:55:02+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1002922797592, 106, 106, '2026-06-26T06:54:07+00:00', '2026-06-26T06:55:22+00:00', 6, 0.98, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6803] Ну так мы про это и возмущались что ХЗ как он там БЕРЕТ бывает элементарнейший хапрос сжирает тонну лимитов (лично было что 15% за 1 текстовый запрос на 1 А4 лист текста тупо! ) а бывает бл пол дня работает в коде правит 2000 строк и сжирает дай бог 10% 

он не зря удалили ЛОГИ токенов же)
2. [raw 6805] - не делать постоянно /new и /clear как советуют дебилы в ютубе
3. [raw 6806] жесть
4. [raw 6817] потому что вы тем самым убиваете кеш в контекстном окне и он создается заново, что жрет лимиты (в апи бабки)
5. [raw 6819] Ну и контекстное окно тоже не стоит забивать, так ведь?
6. [raw 6826] да, надо разумный компромисс, чтобы кеширование было эффектиым не в ущерб окну', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5852, 'context', 'Ну так мы про это и возмущались что ХЗ как он там БЕРЕТ бывает элементарнейший хапрос сжирает тонну лимитов (лично было что 15% за 1 текстовый запрос на 1 А4 лист текста тупо! ) а бывает бл пол дня работает в коде правит 2000 строк и сжирает дай бог 10% 

он не зря удалили ЛОГИ токенов же)', '2026-06-26T06:54:07+00:00'),
  (1, 5853, 'context', '- не делать постоянно /new и /clear как советуют дебилы в ютубе', '2026-06-26T06:54:19+00:00'),
  (2, 5854, 'context', 'жесть', '2026-06-26T06:54:23+00:00'),
  (3, 5855, 'context', 'потому что вы тем самым убиваете кеш в контекстном окне и он создается заново, что жрет лимиты (в апи бабки)', '2026-06-26T06:54:59+00:00'),
  (4, 5871, 'context', 'Ну и контекстное окно тоже не стоит забивать, так ведь?', '2026-06-26T06:55:02+00:00'),
  (5, 5872, 'context', 'да, надо разумный компромисс, чтобы кеширование было эффектиым не в ущерб окну', '2026-06-26T06:55:22+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1002922797592, 106, 106, '2026-06-26T06:54:19+00:00', '2026-06-26T06:55:51+00:00', 6, 0.98, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6805] - не делать постоянно /new и /clear как советуют дебилы в ютубе
2. [raw 6806] жесть
3. [raw 6817] потому что вы тем самым убиваете кеш в контекстном окне и он создается заново, что жрет лимиты (в апи бабки)
4. [raw 6819] Ну и контекстное окно тоже не стоит забивать, так ведь?
5. [raw 6826] да, надо разумный компромисс, чтобы кеширование было эффектиым не в ущерб окну
6. [raw 6836] если можешь тащить в окне хвост и не напрягает - тащи скоко можешь с автокомпактом', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5853, 'context', '- не делать постоянно /new и /clear как советуют дебилы в ютубе', '2026-06-26T06:54:19+00:00'),
  (1, 5854, 'context', 'жесть', '2026-06-26T06:54:23+00:00'),
  (2, 5855, 'context', 'потому что вы тем самым убиваете кеш в контекстном окне и он создается заново, что жрет лимиты (в апи бабки)', '2026-06-26T06:54:59+00:00'),
  (3, 5871, 'context', 'Ну и контекстное окно тоже не стоит забивать, так ведь?', '2026-06-26T06:55:02+00:00'),
  (4, 5872, 'context', 'да, надо разумный компромисс, чтобы кеширование было эффектиым не в ущерб окну', '2026-06-26T06:55:22+00:00'),
  (5, 5886, 'context', 'если можешь тащить в окне хвост и не напрягает - тащи скоко можешь с автокомпактом', '2026-06-26T06:55:51+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1002922797592, 106, 106, '2026-06-26T06:54:23+00:00', '2026-06-26T06:57:21+00:00', 6, 0.98, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6806] жесть
2. [raw 6817] потому что вы тем самым убиваете кеш в контекстном окне и он создается заново, что жрет лимиты (в апи бабки)
3. [raw 6819] Ну и контекстное окно тоже не стоит забивать, так ведь?
4. [raw 6826] да, надо разумный компромисс, чтобы кеширование было эффектиым не в ущерб окну
5. [raw 6836] если можешь тащить в окне хвост и не напрягает - тащи скоко можешь с автокомпактом
6. [raw 6850] чтобы было  проще и задачи не требуют прям 1м окна - отключай его. Иначе такать хвост в 1м окне даже кешированный будет затратно на апи (а на подписке быстрее лимиты), т к чтение из кеша тоже   олщности', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5854, 'context', 'жесть', '2026-06-26T06:54:23+00:00'),
  (1, 5855, 'context', 'потому что вы тем самым убиваете кеш в контекстном окне и он создается заново, что жрет лимиты (в апи бабки)', '2026-06-26T06:54:59+00:00'),
  (2, 5871, 'context', 'Ну и контекстное окно тоже не стоит забивать, так ведь?', '2026-06-26T06:55:02+00:00'),
  (3, 5872, 'context', 'да, надо разумный компромисс, чтобы кеширование было эффектиым не в ущерб окну', '2026-06-26T06:55:22+00:00'),
  (4, 5886, 'context', 'если можешь тащить в окне хвост и не напрягает - тащи скоко можешь с автокомпактом', '2026-06-26T06:55:51+00:00'),
  (5, 5899, 'context', 'чтобы было  проще и задачи не требуют прям 1м окна - отключай его. Иначе такать хвост в 1м окне даже кешированный будет затратно на апи (а на подписке быстрее лимиты), т к чтение из кеша тоже   олщности', '2026-06-26T06:57:21+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1002922797592, 106, 106, '2026-06-26T06:54:59+00:00', '2026-06-26T06:59:11+00:00', 6, 0.98, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6817] потому что вы тем самым убиваете кеш в контекстном окне и он создается заново, что жрет лимиты (в апи бабки)
2. [raw 6819] Ну и контекстное окно тоже не стоит забивать, так ведь?
3. [raw 6826] да, надо разумный компромисс, чтобы кеширование было эффектиым не в ущерб окну
4. [raw 6836] если можешь тащить в окне хвост и не напрягает - тащи скоко можешь с автокомпактом
5. [raw 6850] чтобы было  проще и задачи не требуют прям 1м окна - отключай его. Иначе такать хвост в 1м окне даже кешированный будет затратно на апи (а на подписке быстрее лимиты), т к чтение из кеша тоже   олщности
6. [raw 6876] для понимания, я видел запрос (1 запрос, это не 1 промт, это просто 1 запрос к апи с ответом вход/выход) в 1м окне сожрал 9.8$ .
Так вышло, потому что то то таскал хввост и положилось в кеш сразу 910к токенов в сессии', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5855, 'context', 'потому что вы тем самым убиваете кеш в контекстном окне и он создается заново, что жрет лимиты (в апи бабки)', '2026-06-26T06:54:59+00:00'),
  (1, 5871, 'context', 'Ну и контекстное окно тоже не стоит забивать, так ведь?', '2026-06-26T06:55:02+00:00'),
  (2, 5872, 'context', 'да, надо разумный компромисс, чтобы кеширование было эффектиым не в ущерб окну', '2026-06-26T06:55:22+00:00'),
  (3, 5886, 'context', 'если можешь тащить в окне хвост и не напрягает - тащи скоко можешь с автокомпактом', '2026-06-26T06:55:51+00:00'),
  (4, 5899, 'context', 'чтобы было  проще и задачи не требуют прям 1м окна - отключай его. Иначе такать хвост в 1м окне даже кешированный будет затратно на апи (а на подписке быстрее лимиты), т к чтение из кеша тоже   олщности', '2026-06-26T06:57:21+00:00'),
  (5, 5925, 'context', 'для понимания, я видел запрос (1 запрос, это не 1 промт, это просто 1 запрос к апи с ответом вход/выход) в 1м окне сожрал 9.8$ .
Так вышло, потому что то то таскал хввост и положилось в кеш сразу 910к токенов в сессии', '2026-06-26T06:59:11+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1002922797592, 106, 106, '2026-06-26T06:55:02+00:00', '2026-06-26T06:59:11+00:00', 5, 0.96, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6819] Ну и контекстное окно тоже не стоит забивать, так ведь?
2. [raw 6826] да, надо разумный компромисс, чтобы кеширование было эффектиым не в ущерб окну
3. [raw 6836] если можешь тащить в окне хвост и не напрягает - тащи скоко можешь с автокомпактом
4. [raw 6850] чтобы было  проще и задачи не требуют прям 1м окна - отключай его. Иначе такать хвост в 1м окне даже кешированный будет затратно на апи (а на подписке быстрее лимиты), т к чтение из кеша тоже   олщности
5. [raw 6876] для понимания, я видел запрос (1 запрос, это не 1 промт, это просто 1 запрос к апи с ответом вход/выход) в 1м окне сожрал 9.8$ .
Так вышло, потому что то то таскал хввост и положилось в кеш сразу 910к токенов в сессии', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5871, 'context', 'Ну и контекстное окно тоже не стоит забивать, так ведь?', '2026-06-26T06:55:02+00:00'),
  (1, 5872, 'context', 'да, надо разумный компромисс, чтобы кеширование было эффектиым не в ущерб окну', '2026-06-26T06:55:22+00:00'),
  (2, 5886, 'context', 'если можешь тащить в окне хвост и не напрягает - тащи скоко можешь с автокомпактом', '2026-06-26T06:55:51+00:00'),
  (3, 5899, 'context', 'чтобы было  проще и задачи не требуют прям 1м окна - отключай его. Иначе такать хвост в 1м окне даже кешированный будет затратно на апи (а на подписке быстрее лимиты), т к чтение из кеша тоже   олщности', '2026-06-26T06:57:21+00:00'),
  (4, 5925, 'context', 'для понимания, я видел запрос (1 запрос, это не 1 промт, это просто 1 запрос к апи с ответом вход/выход) в 1м окне сожрал 9.8$ .
Так вышло, потому что то то таскал хввост и положилось в кеш сразу 910к токенов в сессии', '2026-06-26T06:59:11+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1002922797592, 106, 106, '2026-06-26T06:55:22+00:00', '2026-06-26T06:59:11+00:00', 4, 0.76, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6826] да, надо разумный компромисс, чтобы кеширование было эффектиым не в ущерб окну
2. [raw 6836] если можешь тащить в окне хвост и не напрягает - тащи скоко можешь с автокомпактом
3. [raw 6850] чтобы было  проще и задачи не требуют прям 1м окна - отключай его. Иначе такать хвост в 1м окне даже кешированный будет затратно на апи (а на подписке быстрее лимиты), т к чтение из кеша тоже   олщности
4. [raw 6876] для понимания, я видел запрос (1 запрос, это не 1 промт, это просто 1 запрос к апи с ответом вход/выход) в 1м окне сожрал 9.8$ .
Так вышло, потому что то то таскал хввост и положилось в кеш сразу 910к токенов в сессии', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5872, 'context', 'да, надо разумный компромисс, чтобы кеширование было эффектиым не в ущерб окну', '2026-06-26T06:55:22+00:00'),
  (1, 5886, 'context', 'если можешь тащить в окне хвост и не напрягает - тащи скоко можешь с автокомпактом', '2026-06-26T06:55:51+00:00'),
  (2, 5899, 'context', 'чтобы было  проще и задачи не требуют прям 1м окна - отключай его. Иначе такать хвост в 1м окне даже кешированный будет затратно на апи (а на подписке быстрее лимиты), т к чтение из кеша тоже   олщности', '2026-06-26T06:57:21+00:00'),
  (3, 5925, 'context', 'для понимания, я видел запрос (1 запрос, это не 1 промт, это просто 1 запрос к апи с ответом вход/выход) в 1м окне сожрал 9.8$ .
Так вышло, потому что то то таскал хввост и положилось в кеш сразу 910к токенов в сессии', '2026-06-26T06:59:11+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1002922797592, 106, 106, '2026-06-26T06:55:51+00:00', '2026-06-26T06:59:11+00:00', 3, 0.74, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6836] если можешь тащить в окне хвост и не напрягает - тащи скоко можешь с автокомпактом
2. [raw 6850] чтобы было  проще и задачи не требуют прям 1м окна - отключай его. Иначе такать хвост в 1м окне даже кешированный будет затратно на апи (а на подписке быстрее лимиты), т к чтение из кеша тоже   олщности
3. [raw 6876] для понимания, я видел запрос (1 запрос, это не 1 промт, это просто 1 запрос к апи с ответом вход/выход) в 1м окне сожрал 9.8$ .
Так вышло, потому что то то таскал хввост и положилось в кеш сразу 910к токенов в сессии', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5886, 'context', 'если можешь тащить в окне хвост и не напрягает - тащи скоко можешь с автокомпактом', '2026-06-26T06:55:51+00:00'),
  (1, 5899, 'context', 'чтобы было  проще и задачи не требуют прям 1м окна - отключай его. Иначе такать хвост в 1м окне даже кешированный будет затратно на апи (а на подписке быстрее лимиты), т к чтение из кеша тоже   олщности', '2026-06-26T06:57:21+00:00'),
  (2, 5925, 'context', 'для понимания, я видел запрос (1 запрос, это не 1 промт, это просто 1 запрос к апи с ответом вход/выход) в 1м окне сожрал 9.8$ .
Так вышло, потому что то то таскал хввост и положилось в кеш сразу 910к токенов в сессии', '2026-06-26T06:59:11+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1002922797592, 106, 106, '2026-06-26T06:57:21+00:00', '2026-06-26T06:59:11+00:00', 2, 0.62, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","REPEATED_ENTITY"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6850] чтобы было  проще и задачи не требуют прям 1м окна - отключай его. Иначе такать хвост в 1м окне даже кешированный будет затратно на апи (а на подписке быстрее лимиты), т к чтение из кеша тоже   олщности
2. [raw 6876] для понимания, я видел запрос (1 запрос, это не 1 промт, это просто 1 запрос к апи с ответом вход/выход) в 1м окне сожрал 9.8$ .
Так вышло, потому что то то таскал хввост и положилось в кеш сразу 910к токенов в сессии', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5899, 'context', 'чтобы было  проще и задачи не требуют прям 1м окна - отключай его. Иначе такать хвост в 1м окне даже кешированный будет затратно на апи (а на подписке быстрее лимиты), т к чтение из кеша тоже   олщности', '2026-06-26T06:57:21+00:00'),
  (1, 5925, 'context', 'для понимания, я видел запрос (1 запрос, это не 1 промт, это просто 1 запрос к апи с ответом вход/выход) в 1м окне сожрал 9.8$ .
Так вышло, потому что то то таскал хввост и положилось в кеш сразу 910к токенов в сессии', '2026-06-26T06:59:11+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1001758943128, 1369636339712, 1369636339712, '2026-06-26T05:59:57+00:00', '2026-06-26T06:01:54+00:00', 5, 0.56, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6618] ХД
2. [raw 6621] Ты мышь бля
У меня пёс эйрподсы кушает, а в нынешней хуйне звук говна
3. [raw 6622] Я тя тож люблю но подъебать надо было
4. [raw 6630] Помню как чел хвастался новыми аирподсами, а потом на улице у него пердело там как у бегемота под водой
5. [raw 6632] в про 3 примерно такой звук да', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5667, 'context', 'ХД', '2026-06-26T05:59:57+00:00'),
  (1, 5668, 'context', 'Ты мышь бля
У меня пёс эйрподсы кушает, а в нынешней хуйне звук говна', '2026-06-26T06:00:03+00:00'),
  (2, 5669, 'context', 'Я тя тож люблю но подъебать надо было', '2026-06-26T06:00:21+00:00'),
  (3, 5670, 'context', 'Помню как чел хвастался новыми аирподсами, а потом на улице у него пердело там как у бегемота под водой', '2026-06-26T06:01:25+00:00'),
  (4, 5681, 'context', 'в про 3 примерно такой звук да', '2026-06-26T06:01:54+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1001758943128, 1369656262656, 1369656262656, '2026-06-26T06:03:18+00:00', '2026-06-26T06:07:16+00:00', 6, 0.6, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6641] я так скучновато, слишком рано зашел
2. [raw 6644] у меня лимитка
3. [raw 6653] у меня хуй под утро
4. [raw 6655] ну мне подкусило пару стопов перед лимиткой
5. [raw 6656] локальные кидал?
6. [raw 6657] +-', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5691, 'context', 'я так скучновато, слишком рано зашел', '2026-06-26T06:03:18+00:00'),
  (1, 5692, 'context', 'у меня лимитка', '2026-06-26T06:03:26+00:00'),
  (2, 5702, 'context', 'у меня хуй под утро', '2026-06-26T06:05:34+00:00'),
  (3, 5704, 'context', 'ну мне подкусило пару стопов перед лимиткой', '2026-06-26T06:06:32+00:00'),
  (4, 5705, 'context', 'локальные кидал?', '2026-06-26T06:07:03+00:00'),
  (5, 5706, 'context', '+-', '2026-06-26T06:07:16+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1001758943128, 1369656262656, 1369656262656, '2026-06-26T06:03:26+00:00', '2026-06-26T06:07:16+00:00', 5, 0.58, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6644] у меня лимитка
2. [raw 6653] у меня хуй под утро
3. [raw 6655] ну мне подкусило пару стопов перед лимиткой
4. [raw 6656] локальные кидал?
5. [raw 6657] +-', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5692, 'context', 'у меня лимитка', '2026-06-26T06:03:26+00:00'),
  (1, 5702, 'context', 'у меня хуй под утро', '2026-06-26T06:05:34+00:00'),
  (2, 5704, 'context', 'ну мне подкусило пару стопов перед лимиткой', '2026-06-26T06:06:32+00:00'),
  (3, 5705, 'context', 'локальные кидал?', '2026-06-26T06:07:03+00:00'),
  (4, 5706, 'context', '+-', '2026-06-26T06:07:16+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1001758943128, 1369656262656, 1369656262656, '2026-06-26T06:05:34+00:00', '2026-06-26T06:07:16+00:00', 4, 0.56, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6653] у меня хуй под утро
2. [raw 6655] ну мне подкусило пару стопов перед лимиткой
3. [raw 6656] локальные кидал?
4. [raw 6657] +-', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5702, 'context', 'у меня хуй под утро', '2026-06-26T06:05:34+00:00'),
  (1, 5704, 'context', 'ну мне подкусило пару стопов перед лимиткой', '2026-06-26T06:06:32+00:00'),
  (2, 5705, 'context', 'локальные кидал?', '2026-06-26T06:07:03+00:00'),
  (3, 5706, 'context', '+-', '2026-06-26T06:07:16+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1001676121122, 58143539200, 58143539200, '2026-06-26T06:44:53+00:00', '2026-06-26T06:58:01+00:00', 2, 0.7, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","CAREER_RESUME_SIGNAL","RISK_OR_CAUTION_SIGNAL","REPEATED_ENTITY"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6716] Всем привет!
Может кто-нибудь, пожалуйста, подсказать актуальный гайд по поднятию резюме в топ выдачи на hh.ru (с целью прохождения ai-фильтров)?
Слышал сам про такое (как пример): 
1) что если по твоей специальности закончились вакансии, то можно откликаться в другой, чтобы держать активность
2) что можно постоянно обновлять резюме путем внесения минорных изменений: добавить точку, сохранить, убрать точку, сохранить и тд
2. [raw 6861] Актуального гайда никто не даст, актуальность постоянно меняется, плюс гарантированности советов нет ни у кого кроме разрабов hh. В целом такие вопросы часто обсуждаются в чате ОМ: Резюме

Если кратко:
1) Для поднятия резюме в топе: как можно больше писать в чате (в том числе сопроводительные по каждому отклику), как можно больше откликаться (обязательно с сопроводительными), действительно если свои вакансии закончились то можно откликаться на другие как минимум чтобы больше сопроводительных было, либо сделать второе резюме и откликаться им повторно по тем же вакансиям, отвечать на каждое сообщение в чате, как можно больше вакансий просматривать, как можно больше скролить страниц hh, поднимать резюме по КД, обновлять резюме через минорные редактирования, убрать из блока "о себе" контактные данные (телефон, емейл, телеграм и т.д.) - делать это все перед часами активности HR потому что эффект поднятия действует пару часов
2) поднятие в топе работает только если ты не словил автоотказ по AI-фильтрам, как их обходить краткий гайд уже не получится, лучше кидай свое резюме на прожарку в ОМ: Резюме', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5766, 'context', 'Всем привет!
Может кто-нибудь, пожалуйста, подсказать актуальный гайд по поднятию резюме в топ выдачи на hh.ru (с целью прохождения ai-фильтров)?
Слышал сам про такое (как пример): 
1) что если по твоей специальности закончились вакансии, то можно откликаться в другой, чтобы держать активность
2) что можно постоянно обновлять резюме путем внесения минорных изменений: добавить точку, сохранить, убрать точку, сохранить и тд', '2026-06-26T06:44:53+00:00'),
  (1, 5911, 'context', 'Актуального гайда никто не даст, актуальность постоянно меняется, плюс гарантированности советов нет ни у кого кроме разрабов hh. В целом такие вопросы часто обсуждаются в чате ОМ: Резюме

Если кратко:
1) Для поднятия резюме в топе: как можно больше писать в чате (в том числе сопроводительные по каждому отклику), как можно больше откликаться (обязательно с сопроводительными), действительно если свои вакансии закончились то можно откликаться на другие как минимум чтобы больше сопроводительных был', '2026-06-26T06:58:01+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1003919536687, 1302, 1302, '2026-06-25T22:11:26+00:00', '2026-06-25T22:17:29+00:00', 9, 0.98, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6034] а, переключись на рф, не рф пока не стабилен изза усиленного CF
2. [raw 6042] Не знаю, проблема у меня или нет, но на r-api порой вообще отваливается. Hermes тормозит, Droid на домашнем сервере тоже (хотя там скорее проблема в настройках маршрутизации и Fake IP).
3. [raw 6052] Очень хотелось бы страницу мониторинга, чтобы знать наверняка, какой API и какая модель работает, в идеале бы ещё по запросу списка моделей получать информацию о рабочих или не рабочих моделях
4. [raw 6062] Чтобы в Hermes и подобных агентах fallback на другие модели делать нормально
5. [raw 6069] причем тут лимиты то? эти 2 дня у меня кодекс с обычно подпиской работает как всегда обычно. а апи провайдер думает ооочень долго. Я не наговариваю, я факт говорю
6. [raw 6070] но чаще всего зависит не от нас, а чаще всего все сбои на стороне кого то, но мы обязательно подумаем над этим! Спасибо!
7. [raw 6078] я уже добавил статус доступности моделей, пока в работе
8. [raw 6079] Да, понимаю, потому и предлагаю. Ибо точек отказа достаточно много, потому любой реконнект уже вызывает панические атаки xD
9. [raw 6105] Статус openai, тг чат, проверять свой vpn, проверять настройки самого инструмента, проверять автосжатие, в лк смотреть и ловить запросы, переключаться на другие модели, создавать новые чаты', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5085, 'context', 'а, переключись на рф, не рф пока не стабилен изза усиленного CF', '2026-06-25T22:11:26+00:00'),
  (1, 5091, 'context', 'Не знаю, проблема у меня или нет, но на r-api порой вообще отваливается. Hermes тормозит, Droid на домашнем сервере тоже (хотя там скорее проблема в настройках маршрутизации и Fake IP).', '2026-06-25T22:12:32+00:00'),
  (2, 5102, 'context', 'Очень хотелось бы страницу мониторинга, чтобы знать наверняка, какой API и какая модель работает, в идеале бы ещё по запросу списка моделей получать информацию о рабочих или не рабочих моделях', '2026-06-25T22:13:38+00:00'),
  (3, 5103, 'context', 'Чтобы в Hermes и подобных агентах fallback на другие модели делать нормально', '2026-06-25T22:14:10+00:00'),
  (4, 5114, 'context', 'причем тут лимиты то? эти 2 дня у меня кодекс с обычно подпиской работает как всегда обычно. а апи провайдер думает ооочень долго. Я не наговариваю, я факт говорю', '2026-06-25T22:14:43+00:00'),
  (5, 5115, 'context', 'но чаще всего зависит не от нас, а чаще всего все сбои на стороне кого то, но мы обязательно подумаем над этим! Спасибо!', '2026-06-25T22:14:46+00:00'),
  (6, 5129, 'context', 'я уже добавил статус доступности моделей, пока в работе', '2026-06-25T22:15:32+00:00'),
  (7, 5130, 'context', 'Да, понимаю, потому и предлагаю. Ибо точек отказа достаточно много, потому любой реконнект уже вызывает панические атаки xD', '2026-06-25T22:15:43+00:00'),
  (8, 5154, 'context', 'Статус openai, тг чат, проверять свой vpn, проверять настройки самого инструмента, проверять автосжатие, в лк смотреть и ловить запросы, переключаться на другие модели, создавать новые чаты', '2026-06-25T22:17:29+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1002922797592, 106, 106, '2026-06-26T06:49:55+00:00', '2026-06-26T06:59:11+00:00', 11, 0.98, 'GUIDE', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","PROBLEM_SOLUTION","ERROR_OR_STATUS_DIAGNOSIS","TECHNICAL_API_CACHE_COST","REPEATED_ENTITY","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6765] почитайте теорию как работает кеширование в антропике. В подписке оно тоже используется, как и в api. Именно на первых запросах в сессии основная часть падает в кеш на запись, а лимиты в подписке считай что деньги в апи, потому что все измеряется в нагрузке на мощности. Потому так все выходит. 

Это не волшебство, а нагрузка на кеширование
2. [raw 6776] ну как по человечески. Кешироввние, в api на запись есть бабки. Дорого. Зато следующие токены вытаскиваются из кеша. Поэтому дешево. Так в апи.

В подписке то же самое, только вы не видите этого, оно под капотом
3. [raw 6778] потому выходит некоторые запросы жрут лимиты сразу, а некоторые нет (берутся из кеша)
4. [raw 6799] основные постулаты при кодинге
- не ходить в туалет, не жрать и не делать паузы более 5 минут между запросами, кеш сбрасывается. Есть 1 часовой кеш у МАкс тарифов на подписке. Там жрать и ссать можно, но кеш по идее дороже уже, поэтому тратится может быстрее лимиты
5. [raw 6803] Ну так мы про это и возмущались что ХЗ как он там БЕРЕТ бывает элементарнейший хапрос сжирает тонну лимитов (лично было что 15% за 1 текстовый запрос на 1 А4 лист текста тупо! ) а бывает бл пол дня работает в коде правит 2000 строк и сжирает дай бог 10% 

он не зря удалили ЛОГИ токенов же)
6. [raw 6805] - не делать постоянно /new и /clear как советуют дебилы в ютубе
7. [raw 6817] потому что вы тем самым убиваете кеш в контекстном окне и он создается заново, что жрет лимиты (в апи бабки)
8. [raw 6826] да, надо разумный компромисс, чтобы кеширование было эффектиым не в ущерб окну
9. [raw 6836] если можешь тащить в окне хвост и не напрягает - тащи скоко можешь с автокомпактом
10. [raw 6850] чтобы было  проще и задачи не требуют прям 1м окна - отключай его. Иначе такать хвост в 1м окне даже кешированный будет затратно на апи (а на подписке быстрее лимиты), т к чтение из кеша тоже   олщности
11. [raw 6876] для понимания, я видел запрос (1 запрос, это не 1 промт, это просто 1 запрос к апи с ответом вход/выход) в 1м окне сожрал 9.8$ .
Так вышло, потому что то то таскал хввост и положилось в кеш сразу 910к токенов в сессии', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5816, 'context', 'почитайте теорию как работает кеширование в антропике. В подписке оно тоже используется, как и в api. Именно на первых запросах в сессии основная часть падает в кеш на запись, а лимиты в подписке считай что деньги в апи, потому что все измеряется в нагрузке на мощности. Потому так все выходит. 

Это не волшебство, а нагрузка на кеширование', '2026-06-26T06:49:55+00:00'),
  (1, 5825, 'context', 'ну как по человечески. Кешироввние, в api на запись есть бабки. Дорого. Зато следующие токены вытаскиваются из кеша. Поэтому дешево. Так в апи.

В подписке то же самое, только вы не видите этого, оно под капотом', '2026-06-26T06:51:33+00:00'),
  (2, 5826, 'context', 'потому выходит некоторые запросы жрут лимиты сразу, а некоторые нет (берутся из кеша)', '2026-06-26T06:51:57+00:00'),
  (3, 5849, 'context', 'основные постулаты при кодинге
- не ходить в туалет, не жрать и не делать паузы более 5 минут между запросами, кеш сбрасывается. Есть 1 часовой кеш у МАкс тарифов на подписке. Там жрать и ссать можно, но кеш по идее дороже уже, поэтому тратится может быстрее лимиты', '2026-06-26T06:53:47+00:00'),
  (4, 5852, 'context', 'Ну так мы про это и возмущались что ХЗ как он там БЕРЕТ бывает элементарнейший хапрос сжирает тонну лимитов (лично было что 15% за 1 текстовый запрос на 1 А4 лист текста тупо! ) а бывает бл пол дня работает в коде правит 2000 строк и сжирает дай бог 10% 

он не зря удалили ЛОГИ токенов же)', '2026-06-26T06:54:07+00:00'),
  (5, 5853, 'context', '- не делать постоянно /new и /clear как советуют дебилы в ютубе', '2026-06-26T06:54:19+00:00'),
  (6, 5855, 'context', 'потому что вы тем самым убиваете кеш в контекстном окне и он создается заново, что жрет лимиты (в апи бабки)', '2026-06-26T06:54:59+00:00'),
  (7, 5872, 'context', 'да, надо разумный компромисс, чтобы кеширование было эффектиым не в ущерб окну', '2026-06-26T06:55:22+00:00'),
  (8, 5886, 'context', 'если можешь тащить в окне хвост и не напрягает - тащи скоко можешь с автокомпактом', '2026-06-26T06:55:51+00:00'),
  (9, 5899, 'context', 'чтобы было  проще и задачи не требуют прям 1м окна - отключай его. Иначе такать хвост в 1м окне даже кешированный будет затратно на апи (а на подписке быстрее лимиты), т к чтение из кеша тоже   олщности', '2026-06-26T06:57:21+00:00'),
  (10, 5925, 'context', 'для понимания, я видел запрос (1 запрос, это не 1 промт, это просто 1 запрос к апи с ответом вход/выход) в 1м окне сожрал 9.8$ .
Так вышло, потому что то то таскал хввост и положилось в кеш сразу 910к токенов в сессии', '2026-06-26T06:59:11+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1001843792932, 6411, 6411, '2026-06-26T02:35:20+00:00', '2026-06-26T06:55:07+00:00', 6, 0.78, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","CAREER_RESUME_SIGNAL","RISK_OR_CAUTION_SIGNAL","MULTI_MESSAGE_CONTEXT"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6341] Здарова 
Ездил кто на психоделические ретриты? Хочу задать несколько вопросов
2. [raw 6540] Как разные люди, имеющие разные опыты таких ретритов, видят весь процесс и какое мнение они теперь имеют на счет такой практики + какие бенефиты они приобрели. Вот)
3. [raw 6543] У меня есть знакомые (4 человек), которые ретритили.

После ретритов их проблемы не решались, они чаще уходили в "нужно жить в гармонии и по кайфу ваще". 

У всех после этого появились проблемы с целеполаганием типа "у самурая есть только путь". Моё  мнение - это плохо.

Плюсом они начинают погружаться в шизотерику и становятся ёбнутыми. Можешь послушать Мацкевича и Маркес, Саня Ильин и Декабрист делали на них реакт =)

Если хочешь какую-то болячку полечить / смысл жизни найти - ретрит это очень кривой способ достижения этих целей
4. [raw 6592] Работал) 
Такие начинают бегать по потолку, когда оказывается, что можно формулировать и путь на 10/10, и цель на 10/10. Причём это делается за 40-60 минутов

Ещё интересное наблюдение - тревожность у некоторых людей отцепляется просто постановкой очень хорошей цели. Цели, в которую их тревожная голова с удовольствием погрузится. Появляется цель - и они начинают делать, а не гонять мысли по лобной доле 😁
5. [raw 6688] Конечно
Там у людей прилипает всякое дезадаптивное говно вперемешку с религией и шизотерикой

Глубоко там копать на самом деле часто не нужно: за парой-тройкой вопросов вглубь как в известном меме с шахтёром находится бриллиантовая залежь выгребная яма.

Когда ретритнутый это осознает, работа сильно ускоряется =)
6. [raw 6820] А приходят изначально с выгоранием вообще :)', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5390, 'context', 'Здарова 
Ездил кто на психоделические ретриты? Хочу задать несколько вопросов', '2026-06-26T02:35:20+00:00'),
  (1, 5589, 'context', 'Как разные люди, имеющие разные опыты таких ретритов, видят весь процесс и какое мнение они теперь имеют на счет такой практики + какие бенефиты они приобрели. Вот)', '2026-06-26T04:50:24+00:00'),
  (2, 5592, 'context', 'У меня есть знакомые (4 человек), которые ретритили.

После ретритов их проблемы не решались, они чаще уходили в "нужно жить в гармонии и по кайфу ваще". 

У всех после этого появились проблемы с целеполаганием типа "у самурая есть только путь". Моё  мнение - это плохо.

Плюсом они начинают погружаться в шизотерику и становятся ёбнутыми. Можешь послушать Мацкевича и Маркес, Саня Ильин и Декабрист делали на них реакт =)

Если хочешь какую-то болячку полечить / смысл жизни найти - ретрит это очень', '2026-06-26T05:03:23+00:00'),
  (3, 5641, 'context', 'Работал) 
Такие начинают бегать по потолку, когда оказывается, что можно формулировать и путь на 10/10, и цель на 10/10. Причём это делается за 40-60 минутов

Ещё интересное наблюдение - тревожность у некоторых людей отцепляется просто постановкой очень хорошей цели. Цели, в которую их тревожная голова с удовольствием погрузится. Появляется цель - и они начинают делать, а не гонять мысли по лобной доле 😁', '2026-06-26T05:51:39+00:00'),
  (4, 5737, 'context', 'Конечно
Там у людей прилипает всякое дезадаптивное говно вперемешку с религией и шизотерикой

Глубоко там копать на самом деле часто не нужно: за парой-тройкой вопросов вглубь как в известном меме с шахтёром находится бриллиантовая залежь выгребная яма.

Когда ретритнутый это осознает, работа сильно ускоряется =)', '2026-06-26T06:36:19+00:00'),
  (5, 5873, 'context', 'А приходят изначально с выгоранием вообще :)', '2026-06-26T06:55:07+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
WITH inserted AS (
  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
  VALUES (1, -1001676121122, 58133053440, 58133053440, '2026-06-25T22:40:40+00:00', '2026-06-26T03:03:00+00:00', 2, 0.58, 'ANSWER', 'DISCUSSION_SEGMENT_CANDIDATE', 'DRY_RUN_GENERATION_DISABLED', '["Q_AND_A_PAIR","RISK_OR_CAUTION_SIGNAL"]'::jsonb, '[]'::jsonb, '[dry-run 20260626] 1. [raw 6144] Отпрашиваешься или берешь отпуск и едешь. 
Иначе действуешь, как советовал Черчилль.
2. [raw 6367] Сииильно индивидуально. 

Где-то тебя сразу хлопнет СБ, где-то не заметят

Если у тебя есть способ сокрыть локацию, скрой. Меньше рисков =)

В идеале спросить у манагера, либо у коллегов которые именно в этой компании так уже делали', NULL)
  RETURNING id
)
INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
SELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz
FROM inserted
JOIN (VALUES
  (0, 5193, 'context', 'Отпрашиваешься или берешь отпуск и едешь. 
Иначе действуешь, как советовал Черчилль.', '2026-06-25T22:40:40+00:00'),
  (1, 5416, 'context', 'Сииильно индивидуально. 

Где-то тебя сразу хлопнет СБ, где-то не заметят

Если у тебя есть способ сокрыть локацию, скрой. Меньше рисков =)

В идеале спросить у манагера, либо у коллегов которые именно в этой компании так уже делали', '2026-06-26T03:03:00+00:00')
) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true
JOIN dataset_messages dm ON dm.id = v.dataset_message_id
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id
;
COMMIT;
