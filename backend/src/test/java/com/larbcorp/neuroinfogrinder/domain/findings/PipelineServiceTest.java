package com.larbcorp.neuroinfogrinder.domain.findings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.ClassifierEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.SettingsEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.AiProviderRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.AiUsageLogRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.ClassifierRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuideRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuideSourceMessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.PromptRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.SettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private RuleRunner ruleRunner;
    private MessageContextBuilder messageContextBuilder;
    private ClassifierRunner classifierRunner;
    private GuideGenerator guideGenerator;
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
        ruleRunner = mock(RuleRunner.class);
        messageContextBuilder = mock(MessageContextBuilder.class);
        classifierRunner = mock(ClassifierRunner.class);
        guideGenerator = mock(GuideGenerator.class);
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
            new SignalScorer(),
            ruleRunner,
            messageContextBuilder,
            classifierRunner,
            guideGenerator,
            pipelineTraceService,
            new ObjectMapper()
        );

        ReflectionTestUtils.setField(pipelineService, "signalThreshold", 0.30d);
        ReflectionTestUtils.setField(pipelineService, "localGuideFallback", false);
        ReflectionTestUtils.setField(pipelineService, "classificationContextDedupTtlHours", 24L);
        when(messageRepository.save(any(MessageEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.findFirstByClassificationContextHashAndUpdatedAtAfterOrderByUpdatedAtDesc(any(), any()))
            .thenReturn(Optional.empty());
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
        assertThat(message.getProcessingStatus()).isEqualTo("CLASSIFIED");
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
        when(classifierRunner.classify(List.of(message), classifier)).thenReturn(classifierResult);

        var result = pipelineService.processMessage(102L);

        assertThat(result).isNull();
        assertThat(message.getProcessingStatus()).isEqualTo("CLASSIFIED");
        assertThat(message.getClassifierResultJson()).contains("\"guideCandidate\":false");
        verify(guideGenerator, never()).generate(any(), any(), any(), any(), any());
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
}
