package com.larbcorp.neuroinfogrinder.domain.findings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.AiProviderEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.ClassifierEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideSourceMessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.PromptEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.SettingsEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TopicClusterGuideCandidateEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TopicDiscussionClusterEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.AiProviderRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.AiUsageLogRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.ClassifierRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuideRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuideSourceMessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.PromptRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.SettingsRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TopicClusterGuideCandidateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineServiceTest {

    private MessageRepository messageRepository;
    private GroupRepository groupRepository;
    private GuideRepository guideRepository;
    private GuideSourceMessageRepository guideSourceMessageRepository;
    private AiProviderRepository aiProviderRepository;
    private AiUsageLogRepository aiUsageLogRepository;
    private ClassifierRepository classifierRepository;
    private PromptRepository promptRepository;
    private SettingsRepository settingsRepository;
    private TopicClusterGuideCandidateRepository topicClusterGuideCandidateRepository;
    private RuleRunner ruleRunner;
    private MessageContextBuilder messageContextBuilder;
    private ClassifierRunner classifierRunner;
    private GuideGenerator guideGenerator;
    private ContentRoutingService contentRoutingService;
    private ContentQualityGate contentQualityGate;
    private MaterialGenerator materialGenerator;
    private GuidePublicationService guidePublicationService;
    private TopicClusterService topicClusterService;
    private ActiveProviderResolver activeProviderResolver;
    private PipelineTraceService pipelineTraceService;

    private PipelineService pipelineService;

    @BeforeEach
    void setUp() {
        messageRepository = mock(MessageRepository.class);
        groupRepository = mock(GroupRepository.class);
        guideRepository = mock(GuideRepository.class);
        guideSourceMessageRepository = mock(GuideSourceMessageRepository.class);
        aiProviderRepository = mock(AiProviderRepository.class);
        aiUsageLogRepository = mock(AiUsageLogRepository.class);
        classifierRepository = mock(ClassifierRepository.class);
        promptRepository = mock(PromptRepository.class);
        settingsRepository = mock(SettingsRepository.class);
        topicClusterGuideCandidateRepository = mock(TopicClusterGuideCandidateRepository.class);
        ruleRunner = mock(RuleRunner.class);
        messageContextBuilder = mock(MessageContextBuilder.class);
        classifierRunner = mock(ClassifierRunner.class);
        guideGenerator = mock(GuideGenerator.class);
        contentRoutingService = mock(ContentRoutingService.class);
        contentQualityGate = mock(ContentQualityGate.class);
        materialGenerator = mock(MaterialGenerator.class);
        guidePublicationService = mock(GuidePublicationService.class);
        topicClusterService = mock(TopicClusterService.class);
        activeProviderResolver = mock(ActiveProviderResolver.class);
        pipelineTraceService = mock(PipelineTraceService.class);

        pipelineService = new PipelineService(
            messageRepository,
            groupRepository,
            guideRepository,
            guideSourceMessageRepository,
            aiProviderRepository,
            aiUsageLogRepository,
            classifierRepository,
            promptRepository,
            settingsRepository,
            topicClusterGuideCandidateRepository,
            new SignalScorer(),
            ruleRunner,
            messageContextBuilder,
            classifierRunner,
            guideGenerator,
            contentRoutingService,
            contentQualityGate,
            materialGenerator,
            new GuideUsefulnessScorer(),
            guidePublicationService,
            topicClusterService,
            activeProviderResolver,
            pipelineTraceService,
            new ObjectMapper()
        );

        ReflectionTestUtils.setField(pipelineService, "signalThreshold", 0.30d);
        ReflectionTestUtils.setField(pipelineService, "localGuideFallback", false);
        ReflectionTestUtils.setField(pipelineService, "classificationContextDedupTtlHours", 24L);
        ReflectionTestUtils.setField(pipelineService, "guidePotentialThreshold", 60);
        ReflectionTestUtils.setField(pipelineService, "clusterProblemSignalThreshold", 50);
        ReflectionTestUtils.setField(pipelineService, "clusterPainThreshold", 60);
        ReflectionTestUtils.setField(pipelineService, "clusterWillingnessToPayThreshold", 50);
        ReflectionTestUtils.setField(pipelineService, "clusterGuidePotentialThreshold", 60);
        ReflectionTestUtils.setField(pipelineService, "clusterTechnicalDepthThreshold", 70);
        ReflectionTestUtils.setField(pipelineService, "spamBlockThreshold", 70);
        when(messageRepository.save(any(MessageEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.findFirstByClassificationContextHashAndUpdatedAtAfterOrderByUpdatedAtDesc(any(), any()))
            .thenReturn(Optional.empty());
        when(guideRepository.findByTopicClusterId(any())).thenReturn(List.of());
        when(guideRepository.findByTopicClusterGuideCandidateId(any())).thenReturn(List.of());
        when(guideRepository.findTop100ByGroupIdAndCreatedAtAfterOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(guideSourceMessageRepository.findByGuideId(any())).thenReturn(List.of());
        when(contentRoutingService.route(any(), any(), any(), any())).thenReturn(defaultDeferredRoutingDecision());
        when(contentQualityGate.evaluate(any(), any(), any(), any()))
            .thenReturn(new ContentQualityGate.GateResult(false, "default test gate block"));
        when(classifierRunner.classify(any(), any(ClassifierEntity.class), any(ModelCallPurpose.class)))
            .thenReturn(new ClassifierResult(0.0, false, "Cluster classifier not stubbed"));
        when(topicClusterService.upsertFromMessage(any(MessageEntity.class), any(), any(), any()))
            .thenAnswer(invocation -> {
                MessageEntity message = invocation.getArgument(0);
                List<MessageEntity> sourceMessages = invocation.getArgument(1);
                TopicDiscussionClusterEntity cluster = new TopicDiscussionClusterEntity();
                cluster.setId(9001L);
                cluster.setGroupId(message.getGroupId());
                cluster.setTelegramTopicId(message.getTopicId());
                cluster.setTopicTitle(message.getTopicName());
                cluster.setStartAt(message.getMessageDate());
                cluster.setEndAt(message.getMessageDate());
                cluster.setStatus("OPEN");
                cluster.setClassificationStatus("PENDING");
                cluster.setGuideGenerationStatus("NONE");
                cluster.setSemanticHash("test-cluster");
                return new TopicClusterService.ClusterUpdateResult(
                    cluster,
                    sourceMessages,
                    true,
                    sourceMessages.size(),
                    "test grouping"
                );
            });
        doAnswer(invocation -> {
                TopicDiscussionClusterEntity cluster = invocation.getArgument(0);
                ClassifierResult classifierResult = invocation.getArgument(1);
                cluster.setClassificationStatus(classifierResult.matched() ? "CLASSIFIED" : "SKIPPED");
                cluster.setStatus(classifierResult.matched() ? "CLASSIFIED" : "OPEN");
                cluster.setSafetyCategory("normal");
                cluster.setGuideGenerationStatus(classifierResult.guideCandidate() ? "CANDIDATE" : "NONE");
                List<TopicClusterGuideCandidateEntity> candidates = classifierResult.guideCandidate()
                    ? List.of(topicGuideCandidate(cluster.getId()))
                    : List.of();
                return new TopicClusterService.ClusterClassificationResult(cluster, candidates);
            })
            .when(topicClusterService)
            .applyClusterClassification(any(), any(), any(), any());
    }

    @Test
    void preservesUsefulAccessDemandAsClassifiedWhenNoActiveClassifiers() {
        MessageEntity message = buildMessage(101L, "подскажите, где сейчас дешевле всего купить чат гпт плюс?");
        GroupEntity group = buildGroup(7L);
        SettingsEntity settings = buildSettings();
        MessageContextBundle contextBundle = new MessageContextBundle(message, List.of(message), "hash-1", 5, List.of("question", "provider"), 64);

        when(messageRepository.findById(101L)).thenReturn(Optional.of(message));
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(settingsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        when(ruleRunner.evaluate(message)).thenReturn(new RuleResult(true, "pass", List.of()));
        when(messageContextBuilder.buildContext(message)).thenReturn(contextBundle);
        when(messageContextBuilder.isAnchorCandidate(message)).thenReturn(true);
        when(classifierRepository.findByStatusOrderByClassifierOrderAsc("ACTIVE")).thenReturn(List.of());

        var result = pipelineService.processMessage(101L);

        assertThat(result).isNull();
        assertThat(message.getProcessingStatus()).isEqualTo("CLUSTERED");
        assertThat(message.getClassifierScore()).isGreaterThanOrEqualTo(0.75d);
        assertThat(message.getClassifierReason()).contains("No active classifiers found");
        assertThat(message.getClassifierResultJson()).contains("DEMAND_SIGNAL");
        assertThat(message.getSignalBreakdown()).contains("matchedSignals");
        verify(guideRepository, never()).save(any());
    }

    @Test
    void keepsMatchedNonGuideCandidateAsClassifiedWithoutGuideGeneration() {
        MessageEntity message = buildMessage(102L, "где покупать гифты клода по норм цене");
        GroupEntity group = buildGroup(7L);
        SettingsEntity settings = buildSettings();
        ClassifierEntity classifier = activeClassifier();
        MessageContextBundle contextBundle = new MessageContextBundle(message, List.of(message), "hash-2", 5, List.of("question"), 48);
        ClassifierResult classifierResult = new ClassifierResult(
            0.67,
            true,
            List.of(ClassificationLabels.DEMAND_SIGNAL),
            false,
            List.of(message.getId()),
            "Есть полезный спрос, но без полноценного how-to"
        );

        when(messageRepository.findById(102L)).thenReturn(Optional.of(message));
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(settingsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        when(ruleRunner.evaluate(message)).thenReturn(new RuleResult(true, "pass", List.of()));
        when(messageContextBuilder.buildContext(message)).thenReturn(contextBundle);
        when(messageContextBuilder.isAnchorCandidate(message)).thenReturn(true);
        when(classifierRepository.findByStatusOrderByClassifierOrderAsc("ACTIVE")).thenReturn(List.of(classifier));
        when(classifierRunner.classify(List.of(message), classifier, ModelCallPurpose.CLUSTER_CLASSIFICATION))
            .thenReturn(classifierResult);

        var result = pipelineService.processMessage(102L);

        assertThat(result).isNull();
        assertThat(message.getProcessingStatus()).isEqualTo("CLUSTERED");
        assertThat(message.getClassifierResultJson()).contains("\"guideCandidate\":false");
        verify(guideGenerator, never()).generate(any(), any(), any(), any());
    }

    @Test
    void stillSkipsShortNoiseWithoutUsefulSignals() {
        MessageEntity message = buildMessage(103L, "ок");
        GroupEntity group = buildGroup(7L);
        SettingsEntity settings = buildSettings();
        settings.setFilterMinMessageLength(1);

        when(messageRepository.findById(103L)).thenReturn(Optional.of(message));
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(settingsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        when(ruleRunner.evaluate(message)).thenReturn(new RuleResult(true, "pass", List.of()));

        var result = pipelineService.processMessage(103L);

        assertThat(result).isNull();
        assertThat(message.getProcessingStatus()).isEqualTo("SKIPPED");
    }

    @Test
    void keepsContextOnlyMessageSkipped() {
        MessageEntity message = buildMessage(104L, "гигакодер спит");
        message.setReplyToMessageId(999L);
        message.setTopicId(55L);
        GroupEntity group = buildGroup(7L);
        SettingsEntity settings = buildSettings();
        settings.setFilterMinMessageLength(1);

        when(messageRepository.findById(104L)).thenReturn(Optional.of(message));
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(settingsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        when(ruleRunner.evaluate(message)).thenReturn(new RuleResult(true, "pass", List.of()));

        var result = pipelineService.processMessage(104L);

        assertThat(result).isNull();
        assertThat(message.getProcessingStatus()).isEqualTo("SKIPPED");
    }

    @Test
    void skipsBareProviderMentionEvenWhenClassifierMatched() {
        MessageEntity message = buildMessage(105L, "это опенроутер?");
        GroupEntity group = buildGroup(7L);
        SettingsEntity settings = buildSettings();
        settings.setFilterMinMessageLength(1);
        ClassifierEntity classifier = activeClassifier();
        MessageContextBundle contextBundle = new MessageContextBundle(message, List.of(message), "hash-3", 4, List.of("provider"), 24);
        ClassifierResult classifierResult = new ClassifierResult(
            0.75,
            true,
            List.of(ClassificationLabels.AI_TOOL_OR_PROVIDER),
            false,
            List.of(message.getId()),
            "Short provider mention only"
        );

        when(messageRepository.findById(105L)).thenReturn(Optional.of(message));
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(settingsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        when(ruleRunner.evaluate(message)).thenReturn(new RuleResult(true, "pass", List.of()));
        when(messageContextBuilder.buildContext(message)).thenReturn(contextBundle);
        when(messageContextBuilder.isAnchorCandidate(message)).thenReturn(true);
        when(classifierRepository.findByStatusOrderByClassifierOrderAsc("ACTIVE")).thenReturn(List.of(classifier));
        when(classifierRunner.classify(List.of(message), classifier, ModelCallPurpose.CLUSTER_CLASSIFICATION))
            .thenReturn(classifierResult);

        var result = pipelineService.processMessage(105L);

        assertThat(result).isNull();
        assertThat(message.getProcessingStatus()).isEqualTo("SKIPPED");
        assertThat(message.getClassifierReason()).contains("Bare provider mention");
    }

    @Test
    void respectsNonGuideCandidateEvenForTechnicalHowTo() {
        MessageEntity message = buildMessage(107L, "Cloudflare Worker proxy setup: route /api to VPS, add env token, then deploy from GitHub repo.");
        GroupEntity group = buildGroup(7L);
        SettingsEntity settings = buildSettings();
        settings.setFilterMinMessageLength(1);
        ClassifierEntity classifier = activeClassifier();
        MessageContextBundle contextBundle = new MessageContextBundle(message, List.of(message), "hash-5", 5, List.of("technical"), 96);
        ClassifierResult classifierResult = new ClassifierResult(
            0.82,
            true,
            List.of(ClassificationLabels.AI_TOOL_OR_PROVIDER),
            false,
            List.of(message.getId()),
            "High-confidence technical setup, but model did not mark guide_candidate"
        );
        when(messageRepository.findById(107L)).thenReturn(Optional.of(message));
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(settingsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        when(ruleRunner.evaluate(message)).thenReturn(new RuleResult(true, "pass", List.of()));
        when(messageContextBuilder.buildContext(message)).thenReturn(contextBundle);
        when(messageContextBuilder.isAnchorCandidate(message)).thenReturn(true);
        when(classifierRepository.findByStatusOrderByClassifierOrderAsc("ACTIVE")).thenReturn(List.of(classifier));
        when(classifierRunner.classify(List.of(message), classifier, ModelCallPurpose.CLUSTER_CLASSIFICATION))
            .thenReturn(classifierResult);

        GuideEntity result = pipelineService.processMessage(107L);

        assertThat(result).isNull();
        assertThat(message.getProcessingStatus()).isEqualTo("CLUSTERED");
        assertThat(message.getClassifierResultJson()).contains("\"guideCandidate\":false");
        verify(guideGenerator, never()).generate(any(), any(), any(), any());
    }

    @Test
    void nonGuideWithHighPainBecomesClusterCandidateWithoutGuide() {
        MessageEntity message = buildMessage(108L, "после обновления api постоянно 429, рабочий workaround есть?");
        GroupEntity group = buildGroup(7L);
        SettingsEntity settings = buildSettings();
        settings.setFilterMinMessageLength(1);
        ClassifierEntity classifier = activeClassifier();
        MessageContextBundle contextBundle = new MessageContextBundle(message, List.of(message), "hash-6", 5, List.of("pain"), 96);
        ClassifierResult classifierResult = new ClassifierResult(
            0.82,
            true,
            List.of(ClassificationLabels.BUG_OR_LIMITATION, ClassificationLabels.OPPORTUNITY),
            false,
            List.of(message.getId()),
            "Высокая боль, но нет самостоятельного гайда",
            45,
            88,
            0,
            0,
            70,
            60,
            0,
            "API 429 после обновления",
            "Пользователь уперся в ошибку 429",
            "Нужен workaround",
            List.of("api"),
            List.of(),
            List.of("429"),
            List.of("api_access")
        );

        when(messageRepository.findById(108L)).thenReturn(Optional.of(message));
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(settingsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        when(ruleRunner.evaluate(message)).thenReturn(new RuleResult(true, "pass", List.of()));
        when(messageContextBuilder.buildContext(message)).thenReturn(contextBundle);
        when(messageContextBuilder.isAnchorCandidate(message)).thenReturn(true);
        when(classifierRepository.findByStatusOrderByClassifierOrderAsc("ACTIVE")).thenReturn(List.of(classifier));
        when(classifierRunner.classify(List.of(message), classifier, ModelCallPurpose.CLUSTER_CLASSIFICATION))
            .thenReturn(classifierResult);

        GuideEntity result = pipelineService.processMessage(108L);

        assertThat(result).isNull();
        assertThat(message.getProcessingStatus()).isEqualTo("CLUSTERED");
        assertThat(message.getPainScore()).isEqualTo(88);
        assertThat(message.getClusterCandidate()).isTrue();
        assertThat(message.getEmbeddingStatus()).isEqualTo("REQUIRED");
        verify(guideGenerator, never()).generate(any(), any(), any(), any());
    }

    @Test
    void guideCandidateRequiresGuidePotentialThresholdBeforeGeneration() {
        MessageEntity message = buildMessage(109L, "можно сделать гайд по оплате claude, но данных пока мало");
        GroupEntity group = buildGroup(7L);
        SettingsEntity settings = buildSettings();
        settings.setFilterMinMessageLength(1);
        ClassifierEntity classifier = activeClassifier();
        MessageContextBundle contextBundle = new MessageContextBundle(message, List.of(message), "hash-7", 5, List.of("guide"), 96);
        ClassifierResult classifierResult = new ClassifierResult(
            0.82,
            true,
            List.of(ClassificationLabels.PRACTICAL_GUIDE_CANDIDATE, ClassificationLabels.PAYMENT_WORKAROUND),
            true,
            List.of(message.getId()),
            "Guide candidate, but weak guide potential",
            40,
            0,
            55,
            45,
            0,
            0,
            0,
            "Слабый кандидат в гайд",
            null,
            null,
            List.of("claude"),
            List.of(),
            List.of(),
            List.of()
        );

        when(messageRepository.findById(109L)).thenReturn(Optional.of(message));
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(settingsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        when(ruleRunner.evaluate(message)).thenReturn(new RuleResult(true, "pass", List.of()));
        when(messageContextBuilder.buildContext(message)).thenReturn(contextBundle);
        when(messageContextBuilder.isAnchorCandidate(message)).thenReturn(true);
        when(classifierRepository.findByStatusOrderByClassifierOrderAsc("ACTIVE")).thenReturn(List.of(classifier));
        when(classifierRunner.classify(List.of(message), classifier, ModelCallPurpose.CLUSTER_CLASSIFICATION))
            .thenReturn(classifierResult);

        GuideEntity result = pipelineService.processMessage(109L);

        assertThat(result).isNull();
        assertThat(message.getProcessingStatus()).isEqualTo("CLUSTERED");
        assertThat(message.getGuidePotentialScore()).isEqualTo(45);
        assertThat(message.getClusterCandidate()).isTrue();
        verify(guideGenerator, never()).generate(any(), any(), any(), any());
    }

    @Test
    void highSpamScoreBlocksClusterCandidate() {
        MessageEntity message = buildMessage(110L, "скидка на api доступ, пишите в личку");
        GroupEntity group = buildGroup(7L);
        SettingsEntity settings = buildSettings();
        settings.setFilterMinMessageLength(1);
        ClassifierEntity classifier = activeClassifier();
        MessageContextBundle contextBundle = new MessageContextBundle(message, List.of(message), "hash-8", 5, List.of("spam"), 96);
        ClassifierResult classifierResult = new ClassifierResult(
            0.9,
            true,
            List.of(ClassificationLabels.SPAM_OR_AD, ClassificationLabels.VENDOR_OR_SOURCE),
            false,
            List.of(message.getId()),
            "Рекламный оффер",
            80,
            70,
            80,
            0,
            0,
            0,
            95,
            "Рекламное предложение",
            null,
            null,
            List.of("api"),
            List.of(),
            List.of(),
            List.of("spam")
        );

        when(messageRepository.findById(110L)).thenReturn(Optional.of(message));
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(settingsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        when(ruleRunner.evaluate(message)).thenReturn(new RuleResult(true, "pass", List.of()));
        when(messageContextBuilder.buildContext(message)).thenReturn(contextBundle);
        when(messageContextBuilder.isAnchorCandidate(message)).thenReturn(true);
        when(classifierRepository.findByStatusOrderByClassifierOrderAsc("ACTIVE")).thenReturn(List.of(classifier));
        when(classifierRunner.classify(List.of(message), classifier, ModelCallPurpose.CLUSTER_CLASSIFICATION))
            .thenReturn(classifierResult);

        GuideEntity result = pipelineService.processMessage(110L);

        assertThat(result).isNull();
        assertThat(message.getProcessingStatus()).isEqualTo("CLUSTERED");
        assertThat(message.getSpamScore()).isEqualTo(95);
        assertThat(message.getClusterCandidate()).isFalse();
        assertThat(message.getEmbeddingStatus()).isEqualTo("NONE");
        verify(guideGenerator, never()).generate(any(), any(), any(), any());
    }

    @Test
    void reusesExistingClusterGuideBeforeGenerationForOverlappingSourceWindow() {
        MessageEntity message = buildMessage(112L, "Android GPT app: как настроить config и не сломать оплату?");
        GroupEntity group = buildGroup(7L);
        SettingsEntity settings = buildSettings();
        settings.setFilterMinMessageLength(1);
        ClassifierEntity classifier = activeClassifier();
        MessageContextBundle contextBundle = new MessageContextBundle(message, List.of(message), "hash-10", 5, List.of("guide"), 96);
        ClassifierResult classifierResult = new ClassifierResult(
            0.92,
            true,
            List.of(ClassificationLabels.PRACTICAL_GUIDE_CANDIDATE, ClassificationLabels.SOLUTION_MENTION),
            true,
            List.of(message.getId()),
            "Повторное окно относится к уже созданному cluster guide",
            80,
            20,
            0,
            90,
            0,
            70,
            0,
            "Droid config discussion",
            "Нужно настроить Android GPT config",
            "Использовать существующую инструкцию",
            List.of("Android", "GPT"),
            List.of(),
            List.of(),
            List.of("android-config")
        );
        GuideEntity existingGuide = new GuideEntity();
        existingGuide.setId(555L);
        existingGuide.setGroupId(7L);
        existingGuide.setRootMessageId(111L);
        existingGuide.setTopicClusterId(9001L);
        existingGuide.setTopicClusterGuideCandidateId(7100L);
        existingGuide.setStatus("DRAFT");
        existingGuide.setTitle("Existing Android config guide");
        existingGuide.setConfidence(0.88);

        when(messageRepository.findById(112L)).thenReturn(Optional.of(message));
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(settingsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        when(ruleRunner.evaluate(message)).thenReturn(new RuleResult(true, "pass", List.of()));
        when(messageContextBuilder.buildContext(message)).thenReturn(contextBundle);
        when(messageContextBuilder.isAnchorCandidate(message)).thenReturn(true);
        when(classifierRepository.findByStatusOrderByClassifierOrderAsc("ACTIVE")).thenReturn(List.of(classifier));
        when(classifierRunner.classify(List.of(message), classifier, ModelCallPurpose.CLUSTER_CLASSIFICATION))
            .thenReturn(classifierResult);
        doAnswer(invocation -> {
                TopicDiscussionClusterEntity cluster = invocation.getArgument(0);
                cluster.setClassificationStatus("CLASSIFIED");
                cluster.setStatus("CLASSIFIED");
                cluster.setSafetyCategory("normal");
                cluster.setGuideGenerationStatus("GENERATED");
                cluster.setGuideId(555L);
                TopicClusterGuideCandidateEntity candidate = topicGuideCandidate(cluster.getId());
                candidate.setGuideId(555L);
                candidate.setStatus("GENERATED");
                return new TopicClusterService.ClusterClassificationResult(cluster, List.of(candidate));
            })
            .when(topicClusterService)
            .applyClusterClassification(any(), any(), any(), any());
        when(guideRepository.findById(555L)).thenReturn(Optional.of(existingGuide));
        stubSuccessfulGuideGeneration();

        GuideEntity result = pipelineService.processMessage(112L);

        assertThat(result).isSameAs(existingGuide);
        assertThat(message.getProcessingStatus()).isEqualTo("CLUSTERED");
        assertThat(message.getGuideId()).isEqualTo(555L);
        verify(guideGenerator, never()).generate(any(), any(), any(), any());
        verify(guideSourceMessageRepository).save(argThat(source ->
            source.getGuideId().equals(555L) && source.getMessageId().equals(112L)));
        verify(topicClusterService).markGuideMerged(any(), any(), eq(555L));
    }

    @Test
    void reusesExistingGuideAcrossClustersWhenSourceOverlapMatches69And70Case() {
        CrossClusterRun run = arrangeCrossClusterRun(new CrossClusterOptions(
            List.of(729L, 730L, 731L, 732L, 733L, 734L, 735L, 736L, 737L),
            List.of(719L, 729L, 730L, 731L),
            "[]",
            "Practical guide by cluster topic",
            "Practical guide by cluster topic",
            6L,
            179L,
            Instant.parse("2026-06-19T18:36:08Z"),
            "DRAFT"
        ));

        GuideEntity result = pipelineService.processMessage(732L);

        assertThat(result).isSameAs(run.existingGuide());
        assertThat(run.triggerMessage().getGuideId()).isEqualTo(69L);
        assertThat(pipelineService.getProgressLog())
            .anySatisfy(progress -> assertThat(progress.details()).contains("cross-cluster source overlap"));
        verify(guideGenerator, never()).generate(any(), any(), any(), any());
        verify(topicClusterService).markGuideMerged(run.cluster(), run.candidate(), 69L);
    }

    @Test
    void sameSourceOverlapWithDifferentContentTypeCreatesSeparateMaterial() {
        CrossClusterRun run = arrangeCrossClusterRun(new CrossClusterOptions(
            List.of(729L, 730L, 731L, 732L),
            List.of(719L, 729L, 730L, 731L),
            "[]",
            "Codex release update",
            "Practical guide by cluster topic",
            6L,
            179L,
            Instant.parse("2026-06-19T18:36:08Z"),
            "DRAFT"
        ));
        run.existingGuide().setContentType("GUIDE");
        ContentRoutingDecision newsDecision = defaultMaterialRoutingDecision(ContentType.NEWS);
        when(contentRoutingService.route(any(), any(), any(), any())).thenReturn(newsDecision);
        when(contentQualityGate.evaluate(any(), any(), any(), any()))
            .thenReturn(new ContentQualityGate.GateResult(true, "quality gate passed"));
        when(materialGenerator.generate(eq(newsDecision), any())).thenReturn(new GuideContent(
            "Codex release news",
            "Codex release news content",
            "# Codex release news",
            0.76,
            List.of("news"),
            null,
            null
        ));

        GuideEntity result = pipelineService.processMessage(732L);

        assertThat(result).isNotSameAs(run.existingGuide());
        assertThat(result.getId()).isEqualTo(7000L);
        assertThat(result.getContentType()).isEqualTo("NEWS");
        assertThat(run.triggerMessage().getGuideId()).isEqualTo(7000L);
        verify(materialGenerator).generate(eq(newsDecision), any());
        verify(guideGenerator, never()).generate(any(), any(), any(), any());
        verify(topicClusterService, never()).markGuideMerged(any(), any(), eq(69L));
    }

    @Test
    void doesNotReuseCrossClusterGuideWhenOnlyOneSourceMessageOverlaps() {
        CrossClusterRun run = arrangeCrossClusterRun(new CrossClusterOptions(
            List.of(730L, 732L, 733L, 734L, 735L, 736L, 737L),
            List.of(719L, 729L, 730L),
            "[]",
            "Practical guide by cluster topic",
            "Practical guide by cluster topic",
            6L,
            179L,
            Instant.parse("2026-06-19T18:36:08Z"),
            "DRAFT"
        ));

        GuideEntity result = pipelineService.processMessage(732L);

        assertThat(result).isNotSameAs(run.existingGuide());
        assertThat(result.getId()).isEqualTo(7000L);
        verify(guideGenerator).generate(any(), any(), any(), eq(732L));
        verify(topicClusterService, never()).markGuideMerged(any(), any(), eq(69L));
    }

    @Test
    void doesNotReuseCrossClusterGuideFromDifferentGroup() {
        CrossClusterRun run = arrangeCrossClusterRun(new CrossClusterOptions(
            List.of(729L, 730L, 731L, 732L),
            List.of(719L, 729L, 730L, 731L),
            "[]",
            "Practical guide by cluster topic",
            "Practical guide by cluster topic",
            99L,
            179L,
            Instant.parse("2026-06-19T18:36:08Z"),
            "DRAFT"
        ));

        GuideEntity result = pipelineService.processMessage(732L);

        assertThat(result).isNotSameAs(run.existingGuide());
        assertThat(result.getId()).isEqualTo(7000L);
        verify(guideGenerator).generate(any(), any(), any(), eq(732L));
        verify(topicClusterService, never()).markGuideMerged(any(), any(), eq(69L));
    }

    @Test
    void doesNotReuseCrossClusterGuideFromDifferentOwner() {
        CrossClusterRun run = arrangeCrossClusterRun(new CrossClusterOptions(
            List.of(729L, 730L, 731L, 732L),
            List.of(719L, 729L, 730L, 731L),
            "[]",
            "Practical guide by cluster topic",
            "Practical guide by cluster topic",
            6L,
            179L,
            Instant.parse("2026-06-19T18:36:08Z"),
            "DRAFT"
        ));
        run.triggerMessage().setOwnerUserId(1L);
        run.cluster().setOwnerUserId(1L);
        run.existingGuide().setOwnerUserId(2L);

        GuideEntity result = pipelineService.processMessage(732L);

        assertThat(result).isNotSameAs(run.existingGuide());
        assertThat(result.getId()).isEqualTo(7000L);
        verify(guideGenerator).generate(any(), any(), any(), eq(732L));
        verify(topicClusterService, never()).markGuideMerged(any(), any(), eq(69L));
    }

    @Test
    void doesNotReuseCrossClusterGuideWhenSourceWindowIsFarAway() {
        CrossClusterRun run = arrangeCrossClusterRun(new CrossClusterOptions(
            List.of(729L, 730L, 731L, 732L),
            List.of(801L, 802L, 803L, 804L),
            "[]",
            "Practical guide by cluster topic",
            "Practical guide by cluster topic",
            6L,
            179L,
            Instant.parse("2026-06-19T12:00:00Z"),
            "DRAFT"
        ));

        GuideEntity result = pipelineService.processMessage(732L);

        assertThat(result).isNotSameAs(run.existingGuide());
        assertThat(result.getId()).isEqualTo(7000L);
        verify(guideGenerator).generate(any(), any(), any(), eq(732L));
        verify(topicClusterService, never()).markGuideMerged(any(), any(), eq(69L));
    }

    @Test
    void doesNotReuseCrossClusterGuideWhenSpecificAnglesAreDifferent() {
        CrossClusterRun run = arrangeCrossClusterRun(new CrossClusterOptions(
            List.of(729L, 730L, 731L, 732L),
            List.of(719L, 729L, 730L, 731L),
            "[]",
            "payment access workaround",
            "android config setup",
            6L,
            179L,
            Instant.parse("2026-06-19T18:36:08Z"),
            "DRAFT"
        ));

        GuideEntity result = pipelineService.processMessage(732L);

        assertThat(result).isNotSameAs(run.existingGuide());
        assertThat(result.getId()).isEqualTo(7000L);
        verify(guideGenerator).generate(any(), any(), any(), eq(732L));
        verify(topicClusterService, never()).markGuideMerged(any(), any(), eq(69L));
    }

    @Test
    void doesNotReuseFailedExistingGuideEvenWithSourceOverlap() {
        CrossClusterRun run = arrangeCrossClusterRun(new CrossClusterOptions(
            List.of(729L, 730L, 731L, 732L),
            List.of(719L, 729L, 730L, 731L),
            "[]",
            "Practical guide by cluster topic",
            "Practical guide by cluster topic",
            6L,
            179L,
            Instant.parse("2026-06-19T18:36:08Z"),
            "FAILED"
        ));

        GuideEntity result = pipelineService.processMessage(732L);

        assertThat(result).isNotSameAs(run.existingGuide());
        assertThat(result.getId()).isEqualTo(7000L);
        verify(guideGenerator).generate(any(), any(), any(), eq(732L));
        verify(topicClusterService, never()).markGuideMerged(any(), any(), eq(69L));
    }

    @Test
    void reusesExistingGuideWhenOverlapExistsOnlyInExistingCandidateJson() {
        CrossClusterRun run = arrangeCrossClusterRun(new CrossClusterOptions(
            List.of(729L, 730L, 731L, 732L, 733L),
            List.of(),
            "[719,729,730,731]",
            "Practical guide by cluster topic",
            "Practical guide by cluster topic",
            6L,
            179L,
            Instant.parse("2026-06-19T18:36:08Z"),
            "DRAFT"
        ));

        GuideEntity result = pipelineService.processMessage(732L);

        assertThat(result).isSameAs(run.existingGuide());
        verify(guideGenerator, never()).generate(any(), any(), any(), any());
        verify(topicClusterService).markGuideMerged(run.cluster(), run.candidate(), 69L);
    }

    @Test
    void technicalHowToCreatesSignalCandidateWithoutGuide() {
        MessageEntity message = buildMessage(111L, "Docker setup: добавь API endpoint, положи TOKEN в env и проверь curl /health.");
        GroupEntity group = buildGroup(7L);
        SettingsEntity settings = buildSettings();
        settings.setFilterMinMessageLength(1);
        ClassifierEntity classifier = activeClassifier();
        MessageContextBundle contextBundle = new MessageContextBundle(message, List.of(message), "hash-9", 5, List.of("technical"), 96);
        ClassifierResult classifierResult = new ClassifierResult(
            0.78,
            true,
            List.of(ClassificationLabels.SOLUTION_MENTION, ClassificationLabels.AI_TOOL_OR_PROVIDER),
            false,
            List.of(message.getId()),
            "Технический how-to полезен как сигнал, но не самостоятельный гайд",
            35,
            0,
            0,
            0,
            0,
            82,
            0,
            "Настройка API endpoint через Docker",
            null,
            "Docker env и curl healthcheck",
            List.of("docker", "api"),
            List.of(),
            List.of(),
            List.of("technical_howto")
        );

        when(messageRepository.findById(111L)).thenReturn(Optional.of(message));
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(settingsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        when(ruleRunner.evaluate(message)).thenReturn(new RuleResult(true, "pass", List.of()));
        when(messageContextBuilder.buildContext(message)).thenReturn(contextBundle);
        when(messageContextBuilder.isAnchorCandidate(message)).thenReturn(true);
        when(classifierRepository.findByStatusOrderByClassifierOrderAsc("ACTIVE")).thenReturn(List.of(classifier));
        when(classifierRunner.classify(List.of(message), classifier, ModelCallPurpose.CLUSTER_CLASSIFICATION))
            .thenReturn(classifierResult);

        GuideEntity result = pipelineService.processMessage(111L);

        assertThat(result).isNull();
        assertThat(message.getProcessingStatus()).isEqualTo("CLUSTERED");
        assertThat(message.getTechnicalDepthScore()).isEqualTo(82);
        assertThat(message.getClusterCandidate()).isTrue();
        assertThat(message.getEmbeddingStatus()).isEqualTo("REQUIRED");
        verify(guideGenerator, never()).generate(any(), any(), any(), any());
    }

    @Test
    void clusterWithMaterialCandidateBeforeGuideCandidateGeneratesGuideCandidate() {
        ReflectionTestUtils.setField(pipelineService, "signalThreshold", 0.0d);
        MessageEntity trigger = buildMessage(120L, "Нашёл RuFlow для голосового ввода кода и хочу собрать Windows workflow.");
        MessageEntity followUp = buildMessage(121L, "Нужно описать установку, проверку микрофона, запуск и fallback на whispertocode.");
        followUp.setGroupId(trigger.getGroupId());
        followUp.setMessageDate(trigger.getMessageDate().plusSeconds(30));
        List<MessageEntity> clusterMessages = List.of(trigger, followUp);
        GroupEntity group = buildGroup(7L);
        SettingsEntity settings = buildSettings();
        settings.setFilterMinMessageLength(1);
        ClassifierEntity classifier = activeClassifier();
        MessageContextBundle contextBundle = new MessageContextBundle(
            trigger,
            clusterMessages,
            "hash-guide-after-material",
            5,
            List.of("workflow", "voice-input"),
            120
        );
        ClassifierResult classifierResult = new ClassifierResult(
            0.84,
            true,
            List.of(ClassificationLabels.PRACTICAL_GUIDE_CANDIDATE, ClassificationLabels.WORKFLOW_LIFEHACK),
            true,
            clusterMessages.stream().map(MessageEntity::getId).toList(),
            "Cluster contains an actionable workflow candidate.",
            70,
            20,
            0,
            86,
            0,
            75,
            0,
            "Voice-to-code workflow on Windows",
            "Codex API lacks voice mode.",
            "Install and verify a local voice input workflow.",
            List.of("RuFlow", "whispertocode"),
            List.of(),
            List.of(),
            List.of("voice_input")
        );
        TopicDiscussionClusterEntity cluster = topicCluster(
            44L,
            7L,
            null,
            trigger.getMessageDate(),
            followUp.getMessageDate()
        );
        TopicClusterGuideCandidateEntity materialCandidate = topicGuideCandidate(
            7201L,
            cluster.getId(),
            "Short useful note",
            sourceIdsJson(List.of(120L, 121L))
        );
        TopicClusterGuideCandidateEntity guideCandidate = topicGuideCandidate(
            7202L,
            cluster.getId(),
            "Voice input setup workflow",
            sourceIdsJson(List.of(120L, 121L))
        );
        AiProviderEntity provider = activeProvider();
        PromptEntity prompt = guidePrompt();

        when(messageRepository.findById(120L)).thenReturn(Optional.of(trigger));
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(settingsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        when(ruleRunner.evaluate(trigger)).thenReturn(new RuleResult(true, "pass", List.of()));
        when(messageContextBuilder.buildContext(trigger)).thenReturn(contextBundle);
        when(messageContextBuilder.isAnchorCandidate(trigger)).thenReturn(true);
        when(classifierRepository.findByStatusOrderByClassifierOrderAsc("ACTIVE")).thenReturn(List.of(classifier));
        when(classifierRunner.classify(eq(clusterMessages), eq(classifier), eq(ModelCallPurpose.CLUSTER_CLASSIFICATION)))
            .thenReturn(classifierResult);
        when(topicClusterService.upsertFromMessage(eq(trigger), any(), any(), any()))
            .thenReturn(new TopicClusterService.ClusterUpdateResult(
                cluster,
                clusterMessages,
                true,
                clusterMessages.size(),
                "test cluster"
            ));
        doAnswer(invocation -> new TopicClusterService.ClusterClassificationResult(
                cluster,
                List.of(materialCandidate, guideCandidate)
            ))
            .when(topicClusterService)
            .applyClusterClassification(eq(cluster), any(), any(), eq(clusterMessages));
        when(contentRoutingService.route(eq(cluster), eq(materialCandidate), eq(clusterMessages), eq(classifierResult)))
            .thenReturn(defaultMaterialRoutingDecision(ContentType.USEFUL_INFO));
        when(contentRoutingService.route(eq(cluster), eq(guideCandidate), eq(clusterMessages), eq(classifierResult)))
            .thenReturn(defaultGuideRoutingDecision());
        when(contentQualityGate.evaluate(any(), eq(cluster), any(), eq(clusterMessages)))
            .thenReturn(new ContentQualityGate.GateResult(true, "quality gate passed"));
        when(materialGenerator.generate(any(), any())).thenReturn(new GuideContent(
            "Useful note",
            "Useful note content",
            "# Useful note",
            0.80,
            List.of("voice-input"),
            null,
            "{}"
        ));
        when(activeProviderResolver.resolve(ModelCallPurpose.GUIDE_GENERATION))
            .thenReturn(Optional.of(new ActiveProviderResolver.ResolvedProvider(
                provider,
                ModelCallPurpose.GUIDE_GENERATION,
                "test"
            )));
        when(promptRepository.findByStatus("ACTIVE")).thenReturn(List.of(prompt));
        when(promptRepository.findByType("GENERATION")).thenReturn(List.of(prompt));
        when(aiProviderRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
        when(guideGenerator.generate(eq(clusterMessages), eq(classifierResult), eq(prompt.getId()), eq(trigger.getId())))
            .thenReturn(new GuideContent(
                "Voice input setup workflow",
                "Generated guide content",
                "# Voice input setup workflow",
                0.88,
                List.of("voice-input"),
                null,
                "{}",
                provider.getId(),
                provider.getModel()
            ));
        when(guideRepository.save(any(GuideEntity.class))).thenAnswer(invocation -> {
            GuideEntity guide = invocation.getArgument(0);
            guide.setId(7000L);
            return guide;
        });

        GuideEntity result = pipelineService.processMessage(120L);

        assertThat(result.getContentType()).isEqualTo("GUIDE");
        verify(guideGenerator).generate(eq(clusterMessages), eq(classifierResult), eq(prompt.getId()), eq(trigger.getId()));
        verify(materialGenerator, never()).generate(any(), any());
        verify(topicClusterService).markGuideGenerated(cluster, guideCandidate, 7000L, false);
    }

    @Test
    void missingGuideGenerationWithMaterialCandidateBeforeGuideCandidateGeneratesGuideCandidate() {
        MessageEntity message = buildMessage(130L, "Как настроить RuFlow и whispertocode для голосового ввода в Codex API.");
        message.setProcessingStatus("CLASSIFIED");
        message.setClassifierScore(0.84);
        message.setClassifierResultJson("""
            {
              "score": 0.84,
              "matched": true,
              "labels": ["PRACTICAL_GUIDE_CANDIDATE", "WORKFLOW_LIFEHACK"],
              "guide_candidate": true,
              "guide_potential_score": 86,
              "technical_depth_score": 75,
              "reasoning": "Actionable voice input workflow."
            }
            """);
        ClassifierResult classifierResult = new ClassifierResult(
            0.84,
            true,
            List.of(ClassificationLabels.PRACTICAL_GUIDE_CANDIDATE, ClassificationLabels.WORKFLOW_LIFEHACK),
            true,
            List.of(message.getId()),
            "Actionable voice input workflow.",
            0,
            0,
            0,
            86,
            0,
            75,
            0,
            null,
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
        TopicDiscussionClusterEntity cluster = topicCluster(
            45L,
            7L,
            null,
            message.getMessageDate(),
            message.getMessageDate()
        );
        TopicClusterGuideCandidateEntity materialCandidate = topicGuideCandidate(
            7301L,
            cluster.getId(),
            "Short useful note",
            sourceIdsJson(List.of(130L))
        );
        TopicClusterGuideCandidateEntity guideCandidate = topicGuideCandidate(
            7302L,
            cluster.getId(),
            "Voice input setup workflow",
            sourceIdsJson(List.of(130L))
        );
        AiProviderEntity provider = activeProvider();
        PromptEntity prompt = guidePrompt();

        when(messageRepository.findByProcessingStatusIn(eq(List.of("CLASSIFIED")), any()))
            .thenReturn(new PageImpl<>(List.of(message)));
        when(topicClusterService.upsertFromMessage(eq(message), eq(List.of(message)), any(), any()))
            .thenReturn(new TopicClusterService.ClusterUpdateResult(
                cluster,
                List.of(message),
                true,
                1,
                "test missing guide cluster"
            ));
        doAnswer(invocation -> new TopicClusterService.ClusterClassificationResult(
                cluster,
                List.of(materialCandidate, guideCandidate)
            ))
            .when(topicClusterService)
            .applyClusterClassification(eq(cluster), any(), any(), eq(List.of(message)));
        when(contentRoutingService.route(eq(cluster), eq(materialCandidate), eq(List.of(message)), any()))
            .thenReturn(defaultMaterialRoutingDecision(ContentType.USEFUL_INFO));
        when(contentRoutingService.route(eq(cluster), eq(guideCandidate), eq(List.of(message)), any()))
            .thenReturn(defaultGuideRoutingDecision());
        when(contentQualityGate.evaluate(any(), eq(cluster), any(), eq(List.of(message))))
            .thenReturn(new ContentQualityGate.GateResult(true, "quality gate passed"));
        when(materialGenerator.generate(any(), any())).thenReturn(new GuideContent(
            "Useful note",
            "Useful note content",
            "# Useful note",
            0.80,
            List.of("voice-input"),
            null,
            "{}"
        ));
        when(activeProviderResolver.resolve(ModelCallPurpose.GUIDE_GENERATION))
            .thenReturn(Optional.of(new ActiveProviderResolver.ResolvedProvider(
                provider,
                ModelCallPurpose.GUIDE_GENERATION,
                "test"
            )));
        when(promptRepository.findByStatus("ACTIVE")).thenReturn(List.of(prompt));
        when(promptRepository.findByType("GENERATION")).thenReturn(List.of(prompt));
        when(aiProviderRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
        when(guideGenerator.generate(eq(List.of(message)), any(), eq(prompt.getId()), eq(message.getId())))
            .thenReturn(new GuideContent(
                "Voice input setup workflow",
                "Generated guide content",
                "# Voice input setup workflow",
                0.88,
                List.of("voice-input"),
                null,
                "{}",
                provider.getId(),
                provider.getModel()
            ));
        when(guideRepository.save(any(GuideEntity.class))).thenAnswer(invocation -> {
            GuideEntity guide = invocation.getArgument(0);
            guide.setId(7001L);
            return guide;
        });

        PipelineService.GenerateMissingGuidesResponse response = pipelineService.generateMissingGuides(1);

        assertThat(response.generated()).isEqualTo(1);
        verify(guideGenerator).generate(eq(List.of(message)), any(), eq(prompt.getId()), eq(message.getId()));
        verify(materialGenerator, never()).generate(any(), any());
        verify(topicClusterService).markGuideGenerated(cluster, guideCandidate, 7001L, false);
        assertThat(message.getGuideId()).isEqualTo(7001L);
    }

    @Test
    void retriesPersistenceWithCompactedFallbackOnDataIntegrityViolation() {
        MessageEntity message = buildMessage(106L, "подскажите, где купить Claude Plus подешевле?");
        GroupEntity group = buildGroup(7L);
        SettingsEntity settings = buildSettings();
        settings.setFilterMinMessageLength(1);

        when(messageRepository.findById(106L)).thenReturn(Optional.of(message));
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(settingsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        when(ruleRunner.evaluate(message)).thenReturn(new RuleResult(true, "pass", List.of()));
        when(messageContextBuilder.buildContext(message)).thenReturn(new MessageContextBundle(message, List.of(message), "hash-4", 5, List.of("question"), 48));
        when(messageContextBuilder.isAnchorCandidate(message)).thenReturn(true);
        when(classifierRepository.findByStatusOrderByClassifierOrderAsc("ACTIVE")).thenReturn(List.of());
        when(messageRepository.save(argThat(saved ->
            saved.getClassifierReason() != null && saved.getClassifierReason().contains("No active classifiers found"))))
            .thenThrow(new DataIntegrityViolationException("value too long"))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var result = pipelineService.processMessage(106L);

        assertThat(result).isNull();
        assertThat(message.getProcessingStatus()).isEqualTo("CLUSTERED");
        verify(messageRepository, atLeast(2)).save(argThat(saved ->
            saved.getClassifierReason() != null && !saved.getClassifierReason().isBlank()));
    }

    private CrossClusterRun arrangeCrossClusterRun(CrossClusterOptions options) {
        Instant currentStart = Instant.parse("2026-06-19T18:45:21Z");
        List<MessageEntity> currentMessages = new ArrayList<>();
        for (int index = 0; index < options.currentSourceIds().size(); index++) {
            Long id = options.currentSourceIds().get(index);
            currentMessages.add(crossClusterMessage(
                id,
                6L,
                options.topicId(),
                currentStart.plusSeconds(index * 30L),
                "how to restart Codex when access settings changed " + id
            ));
            currentMessages.get(currentMessages.size() - 1).setOwnerUserId(1L);
        }
        MessageEntity trigger = currentMessages.stream()
            .filter(message -> message.getId().equals(732L))
            .findFirst()
            .orElse(currentMessages.get(0));
        trigger.setProcessingStatus("UNPROCESSED");

        GroupEntity group = buildGroup(6L);
        group.setOwnerUserId(1L);
        SettingsEntity settings = buildSettings();
        settings.setFilterMinMessageLength(1);
        ClassifierEntity classifier = activeClassifier();
        MessageContextBundle contextBundle = new MessageContextBundle(
            trigger,
            currentMessages,
            "cross-cluster-hash",
            5,
            List.of("guide"),
            240
        );
        ClassifierResult classifierResult = new ClassifierResult(
            0.92,
            true,
            List.of(ClassificationLabels.PRACTICAL_GUIDE_CANDIDATE),
            true,
            currentMessages.stream().map(MessageEntity::getId).toList(),
            "cluster can produce a practical guide",
            80,
            20,
            0,
            90,
            0,
            70,
            0,
            "Codex restart troubleshooting",
            "Codex may need full restart after access settings changed",
            "Stop Codex, restart it, and check active sessions",
            List.of("Codex"),
            List.of(),
            List.of(),
            List.of("codex-restart")
        );
        TopicDiscussionClusterEntity cluster = topicCluster(
            2L,
            6L,
            options.topicId(),
            currentStart,
            currentStart.plusSeconds(600)
        );
        cluster.setOwnerUserId(1L);
        TopicClusterGuideCandidateEntity candidate = topicGuideCandidate(
            2L,
            2L,
            options.currentAngle(),
            sourceIdsJson(options.currentSourceIds())
        );

        GuideEntity existingGuide = new GuideEntity();
        existingGuide.setId(69L);
        existingGuide.setGroupId(options.existingGroupId());
        existingGuide.setRootMessageId(730L);
        existingGuide.setTopicClusterId(1L);
        existingGuide.setTopicClusterGuideCandidateId(1L);
        existingGuide.setStatus(options.existingGuideStatus());
        existingGuide.setTitle("Existing Codex restart guide");
        existingGuide.setProviderId(1L);
        existingGuide.setModel("gpt-5.3-codex-spark");
        existingGuide.setConfidence(0.91);
        existingGuide.setCreatedAt(currentStart.plusSeconds(20));
        existingGuide.setOwnerUserId(1L);

        TopicClusterGuideCandidateEntity existingCandidate = topicGuideCandidate(
            1L,
            1L,
            options.existingAngle(),
            options.existingCandidateSourceJson()
        );

        Map<Long, MessageEntity> messagesById = new LinkedHashMap<>();
        currentMessages.forEach(message -> messagesById.put(message.getId(), message));
        List<Long> existingIds = new ArrayList<>(options.existingGuideSourceIds());
        existingIds.addAll(sourceIdsFromJson(options.existingCandidateSourceJson()));
        for (int index = 0; index < existingIds.size(); index++) {
            Long id = existingIds.get(index);
            messagesById.putIfAbsent(id, crossClusterMessage(
                id,
                options.existingGroupId(),
                options.topicId(),
                options.existingSourceStart().plusSeconds(index * 30L),
                "existing Codex restart source " + id
            ));
            MessageEntity existingMessage = messagesById.get(id);
            if (existingMessage != null) {
                existingMessage.setOwnerUserId(1L);
            }
        }

        when(messageRepository.findById(trigger.getId())).thenReturn(Optional.of(trigger));
        when(groupRepository.findById(6L)).thenReturn(Optional.of(group));
        when(settingsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        when(ruleRunner.evaluate(trigger)).thenReturn(new RuleResult(true, "pass", List.of()));
        when(messageContextBuilder.buildContext(trigger)).thenReturn(contextBundle);
        when(messageContextBuilder.isAnchorCandidate(trigger)).thenReturn(true);
        when(classifierRepository.findByStatusOrderByClassifierOrderAsc("ACTIVE")).thenReturn(List.of(classifier));
        when(classifierRunner.classify(any(), eq(classifier), eq(ModelCallPurpose.CLUSTER_CLASSIFICATION)))
            .thenReturn(classifierResult);
        when(topicClusterService.upsertFromMessage(eq(trigger), any(), any(), any()))
            .thenReturn(new TopicClusterService.ClusterUpdateResult(
                cluster,
                currentMessages,
                true,
                currentMessages.size(),
                "test cross-cluster grouping"
            ));
        doAnswer(invocation -> new TopicClusterService.ClusterClassificationResult(cluster, List.of(candidate)))
            .when(topicClusterService)
            .applyClusterClassification(eq(cluster), any(), any(), eq(currentMessages));
        when(guideRepository.findTop100ByGroupIdAndCreatedAtAfterOrderByCreatedAtDesc(eq(6L), any()))
            .thenReturn(List.of(existingGuide));
        when(topicClusterGuideCandidateRepository.findById(1L)).thenReturn(Optional.of(existingCandidate));
        when(guideSourceMessageRepository.findByGuideId(69L))
            .thenReturn(sourceLinks(options.existingGuideSourceIds()));
        when(guideSourceMessageRepository.existsByGuideIdAndMessageId(eq(69L), any()))
            .thenAnswer(invocation -> options.existingGuideSourceIds().contains(invocation.getArgument(1)));
        when(messageRepository.findByIdInOrderByMessageDateAsc(any()))
            .thenAnswer(invocation -> {
                List<Long> ids = invocation.getArgument(0);
                return ids.stream()
                    .map(messagesById::get)
                    .filter(message -> message != null)
                    .sorted((left, right) -> left.getMessageDate().compareTo(right.getMessageDate()))
                    .toList();
            });
        stubSuccessfulGuideGeneration();

        return new CrossClusterRun(trigger, cluster, candidate, existingGuide);
    }

    private void stubSuccessfulGuideGeneration() {
        when(contentRoutingService.route(any(), any(), any(), any())).thenReturn(defaultGuideRoutingDecision());
        when(contentQualityGate.evaluate(any(), any(), any(), any()))
            .thenReturn(new ContentQualityGate.GateResult(true, "quality gate passed"));
        AiProviderEntity provider = activeProvider();
        PromptEntity prompt = guidePrompt();
        when(activeProviderResolver.resolve(ModelCallPurpose.GUIDE_GENERATION))
            .thenReturn(Optional.of(new ActiveProviderResolver.ResolvedProvider(
                provider,
                ModelCallPurpose.GUIDE_GENERATION,
                "test"
            )));
        when(promptRepository.findByStatus("ACTIVE")).thenReturn(List.of(prompt));
        when(promptRepository.findByType("GENERATION")).thenReturn(List.of(prompt));
        when(aiProviderRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
        when(guideGenerator.generate(any(), any(), any(), any())).thenReturn(new GuideContent(
            "Generated guide",
            "Generated content",
            "# Generated guide",
            0.86,
            List.of("Codex"),
            null,
            "{}",
            provider.getId(),
            provider.getModel()
        ));
        when(guideRepository.save(any(GuideEntity.class))).thenAnswer(invocation -> {
            GuideEntity guide = invocation.getArgument(0);
            guide.setId(7000L);
            return guide;
        });
    }

    private TopicDiscussionClusterEntity topicCluster(Long id, Long groupId, Long topicId, Instant start, Instant end) {
        TopicDiscussionClusterEntity cluster = new TopicDiscussionClusterEntity();
        cluster.setId(id);
        cluster.setGroupId(groupId);
        cluster.setTelegramTopicId(topicId);
        cluster.setTopicTitle("Codex");
        cluster.setStartAt(start);
        cluster.setEndAt(end);
        cluster.setStatus("CLASSIFIED");
        cluster.setClassificationStatus("CLASSIFIED");
        cluster.setGuideGenerationStatus("CANDIDATE");
        cluster.setSafetyCategory("normal");
        cluster.setSemanticHash("cluster-" + id);
        return cluster;
    }

    private TopicClusterGuideCandidateEntity topicGuideCandidate(
        Long id,
        Long clusterId,
        String angle,
        String sourceMessageIdsJson
    ) {
        TopicClusterGuideCandidateEntity candidate = new TopicClusterGuideCandidateEntity();
        candidate.setId(id);
        candidate.setClusterId(clusterId);
        candidate.setStatus("PENDING");
        candidate.setGuideAngle(angle);
        candidate.setSafetyCategory("normal");
        candidate.setWhyThisCluster("test");
        candidate.setSourceMessageIdsJson(sourceMessageIdsJson);
        return candidate;
    }

    private MessageEntity crossClusterMessage(Long id, Long groupId, Long topicId, Instant date, String text) {
        MessageEntity message = buildMessage(id, text);
        message.setGroupId(groupId);
        message.setTopicId(topicId);
        message.setTopicName("Codex");
        message.setMessageDate(date);
        message.setProcessingStatus("CLUSTERED");
        return message;
    }

    private List<GuideSourceMessageEntity> sourceLinks(List<Long> messageIds) {
        return messageIds.stream()
            .map(messageId -> {
                GuideSourceMessageEntity link = new GuideSourceMessageEntity();
                link.setGuideId(69L);
                link.setMessageId(messageId);
                link.setUsedInPrompt(true);
                return link;
            })
            .toList();
    }

    private String sourceIdsJson(List<Long> ids) {
        return "[" + ids.stream().map(String::valueOf).reduce((left, right) -> left + "," + right).orElse("") + "]";
    }

    private List<Long> sourceIdsFromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        String normalized = json.replaceAll("[^0-9]+", " ").trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : normalized.split("\\s+")) {
            if (!part.isBlank()) {
                ids.add(Long.parseLong(part));
            }
        }
        return ids;
    }

    private ContentRoutingDecision defaultGuideRoutingDecision() {
        return new ContentRoutingDecision(
            ContentType.GUIDE,
            "PRACTICAL_WORKFLOW",
            "Codex restart workflow",
            "Cluster contains an actionable Codex workflow.",
            "Codex restart workflow",
            "Cluster contains an actionable Codex workflow.",
            "codex_restart_workflow",
            "How to handle a Codex restart workflow",
            true,
            true,
            0.86,
            82,
            84,
            30,
            72,
            10,
            20,
            "NORMAL",
            "GUIDE",
            "test default routing",
            List.of(),
            List.of("Steps", "Validation", "Risks"),
            2L,
            "gpt-test"
        );
    }

    private ContentRoutingDecision defaultMaterialRoutingDecision(ContentType contentType) {
        return new ContentRoutingDecision(
            contentType,
            "ROUTED_MATERIAL",
            "Codex release update",
            "Cluster contains a product/news update, not a workflow.",
            "Codex release news",
            "Codex release context from the discussion.",
            "codex_release_update",
            "Codex release update",
            true,
            false,
            0.76,
            70,
            20,
            82,
            66,
            10,
            18,
            "NORMAL",
            "MATERIAL",
            "test material routing",
            List.of(),
            List.of("What happened", "Why it matters"),
            null,
            null
        );
    }

    private ContentRoutingDecision defaultDeferredRoutingDecision() {
        return new ContentRoutingDecision(
            ContentType.DEFERRED,
            "WEAK_EVIDENCE",
            "Deferred test cluster",
            "Cluster is deferred in default test setup.",
            "Deferred test cluster",
            "Cluster is deferred in default test setup.",
            "deferred_test_cluster",
            "Material deferred",
            false,
            false,
            0.40,
            30,
            10,
            20,
            30,
            10,
            65,
            "NORMAL",
            "MATERIAL",
            "default deferred routing",
            List.of(),
            List.of(),
            null,
            null
        );
    }

    private record CrossClusterOptions(
        List<Long> currentSourceIds,
        List<Long> existingGuideSourceIds,
        String existingCandidateSourceJson,
        String currentAngle,
        String existingAngle,
        Long existingGroupId,
        Long topicId,
        Instant existingSourceStart,
        String existingGuideStatus
    ) {}

    private record CrossClusterRun(
        MessageEntity triggerMessage,
        TopicDiscussionClusterEntity cluster,
        TopicClusterGuideCandidateEntity candidate,
        GuideEntity existingGuide
    ) {}

    private MessageEntity buildMessage(Long id, String text) {
        MessageEntity message = new MessageEntity();
        message.setId(id);
        message.setGroupId(7L);
        message.setTelegramMessageId(id);
        message.setText(text);
        message.setIsBot(false);
        message.setReplyCount(0);
        message.setProcessingStatus("UNPROCESSED");
        message.setMessageDate(Instant.now());
        return message;
    }

    private GroupEntity buildGroup(Long id) {
        GroupEntity group = new GroupEntity();
        group.setId(id);
        group.setTelegramChatId(77L);
        group.setTitle("Test Group");
        group.setEnabled(true);
        return group;
    }

    private SettingsEntity buildSettings() {
        SettingsEntity settings = new SettingsEntity();
        settings.setFilterSkipBots(true);
        settings.setFilterMinMessageLength(12);
        settings.setPublicationMode("WITH_MODERATION");
        return settings;
    }

    private ClassifierEntity activeClassifier() {
        ClassifierEntity classifier = new ClassifierEntity();
        classifier.setId(1L);
        classifier.setName("LLM Classifier");
        classifier.setType("LLM");
        classifier.setStatus("ACTIVE");
        classifier.setClassifierOrder(1);
        return classifier;
    }

    private TopicClusterGuideCandidateEntity topicGuideCandidate(Long clusterId) {
        TopicClusterGuideCandidateEntity candidate = new TopicClusterGuideCandidateEntity();
        candidate.setId(7100L);
        candidate.setClusterId(clusterId);
        candidate.setStatus("PENDING");
        candidate.setGuideAngle("practical guide");
        candidate.setSafetyCategory("normal");
        candidate.setWhyThisCluster("test");
        candidate.setSourceMessageIdsJson("[]");
        return candidate;
    }

    private AiProviderEntity activeProvider() {
        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(2L);
        provider.setName("Provider");
        provider.setProtocol("OPENAI_COMPATIBLE");
        provider.setStatus("ACTIVE");
        provider.setEndpointUrl("https://api.example.test");
        provider.setApiKeyEncrypted("key");
        provider.setModel("gpt-test");
        return provider;
    }

    private PromptEntity guidePrompt() {
        PromptEntity prompt = new PromptEntity();
        prompt.setId(3L);
        prompt.setName("Guide prompt");
        prompt.setType("GENERATION");
        prompt.setStatus("ACTIVE");
        prompt.setVersion("v1");
        prompt.setContent("Write a guide");
        return prompt;
    }
}
