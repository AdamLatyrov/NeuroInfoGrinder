package com.larbcorp.neuroinfogrinder2.ingest;

public record IngestResult(
        long messageId,
        long groupId,
        long accountId,
        long telegramChatId,
        long telegramMessageId,
        int linksExtracted,
        boolean duplicateUpdate
) {
}
