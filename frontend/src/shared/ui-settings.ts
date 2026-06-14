/**
 * Client-side UI settings stored in localStorage.
 * These are separate from server-side AppSettings.
 */

const KEYS = {
  messageRefreshSeconds: "nig_messageRefreshSeconds",
  telegramSyncSeconds: "nig_telegramSyncSeconds",
} as const;

const DEFAULTS = {
  messageRefreshSeconds: 2,
  telegramSyncSeconds: 8,
} as const;

function readNumber(key: string, fallback: number): number {
  try {
    const raw = localStorage.getItem(key);
    if (raw == null) return fallback;
    const n = Number(raw);
    if (!Number.isFinite(n) || n <= 0) {
      return fallback;
    }

    if (key === KEYS.messageRefreshSeconds) {
      return Math.min(Math.max(n, 0.5), 10);
    }

    if (key === KEYS.telegramSyncSeconds) {
      return Math.min(Math.max(n, 3), 60);
    }

    return n;
  } catch {
    return fallback;
  }
}

function writeNumber(key: string, value: number) {
  try {
    localStorage.setItem(key, String(value));
  } catch {
    // ignore storage errors
  }
}

/** How often (seconds) the messages list auto-refreshes from DB. Min 0.1s */
export function getMessageRefreshSeconds(): number {
  return readNumber(KEYS.messageRefreshSeconds, DEFAULTS.messageRefreshSeconds);
}

export function setMessageRefreshSeconds(seconds: number) {
  writeNumber(KEYS.messageRefreshSeconds, Math.min(Math.max(0.5, seconds), 10));
}

/** How often (seconds) the Telegram sync auto-runs. Min 1s */
export function getTelegramSyncSeconds(): number {
  return readNumber(KEYS.telegramSyncSeconds, DEFAULTS.telegramSyncSeconds);
}

export function setTelegramSyncSeconds(seconds: number) {
  writeNumber(KEYS.telegramSyncSeconds, Math.min(Math.max(3, seconds), 60));
}

/** Subscribe to changes via storage event (cross-tab) or polling */
export function onUiSettingsChange(callback: () => void): () => void {
  const handler = (e: StorageEvent) => {
    if (e.key?.startsWith("nig_")) callback();
  };
  window.addEventListener("storage", handler);
  return () => window.removeEventListener("storage", handler);
}
