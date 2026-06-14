package com.larbcorp.neuroinfogrinder.domain.findings;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;

import java.util.List;

public record MessageContextBundle(
    MessageEntity anchorMessage,
    List<MessageEntity> messages,
    String contextHash,
    int anchorScore,
    List<String> anchorSignals,
    int totalChars
) {}
