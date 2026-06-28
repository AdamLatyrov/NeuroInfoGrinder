package com.larbcorp.neuroinfogrinder.domain.messages;

public enum TelegramIngestionResult {
    SAVED,
    DUPLICATE,
    IGNORED_DISABLED,
    IGNORED_UNMONITORED_CHAT,
    IGNORED_EMPTY,
    FAILED
}
