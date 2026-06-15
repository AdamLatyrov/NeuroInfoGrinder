package com.larbcorp.neuroinfogrinder.domain.findings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.GuideDetailResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.GuideRegenerateResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.AiProviderEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideSourceMessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.AiProviderRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuideRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuideSourceMessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GuideServiceTest {

    @Test
    void detailExposesInternalAndTelegramLinks() throws Exception {
        GuideRepository guideRepository = mock(GuideRepository.class);
        GuideSourceMessageRepository guideSourceMessageRepository = mock(GuideSourceMessageRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        AiProviderRepository aiProviderRepository = mock(AiProviderRepository.class);
        GuideGenerator guideGenerator = mock(GuideGenerator.class);

        GuideService guideService = new GuideService(
            guideRepository,
            guideSourceMessageRepository,
            messageRepository,
            groupRepository,
            aiProviderRepository,
            guideGenerator,
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
    void regenerateFallsBackToFirstActiveProviderAndCreatesNewGuide() {
        GuideRepository guideRepository = mock(GuideRepository.class);
        GuideSourceMessageRepository guideSourceMessageRepository = mock(GuideSourceMessageRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        AiProviderRepository aiProviderRepository = mock(AiProviderRepository.class);
        GuideGenerator guideGenerator = mock(GuideGenerator.class);

        GuideService guideService = new GuideService(
            guideRepository,
            guideSourceMessageRepository,
            messageRepository,
            groupRepository,
            aiProviderRepository,
            guideGenerator,
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
            "{\"ok\":true}"
        );

        when(guideRepository.findById(7L)).thenReturn(Optional.of(oldGuide));
        when(guideSourceMessageRepository.findByGuideId(7L)).thenReturn(List.of(source));
        when(messageRepository.findAllById(List.of(11L))).thenReturn(List.of(message));
        when(aiProviderRepository.findById(2L)).thenReturn(Optional.empty());
        when(aiProviderRepository.findAll()).thenReturn(List.of(fallbackProvider));
        when(guideGenerator.generate(any(), any(), any(), any(), any())).thenReturn(newContent);
        when(guideRepository.save(any(GuideEntity.class))).thenAnswer(invocation -> {
            GuideEntity saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(8L);
            }
            return saved;
        });

        GuideRegenerateResponse response = guideService.regenerate(7L);

        assertThat(response.oldGuideId()).isEqualTo(7L);
        assertThat(response.newGuideId()).isEqualTo(8L);
        assertThat(response.status()).isEqualTo("DRAFT");
    }
}
