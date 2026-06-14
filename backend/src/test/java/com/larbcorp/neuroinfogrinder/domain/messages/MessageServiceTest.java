package com.larbcorp.neuroinfogrinder.domain.messages;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.telegram.TelegramTdlibService;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramMessageDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
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

        TelegramMessageDto dto = new TelegramMessageDto(1L, 99L, 0L, "topic", "text", "payload", "sender", 123L, false, 0L, 1L);

        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(telegramTdlibService.getMessages(99L, 0, 20)).thenReturn(List.of(dto));

        messageService.syncMessagesFromTelegram(7L);

        var order = inOrder(groupRepository, telegramTdlibService, persistenceService);
        order.verify(groupRepository).findById(7L);
        order.verify(telegramTdlibService).getMessages(99L, 0, 20);
        order.verify(persistenceService).persistSyncedMessages(eq(7L), eq(List.of(dto)));
        verify(persistenceService).persistSyncedMessages(eq(7L), eq(List.of(dto)));
    }
}
