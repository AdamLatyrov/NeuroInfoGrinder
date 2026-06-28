package com.larbcorp.neuroinfogrinder.domain.findings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TopicClusterGuideCandidateEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TopicDiscussionClusterEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TopicClusterGuideCandidateRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TopicClusterMessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TopicDiscussionClusterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TopicClusterServiceTest {

    private MessageRepository messageRepository;
    private GroupRepository groupRepository;
    private TopicDiscussionClusterRepository clusterRepository;
    private TopicClusterMessageRepository clusterMessageRepository;
    private TopicClusterGuideCandidateRepository guideCandidateRepository;
    private TopicClusterService topicClusterService;

    @BeforeEach
    void setUp() {
        messageRepository = mock(MessageRepository.class);
        groupRepository = mock(GroupRepository.class);
        clusterRepository = mock(TopicDiscussionClusterRepository.class);
        clusterMessageRepository = mock(TopicClusterMessageRepository.class);
        guideCandidateRepository = mock(TopicClusterGuideCandidateRepository.class);
        topicClusterService = new TopicClusterService(
            messageRepository,
            groupRepository,
            clusterRepository,
            clusterMessageRepository,
            guideCandidateRepository,
            new ObjectMapper()
        );
    }

    @Test
    void topicCandidatesGroupEightRelatedMessagesIntoOneCluster() {
        GroupEntity group = group();
        Instant start = Instant.parse("2026-06-19T12:00:00Z");
        List<MessageEntity> messages = List.of(
            message(1L, start, "Как протестировать GLM-5.2 от Zhipu сейчас?", true),
            message(2L, start.plusSeconds(60), "GLM-5.2 доступен через zhipu playground и api trial", false),
            message(3L, start.plusSeconds(120), "У кого получилось проверить zhipu glm 5.2 без китайской карты?", true),
            message(4L, start.plusSeconds(180), "Нужен короткий чеклист тестирования модели GLM-5.2", true),
            message(5L, start.plusSeconds(240), "Сравните ответы glm 5.2 на код и длинный контекст", false),
            message(6L, start.plusSeconds(300), "Zhipu API иногда отдаёт 429, но retry помогает", false),
            message(7L, start.plusSeconds(360), "Там есть лимиты trial, лучше фиксировать промпты", false),
            message(8L, start.plusSeconds(420), "Итого нужен гайд как тестировать GLM-5.2", false)
        );
        messages.get(0).setWillingnessToPayScore(65);
        messages.get(0).setProblemStatement("Пользователь ищет практичный способ протестировать GLM-5.2");
        messages.get(0).setSolutionHint("Собрать чеклист тестирования GLM-5.2 через playground и API");
        stubCandidateQuery(group, start, start.plusSeconds(600), messages);

        TopicClusterService.TopicCandidatesResponse response = topicClusterService.getTopicCandidates(
            "Vibecoder",
            "Claude",
            start.toString(),
            start.plusSeconds(600).toString(),
            20,
            false,
            null,
            null,
            null,
            null,
            70,
            100
        );

        assertThat(response.topicCandidateCount()).isEqualTo(1);
        assertThat(response.topicCandidates().get(0).sourceMessageIds()).hasSize(8);
        assertThat(response.topicCandidates().get(0).bestEvidenceMessageIds())
            .contains(1L, 3L, 4L);
        TopicClusterService.TopicExplainMessage enriched = response.topicCandidates().get(0).sourceMessages().stream()
            .filter(message -> message.messageId().equals(1L))
            .findFirst()
            .orElseThrow();
        assertThat(enriched.willingnessToPayScore()).isEqualTo(65);
        assertThat(enriched.problemStatement()).isEqualTo("Пользователь ищет практичный способ протестировать GLM-5.2");
        assertThat(enriched.solutionHint()).isEqualTo("Собрать чеклист тестирования GLM-5.2 через playground и API");
    }

    @Test
    void topicCandidatesSplitTwoDifferentThemesInSameWindow() {
        GroupEntity group = group();
        Instant start = Instant.parse("2026-06-19T12:00:00Z");
        List<MessageEntity> messages = List.of(
            message(11L, start, "Как протестировать GLM-5.2 от Zhipu сейчас?", true),
            message(12L, start.plusSeconds(60), "Нужен чеклист для zhipu glm api trial", true),
            message(13L, start.plusSeconds(120), "Account resale и backdoor-сервисы выглядят рискованно", true),
            message(14L, start.plusSeconds(180), "Нужна модерация серого account resale потока", true)
        );
        stubCandidateQuery(group, start, start.plusSeconds(600), messages);

        TopicClusterService.TopicCandidatesResponse response = topicClusterService.getTopicCandidates(
            "Vibecoder",
            "Claude",
            start.toString(),
            start.plusSeconds(600).toString(),
            20,
            false,
            null,
            null,
            null,
            null,
            70,
            100
        );

        assertThat(response.topicCandidateCount()).isEqualTo(2);
        assertThat(response.topicCandidates())
            .extracting(TopicClusterService.TopicCandidateItem::sourceMessageIds)
            .containsExactly(List.of(11L, 12L), List.of(13L, 14L));
    }

    @Test
    void clusterClassificationCreatesTwoGuideCandidatesFromOneCluster() {
        TopicDiscussionClusterEntity cluster = new TopicDiscussionClusterEntity();
        cluster.setId(700L);
        cluster.setGroupId(78L);
        cluster.setStartAt(Instant.parse("2026-06-19T12:00:00Z"));
        cluster.setEndAt(Instant.parse("2026-06-19T12:05:00Z"));
        cluster.setStatus("OPEN");
        cluster.setClassificationStatus("PENDING");
        cluster.setGuideGenerationStatus("NONE");
        cluster.setSemanticHash("cluster-hash");
        when(clusterRepository.save(any(TopicDiscussionClusterEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(guideCandidateRepository.findByClusterId(700L)).thenReturn(List.of());
        when(guideCandidateRepository.save(any(TopicClusterGuideCandidateEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ClassifierResult result = new ClassifierResult(
            0.92,
            true,
            List.of(ClassificationLabels.DEMAND_SIGNAL),
            true,
            List.of(1L, 2L),
            "Cluster supports multiple guide angles",
            80,
            70,
            0,
            90,
            0,
            65,
            0,
            "GLM-5.2 testing discussion",
            "Users need a way to test GLM-5.2",
            "Create checklist and comparison workflow",
            List.of("GLM-5.2", "Zhipu"),
            List.of(),
            List.of(),
            List.of("model-testing", "api-access")
        );

        TopicClusterService.ClusterClassificationResult classification =
            topicClusterService.applyClusterClassification(
                cluster,
                result,
                42L,
                List.of(message(1L, cluster.getStartAt(), "GLM testing", true))
            );

        assertThat(classification.guideCandidates()).hasSize(2);
        assertThat(classification.guideCandidates())
            .extracting(TopicClusterGuideCandidateEntity::getGuideAngle)
            .containsExactly("Guide angle: model-testing", "Guide angle: api-access");
        assertThat(classification.cluster().getGuideGenerationStatus()).isEqualTo("CANDIDATE");
    }

    @Test
    void upsertFromMessageFindsMatchingClusterOnlyInsideSameOwner() {
        Instant start = Instant.parse("2026-06-19T12:00:00Z");
        MessageEntity anchor = message(21L, start, "Same text from owner A", true);
        anchor.setOwnerUserId(1L);
        when(clusterRepository.findByOwnerUserIdAndGroupIdAndTelegramTopicIdAndStatusInAndEndAtAfterOrderByEndAtDesc(
            eq(1L),
            eq(78L),
            eq(42L),
            any(),
            any()
        )).thenReturn(List.of());
        when(clusterRepository.save(any(TopicDiscussionClusterEntity.class)))
            .thenAnswer(invocation -> {
                TopicDiscussionClusterEntity saved = invocation.getArgument(0);
                saved.setId(700L);
                return saved;
            });
        when(clusterMessageRepository.findByMessageId(21L)).thenReturn(java.util.Optional.empty());

        TopicClusterService.ClusterUpdateResult result = topicClusterService.upsertFromMessage(
            anchor,
            List.of(anchor),
            classifierResult(),
            null
        );

        assertThat(result.created()).isTrue();
        assertThat(result.cluster().getOwnerUserId()).isEqualTo(1L);
        verify(clusterRepository).findByOwnerUserIdAndGroupIdAndTelegramTopicIdAndStatusInAndEndAtAfterOrderByEndAtDesc(
            eq(1L),
            eq(78L),
            eq(42L),
            any(),
            any()
        );
    }

    private void stubCandidateQuery(GroupEntity group, Instant from, Instant to, List<MessageEntity> messages) {
        when(groupRepository.findByEnabledTrue()).thenReturn(List.of(group));
        when(messageRepository.findByGroupIdInAndProcessingStatusInAndMessageDateBetween(
            eq(List.of(group.getId())),
            any(),
            eq(from),
            eq(to),
            any()
        )).thenReturn(new PageImpl<>(messages, PageRequest.of(0, 100), messages.size()));
    }

    private GroupEntity group() {
        GroupEntity group = new GroupEntity();
        group.setId(78L);
        group.setTitle("Vibecoder Chat [Public]");
        group.setEnabled(true);
        return group;
    }

    private MessageEntity message(Long id, Instant date, String text, boolean highSignal) {
        MessageEntity message = new MessageEntity();
        message.setId(id);
        message.setTelegramMessageId(id * 100);
        message.setGroupId(78L);
        message.setTopicId(42L);
        message.setTopicName("Claude Code");
        message.setSenderName("User " + id);
        message.setText(text);
        message.setProcessingStatus("CLUSTERED");
        message.setMessageDate(date);
        message.setClusterCandidate(highSignal);
        message.setProblemSignalScore(highSignal ? 80 : 45);
        message.setGuidePotentialScore(highSignal ? 75 : 40);
        message.setPainScore(highSignal ? 70 : 20);
        message.setSpamScore(0);
        message.setClassifierScore(highSignal ? 0.88 : 0.55);
        message.setClassifierReason(text);
        return message;
    }

    private ClassifierResult classifierResult() {
        return new ClassifierResult(
            0.9,
            true,
            List.of(ClassificationLabels.DEMAND_SIGNAL),
            true,
            List.of(21L),
            "same-owner cluster only",
            80,
            70,
            0,
            80,
            0,
            60,
            0,
            "Owner-scoped topic",
            "Topic is scoped per owner",
            "Keep owner isolation",
            List.of(),
            List.of(),
            List.of(),
            List.of("owner-isolation")
        );
    }
}
