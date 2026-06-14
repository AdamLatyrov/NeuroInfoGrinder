package com.larbcorp.neuroinfogrinder.domain.messages;

import com.larbcorp.neuroinfogrinder.domain.findings.PipelineEventBus;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.telegram.TelegramTdlibService;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramAuthStateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

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
    void runScheduledCatchUpRequestsSyncForEnabledGroupsWhenTdlibIsReady() {
        GroupEntity first = new GroupEntity();
        first.setId(101L);
        GroupEntity second = new GroupEntity();
        second.setId(202L);
        GroupEntity third = new GroupEntity();
        third.setId(303L);

        when(telegramTdlibService.getAuthorizationState())
                .thenReturn(new TelegramAuthStateResponse("AuthorizationStateReady", true, false, false, false, null));
        when(groupRepository.findByEnabledTrue()).thenReturn(List.of(first, second, third));
        when(syncTaskExecutor.execute(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Runnable.class)))
                .thenReturn(true);

        coordinator.runScheduledCatchUp();

        verify(groupRepository).findByEnabledTrue();
        verify(syncTaskExecutor, org.mockito.Mockito.times(2))
                .execute(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Runnable.class));
    }

    @Test
    void runScheduledCatchUpSkipsGroupsWhenTdlibIsNotReady() {
        when(telegramTdlibService.getAuthorizationState())
                .thenReturn(new TelegramAuthStateResponse("AuthorizationStateWaitCode", false, false, true, false, null));

        coordinator.runScheduledCatchUp();

        verify(groupRepository, never()).findByEnabledTrue();
    }

    @Test
    void requestMessageSyncSkipsGroupAlreadyInFlight() {
        when(syncTaskExecutor.execute(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Runnable.class)))
                .thenReturn(true);

        boolean first = coordinator.requestMessageSync(10L, "test");
        boolean second = coordinator.requestMessageSync(10L, "test");

        org.junit.jupiter.api.Assertions.assertTrue(first);
        org.junit.jupiter.api.Assertions.assertFalse(second);
        verify(syncTaskExecutor, org.mockito.Mockito.times(1))
                .execute(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Runnable.class));
    }

    @Test
    void requestMessageSyncSkipsWhenExecutorIsSaturated() {
        when(syncTaskExecutor.execute(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Runnable.class)))
                .thenReturn(false);

        boolean accepted = coordinator.requestMessageSync(10L, "test");

        org.junit.jupiter.api.Assertions.assertFalse(accepted);
    }
}
