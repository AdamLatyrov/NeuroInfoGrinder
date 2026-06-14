package com.larbcorp.neuroinfogrinder.domain.findings.dto;

public record SourceMessageDto(
    Long messageId,
    Long telegramMessageId,
    String senderName,
    String text,
    Boolean usedInPrompt,
    String relation,
    Long replyToTelegramMessageId,
    Long topicId,
    String topicName
) {}
