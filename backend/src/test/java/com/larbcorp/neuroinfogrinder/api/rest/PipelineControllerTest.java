package com.larbcorp.neuroinfogrinder.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder.domain.findings.PipelineService;
import com.larbcorp.neuroinfogrinder.domain.findings.QueueProcessor;
import com.larbcorp.neuroinfogrinder.domain.findings.TopicClusterService;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuideRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.PipelineTraceRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TopicClusterGuideCandidateRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TopicClusterMessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TopicDiscussionClusterRepository;
import com.larbcorp.neuroinfogrinder.shared.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        GuideRepository guideRepository = mock(GuideRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        PipelineTraceRepository pipelineTraceRepository = mock(PipelineTraceRepository.class);
        PipelineController controller = newController(
                pipelineService,
                queueProcessor,
                messageRepository,
                guideRepository,
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

        when(groupRepository.findByOwnerUserIdAndEnabledTrue(1L)).thenReturn(List.of(group));
        when(messageRepository.findByGroupIdInAndProcessingStatusIn(
                eq(List.of(78L)),
                eq(List.of("CLASSIFIED")),
                any()
        )).thenReturn(new PageImpl<>(List.of(message), PageRequest.of(0, 20), 1));

        PageResponse<PipelineController.PipelineResultItem> response =
                controller.getResults(1L, "CLASSIFIED", null, null, null, PageRequest.of(0, 20));

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
        GuideRepository guideRepository = mock(GuideRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        PipelineTraceRepository pipelineTraceRepository = mock(PipelineTraceRepository.class);
        PipelineController controller = newController(
                pipelineService,
                queueProcessor,
                messageRepository,
                guideRepository,
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

        when(messageRepository.findByIdAndOwnerUserId(30805L, 1L)).thenReturn(java.util.Optional.of(message));
        when(groupRepository.findByIdAndOwnerUserId(78L, 1L)).thenReturn(java.util.Optional.of(group));
        when(messageRepository.save(any(MessageEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PipelineController.RequeueResponse response = controller.requeueMessage(1L, 30805L);

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

    @Test
    void topicExplainSplitsUnrelatedNeighboringClassifiedMessages() {
        PipelineService pipelineService = mock(PipelineService.class);
        QueueProcessor queueProcessor = mock(QueueProcessor.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        GuideRepository guideRepository = mock(GuideRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        PipelineTraceRepository pipelineTraceRepository = mock(PipelineTraceRepository.class);
        PipelineController controller = newController(
                pipelineService,
                queueProcessor,
                messageRepository,
                guideRepository,
                groupRepository,
                pipelineTraceRepository
        );

        GroupEntity group = new GroupEntity();
        group.setId(78L);
        group.setTitle("Vibecoder Chat [Public]");
        group.setEnabled(true);

        MessageEntity first = classifiedTopicMessage(
                101L,
                Instant.parse("2026-06-19T12:37:00Z"),
                "Q",
                "security/backdoor/server protection discussion"
        );
        MessageEntity second = classifiedTopicMessage(
                102L,
                Instant.parse("2026-06-19T12:42:00Z"),
                "InstaLab",
                "Instagram automation account resale passive income api_key=secret-value"
        );

        Instant from = Instant.parse("2026-06-19T12:37:00Z");
        Instant to = Instant.parse("2026-06-19T12:45:59Z");
        when(groupRepository.findByOwnerUserIdAndEnabledTrue(1L)).thenReturn(List.of(group));
        when(messageRepository.findByGroupIdInAndProcessingStatusInAndMessageDateBetween(
                eq(List.of(78L)),
                any(),
                eq(from),
                eq(to),
                any()
        )).thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 20), 2));

        TopicClusterService.TopicExplainResponse response = controller.explainTopics(
                1L,
                "Vibecoder",
                "Claude",
                from.toString(),
                to.toString(),
                null,
                20
        );

        assertEquals("cluster", response.currentProcessingUnit());
        assertEquals(2, response.sourceMessageCount());
        assertEquals(2, response.topicCandidates().size());
        assertEquals(List.of(101L), response.topicCandidates().get(0).sourceMessageIds());
        assertEquals(List.of(102L), response.topicCandidates().get(1).sourceMessageIds());
        assertEquals("candidate", response.topicCandidates().get(0).status());
        assertEquals("candidate", response.topicCandidates().get(1).status());
        assertFalse(response.sourceMessages().get(1).sanitizedText().contains("secret-value"));
    }

    @Test
    void topicCandidatesGroupRelatedMessagesAndExposeSafeEvidence() {
        PipelineService pipelineService = mock(PipelineService.class);
        QueueProcessor queueProcessor = mock(QueueProcessor.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        GuideRepository guideRepository = mock(GuideRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        PipelineTraceRepository pipelineTraceRepository = mock(PipelineTraceRepository.class);
        PipelineController controller = newController(
                pipelineService,
                queueProcessor,
                messageRepository,
                guideRepository,
                groupRepository,
                pipelineTraceRepository
        );

        GroupEntity group = new GroupEntity();
        group.setId(78L);
        group.setTitle("Vibecoder Chat [Public]");
        group.setEnabled(true);

        MessageEntity first = classifiedTopicMessage(
                201L,
                Instant.parse("2026-06-19T12:37:00Z"),
                "Q",
                "server scanner found backdoor risk and defensive server hardening"
        );
        MessageEntity second = classifiedTopicMessage(
                202L,
                Instant.parse("2026-06-19T12:40:00Z"),
                "SecOps",
                "which scanner detects backdoor and server exposure api_key=secret-value"
        );
        second.setReplyToMessageId(first.getTelegramMessageId());
        second.setProblemStatement("Пользователь просит способ диагностировать риск");

        Instant from = Instant.parse("2026-06-19T12:37:00Z");
        Instant to = Instant.parse("2026-06-19T12:45:59Z");
        when(groupRepository.findByOwnerUserIdAndEnabledTrue(1L)).thenReturn(List.of(group));
        when(messageRepository.findByGroupIdInAndProcessingStatusInAndMessageDateBetween(
                eq(List.of(78L)),
                any(),
                eq(from),
                eq(to),
                any()
        )).thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 100), 2));

        TopicClusterService.TopicCandidatesResponse response = controller.getTopicCandidates(
                1L,
                "Vibecoder",
                "Claude",
                from.toString(),
                to.toString(),
                20,
                true,
                50,
                null,
                null,
                null,
                70,
                100
        );

        assertEquals("topic_cluster", response.processingUnit());
        assertEquals(2, response.sourceMessageCount());
        assertEquals(1, response.topicCandidateCount());
        TopicClusterService.TopicCandidateItem candidate = response.topicCandidates().get(0);
        assertEquals(List.of(201L, 202L), candidate.sourceMessageIds());
        assertEquals("risk_abuse_cyber_safety", candidate.riskSafetyCategory());
        assertTrue(candidate.guideAngles().contains("defensive checklist"));
        assertEquals(2, candidate.sourceMessages().size());
        TopicClusterService.TopicExplainMessage secondEvidence = candidate.sourceMessages().stream()
                .filter(message -> message.messageId().equals(202L))
                .findFirst()
                .orElseThrow();
        assertEquals("Пользователь просит способ диагностировать риск", secondEvidence.problemStatement());
        assertFalse(secondEvidence.sanitizedText().contains("secret-value"));
    }

    private PipelineController newController(
            PipelineService pipelineService,
            QueueProcessor queueProcessor,
            MessageRepository messageRepository,
            GuideRepository guideRepository,
            GroupRepository groupRepository,
            PipelineTraceRepository pipelineTraceRepository
    ) {
        TopicClusterService topicClusterService = new TopicClusterService(
                messageRepository,
                groupRepository,
                mock(TopicDiscussionClusterRepository.class),
                mock(TopicClusterMessageRepository.class),
                mock(TopicClusterGuideCandidateRepository.class),
                new ObjectMapper()
        );
        return new PipelineController(
                pipelineService,
                topicClusterService,
                queueProcessor,
                messageRepository,
                guideRepository,
                groupRepository,
                pipelineTraceRepository
        );
    }

    private MessageEntity classifiedTopicMessage(Long id, Instant date, String sender, String text) {
        MessageEntity message = new MessageEntity();
        message.setId(id);
        message.setTelegramMessageId(id * 100);
        message.setGroupId(78L);
        message.setTopicId(42L);
        message.setTopicName("Claude Code");
        message.setSenderName(sender);
        message.setText(text);
        message.setProcessingStatus("CLASSIFIED");
        message.setMessageDate(date);
        message.setClusterCandidate(true);
        message.setProblemSignalScore(70);
        message.setGuidePotentialScore(65);
        message.setSpamScore(0);
        message.setClassifierScore(0.82);
        message.setClassifierReason("topic-level evidence");
        return message;
    }
}
