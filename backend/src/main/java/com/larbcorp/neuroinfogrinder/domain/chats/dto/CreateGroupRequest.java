package com.larbcorp.neuroinfogrinder.domain.chats.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateGroupRequest(
        @NotNull Long telegramChatId,
        @NotBlank String title,
        String username,
        String category,
        Long accountId
) {
}
