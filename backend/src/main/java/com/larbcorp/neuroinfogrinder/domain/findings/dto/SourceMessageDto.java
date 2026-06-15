package com.larbcorp.neuroinfogrinder.domain.findings.dto;

public record SourceMessageDto(
    Long messageId,
    Long groupId,
    Long telegramChatId,
    Long telegramMessageId,
    String senderDisplayName,
    String senderUsername,
    Long senderTelegramUserId,
    String senderNameSource,
    String text,
    java.util.List<SourceMessageTextEntityDto> textEntities,
    Boolean usedInPrompt,
    String relation,
    Long replyToTelegramMessageId,
    Long topicId,
    String topicName,
    String internalMessageUrl,
    String telegramMessageUrl,
    boolean telegramLinkAvailable,
    String telegramLinkReason
) {}
