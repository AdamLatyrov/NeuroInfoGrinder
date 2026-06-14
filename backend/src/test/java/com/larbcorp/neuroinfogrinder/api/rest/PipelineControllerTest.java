package com.larbcorp.neuroinfogrinder.api.rest;

import com.larbcorp.neuroinfogrinder.domain.findings.PipelineService;
import com.larbcorp.neuroinfogrinder.domain.findings.QueueProcessor;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.PipelineTraceRepository;
import com.larbcorp.neuroinfogrinder.shared.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelineControllerTest {

    @Test
    void resultsExposeClassifierPayloadAndContextHash() {
        PipelineService pipelineService = mock(PipelineService.class);
        QueueProcessor queueProcessor = mock(QueueProcessor.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        PipelineTraceRepository pipelineTraceRepository = mock(PipelineTraceRepository.class);
        PipelineController controller = new PipelineController(
                pipelineService,
                queueProcessor,
                messageRepository,
                groupRepository,
                pipelineTraceRepository
        );

        GroupEntity group = new GroupEntity();
        group.setId(78L);
        group.setEnabled(true);

        MessageEntity message = new MessageEntity();
        message.setId(30805L);
        message.setGroupId(78L);
        message.setSenderName("Adam");
        message.setText("где покупать гифты клода по норм цене");
        message.setProcessingStatus("CLASSIFIED");
        message.setSignalScore(0.91);
        message.setClassifierScore(1.0);
        message.setClassifierReason("User asks where to buy Claude gifts cheaper");
        message.setClassifierResultJson("{\"labels\":[\"DEMAND_SIGNAL\",\"AI_TOOL_OR_PROVIDER\"],\"guideCandidate\":false}");
        message.setClassificationContextHash("ctx-hash-1");
        message.setMessageDate(Instant.parse("2026-06-14T16:50:19Z"));

        when(groupRepository.findByEnabledTrue()).thenReturn(List.of(group));
        when(messageRepository.findByGroupIdInAndProcessingStatusIn(
                eq(List.of(78L)),
                eq(List.of("CLASSIFIED")),
                any()
        )).thenReturn(new PageImpl<>(List.of(message), PageRequest.of(0, 20), 1));

        PageResponse<PipelineController.PipelineResultItem> response =
                controller.getResults("CLASSIFIED", null, null, PageRequest.of(0, 20));

        assertEquals(1, response.content().size());
        PipelineController.PipelineResultItem item = response.content().get(0);
        assertEquals(30805L, item.id());
        assertEquals("CLASSIFIED", item.status());
        assertNotNull(item.classifierResultJson());
        assertEquals("ctx-hash-1", item.classificationContextHash());
        assertEquals("User asks where to buy Claude gifts cheaper", item.classifierReason());
    }

    @Test
    void requeueMessageResetsClassifiedState() {
        PipelineService pipelineService = mock(PipelineService.class);
        QueueProcessor queueProcessor = mock(QueueProcessor.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        PipelineTraceRepository pipelineTraceRepository = mock(PipelineTraceRepository.class);
        PipelineController controller = new PipelineController(
                pipelineService,
                queueProcessor,
                messageRepository,
                groupRepository,
                pipelineTraceRepository
        );

        GroupEntity group = new GroupEntity();
        group.setId(78L);
        group.setEnabled(true);

        MessageEntity message = new MessageEntity();
        message.setId(30805L);
        message.setGroupId(78L);
        message.setProcessingStatus("CLASSIFIED");
        message.setSignalScore(0.91);
        message.setClassifierScore(1.0);
        message.setClassifierReason("User asks where to buy Claude gifts cheaper");
        message.setClassifierResultJson("{\"labels\":[\"DEMAND_SIGNAL\",\"AI_TOOL_OR_PROVIDER\"],\"guideCandidate\":false}");
        message.setClassificationContextHash("ctx-hash-1");
        message.setRuleResultJson("{\"matchedSignals\":[\"клода\",\"цена\"]}");
        message.setGuideId(55L);

        when(messageRepository.findById(30805L)).thenReturn(java.util.Optional.of(message));
        when(groupRepository.findById(78L)).thenReturn(java.util.Optional.of(group));
        when(messageRepository.save(any(MessageEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PipelineController.RequeueResponse response = controller.requeueMessage(30805L);

        assertEquals(1, response.requeued());
        assertEquals("UNPROCESSED", message.getProcessingStatus());
        assertNull(message.getSignalScore());
        assertNull(message.getClassifierScore());
        assertNull(message.getClassifierReason());
        assertNull(message.getClassifierResultJson());
        assertNull(message.getClassificationContextHash());
        assertNull(message.getRuleResultJson());
        assertNull(message.getGuideId());
    }
}
