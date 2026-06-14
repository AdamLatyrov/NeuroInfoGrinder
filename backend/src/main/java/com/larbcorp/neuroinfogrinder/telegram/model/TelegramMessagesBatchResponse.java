package com.larbcorp.neuroinfogrinder.telegram.model;

import java.util.List;

public record TelegramMessagesBatchResponse(
        List<TelegramChatMessagesDto> items
) {
}
