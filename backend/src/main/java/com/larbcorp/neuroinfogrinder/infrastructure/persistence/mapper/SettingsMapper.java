package com.larbcorp.neuroinfogrinder.infrastructure.persistence.mapper;

import com.larbcorp.neuroinfogrinder.domain.questions.dto.SettingsResponse;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.SettingsResponse.ChainWindow;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.SettingsResponse.Filters;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.SettingsResponse.Limits;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.SettingsResponse.Notifications;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.SettingsResponse.Processing;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.SettingsResponse.Publication;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.SettingsEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SettingsMapper {

    @Mapping(target = "publication", source = ".")
    @Mapping(target = "processing", source = ".")
    @Mapping(target = "filters", source = ".")
    @Mapping(target = "limits", source = ".")
    @Mapping(target = "notifications", source = ".")
    SettingsResponse toResponse(SettingsEntity entity);

    default Publication mapPublication(SettingsEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Publication(
                entity.getPublicationTargetGroupId(),
                entity.getPublicationMode()
        );
    }

    default Processing mapProcessing(SettingsEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Processing(
                entity.getProcessingMode(),
                entity.getPollIntervalSeconds(),
                new ChainWindow(
                        entity.getChainIncludeReplies(),
                        entity.getChainTimeWindowMinutes(),
                        entity.getChainMinMessages(),
                        entity.getChainMaxMessages()
                )
        );
    }

    default Filters mapFilters(SettingsEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Filters(
                entity.getFilterSkipBots(),
                entity.getFilterMinMessageLength(),
                entity.getFilterBlacklistWords()
        );
    }

    default Limits mapLimits(SettingsEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Limits(
                entity.getLimitDailyTokenLimit(),
                entity.getLimitMonthlyTokenLimit(),
                entity.getLimitAlertThresholdPct()
        );
    }

    default Notifications mapNotifications(SettingsEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Notifications(
                entity.getNotificationTelegramChat(),
                entity.getNotificationWebhookUrl()
        );
    }
}
