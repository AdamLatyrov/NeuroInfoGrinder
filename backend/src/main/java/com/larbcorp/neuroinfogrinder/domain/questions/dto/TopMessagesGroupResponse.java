package com.larbcorp.neuroinfogrinder.domain.questions.dto;

public record TopMessagesGroupResponse(
    Long groupId,
    String groupTitle,
    long messagesPerDay
) {}
