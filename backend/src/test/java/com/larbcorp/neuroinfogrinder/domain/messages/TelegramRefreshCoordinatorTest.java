package com.larbcorp.neuroinfogrinder.domain.messages;

import com.larbcorp.neuroinfogrinder.domain.findings.PipelineEventBus;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.telegram.TelegramTdlibService;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramAuthStateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramRefreshCoordinatorTest {

    private MessageService messageService;
    private TelegramTdlibService telegramTdlibService;
    private PipelineEventBus pipelineEventBus;
    private GroupRepository groupRepository;
    private TelegramSyncTaskExecutor syncTaskExecutor;
    private TelegramRefreshCoordinator coordinator;

    @BeforeEach
    void setUp() {
        messageService = mock(MessageService.class);
        telegramTdlibService = mock(TelegramTdlibService.class);
        pipelineEventBus = mock(PipelineEventBus.class);
        groupRepository = mock(GroupRepository.class);
        syncTaskExecutor = mock(TelegramSyncTaskExecutor.class);
        coordinator = new TelegramRefreshCoordinator(
                messageService,
                telegramTdlibService,
                pipelineEventBus,
                groupRepository,
                syncTaskExecutor
        );
        ReflectionTestUtils.setField(coordinator, "scheduledSyncEnabled", true);
        ReflectionTestUtils.setField(coordinator, "scheduledBatchSize", 2);
    }

    @Test
    void runScheduledCatchUpDoesNotScanEnabledGroupsOrScheduleAutomaticSync() {

        coordinator.runScheduledCatchUp();

        verify(groupRepository, never()).findByEnabledTrue();
        verify(syncTaskExecutor, never())
                .execute(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Runnable.class));
    }

    @Test
    void requestMessageSyncSkipsGroupAlreadyInFlight() {
        GroupEntity group = new GroupEntity();
        group.setId(10L);
        group.setTelegramChatId(-100L);
        group.setEnabled(true);

        when(groupRepository.findById(10L)).thenReturn(java.util.Optional.of(group));
        when(telegramTdlibService.getAuthorizationState())
                .thenReturn(new TelegramAuthStateResponse("AuthorizationStateReady", true, false, false, false, null));
        when(syncTaskExecutor.execute(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Runnable.class)))
                .thenReturn(true);

        MessageSyncRequestResponse first = coordinator.requestMessageSync(10L, "test");
        MessageSyncRequestResponse second = coordinator.requestMessageSync(10L, "test");

        org.junit.jupiter.api.Assertions.assertTrue(first.scheduled());
        org.junit.jupiter.api.Assertions.assertFalse(second.scheduled());
        org.junit.jupiter.api.Assertions.assertEquals("already_running", second.reason());
        verify(syncTaskExecutor, org.mockito.Mockito.times(1))
                .execute(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Runnable.class));
    }

    @Test
    void requestMessageSyncAllowsSameTelegramChatAcrossDifferentAccounts() {
        GroupEntity first = new GroupEntity();
        first.setId(10L);
        first.setAccountId(1L);
        first.setTelegramChatId(-100L);
        first.setEnabled(true);
        GroupEntity duplicateChat = new GroupEntity();
        duplicateChat.setId(20L);
        duplicateChat.setAccountId(2L);
        duplicateChat.setTelegramChatId(-100L);
        duplicateChat.setEnabled(true);

        when(groupRepository.findById(10L)).thenReturn(java.util.Optional.of(first));
        when(groupRepository.findById(20L)).thenReturn(java.util.Optional.of(duplicateChat));
        when(telegramTdlibService.getAuthorizationState(1L))
                .thenReturn(new TelegramAuthStateResponse("AuthorizationStateReady", true, false, false, false, null));
        when(telegramTdlibService.getAuthorizationState(2L))
                .thenReturn(new TelegramAuthStateResponse("AuthorizationStateReady", true, false, false, false, null));
        when(syncTaskExecutor.execute(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Runnable.class)))
                .thenReturn(true);

        MessageSyncRequestResponse firstResponse = coordinator.requestMessageSync(10L, "test");
        MessageSyncRequestResponse secondResponse = coordinator.requestMessageSync(20L, "test");

        org.junit.jupiter.api.Assertions.assertTrue(firstResponse.scheduled());
        org.junit.jupiter.api.Assertions.assertTrue(secondResponse.scheduled());
        verify(syncTaskExecutor, org.mockito.Mockito.times(2))
                .execute(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Runnable.class));
    }

    @Test
    void requestMessageSyncSkipsWhenExecutorIsSaturated() {
        GroupEntity group = new GroupEntity();
        group.setId(10L);
        group.setTelegramChatId(-100L);
        group.setEnabled(true);

        when(groupRepository.findById(10L)).thenReturn(java.util.Optional.of(group));
        when(telegramTdlibService.getAuthorizationState())
                .thenReturn(new TelegramAuthStateResponse("AuthorizationStateReady", true, false, false, false, null));
        when(syncTaskExecutor.execute(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Runnable.class)))
                .thenReturn(false);

        MessageSyncRequestResponse accepted = coordinator.requestMessageSync(10L, "test");

        org.junit.jupiter.api.Assertions.assertFalse(accepted.scheduled());
        org.junit.jupiter.api.Assertions.assertEquals("executor_saturated", accepted.reason());
    }

    @Test
    void requestMessageSyncReturnsErrorWhenSchedulingFailsUnexpectedly() {
        when(groupRepository.findById(10L)).thenThrow(new IllegalStateException("database unavailable"));

        MessageSyncRequestResponse response = coordinator.requestMessageSync(10L, "test");

        org.junit.jupiter.api.Assertions.assertFalse(response.scheduled());
        org.junit.jupiter.api.Assertions.assertEquals("error", response.reason());
    }
}
