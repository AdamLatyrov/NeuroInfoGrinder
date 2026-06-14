package com.larbcorp.neuroinfogrinder.domain.questions;

import com.larbcorp.neuroinfogrinder.domain.questions.dto.UpdateSettingsRequest;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.SettingsEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.SettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SettingsRepository settingsRepository;

    @Transactional(readOnly = true)
    public SettingsEntity getSettings() {
        return settingsRepository.findFirstByOrderByIdAsc()
            .orElseThrow(() -> new IllegalStateException("Settings not found"));
    }

    @Transactional
    public SettingsEntity updateSettings(UpdateSettingsRequest request) {
        SettingsEntity entity = getSettings();

        if (request.publicationTargetGroupId() != null) {
            entity.setPublicationTargetGroupId(request.publicationTargetGroupId());
        }
        if (request.publicationMode() != null) {
            entity.setPublicationMode(request.publicationMode());
        }
        if (request.processingMode() != null) {
            entity.setProcessingMode(request.processingMode());
        }
        if (request.pollIntervalSeconds() != null) {
            entity.setPollIntervalSeconds(request.pollIntervalSeconds());
        }
        if (request.chainIncludeReplies() != null) {
            entity.setChainIncludeReplies(request.chainIncludeReplies());
        }
        if (request.chainTimeWindowMinutes() != null) {
            entity.setChainTimeWindowMinutes(request.chainTimeWindowMinutes());
        }
        if (request.chainMinMessages() != null) {
            entity.setChainMinMessages(request.chainMinMessages());
        }
        if (request.chainMaxMessages() != null) {
            entity.setChainMaxMessages(request.chainMaxMessages());
        }
        if (request.filterSkipBots() != null) {
            entity.setFilterSkipBots(request.filterSkipBots());
        }
        if (request.filterMinMessageLength() != null) {
            entity.setFilterMinMessageLength(request.filterMinMessageLength());
        }
        if (request.filterBlacklistWords() != null) {
            entity.setFilterBlacklistWords(request.filterBlacklistWords());
        }
        if (request.limitDailyTokenLimit() != null) {
            entity.setLimitDailyTokenLimit(request.limitDailyTokenLimit());
        }
        if (request.limitMonthlyTokenLimit() != null) {
            entity.setLimitMonthlyTokenLimit(request.limitMonthlyTokenLimit());
        }
        if (request.limitAlertThresholdPct() != null) {
            entity.setLimitAlertThresholdPct(request.limitAlertThresholdPct());
        }
        if (request.notificationTelegramChat() != null) {
            entity.setNotificationTelegramChat(request.notificationTelegramChat());
        }
        if (request.notificationWebhookUrl() != null) {
            entity.setNotificationWebhookUrl(request.notificationWebhookUrl());
        }

        return settingsRepository.save(entity);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public SettingsEntity updateSection(String section, Map<String, Object> data) {
        SettingsEntity entity = getSettings();

        switch (section) {
            case "publication" -> {
                if (data.containsKey("targetGroupId")) {
                    entity.setPublicationTargetGroupId(toLong(data.get("targetGroupId")));
                }
                if (data.containsKey("mode")) {
                    entity.setPublicationMode((String) data.get("mode"));
                }
            }
            case "processing" -> {
                if (data.containsKey("mode")) {
                    entity.setProcessingMode((String) data.get("mode"));
                }
                if (data.containsKey("pollIntervalSeconds")) {
                    entity.setPollIntervalSeconds(toInteger(data.get("pollIntervalSeconds")));
                }
                if (data.containsKey("chainIncludeReplies")) {
                    entity.setChainIncludeReplies(toBoolean(data.get("chainIncludeReplies")));
                }
                if (data.containsKey("chainTimeWindowMinutes")) {
                    entity.setChainTimeWindowMinutes(toInteger(data.get("chainTimeWindowMinutes")));
                }
                if (data.containsKey("chainMinMessages")) {
                    entity.setChainMinMessages(toInteger(data.get("chainMinMessages")));
                }
                if (data.containsKey("chainMaxMessages")) {
                    entity.setChainMaxMessages(toInteger(data.get("chainMaxMessages")));
                }
            }
            case "filters" -> {
                if (data.containsKey("skipBots")) {
                    entity.setFilterSkipBots(toBoolean(data.get("skipBots")));
                }
                if (data.containsKey("minMessageLength")) {
                    entity.setFilterMinMessageLength(toInteger(data.get("minMessageLength")));
                }
                if (data.containsKey("blacklistWords")) {
                    entity.setFilterBlacklistWords((String) data.get("blacklistWords"));
                }
            }
            case "limits" -> {
                if (data.containsKey("dailyTokenLimit")) {
                    entity.setLimitDailyTokenLimit(toLong(data.get("dailyTokenLimit")));
                }
                if (data.containsKey("monthlyTokenLimit")) {
                    entity.setLimitMonthlyTokenLimit(toLong(data.get("monthlyTokenLimit")));
                }
                if (data.containsKey("alertThresholdPercent")) {
                    entity.setLimitAlertThresholdPct(toInteger(data.get("alertThresholdPercent")));
                }
            }
            case "notifications" -> {
                if (data.containsKey("telegramChat")) {
                    entity.setNotificationTelegramChat((String) data.get("telegramChat"));
                }
                if (data.containsKey("webhookUrl")) {
                    entity.setNotificationWebhookUrl((String) data.get("webhookUrl"));
                }
            }
            default -> throw new IllegalArgumentException("Unknown settings section: " + section);
        }

        return settingsRepository.save(entity);
    }

    private static Long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) return Long.parseLong(s);
        throw new IllegalArgumentException("Cannot convert to Long: " + value);
    }

    private static Integer toInteger(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) return Integer.parseInt(s);
        throw new IllegalArgumentException("Cannot convert to Integer: " + value);
    }

    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        throw new IllegalArgumentException("Cannot convert to Boolean: " + value);
    }
}
