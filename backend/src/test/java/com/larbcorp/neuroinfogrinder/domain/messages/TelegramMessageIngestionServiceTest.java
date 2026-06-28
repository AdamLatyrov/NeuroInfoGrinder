package com.larbcorp.neuroinfogrinder.domain.messages;

import com.larbcorp.neuroinfogrinder.domain.findings.GuideGenerator;
import com.larbcorp.neuroinfogrinder.domain.findings.MaterialGenerator;
import com.larbcorp.neuroinfogrinder.domain.findings.PipelineEventBus;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TelegramMonitoredChatEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TelegramAccountRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TelegramMonitoredChatRepository;
import com.larbcorp.neuroinfogrinder.telegram.TelegramUpdateListener;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramMessageDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramMessageIngestionServiceTest {

    @Test
    void monitoredEnabledChatSavesRawMessage() {
        TestContext ctx = new TestContext();
        TelegramMonitoredChatEntity monitored = monitoredChat(7L, 11L, -1001L, true, true, false);
        GroupEntity group = group(42L, 7L, 11L, -1001L, true);
        AtomicReference<MessageEntity> savedMessage = new AtomicReference<>();

        when(ctx.monitoredChatRepository.findLiveMonitoredChat(7L, -1001L, null))
                .thenReturn(Optional.of(monitored));
        when(ctx.groupRepository.findByAccountIdAndTelegramChatId(7L, -1001L))
                .thenReturn(Optional.of(group));
        when(ctx.messageRepository.findByTelegramAccountIdAndTelegramChatIdAndTelegramMessageId(7L, -1001L, 9001L))
                .thenReturn(Optional.empty());
        when(ctx.messageRepository.save(any(MessageEntity.class))).thenAnswer(invocation -> {
            MessageEntity entity = invocation.getArgument(0);
            entity.setId(501L);
            savedMessage.set(entity);
            return entity;
        });
        when(ctx.groupRepository.save(any(GroupEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ctx.monitoredChatRepository.save(any(TelegramMonitoredChatEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TelegramIngestionResult result = ctx.service.ingestRealtimeMessage(7L, message(9001L, -1001L, 0L));

        assertThat(result).isEqualTo(TelegramIngestionResult.SAVED);
        assertThat(savedMessage.get()).isNotNull();
        assertThat(savedMessage.get().getTelegramAccountId()).isEqualTo(7L);
        assertThat(savedMessage.get().getTelegramChatId()).isEqualTo(-1001L);
        assertThat(savedMessage.get().getTelegramMessageId()).isEqualTo(9001L);
        assertThat(savedMessage.get().getProcessingStatus()).isEqualTo("QUEUED");
    }

    @Test
    void disabledOrUnmonitoredChatIsIgnoredWithoutCreatingGroupOrSkippedMessage() {
        TestContext ctx = new TestContext();
        when(ctx.monitoredChatRepository.findLiveMonitoredChat(7L, -2002L, null))
                .thenReturn(Optional.empty());

        TelegramIngestionResult result = ctx.service.ingestRealtimeMessage(7L, message(9002L, -2002L, 0L));

        assertThat(result).isEqualTo(TelegramIngestionResult.IGNORED_UNMONITORED_CHAT);
        verify(ctx.groupRepository, never()).save(any(GroupEntity.class));
        verify(ctx.messageRepository, never()).save(any(MessageEntity.class));
    }

    @Test
    void duplicateUpdateUsesAccountChatMessageKeyAndDoesNotDuplicateMessage() {
        TestContext ctx = new TestContext();
        TelegramMonitoredChatEntity monitored = monitoredChat(7L, 11L, -1001L, true, true, false);
        GroupEntity group = group(42L, 7L, 11L, -1001L, true);
        MessageEntity existing = new MessageEntity();
        existing.setId(501L);

        when(ctx.monitoredChatRepository.findLiveMonitoredChat(7L, -1001L, null))
                .thenReturn(Optional.of(monitored));
        when(ctx.groupRepository.findByAccountIdAndTelegramChatId(7L, -1001L))
                .thenReturn(Optional.of(group));
        when(ctx.messageRepository.findByTelegramAccountIdAndTelegramChatIdAndTelegramMessageId(7L, -1001L, 9001L))
                .thenReturn(Optional.of(existing));

        TelegramIngestionResult result = ctx.service.ingestRealtimeMessage(7L, message(9001L, -1001L, 0L));

        assertThat(result).isEqualTo(TelegramIngestionResult.DUPLICATE);
        verify(ctx.messageRepository, never()).save(any(MessageEntity.class));
    }

    @Test
    void ingestionDisabledIgnoresUpdateBeforePersistence() {
        TestContext ctx = new TestContext();
        ReflectionTestUtils.setField(ctx.service, "ingestionEnabled", false);

        TelegramIngestionResult result = ctx.service.ingestRealtimeMessage(7L, message(9001L, -1001L, 0L));

        assertThat(result).isEqualTo(TelegramIngestionResult.IGNORED_DISABLED);
        verify(ctx.monitoredChatRepository, never()).findLiveMonitoredChat(any(), any(), any());
        verify(ctx.messageRepository, never()).save(any(MessageEntity.class));
    }

    @Test
    void pipelineDisabledSavesRawMessageWithoutQueueingPipelineWork() {
        TestContext ctx = new TestContext();
        ReflectionTestUtils.setField(ctx.service, "pipelineProcessingEnabled", false);
        TelegramMonitoredChatEntity monitored = monitoredChat(7L, 11L, -1001L, true, true, false);
        GroupEntity group = group(42L, 7L, 11L, -1001L, true);
        AtomicReference<MessageEntity> savedMessage = new AtomicReference<>();

        when(ctx.monitoredChatRepository.findLiveMonitoredChat(7L, -1001L, null))
                .thenReturn(Optional.of(monitored));
        when(ctx.groupRepository.findByAccountIdAndTelegramChatId(7L, -1001L))
                .thenReturn(Optional.of(group));
        when(ctx.messageRepository.findByTelegramAccountIdAndTelegramChatIdAndTelegramMessageId(7L, -1001L, 9001L))
                .thenReturn(Optional.empty());
        when(ctx.messageRepository.save(any(MessageEntity.class))).thenAnswer(invocation -> {
            MessageEntity entity = invocation.getArgument(0);
            savedMessage.set(entity);
            return entity;
        });

        TelegramIngestionResult result = ctx.service.ingestRealtimeMessage(7L, message(9001L, -1001L, 0L));

        assertThat(result).isEqualTo(TelegramIngestionResult.SAVED);
        assertThat(savedMessage.get().getProcessingStatus()).isEqualTo("UNPROCESSED");
        verify(ctx.pipelineEventBus, never()).publish(any());
    }

    @Test
    void multipleAccountsDoNotMixSessionsOrMessages() {
        TestContext ctx = new TestContext();
        when(ctx.monitoredChatRepository.findLiveMonitoredChat(8L, -1001L, null))
                .thenReturn(Optional.empty());

        TelegramIngestionResult result = ctx.service.ingestRealtimeMessage(8L, message(9001L, -1001L, 0L));

        assertThat(result).isEqualTo(TelegramIngestionResult.IGNORED_UNMONITORED_CHAT);
        verify(ctx.messageRepository, never())
                .findByTelegramAccountIdAndTelegramChatIdAndTelegramMessageId(eq(7L), eq(-1001L), eq(9001L));
        verify(ctx.messageRepository, never()).save(any(MessageEntity.class));
    }

    @Test
    void updateHandlerIsNotTransactionalDbWriter() throws Exception {
        assertThat(TelegramUpdateListener.class.isAssignableFrom(TelegramMessageIngestionService.class)).isFalse();

        Method listenerMethod = TelegramRealtimeIngestionListener.class
                .getMethod("onNewMessage", Long.class, TelegramMessageDto.class);

        assertThat(listenerMethod.getAnnotation(Transactional.class)).isNull();
    }

    @Test
    void realtimeHandlerDoesNotDependOnGeneratorsDirectly() {
        boolean hasGeneratorField = Arrays.stream(TelegramRealtimeIngestionListener.class.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(type -> type.equals(GuideGenerator.class) || type.equals(MaterialGenerator.class));

        assertThat(hasGeneratorField).isFalse();
    }

    private static TelegramMessageDto message(long messageId, long chatId, long topicId) {
        return new TelegramMessageDto(
                messageId,
                chatId,
                topicId,
                null,
                "MessageText",
                "hello",
                "Alice",
                "alice",
                501L,
                false,
                null,
                0L,
                Instant.parse("2026-06-20T10:00:00Z").getEpochSecond()
        );
    }

    private static TelegramMonitoredChatEntity monitoredChat(
            Long accountId,
            Long ownerUserId,
            long chatId,
            boolean enabled,
            boolean liveEnabled,
            boolean backfillEnabled
    ) {
        TelegramMonitoredChatEntity entity = new TelegramMonitoredChatEntity();
        entity.setTelegramAccountId(accountId);
        entity.setOwnerUserId(ownerUserId);
        entity.setTelegramChatId(chatId);
        entity.setChatTitle("Chat " + chatId);
        entity.setChatType("GROUP");
        entity.setEnabled(enabled);
        entity.setLiveIngestionEnabled(liveEnabled);
        entity.setBackfillEnabled(backfillEnabled);
        entity.setBackfillStatus("IDLE");
        return entity;
    }

    private static GroupEntity group(Long id, Long accountId, Long ownerUserId, long chatId, boolean enabled) {
        GroupEntity group = new GroupEntity();
        group.setId(id);
        group.setAccountId(accountId);
        group.setOwnerUserId(ownerUserId);
        group.setTelegramChatId(chatId);
        group.setTitle("Chat " + chatId);
        group.setEnabled(enabled);
        group.setLastReadMessageId(0L);
        return group;
    }

    private static final class TestContext {
        private final GroupRepository groupRepository = mock(GroupRepository.class);
        private final MessageRepository messageRepository = mock(MessageRepository.class);
        private final TelegramAccountRepository telegramAccountRepository = mock(TelegramAccountRepository.class);
        private final TelegramMonitoredChatRepository monitoredChatRepository = mock(TelegramMonitoredChatRepository.class);
        private final PipelineEventBus pipelineEventBus = mock(PipelineEventBus.class);
        private final TelegramMessageIngestionService service = new TelegramMessageIngestionService(
                groupRepository,
                messageRepository,
                telegramAccountRepository,
                monitoredChatRepository,
                pipelineEventBus
        );

        private TestContext() {
            ReflectionTestUtils.setField(service, "ingestionEnabled", true);
            ReflectionTestUtils.setField(service, "pipelineProcessingEnabled", true);
        }
    }
}
