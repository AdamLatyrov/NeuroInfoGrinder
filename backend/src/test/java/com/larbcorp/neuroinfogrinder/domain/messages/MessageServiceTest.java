package com.larbcorp.neuroinfogrinder.domain.messages;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.telegram.TelegramTdlibService;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramMessageDto;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageServiceTest {

    @Test
    void syncMessagesFetchesBeforePersisting() {
        MessageRepository messageRepository = mock(MessageRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        TelegramTdlibService telegramTdlibService = mock(TelegramTdlibService.class);
        MessageSyncPersistenceService persistenceService = mock(MessageSyncPersistenceService.class);
        MessageService messageService = new MessageService(
                messageRepository,
                groupRepository,
                telegramTdlibService,
                persistenceService
        );

        GroupEntity group = new GroupEntity();
        group.setId(7L);
        group.setTelegramChatId(99L);
        group.setLastReadMessageId(0L);

        TelegramMessageDto dto = new TelegramMessageDto(
            1L, 99L, 0L, "topic", "text", "payload", "sender", null, 123L, false, null, 0L, 1L
        );

        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(messageRepository.findFirstByGroupIdOrderByMessageDateDesc(7L)).thenReturn(Optional.empty());
        when(telegramTdlibService.getMessages(99L, 0, 20)).thenReturn(List.of(dto));
        when(persistenceService.persistSyncedMessagesDetailed(eq(7L), anyList()))
                .thenReturn(new MessageSyncPersistenceResult(1, 0, 0, 0, 1));

        MessageSyncResult result = messageService.syncMessagesFromTelegram(7L);

        var order = inOrder(groupRepository, telegramTdlibService, persistenceService);
        order.verify(groupRepository).findById(7L);
        order.verify(telegramTdlibService).getMessages(99L, 0, 20);
        order.verify(persistenceService).persistSyncedMessagesDetailed(eq(7L), eq(List.of(dto)));
        verify(persistenceService).persistSyncedMessagesDetailed(eq(7L), eq(List.of(dto)));
        assertThat(result.changed()).isEqualTo(1);
    }

    @Test
    void getMessagesDoesNotCallTdlibMetadataRepairInReadPath() {
        MessageRepository messageRepository = mock(MessageRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        TelegramTdlibService telegramTdlibService = mock(TelegramTdlibService.class);
        MessageSyncPersistenceService persistenceService = mock(MessageSyncPersistenceService.class);
        MessageService messageService = new MessageService(
                messageRepository,
                groupRepository,
                telegramTdlibService,
                persistenceService
        );

        GroupEntity group = new GroupEntity();
        group.setId(7L);
        group.setTelegramChatId(99L);
        group.setForum(true);

        MessageEntity message = new MessageEntity();
        message.setId(1L);
        message.setGroupId(7L);
        message.setTelegramMessageId(123L);
        message.setSenderName("Unknown");
        message.setSenderTelegramUserId(456L);
        message.setIsBot(false);
        message.setText("hello");
        message.setReplyCount(0);
        message.setProcessingStatus("UNPROCESSED");
        message.setTopicId(1L);
        message.setTopicName(null);
        message.setMessageDate(Instant.parse("2026-06-16T07:00:00Z"));

        when(messageRepository.findByGroupId(eq(7L), eq(PageRequest.of(0, 10))))
                .thenReturn(new PageImpl<>(List.of(message), PageRequest.of(0, 10), 1));
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));

        var response = messageService.getMessages(7L, null, null, PageRequest.of(0, 10));

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).topicName()).isEqualTo("Основной");
        verify(telegramTdlibService, never()).resolveUserDisplayName(456L);
        verify(telegramTdlibService, never()).getTopics(99L, 100);
    }

    @Test
    void syncMessagesMarksHistoryTimeoutAsDegradedWhenFallbackReturnsNothing() {
        MessageRepository messageRepository = mock(MessageRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        TelegramTdlibService telegramTdlibService = mock(TelegramTdlibService.class);
        MessageSyncPersistenceService persistenceService = mock(MessageSyncPersistenceService.class);
        MessageService messageService = new MessageService(
                messageRepository,
                groupRepository,
                telegramTdlibService,
                persistenceService
        );

        GroupEntity group = new GroupEntity();
        group.setId(7L);
        group.setTelegramChatId(99L);
        group.setTitle("Vibe Dev");
        group.setLastReadMessageId(123L);

        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(messageRepository.findFirstByGroupIdOrderByMessageDateDesc(7L)).thenReturn(Optional.empty());
        when(telegramTdlibService.getMessages(99L, 0, 100))
                .thenThrow(new RuntimeException("Timed out while waiting for TDLib JNI response"));
        when(telegramTdlibService.getLatestMessage(99L)).thenReturn(Optional.empty());
        when(persistenceService.persistSyncedMessagesDetailed(eq(7L), anyList()))
                .thenReturn(new MessageSyncPersistenceResult(0, 0, 0, 0, 0));

        MessageSyncResult result = messageService.syncMessagesFromTelegram(7L);

        assertThat(result.historyTimeout()).isTrue();
        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.reason()).isEqualTo("history_timeout");
        assertThat(result.fullSuccess()).isFalse();
        verify(persistenceService).persistSyncedMessagesDetailed(eq(7L), eq(List.of()));
    }

    @Test
    void getMessagesRejectsGroupOwnedByAnotherUser() {
        MessageRepository messageRepository = mock(MessageRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        TelegramTdlibService telegramTdlibService = mock(TelegramTdlibService.class);
        MessageSyncPersistenceService persistenceService = mock(MessageSyncPersistenceService.class);
        MessageService messageService = new MessageService(
                messageRepository,
                groupRepository,
                telegramTdlibService,
                persistenceService
        );

        when(groupRepository.findByIdAndOwnerUserId(7L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.getMessages(1L, 7L, null, null, PageRequest.of(0, 10)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Group not found");

        verify(messageRepository, never()).findByOwnerUserIdAndGroupId(1L, 7L, PageRequest.of(0, 10));
    }
}
