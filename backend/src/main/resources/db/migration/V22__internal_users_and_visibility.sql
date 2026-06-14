ALTER TABLE users ALTER COLUMN external_telegram_user_id DROP NOT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS hidden BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users
SET username = 'user_' || id
WHERE username IS NULL OR BTRIM(username) = '';

WITH duplicate_usernames AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY LOWER(username) ORDER BY id) AS row_num
    FROM users
)
UPDATE users u
SET username = u.username || '_' || u.id
FROM duplicate_usernames d
WHERE u.id = d.id
  AND d.row_num > 1;

ALTER TABLE users ALTER COLUMN username SET NOT NULL;
ALTER TABLE users ALTER COLUMN role SET DEFAULT 'ADMIN';

UPDATE users
SET role = 'ADMIN'
WHERE role IS NULL OR role <> 'ADMIN';

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_username_lower ON users ((LOWER(username)));
