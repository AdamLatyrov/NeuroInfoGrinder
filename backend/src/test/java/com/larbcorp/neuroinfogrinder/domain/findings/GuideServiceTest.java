package com.larbcorp.neuroinfogrinder.domain.findings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.GuideDetailResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.GuideRegenerateResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.AiProviderEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideSourceMessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.SettingsEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.AiProviderRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuidePublicationLogRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuideRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuideSourceMessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.SettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuideServiceTest {

    @Test
    void guidesEndpointScopeReturnsOnlyGuideContentAndMaterialsCanReturnAllTypes() {
        GuideRepository guideRepository = mock(GuideRepository.class);
        GuideSourceMessageRepository guideSourceMessageRepository = mock(GuideSourceMessageRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        GuidePublicationLogRepository guidePublicationLogRepository = mock(GuidePublicationLogRepository.class);
        GuideGenerator guideGenerator = mock(GuideGenerator.class);

        GuideService guideService = new GuideService(
            guideRepository,
            guideSourceMessageRepository,
            messageRepository,
            groupRepository,
            guidePublicationLogRepository,
            guideGenerator,
            new GuideUsefulnessScorer(),
            new ObjectMapper()
        );

        GuideEntity guide = guide(1L, "GUIDE");
        GuideEntity news = guide(2L, "NEWS");
        GuideEntity warning = guide(3L, "WARNING");
        when(guideRepository.findAll()).thenReturn(List.of(guide, news, warning));

        PageRequest page = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

        assertThat(guideService.getGuides(null, null, null, null, null, null, null, null, null, page).getContent())
            .extracting(GuideEntity::getId)
            .containsExactly(1L);
        assertThat(guideService.getMaterials(null, null, null, null, null, null, null, null, null, null, page).getContent())
            .extracting(GuideEntity::getId)
            .containsExactly(3L, 2L, 1L);
        assertThat(guideService.getMaterials(null, "WARNING", null, null, null, null, null, null, null, null, page).getContent())
            .extracting(GuideEntity::getId)
            .containsExactly(3L);
        assertThat(guideService.getMaterials(null, "GUIDE,NEWS", null, null, null, null, null, null, null, null, page).getContent())
            .extracting(GuideEntity::getId)
            .containsExactly(2L, 1L);
    }

    @Test
    void summaryResponseIncludesSourceCount() {
        GuideRepository guideRepository = mock(GuideRepository.class);
        GuideSourceMessageRepository guideSourceMessageRepository = mock(GuideSourceMessageRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        GuidePublicationLogRepository guidePublicationLogRepository = mock(GuidePublicationLogRepository.class);
        GuideGenerator guideGenerator = mock(GuideGenerator.class);

        GuideService guideService = new GuideService(
            guideRepository,
            guideSourceMessageRepository,
            messageRepository,
            groupRepository,
            guidePublicationLogRepository,
            guideGenerator,
            new GuideUsefulnessScorer(),
            new ObjectMapper()
        );

        GuideEntity guide = guide(9L, "GUIDE");
        when(guideSourceMessageRepository.countByGuideId(9L)).thenReturn(4L);

        assertThat(guideService.toSummaryResponse(guide).sourceCount()).isEqualTo(4);
    }

    @Test
    void summaryResponseReplacesGenericClusterGuideTitleFromContent() {
        GuideRepository guideRepository = mock(GuideRepository.class);
        GuideSourceMessageRepository guideSourceMessageRepository = mock(GuideSourceMessageRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        GuidePublicationLogRepository guidePublicationLogRepository = mock(GuidePublicationLogRepository.class);
        GuideGenerator guideGenerator = mock(GuideGenerator.class);

        GuideService guideService = new GuideService(
            guideRepository,
            guideSourceMessageRepository,
            messageRepository,
            groupRepository,
            guidePublicationLogRepository,
            guideGenerator,
            new GuideUsefulnessScorer(),
            new ObjectMapper()
        );

        GuideEntity material = guide(77L, "USEFUL_INFO");
        material.setTitle("FAQ: Практический гайд по теме кластера");
        material.setTopicLabel("Практический гайд по теме кластера");
        material.setContentTitle("FAQ: Практический гайд по теме кластера");
        material.setContentSummary("Практический гайд по теме кластера: Я пытаюсь подобрать лучшую модель для итеративного планирования. Gemini нравится, но на длинных диалогах галюны.");
        when(guideSourceMessageRepository.countByGuideId(77L)).thenReturn(4L);

        var response = guideService.toSummaryResponse(material);

        assertThat(response.title()).isEqualTo("Выбор AI-модели для стратегического планирования");
        assertThat(response.contentTitle()).isEqualTo("Выбор AI-модели для стратегического планирования");
        assertThat(response.topicLabel()).isEqualTo("Выбор AI-модели для стратегического планирования");
        assertThat(response.contentSummary()).startsWith("Я пытаюсь подобрать лучшую модель");
    }

    @Test
    void summaryResponsePrefersProductNameFromContentOverWrongClusterTopic() {
        GuideRepository guideRepository = mock(GuideRepository.class);
        GuideSourceMessageRepository guideSourceMessageRepository = mock(GuideSourceMessageRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        GuidePublicationLogRepository guidePublicationLogRepository = mock(GuidePublicationLogRepository.class);
        GuideGenerator guideGenerator = mock(GuideGenerator.class);

        GuideService guideService = new GuideService(
            guideRepository,
            guideSourceMessageRepository,
            messageRepository,
            groupRepository,
            guidePublicationLogRepository,
            guideGenerator,
            new GuideUsefulnessScorer(),
            new ObjectMapper()
        );

        GuideEntity material = guide(78L, "USEFUL_INFO");
        material.setTitle("Обновление: Настройка Droid-конфига и доступов");
        material.setTopicLabel("Настройка Droid-конфига и доступов");
        material.setContentTitle("Обновление: Настройка Droid-конфига и доступов");
        material.setContentSummary("Настройка Droid-конфига и доступов: Название: RamTeamAi Что делает: Настольный open-source AI-клиент для вайбкодеров.");
        when(guideSourceMessageRepository.countByGuideId(78L)).thenReturn(3L);

        var response = guideService.toSummaryResponse(material);

        assertThat(response.title()).isEqualTo("RamTeamAi: desktop-клиент для AI-агентов");
        assertThat(response.contentTitle()).isEqualTo("RamTeamAi: desktop-клиент для AI-агентов");
    }

    @Test
    void detailExposesInternalAndTelegramLinks() throws Exception {
        GuideRepository guideRepository = mock(GuideRepository.class);
        GuideSourceMessageRepository guideSourceMessageRepository = mock(GuideSourceMessageRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        AiProviderRepository aiProviderRepository = mock(AiProviderRepository.class);
        GuidePublicationLogRepository guidePublicationLogRepository = mock(GuidePublicationLogRepository.class);
        GuideGenerator guideGenerator = mock(GuideGenerator.class);

        GuideService guideService = new GuideService(
            guideRepository,
            guideSourceMessageRepository,
            messageRepository,
            groupRepository,
            guidePublicationLogRepository,
            guideGenerator,
            new GuideUsefulnessScorer(),
            new ObjectMapper()
        );

        GuideEntity guide = new GuideEntity();
        guide.setId(7L);
        guide.setGroupId(3L);
        guide.setRootMessageId(11L);
        guide.setTitle("Guide title");
        guide.setCreatedAt(Instant.now());

        GroupEntity group = new GroupEntity();
        group.setId(3L);
        group.setTelegramChatId(-1001234567890L);
        group.setUsername("publicgroup");
        group.setTitle("Group");

        GuideSourceMessageEntity source = new GuideSourceMessageEntity();
        source.setGuideId(7L);
        source.setMessageId(11L);
        source.setUsedInPrompt(true);

        MessageEntity message = new MessageEntity();
        message.setId(11L);
        message.setGroupId(3L);
        message.setTelegramMessageId(222L);
        message.setSenderName("Alice");
        message.setSenderUsername("alice");
        message.setSenderTelegramUserId(99L);
        message.setText("Ссылка https://example.com");
        message.setTextEntitiesJson("""
            [{"type":"url","offset":7,"length":19,"url":"https://example.com","text":"https://example.com"}]
            """);
        message.setMessageDate(Instant.now());

        when(guideRepository.findById(7L)).thenReturn(Optional.of(guide));
        when(groupRepository.findById(3L)).thenReturn(Optional.of(group));
        when(guideSourceMessageRepository.findByGuideId(7L)).thenReturn(List.of(source));
        when(messageRepository.findAllById(List.of(11L))).thenReturn(List.of(message));
        when(guideRepository.findAll()).thenReturn(List.of(guide));

        GuideDetailResponse detail = guideService.getGuideDetail(7L);

        assertThat(detail.sourceMessages()).hasSize(1);
        assertThat(detail.sourceMessages().get(0).internalMessageUrl()).isEqualTo("/groups?group=3&message=11");
        assertThat(detail.sourceMessages().get(0).telegramMessageUrl()).isEqualTo("https://t.me/publicgroup/222");
        assertThat(detail.sourceMessages().get(0).senderDisplayName()).isEqualTo("Alice");
        assertThat(detail.sourceMessages().get(0).textEntities()).hasSize(1);
    }

    @Test
    void regenerateSavesRoutedProviderFromGeneratedContent() {
        GuideRepository guideRepository = mock(GuideRepository.class);
        GuideSourceMessageRepository guideSourceMessageRepository = mock(GuideSourceMessageRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        AiProviderRepository aiProviderRepository = mock(AiProviderRepository.class);
        SettingsRepository settingsRepository = mock(SettingsRepository.class);
        GuidePublicationLogRepository guidePublicationLogRepository = mock(GuidePublicationLogRepository.class);
        GuideGenerator guideGenerator = mock(GuideGenerator.class);

        GuideService guideService = new GuideService(
            guideRepository,
            guideSourceMessageRepository,
            messageRepository,
            groupRepository,
            guidePublicationLogRepository,
            guideGenerator,
            new GuideUsefulnessScorer(),
            new ObjectMapper()
        );

        GuideEntity oldGuide = new GuideEntity();
        oldGuide.setId(7L);
        oldGuide.setGroupId(3L);
        oldGuide.setRootMessageId(11L);
        oldGuide.setProviderId(2L);
        oldGuide.setPromptId(5L);
        oldGuide.setPromptVersion("v1");
        oldGuide.setModel("old-model");
        oldGuide.setClassifierId(9L);

        GuideSourceMessageEntity source = new GuideSourceMessageEntity();
        source.setGuideId(7L);
        source.setMessageId(11L);
        source.setUsedInPrompt(true);

        MessageEntity message = new MessageEntity();
        message.setId(11L);
        message.setGroupId(3L);
        message.setTelegramMessageId(333L);
        message.setText("Полезный исходник");
        message.setMessageDate(Instant.now());
        message.setClassifierResultJson("""
            {"score":0.9,"matched":true,"labels":["PRACTICAL_GUIDE_CANDIDATE"],"guideCandidate":true,"evidenceMessageIds":[11],"reasoning":"ok"}
            """);

        AiProviderEntity fallbackProvider = new AiProviderEntity();
        fallbackProvider.setId(4L);
        fallbackProvider.setStatus("ACTIVE");
        fallbackProvider.setModel("active-model");

        GuideContent newContent = new GuideContent(
            "Новый гайд",
            "content",
            "# Новый гайд",
            0.91,
            List.of("AI"),
            null,
            "{\"ok\":true}",
            4L,
            "active-model"
        );

        when(guideRepository.findById(7L)).thenReturn(Optional.of(oldGuide));
        when(guideSourceMessageRepository.findByGuideId(7L)).thenReturn(List.of(source));
        when(messageRepository.findAllById(List.of(11L))).thenReturn(List.of(message));
        when(aiProviderRepository.findById(2L)).thenReturn(Optional.empty());
        when(aiProviderRepository.findAll()).thenReturn(List.of(fallbackProvider));
        when(settingsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(guideGenerator.generate(any(), any(), any(), any())).thenReturn(newContent);
        when(guideRepository.save(any(GuideEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GuideRegenerateResponse response = guideService.regenerate(7L);

        assertThat(response.oldGuideId()).isEqualTo(7L);
        assertThat(response.newGuideId()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo("DRAFT");
        verify(guideSourceMessageRepository, never()).save(any(GuideSourceMessageEntity.class));
    }

    @Test
    void regenerateDoesNotPassProviderIdAndStoresRoutedProvider() {
        GuideRepository guideRepository = mock(GuideRepository.class);
        GuideSourceMessageRepository guideSourceMessageRepository = mock(GuideSourceMessageRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        AiProviderRepository aiProviderRepository = mock(AiProviderRepository.class);
        SettingsRepository settingsRepository = mock(SettingsRepository.class);
        GuidePublicationLogRepository guidePublicationLogRepository = mock(GuidePublicationLogRepository.class);
        GuideGenerator guideGenerator = mock(GuideGenerator.class);

        GuideService guideService = new GuideService(
            guideRepository,
            guideSourceMessageRepository,
            messageRepository,
            groupRepository,
            guidePublicationLogRepository,
            guideGenerator,
            new GuideUsefulnessScorer(),
            new ObjectMapper()
        );

        GuideEntity failedGuide = new GuideEntity();
        failedGuide.setId(1007L);
        failedGuide.setGroupId(3L);
        failedGuide.setRootMessageId(11L);
        failedGuide.setProviderId(3L);
        failedGuide.setPromptId(5L);
        failedGuide.setPromptVersion("v1");
        failedGuide.setModel("openrouter/free");
        failedGuide.setGenerationError("429 rate limit from openrouter/free");
        failedGuide.setClassifierId(9L);

        GuideSourceMessageEntity source = new GuideSourceMessageEntity();
        source.setGuideId(1007L);
        source.setMessageId(11L);
        source.setUsedInPrompt(true);

        MessageEntity message = new MessageEntity();
        message.setId(11L);
        message.setGroupId(3L);
        message.setTelegramMessageId(333L);
        message.setText("Полезный исходник");
        message.setMessageDate(Instant.now());
        message.setClassifierResultJson("""
            {"score":0.9,"matched":true,"labels":["PRACTICAL_GUIDE_CANDIDATE"],"guideCandidate":true,"evidenceMessageIds":[11],"reasoning":"ok"}
            """);

        AiProviderEntity failedProvider = provider(3L, "openrouter/free");
        AiProviderEntity activeProvider = provider(7L, "glm-5.1");
        SettingsEntity settings = new SettingsEntity();
        settings.setActiveProviderId(7L);

        GuideContent newContent = new GuideContent(
            "Новый гайд",
            "content",
            "# Новый гайд",
            0.91,
            List.of("AI"),
            null,
            "{\"ok\":true}",
            7L,
            "glm-5.1"
        );

        when(guideRepository.findById(1007L)).thenReturn(Optional.of(failedGuide));
        when(guideSourceMessageRepository.findByGuideId(1007L)).thenReturn(List.of(source));
        when(messageRepository.findAllById(List.of(11L))).thenReturn(List.of(message));
        when(aiProviderRepository.findAll()).thenReturn(List.of(failedProvider, activeProvider));
        when(aiProviderRepository.findById(7L)).thenReturn(Optional.of(activeProvider));
        when(settingsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        when(guideGenerator.generate(any(), any(), any(), any())).thenReturn(newContent);
        when(guideRepository.save(any(GuideEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        guideService.regenerate(1007L);

        org.mockito.ArgumentCaptor<GuideEntity> guideCaptor = org.mockito.ArgumentCaptor.forClass(GuideEntity.class);
        verify(guideRepository).save(guideCaptor.capture());
        assertThat(guideCaptor.getValue().getProviderId()).isEqualTo(7L);
        assertThat(guideCaptor.getValue().getModel()).isEqualTo("glm-5.1");
    }

    @Test
    void regenerateStoresRoutedProviderModel() {
        GuideRepository guideRepository = mock(GuideRepository.class);
        GuideSourceMessageRepository guideSourceMessageRepository = mock(GuideSourceMessageRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        AiProviderRepository aiProviderRepository = mock(AiProviderRepository.class);
        SettingsRepository settingsRepository = mock(SettingsRepository.class);
        GuidePublicationLogRepository guidePublicationLogRepository = mock(GuidePublicationLogRepository.class);
        GuideGenerator guideGenerator = mock(GuideGenerator.class);

        GuideService guideService = new GuideService(
            guideRepository,
            guideSourceMessageRepository,
            messageRepository,
            groupRepository,
            guidePublicationLogRepository,
            guideGenerator,
            new GuideUsefulnessScorer(),
            new ObjectMapper()
        );

        GuideEntity failedGuide = retryableGuide("404 model not found: deprecated-model");
        AiProviderEntity failedProvider = provider(3L, "deprecated-model");
        AiProviderEntity activeProvider = provider(7L, "glm-5.1");
        SettingsEntity settings = new SettingsEntity();
        settings.setActiveProviderId(7L);

        when(guideRepository.findById(1007L)).thenReturn(Optional.of(failedGuide));
        when(guideSourceMessageRepository.findByGuideId(1007L)).thenReturn(List.of(sourceLink()));
        when(messageRepository.findAllById(List.of(11L))).thenReturn(List.of(sourceMessage()));
        when(aiProviderRepository.findAll()).thenReturn(List.of(failedProvider, activeProvider));
        when(aiProviderRepository.findById(7L)).thenReturn(Optional.of(activeProvider));
        when(settingsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        when(guideGenerator.generate(any(), any(), any(), any())).thenReturn(generatedContent());
        when(guideRepository.save(any(GuideEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        guideService.regenerate(1007L);

        org.mockito.ArgumentCaptor<GuideEntity> guideCaptor = org.mockito.ArgumentCaptor.forClass(GuideEntity.class);
        verify(guideRepository).save(guideCaptor.capture());
        assertThat(guideCaptor.getValue().getProviderId()).isEqualTo(7L);
        assertThat(guideCaptor.getValue().getModel()).isEqualTo("glm-5.1");
    }

    @Test
    void regenerateStoresGenerationFailureWhenRouterHasNoUsableProvider() {
        GuideRepository guideRepository = mock(GuideRepository.class);
        GuideSourceMessageRepository guideSourceMessageRepository = mock(GuideSourceMessageRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        AiProviderRepository aiProviderRepository = mock(AiProviderRepository.class);
        SettingsRepository settingsRepository = mock(SettingsRepository.class);
        GuidePublicationLogRepository guidePublicationLogRepository = mock(GuidePublicationLogRepository.class);
        GuideGenerator guideGenerator = mock(GuideGenerator.class);

        GuideService guideService = new GuideService(
            guideRepository,
            guideSourceMessageRepository,
            messageRepository,
            groupRepository,
            guidePublicationLogRepository,
            guideGenerator,
            new GuideUsefulnessScorer(),
            new ObjectMapper()
        );

        GuideEntity failedGuide = retryableGuide("429 too many requests");
        AiProviderEntity failedProvider = provider(3L, "openrouter/free");
        SettingsEntity settings = new SettingsEntity();
        settings.setActiveProviderId(3L);

        when(guideRepository.findById(1007L)).thenReturn(Optional.of(failedGuide));
        when(guideSourceMessageRepository.findByGuideId(1007L)).thenReturn(List.of(sourceLink()));
        when(messageRepository.findAllById(List.of(11L))).thenReturn(List.of(sourceMessage()));
        when(aiProviderRepository.findAll()).thenReturn(List.of(failedProvider));
        when(aiProviderRepository.findById(3L)).thenReturn(Optional.of(failedProvider));
        when(settingsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));

        GuideContent failedContent = new GuideContent(
            "Ошибка генерации гайда",
            null,
            null,
            0.0,
            List.of("AI"),
            "No usable active provider available for GUIDE_GENERATION",
            null,
            null,
            null
        );
        when(guideGenerator.generate(any(), any(), any(), any())).thenReturn(failedContent);
        when(guideRepository.save(any(GuideEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GuideRegenerateResponse response = guideService.regenerate(1007L);

        assertThat(response.status()).isEqualTo("FAILED");
    }

    @Test
    void filtersByUsefulnessRangeAndFallsBackForOldGuides() {
        GuideRepository guideRepository = mock(GuideRepository.class);
        GuideSourceMessageRepository guideSourceMessageRepository = mock(GuideSourceMessageRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        AiProviderRepository aiProviderRepository = mock(AiProviderRepository.class);
        GuidePublicationLogRepository guidePublicationLogRepository = mock(GuidePublicationLogRepository.class);
        GuideGenerator guideGenerator = mock(GuideGenerator.class);

        GuideService guideService = new GuideService(
            guideRepository,
            guideSourceMessageRepository,
            messageRepository,
            groupRepository,
            guidePublicationLogRepository,
            guideGenerator,
            new GuideUsefulnessScorer(),
            new ObjectMapper()
        );

        GuideEntity weak = new GuideEntity();
        weak.setId(1L);
        weak.setGroupId(3L);
        weak.setTitle("{bad}");
        weak.setContent("short");
        weak.setConfidence(0.95);

        GuideEntity oldUseful = new GuideEntity();
        oldUseful.setId(2L);
        oldUseful.setGroupId(3L);
        oldUseful.setTitle("API setup guide");
        oldUseful.setContent("Step 1: configure /api proxy. Step 2: deploy from GitHub. Source: https://example.com/setup");
        oldUseful.setConfidence(0.76);

        GuideEntity storedUseful = new GuideEntity();
        storedUseful.setId(3L);
        storedUseful.setGroupId(3L);
        storedUseful.setTitle("Stored score");
        storedUseful.setContent("content with enough length for stored score");
        storedUseful.setConfidence(0.1);
        storedUseful.setUsefulnessScore(88);

        when(guideRepository.findAll()).thenReturn(List.of(weak, oldUseful, storedUseful));

        var page = guideService.getGuides(
            null,
            null,
            null,
            null,
            null,
            null,
            70,
            100,
            PageRequest.of(0, 20)
        );

        assertThat(page.getContent()).extracting(GuideEntity::getId).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void sortsGuidesByCreatedAtFromPageable() {
        GuideRepository guideRepository = mock(GuideRepository.class);
        GuideSourceMessageRepository guideSourceMessageRepository = mock(GuideSourceMessageRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        GuidePublicationLogRepository guidePublicationLogRepository = mock(GuidePublicationLogRepository.class);
        GuideGenerator guideGenerator = mock(GuideGenerator.class);

        GuideService guideService = new GuideService(
            guideRepository,
            guideSourceMessageRepository,
            messageRepository,
            groupRepository,
            guidePublicationLogRepository,
            guideGenerator,
            new GuideUsefulnessScorer(),
            new ObjectMapper()
        );

        GuideEntity oldest = guideWithCreatedAt(1L, "Oldest", Instant.parse("2026-06-17T10:00:00Z"));
        GuideEntity newest = guideWithCreatedAt(2L, "Newest", Instant.parse("2026-06-19T10:00:00Z"));
        GuideEntity middle = guideWithCreatedAt(3L, "Middle", Instant.parse("2026-06-18T10:00:00Z"));
        when(guideRepository.findAll()).thenReturn(List.of(oldest, newest, middle));

        var newestFirst = guideService.getGuides(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        var oldestFirst = guideService.getGuides(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "createdAt"))
        );

        assertThat(newestFirst.getContent()).extracting(GuideEntity::getId).containsExactly(2L, 3L, 1L);
        assertThat(oldestFirst.getContent()).extracting(GuideEntity::getId).containsExactly(1L, 3L, 2L);
    }

    @Test
    void detailRejectsGuideOwnedByAnotherUser() {
        GuideRepository guideRepository = mock(GuideRepository.class);
        GuideSourceMessageRepository guideSourceMessageRepository = mock(GuideSourceMessageRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        GuidePublicationLogRepository guidePublicationLogRepository = mock(GuidePublicationLogRepository.class);
        GuideGenerator guideGenerator = mock(GuideGenerator.class);

        GuideService guideService = new GuideService(
            guideRepository,
            guideSourceMessageRepository,
            messageRepository,
            groupRepository,
            guidePublicationLogRepository,
            guideGenerator,
            new GuideUsefulnessScorer(),
            new ObjectMapper()
        );

        when(guideRepository.findByIdAndOwnerUserId(70L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guideService.getGuideDetail(1L, 70L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Guide not found");

        org.mockito.Mockito.verifyNoInteractions(guideSourceMessageRepository, messageRepository, groupRepository);
    }

    private AiProviderEntity provider(Long id, String model) {
        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(id);
        provider.setName("Provider " + id);
        provider.setProtocol("OPENAI_COMPATIBLE");
        provider.setEndpointUrl("http://example/" + id);
        provider.setStatus("ACTIVE");
        provider.setModel(model);
        return provider;
    }

    private GuideEntity retryableGuide(String generationError) {
        GuideEntity failedGuide = new GuideEntity();
        failedGuide.setId(1007L);
        failedGuide.setGroupId(3L);
        failedGuide.setRootMessageId(11L);
        failedGuide.setProviderId(3L);
        failedGuide.setPromptId(5L);
        failedGuide.setPromptVersion("v1");
        failedGuide.setModel("openrouter/free");
        failedGuide.setGenerationError(generationError);
        failedGuide.setClassifierId(9L);
        return failedGuide;
    }

    private GuideEntity guideWithCreatedAt(Long id, String title, Instant createdAt) {
        GuideEntity guide = new GuideEntity();
        guide.setId(id);
        guide.setGroupId(3L);
        guide.setTitle(title);
        guide.setContent("content");
        guide.setConfidence(0.8);
        guide.setCreatedAt(createdAt);
        return guide;
    }

    private GuideEntity guide(Long id, String contentType) {
        GuideEntity guide = new GuideEntity();
        guide.setId(id);
        guide.setGroupId(10L);
        guide.setTitle(contentType + " title");
        guide.setContentType(contentType);
        guide.setStatus("DRAFT");
        guide.setCreatedAt(Instant.parse("2026-06-19T10:00:00Z").plusSeconds(id));
        return guide;
    }

    private GuideSourceMessageEntity sourceLink() {
        GuideSourceMessageEntity source = new GuideSourceMessageEntity();
        source.setGuideId(1007L);
        source.setMessageId(11L);
        source.setUsedInPrompt(true);
        return source;
    }

    private MessageEntity sourceMessage() {
        MessageEntity message = new MessageEntity();
        message.setId(11L);
        message.setGroupId(3L);
        message.setTelegramMessageId(333L);
        message.setText("Useful source");
        message.setMessageDate(Instant.now());
        message.setClassifierResultJson("""
            {"score":0.9,"matched":true,"labels":["PRACTICAL_GUIDE_CANDIDATE"],"guideCandidate":true,"evidenceMessageIds":[11],"reasoning":"ok"}
            """);
        return message;
    }

    private GuideContent generatedContent() {
        return new GuideContent(
            "New guide",
            "content",
            "# New guide",
            0.91,
            List.of("AI"),
            null,
            "{\"ok\":true}",
            7L,
            "glm-5.1"
        );
    }
}
