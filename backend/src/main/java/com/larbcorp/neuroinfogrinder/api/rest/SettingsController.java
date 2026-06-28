package com.larbcorp.neuroinfogrinder.api.rest;

import com.larbcorp.neuroinfogrinder.domain.questions.SettingsService;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.SettingsResponse;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.UpdateSettingsRequest;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.UpdateSettingsSectionRequest;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.SettingsEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public SettingsResponse getSettings() {
        return toResponse(settingsService.getSettings());
    }

    @PutMapping
    public SettingsResponse updateSettings(@RequestBody UpdateSettingsRequest request) {
        return toResponse(settingsService.updateSettings(request));
    }

    @PatchMapping("/{section}")
    public SettingsResponse updateSection(@PathVariable String section,
                                          @RequestBody UpdateSettingsSectionRequest request) {
        return toResponse(settingsService.updateSection(section, request.data()));
    }

    private SettingsResponse toResponse(SettingsEntity entity) {
        return new SettingsResponse(
            new SettingsResponse.Publication(
                entity.getPublicationTargetGroupId(),
                entity.getPublicationMode()
            ),
            entity.getActiveProviderId(),
            new SettingsResponse.Processing(
                entity.getProcessingMode(),
                entity.getPollIntervalSeconds(),
                new SettingsResponse.ChainWindow(
                    entity.getChainIncludeReplies(),
                    entity.getChainTimeWindowMinutes(),
                    entity.getChainMinMessages(),
                    entity.getChainMaxMessages()
                )
            ),
            new SettingsResponse.Filters(
                entity.getFilterSkipBots(),
                entity.getFilterMinMessageLength(),
                entity.getFilterBlacklistWords()
            ),
            new SettingsResponse.Limits(
                entity.getLimitDailyTokenLimit(),
                entity.getLimitMonthlyTokenLimit(),
                entity.getLimitAlertThresholdPct()
            ),
            new SettingsResponse.Notifications(
                entity.getNotificationTelegramChat(),
                entity.getNotificationWebhookUrl()
            )
        );
    }
}
