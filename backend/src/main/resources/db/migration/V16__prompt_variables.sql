UPDATE prompts
SET
    variables_json = 'text,senderName,senderUsername,groupName,topicName,date,hasMedia,replyThread,previousMessages',
    updated_at = CURRENT_TIMESTAMP
WHERE type IN ('CLASSIFICATION', 'CLASSIFIER');

UPDATE prompts
SET
    variables_json = 'text,groupName,topicName,date,replyThread,previousMessages,classifierScore,classifierReason',
    updated_at = CURRENT_TIMESTAMP
WHERE type IN ('GENERATION', 'GUIDE_GENERATOR');
