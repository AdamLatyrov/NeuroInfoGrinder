package com.larbcorp.neuroinfogrinder.domain.findings;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageContextBuilderTest {

    private MessageRepository messageRepository;
    private MessageContextBuilder builder;

    @BeforeEach
    void setUp() {
        messageRepository = mock(MessageRepository.class);
        builder = new MessageContextBuilder(messageRepository);
        ReflectionTestUtils.setField(builder, "contextEnabled", true);
        ReflectionTestUtils.setField(builder, "contextWindowMinutes", 10);
        ReflectionTestUtils.setField(builder, "contextMaxMessages", 80);
        ReflectionTestUtils.setField(builder, "contextMaxChars", 20000);
        ReflectionTestUtils.setField(builder, "replyDepth", 1);
        ReflectionTestUtils.setField(builder, "anchorMinScore", 3);
    }

    @Test
    void anchorScoreSelectsUsefulQuestionAndRejectsNoise() {
        MessageEntity useful = message(10L, 100L, 1L, null, 55L, "где купить claude api дешевле?", Instant.parse("2026-06-10T10:00:00Z"));
        MessageEntity noise = message(11L, 101L, 1L, null, null, "ок", Instant.parse("2026-06-10T10:00:30Z"));

        when(messageRepository.findByGroupIdAndReplyToMessageId(1L, 100L)).thenReturn(List.of());
        when(messageRepository.findByGroupIdAndReplyToMessageId(1L, 101L)).thenReturn(List.of());
        when(messageRepository.findByGroupIdAndMessageDateBetweenOrderByMessageDateAsc(1L,
            Instant.parse("2026-06-10T09:50:00Z"), Instant.parse("2026-06-10T10:10:00Z")))
            .thenReturn(List.of(useful, message(12L, 102L, 1L, null, 55L, "openrouter дешевле", Instant.parse("2026-06-10T10:01:00Z"))));
        when(messageRepository.findByGroupIdAndMessageDateBetweenOrderByMessageDateAsc(1L,
            Instant.parse("2026-06-10T09:50:30Z"), Instant.parse("2026-06-10T10:10:30Z")))
            .thenReturn(List.of(noise));

        assertThat(builder.scoreAnchor(useful)).isGreaterThanOrEqualTo(3);
        assertThat(builder.scoreAnchor(noise)).isLessThan(3);
    }

    @Test
    void buildsTwoWindowsForReplyAcrossDifferentDaysAndDeduplicates() {
        MessageEntity parent = message(20L, 200L, 1L, null, 77L, "где купить chatgpt plus?", Instant.parse("2026-06-01T10:00:00Z"));
        MessageEntity reply = message(21L, 201L, 1L, 200L, 77L, "на фанпее купи за 300", Instant.parse("2026-06-04T10:00:00Z"));
        MessageEntity nearbyParent = message(22L, 202L, 1L, null, 77L, "openrouter тоже вариант", Instant.parse("2026-06-01T10:05:00Z"));
        MessageEntity nearbyReply = message(23L, 203L, 1L, null, 77L, "ещё есть китайские сервисы пополнения", Instant.parse("2026-06-04T10:06:00Z"));

        when(messageRepository.findByGroupIdAndReplyToMessageId(1L, 201L)).thenReturn(List.of());
        when(messageRepository.findByGroupIdAndTelegramMessageId(1L, 200L)).thenReturn(Optional.of(parent));
        when(messageRepository.findByGroupIdAndReplyToMessageIdIn(1L, List.of(201L))).thenReturn(List.of());
        when(messageRepository.findByGroupIdAndMessageDateBetweenOrderByMessageDateAsc(
            1L, Instant.parse("2026-06-04T09:50:00Z"), Instant.parse("2026-06-04T10:10:00Z")))
            .thenReturn(List.of(reply, nearbyReply));
        when(messageRepository.findByGroupIdAndMessageDateBetweenOrderByMessageDateAsc(
            1L, Instant.parse("2026-06-01T09:50:00Z"), Instant.parse("2026-06-01T10:10:00Z")))
            .thenReturn(List.of(parent, nearbyParent));

        MessageContextBundle bundle = builder.buildContext(reply);

        assertThat(bundle.messages()).extracting(MessageEntity::getId)
            .containsExactly(20L, 22L, 21L, 23L);
        assertThat(bundle.totalChars()).isPositive();
        assertThat(bundle.contextHash()).isNotBlank();
    }

    private MessageEntity message(Long id, Long telegramMessageId, Long groupId, Long replyToTelegramId,
                                  Long topicId, String text, Instant instant) {
        MessageEntity entity = new MessageEntity();
        entity.setId(id);
        entity.setTelegramMessageId(telegramMessageId);
        entity.setGroupId(groupId);
        entity.setReplyToMessageId(replyToTelegramId);
        entity.setTopicId(topicId);
        entity.setTopicName("Claude Code");
        entity.setText(text);
        entity.setIsBot(false);
        entity.setMessageDate(instant);
        entity.setSenderTelegramUserId(id + 1000);
        return entity;
    }
}
