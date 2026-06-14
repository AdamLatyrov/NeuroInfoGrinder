package com.larbcorp.neuroinfogrinder.domain.messages.dto;

import java.util.List;

public record MessageChainResponse(
        Long chainId,
        Long groupId,
        Long rootMessageId,
        List<MessageResponse> messages,
        String chainType
) {
}
