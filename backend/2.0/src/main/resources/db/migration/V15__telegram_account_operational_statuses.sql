ALTER TABLE telegram_accounts DROP CONSTRAINT IF EXISTS chk_telegram_accounts_status;

ALTER TABLE telegram_accounts ADD CONSTRAINT chk_telegram_accounts_status CHECK (status IN (
    'AUTH_REQUIRED',
    'WAIT_PHONE',
    'WAIT_CODE',
    'WAIT_PASSWORD',
    'CONNECTING',
    'CONNECTED',
    'DEGRADED',
    'DISCONNECTED',
    'FLOOD_WAIT',
    'PAUSED',
    'DISABLED',
    'ERROR'
));
