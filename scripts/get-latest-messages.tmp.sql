select
  m.id,
  m.group_id,
  g.title as group_title,
  m.telegram_message_id,
  m.message_date,
  m.created_at,
  m.sender_name,
  m.sender_telegram_user_id,
  m.is_bot,
  m.topic_id,
  m.topic_name,
  m.processing_status,
  left(coalesce(m.text, ''), 300) as text_preview
from messages m
left join groups g on g.id = m.group_id

order by m.message_date desc, m.id desc
limit 150;