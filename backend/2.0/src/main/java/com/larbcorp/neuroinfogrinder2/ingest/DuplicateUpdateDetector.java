package com.larbcorp.neuroinfogrinder2.ingest;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DuplicateUpdateDetector {
    private final Set<String> seen = ConcurrentHashMap.newKeySet();

    public boolean markIfNew(long accountId, String updateType, long telegramChatId, long telegramMessageId) {
        String key = accountId + "|" + updateType + "|" + telegramChatId + "|" + telegramMessageId;
        return seen.add(key);
    }
}
