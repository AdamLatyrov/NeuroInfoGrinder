package com.larbcorp.neuroinfogrinder.domain.findings;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueueProcessorTest {

    private MessageRepository messageRepository;
    private GroupRepository groupRepository;
    private PipelineService pipelineService;
    private QueueProcessor queueProcessor;

    @BeforeEach
    void setUp() {
        messageRepository = mock(MessageRepository.class);
        groupRepository = mock(GroupRepository.class);
        pipelineService = mock(PipelineService.class);
        queueProcessor = new QueueProcessor(messageRepository, groupRepository, pipelineService);
        when(messageRepository.save(any(MessageEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void marksMessageSkippedOnNonTransientPersistenceError() {
        GroupEntity group = new GroupEntity();
        group.setId(7L);
        group.setEnabled(true);

        MessageEntity message = new MessageEntity();
        message.setId(101L);
        message.setGroupId(7L);
        message.setTelegramMessageId(101L);
        message.setText("test");
        message.setIsBot(false);
        message.setProcessingStatus("UNPROCESSED");
        message.setMessageDate(Instant.now());

        when(groupRepository.findByEnabledTrue()).thenReturn(List.of(group));
        when(messageRepository.findByGroupIdInAndProcessingStatusInOrderByMessageDateAsc(
            any(), any(), any())).thenReturn(new PageImpl<>(List.of(message)));
        when(messageRepository.findById(101L)).thenReturn(Optional.of(message));
        when(pipelineService.processMessage(101L))
            .thenThrow(new DataIntegrityViolationException("value too long"));

        queueProcessor.processQueue();

        assertThat(message.getProcessingStatus()).isEqualTo("SKIPPED");
        assertThat(message.getClassifierReason()).contains("Persistence error");
    }
}
