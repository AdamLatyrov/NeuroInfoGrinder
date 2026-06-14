package com.larbcorp.neuroinfogrinder.telegram.model;

import java.util.List;

public record TelegramChatMessagesDto(
        long chatId,
        String chatTitle,
        List<TelegramMessageDto> messages
) {
}
