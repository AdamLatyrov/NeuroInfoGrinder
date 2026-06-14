package com.larbcorp.neuroinfogrinder.domain.findings;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.ChainConfigEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.ChainConfigRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageChainBuilderTest {

    @Test
    void buildsReplyTreeAndSkipsUnrelatedNearbyMessages() {
        MessageRepository messageRepository = mock(MessageRepository.class);
        ChainConfigRepository chainConfigRepository = mock(ChainConfigRepository.class);
        MessageChainBuilder builder = new MessageChainBuilder(messageRepository, chainConfigRepository);

        MessageEntity parent = message(10L, 100L, 1L, null, null, Instant.parse("2026-06-10T10:00:00Z"));
        MessageEntity root = message(11L, 101L, 1L, 100L, 55L, Instant.parse("2026-06-10T10:02:00Z"));
        MessageEntity reply = message(12L, 102L, 1L, 101L, 55L, Instant.parse("2026-06-10T10:03:00Z"));
        MessageEntity nestedReply = message(13L, 103L, 1L, 102L, 55L, Instant.parse("2026-06-10T10:04:00Z"));
        MessageEntity unrelated = message(14L, 104L, 1L, null, 77L, Instant.parse("2026-06-10T10:03:30Z"));
        MessageEntity sameAuthorNearby = message(15L, 105L, 1L, null, 88L, Instant.parse("2026-06-10T10:01:30Z"));
        root.setSenderTelegramUserId(900L);
        sameAuthorNearby.setSenderTelegramUserId(900L);
        parent.setSenderTelegramUserId(901L);
        reply.setSenderTelegramUserId(902L);
        nestedReply.setSenderTelegramUserId(903L);
        unrelated.setSenderTelegramUserId(904L);

        ChainConfigEntity config = new ChainConfigEntity();
        config.setIncludeReplies(true);
        config.setTimeWindowMinutes(5);
        config.setMaxMessagesPerChain(20);

        when(messageRepository.findById(11L)).thenReturn(Optional.of(root));
        when(chainConfigRepository.findAll()).thenReturn(List.of(config));
        when(messageRepository.findByGroupIdAndTelegramMessageId(1L, 100L)).thenReturn(Optional.of(parent));
        when(messageRepository.findByGroupIdAndReplyToMessageIdIn(1L, List.of(101L))).thenReturn(List.of(reply));
        when(messageRepository.findByGroupIdAndReplyToMessageIdIn(1L, List.of(102L))).thenReturn(List.of(nestedReply));
        when(messageRepository.findByGroupIdAndReplyToMessageIdIn(1L, List.of(103L))).thenReturn(List.of());
        when(messageRepository.findByGroupIdAndMessageDateAfter(1L, Instant.parse("2026-06-10T09:57:00Z")))
            .thenReturn(List.of(parent, root, reply, nestedReply, unrelated, sameAuthorNearby));

        List<MessageEntity> chain = builder.buildChain(11L);

        assertThat(chain)
            .extracting(MessageEntity::getId)
            .containsExactly(10L, 15L, 11L, 12L, 13L);
    }

    private MessageEntity message(Long id, Long telegramMessageId, Long groupId,
                                  Long replyToMessageId, Long topicId, Instant messageDate) {
        MessageEntity entity = new MessageEntity();
        entity.setId(id);
        entity.setTelegramMessageId(telegramMessageId);
        entity.setGroupId(groupId);
        entity.setReplyToMessageId(replyToMessageId);
        entity.setTopicId(topicId);
        entity.setMessageDate(messageDate);
        entity.setIsBot(false);
        entity.setText("message-" + id);
        return entity;
    }
}
