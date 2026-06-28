package com.larbcorp.neuroinfogrinder.domain.messages;

public record MessageSyncRequestResponse(
        boolean scheduled,
        String reason,
        Long groupId
) {
    public static MessageSyncRequestResponse scheduled(Long groupId) {
        return new MessageSyncRequestResponse(true, "scheduled", groupId);
    }

    public static MessageSyncRequestResponse rejected(Long groupId, String reason) {
        return new MessageSyncRequestResponse(false, reason, groupId);
    }
}
