package com.larbcorp.neuroinfogrinder.domain.findings;

import com.larbcorp.neuroinfogrinder.domain.messages.TelegramMessageLinkBuilder;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuidePublicationSettingsEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuidePublicationLogRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuidePublicationSettingsRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuideRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuideSourceMessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.telegram.TelegramTdlibService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GuidePublicationServiceTest {

    @Test
    void sendGuideRejectsTargetGroupOwnedByAnotherUser() {
        GuideRepository guideRepository = mock(GuideRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        GuideSourceMessageRepository guideSourceMessageRepository = mock(GuideSourceMessageRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        GuidePublicationSettingsRepository settingsRepository = mock(GuidePublicationSettingsRepository.class);
        GuidePublicationLogRepository logRepository = mock(GuidePublicationLogRepository.class);
        TelegramTdlibService telegramTdlibService = mock(TelegramTdlibService.class);
        GuidePublicationService service = new GuidePublicationService(
            guideRepository,
            groupRepository,
            guideSourceMessageRepository,
            messageRepository,
            settingsRepository,
            logRepository,
            telegramTdlibService
        );

        GuideEntity guide = new GuideEntity();
        guide.setId(69L);
        guide.setOwnerUserId(1L);
        guide.setGroupId(7L);
        guide.setTitle("Guide");
        guide.setStatus("DRAFT");
        guide.setContentMarkdown("# Guide");

        GuidePublicationSettingsEntity settings = new GuidePublicationSettingsEntity();
        settings.setOwnerUserId(1L);
        settings.setMode("TDLIB_ACCOUNT");

        when(guideRepository.findByIdAndOwnerUserId(69L, 1L)).thenReturn(Optional.of(guide));
        when(settingsRepository.findFirstByOwnerUserIdOrderByIdAsc(1L)).thenReturn(Optional.of(settings));
        when(groupRepository.findByIdAndOwnerUserId(70L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendGuideViaTelegramAccount(1L, 69L, 70L, null, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Target group not found");

        verifyNoInteractions(telegramTdlibService);
    }
}
