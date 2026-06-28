package com.larbcorp.neuroinfogrinder.domain.chats;

import com.larbcorp.neuroinfogrinder.domain.chats.dto.GroupResponse;
import com.larbcorp.neuroinfogrinder.domain.chats.dto.UpdateGroupRequest;
import com.larbcorp.neuroinfogrinder.domain.messages.TelegramSyncTaskExecutor;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuideRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TelegramAccountRepository;
import com.larbcorp.neuroinfogrinder.shared.dto.PageResponse;
import com.larbcorp.neuroinfogrinder.telegram.TelegramTdlibService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroupServiceTest {

    @Test
    void getGroupsDeduplicatesByTelegramChatId() {
        GroupRepository groupRepository = mock(GroupRepository.class);
        TelegramAccountRepository accountRepository = mock(TelegramAccountRepository.class);
        TelegramTdlibService telegramTdlibService = mock(TelegramTdlibService.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        GuideRepository guideRepository = mock(GuideRepository.class);
        TelegramSyncTaskExecutor telegramSyncTaskExecutor = mock(TelegramSyncTaskExecutor.class);
        GroupService service = new GroupService(
                groupRepository,
                accountRepository,
                telegramTdlibService,
                messageRepository,
                guideRepository,
                telegramSyncTaskExecutor
        );

        GroupEntity stale = new GroupEntity();
        stale.setId(338L);
        stale.setTelegramChatId(-1001732054517L);
        stale.setTitle("Поздняков 3.0");
        stale.setEnabled(false);
        stale.setLastReadMessageId(0L);
        stale.setUpdatedAt(Instant.parse("2026-06-09T18:19:28.483481Z"));

        GroupEntity fresh = new GroupEntity();
        fresh.setId(340L);
        fresh.setTelegramChatId(-1001732054517L);
        fresh.setTitle("Поздняков 3.0");
        fresh.setEnabled(true);
        fresh.setLastReadMessageId(25L);
        fresh.setUpdatedAt(Instant.parse("2026-06-09T18:25:28.240371Z"));

        when(groupRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), eq(PageRequest.of(0, 50))))
                .thenReturn(new PageImpl<>(List.of(stale, fresh), PageRequest.of(0, 50), 2));
        when(messageRepository.countByGroupIdAndMessageDateAfter(any(Long.class), any(Instant.class))).thenReturn(0L);
        when(guideRepository.countByGroupId(any(Long.class))).thenReturn(0L);

        PageResponse<GroupResponse> response = service.getGroups(null, null, null, null, PageRequest.of(0, 50));

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).id()).isEqualTo(340L);
        assertThat(response.content().get(0).telegramChatId()).isEqualTo(-1001732054517L);
    }

    @Test
    void updateGroupEnableSchedulesBackgroundWarmupWithoutSynchronousTdlibCall() {
        GroupRepository groupRepository = mock(GroupRepository.class);
        TelegramAccountRepository accountRepository = mock(TelegramAccountRepository.class);
        TelegramTdlibService telegramTdlibService = mock(TelegramTdlibService.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        GuideRepository guideRepository = mock(GuideRepository.class);
        TelegramSyncTaskExecutor telegramSyncTaskExecutor = mock(TelegramSyncTaskExecutor.class);
        GroupService service = new GroupService(
                groupRepository,
                accountRepository,
                telegramTdlibService,
                messageRepository,
                guideRepository,
                telegramSyncTaskExecutor
        );

        GroupEntity group = new GroupEntity();
        group.setId(340L);
        group.setTelegramChatId(-1001732054517L);
        group.setTitle("Test group");
        group.setEnabled(false);
        group.setLastReadMessageId(0L);

        when(groupRepository.findById(340L)).thenReturn(java.util.Optional.of(group));
        when(groupRepository.save(any(GroupEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.countByGroupIdAndMessageDateAfter(any(Long.class), any(Instant.class))).thenReturn(0L);
        when(guideRepository.countByGroupId(any(Long.class))).thenReturn(0L);
        when(telegramSyncTaskExecutor.execute(any(), any())).thenReturn(true);

        GroupResponse response = service.updateGroup(340L, new UpdateGroupRequest(true, null, null, null));

        assertThat(response.enabled()).isTrue();
        verify(telegramSyncTaskExecutor).execute(eq("group-enable-sync:340"), any(Runnable.class));
        verify(telegramTdlibService, never()).getMessages(any(Long.class), any(Long.class), any(Integer.class));
        verify(telegramTdlibService, never()).getLatestMessage(any(Long.class));
    }

    @Test
    void updateGroupRejectsGroupOwnedByAnotherUser() {
        GroupRepository groupRepository = mock(GroupRepository.class);
        TelegramAccountRepository accountRepository = mock(TelegramAccountRepository.class);
        TelegramTdlibService telegramTdlibService = mock(TelegramTdlibService.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        GuideRepository guideRepository = mock(GuideRepository.class);
        TelegramSyncTaskExecutor telegramSyncTaskExecutor = mock(TelegramSyncTaskExecutor.class);
        GroupService service = new GroupService(
                groupRepository,
                accountRepository,
                telegramTdlibService,
                messageRepository,
                guideRepository,
                telegramSyncTaskExecutor
        );

        when(groupRepository.findByIdAndOwnerUserId(340L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateGroup(1L, 340L, new UpdateGroupRequest(true, null, null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Group not found");

        verify(groupRepository, never()).save(any(GroupEntity.class));
    }
}
