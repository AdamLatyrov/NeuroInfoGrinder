SELECT dm.id, dm.account_id, dm.telegram_chat_id, dm.telegram_message_id, dm.forum_topic_id, dm.message_thread_id, rm.id AS raw_id, coalesce(rm.text, dm.text) AS text
FROM dataset_messages dm
LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
WHERE dm.id IN (5103,5113,5114,5115,5120,5129,5825,5826,5849,5852,5853,5854,5269,5270,5271,5272,5273,5274)
ORDER BY dm.id;

SELECT id, account_id, telegram_chat_id, telegram_message_id, telegram_topic_id, message_thread_id, text
FROM raw_messages
WHERE id IN (6062,6064,6069,6070,6071,6078,6776,6778,6799,6803,6805,6806,6220,6221,6222,6223,6224,6225)
ORDER BY id;
