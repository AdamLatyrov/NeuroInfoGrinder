package com.larbcorp.neuroinfogrinder.domain.telegramaccounts.dto;

public record UpdateProxyRequest(
        String proxyType,
        String proxyHost,
        Integer proxyPort,
        String proxyUsername,
        String proxyPassword
) {
}
