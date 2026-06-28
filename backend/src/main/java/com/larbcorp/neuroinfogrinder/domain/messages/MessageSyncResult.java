package com.larbcorp.neuroinfogrinder.domain.messages;

import java.time.Instant;

public record MessageSyncResult(
        Long groupId,
        long telegramChatId,
        String title,
        int tdlibReturned,
        int changed,
        int created,
        int repaired,
        int skipped,
        int tooOld,
        boolean historyTimeout,
        boolean fallbackUsed,
        String reason,
        Long latestDbMessageIdBefore,
        Instant latestDbMessageDateBefore,
        Long latestDbTopicIdBefore,
        Long newestTdlibMessageId,
        Instant newestTdlibMessageDate,
        Long newestTdlibTopicId,
        Long latestDbMessageIdAfter,
        Instant latestDbMessageDateAfter,
        Long latestDbTopicIdAfter
) {
    public boolean fullSuccess() {
        return !historyTimeout && "completed".equals(reason);
    }
}
