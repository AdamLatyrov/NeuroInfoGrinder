package com.larbcorp.neuroinfogrinder.domain.telegramaccounts.dto;

import java.time.Instant;

public record AccountResponse(
        Long id,
        Long telegramUserId,
        String username,
        String phone,
        String firstName,
        String lastName,
        String status,
        ProxyInfo proxy,
        int groupsCount,
        long tokensUsed,
        Instant lastActivityAt
) {
    public record ProxyInfo(
            String type,
            String host,
            Integer port,
            String username,
            boolean hasPassword
    ) {
    }
}
