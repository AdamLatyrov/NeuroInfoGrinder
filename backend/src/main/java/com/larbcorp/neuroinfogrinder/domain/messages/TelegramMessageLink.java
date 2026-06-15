package com.larbcorp.neuroinfogrinder.domain.messages;

public record TelegramMessageLink(
    String url,
    boolean available,
    String reason
) {
}
