package com.larbcorp.neuroinfogrinder.domain.messages;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramMessageDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageSyncPersistenceServiceTest {

    @Test
    void disabledGroupsDoNotCreateSkippedMessageRows() {
        MessageRepository messageRepository = mock(MessageRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        MessageSyncPersistenceService service = new MessageSyncPersistenceService(messageRepository, groupRepository);

        GroupEntity group = new GroupEntity();
        group.setId(42L);
        group.setEnabled(false);
        group.setLastReadMessageId(0L);

        when(groupRepository.findById(42L)).thenReturn(Optional.of(group));

        TelegramMessageDto message = new TelegramMessageDto(
            1001L,
            -100L,
            0L,
            null,
            "text",
            "hello",
            "Alice",
            "alice",
            501L,
            false,
            null,
            0L,
            Instant.now().getEpochSecond()
        );

        int changed = service.persistSyncedMessages(42L, List.of(message));

        assertThat(changed).isZero();
        org.mockito.Mockito.verify(messageRepository, org.mockito.Mockito.never()).saveAll(any());
        org.mockito.Mockito.verify(groupRepository, org.mockito.Mockito.never()).save(any(GroupEntity.class));
    }

    @Test
    void syncedMessagesStoreAccountChatMessageIdempotencyFields() {
        MessageRepository messageRepository = mock(MessageRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        MessageSyncPersistenceService service = new MessageSyncPersistenceService(messageRepository, groupRepository);

        GroupEntity group = new GroupEntity();
        group.setId(42L);
        group.setAccountId(7L);
        group.setOwnerUserId(11L);
        group.setTelegramChatId(-100L);
        group.setEnabled(true);
        group.setLastReadMessageId(0L);

        AtomicReference<List<MessageEntity>> savedMessages = new AtomicReference<>();

        when(groupRepository.findById(42L)).thenReturn(Optional.of(group));
        when(messageRepository.findByTelegramAccountIdAndTelegramChatIdAndTelegramMessageId(7L, -100L, 1001L))
                .thenReturn(Optional.empty());
        when(messageRepository.saveAll(any())).thenAnswer(invocation -> {
            savedMessages.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(groupRepository.save(any(GroupEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TelegramMessageDto message = new TelegramMessageDto(
            1001L,
            -100L,
            0L,
            null,
            "text",
            "hello",
            "Alice",
            "alice",
            501L,
            false,
            null,
            0L,
            Instant.now().getEpochSecond()
        );

        int changed = service.persistSyncedMessages(42L, List.of(message));

        assertThat(changed).isEqualTo(1);
        assertThat(savedMessages.get()).hasSize(1);
        MessageEntity saved = savedMessages.get().get(0);
        assertThat(saved.getTelegramAccountId()).isEqualTo(7L);
        assertThat(saved.getTelegramChatId()).isEqualTo(-100L);
        assertThat(saved.getTelegramMessageId()).isEqualTo(1001L);
    }
}
