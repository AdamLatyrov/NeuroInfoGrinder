package com.larbcorp.neuroinfogrinder.domain.messages;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;

public final class TelegramTopicNames {

    public static final String GENERAL_TOPIC_NAME_RU = "Основной";

    private TelegramTopicNames() {
    }

    public static String visibleTopicName(GroupEntity group, Long topicId, String topicName) {
        if (topicName != null && !topicName.isBlank()) {
            return topicName;
        }
        if (group != null && Boolean.TRUE.equals(group.getForum()) && topicId != null && topicId == 1L) {
            return GENERAL_TOPIC_NAME_RU;
        }
        return topicName;
    }

    public static String storedTopicName(GroupEntity group, long topicId, String topicName) {
        if (topicName != null && !topicName.isBlank()) {
            return topicName;
        }
        if (group != null && Boolean.TRUE.equals(group.getForum()) && topicId == 1L) {
            return GENERAL_TOPIC_NAME_RU;
        }
        return topicName;
    }
}
