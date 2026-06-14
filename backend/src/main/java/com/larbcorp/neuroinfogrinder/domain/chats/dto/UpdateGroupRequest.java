package com.larbcorp.neuroinfogrinder.domain.chats.dto;

public record UpdateGroupRequest(
        Boolean enabled,
        String category,
        Long accountId,
        Boolean forum
) {
}
