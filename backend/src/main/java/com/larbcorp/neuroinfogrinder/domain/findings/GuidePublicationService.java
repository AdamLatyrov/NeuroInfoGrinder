package com.larbcorp.neuroinfogrinder.domain.findings;

import com.larbcorp.neuroinfogrinder.domain.findings.dto.GuidePublicationLogResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.GuidePublicationResultResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.GuidePublicationSettingsResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.UpdateGuidePublicationSettingsRequest;
import com.larbcorp.neuroinfogrinder.domain.messages.TelegramMessageLink;
import com.larbcorp.neuroinfogrinder.domain.messages.TelegramMessageLinkBuilder;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuidePublicationLogEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuidePublicationSettingsEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideSourceMessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuidePublicationLogRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuidePublicationSettingsRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuideRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuideSourceMessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.telegram.TelegramTdlibService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GuidePublicationService {

    private static final int TELEGRAM_SAFE_TEXT_LIMIT = 3900;
    private static final Set<String> DEFAULT_SEND_STATUSES = Set.of("DRAFT", "APPROVED");

    private final GuideRepository guideRepository;
    private final GroupRepository groupRepository;
    private final GuideSourceMessageRepository guideSourceMessageRepository;
    private final MessageRepository messageRepository;
    private final GuidePublicationSettingsRepository settingsRepository;
    private final GuidePublicationLogRepository logRepository;
    private final TelegramTdlibService telegramTdlibService;

    @Transactional
    public GuidePublicationSettingsResponse getSettingsResponse() {
        return getSettingsResponse(null);
    }

    @Transactional
    public GuidePublicationSettingsResponse getSettingsResponse(Long ownerUserId) {
        return toSettingsResponse(getOrCreateSettings(ownerUserId));
    }

    @Transactional
    public GuidePublicationSettingsResponse updateSettings(UpdateGuidePublicationSettingsRequest request) {
        return updateSettings(null, request);
    }

    @Transactional
    public GuidePublicationSettingsResponse updateSettings(Long ownerUserId, UpdateGuidePublicationSettingsRequest request) {
        GuidePublicationSettingsEntity settings = getOrCreateSettings(ownerUserId);
        settings.setMode("TDLIB_ACCOUNT");
        if (request.enabled() != null) {
            settings.setEnabled(request.enabled());
        }
        if (request.targetGroupId() != null) {
            GroupEntity group = resolveTargetGroupById(ownerUserId, request.targetGroupId());
            settings.setTargetGroupId(group.getId());
            settings.setTargetTelegramChatId(group.getTelegramChatId());
        } else if (request.targetTelegramChatId() != null) {
            if (ownerUserId != null) {
                GroupEntity group = groupRepository.findByTelegramChatIdAndOwnerUserId(request.targetTelegramChatId(), ownerUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Target group not found for current user: " + request.targetTelegramChatId()));
                settings.setTargetGroupId(group.getId());
                settings.setTargetTelegramChatId(group.getTelegramChatId());
            } else {
                settings.setTargetGroupId(null);
                settings.setTargetTelegramChatId(request.targetTelegramChatId());
            }
        }
        settings.setTargetTopicId(request.targetTopicId());
        if (request.appendSourceLink() != null) {
            settings.setAppendSourceLink(request.appendSourceLink());
        }
        if (request.minConfidence() != null) {
            settings.setMinConfidence(Math.max(0.0, Math.min(1.0, request.minConfidence())));
        }
        if (request.sendOnlyStatuses() != null && !request.sendOnlyStatuses().isEmpty()) {
            settings.setSendOnlyStatuses(String.join(",", normalizeStatuses(request.sendOnlyStatuses())));
        }
        return toSettingsResponse(settingsRepository.save(settings));
    }

    @Transactional
    public GuidePublicationResultResponse sendGuideViaTelegramAccount(Long guideId, Long targetGroupId, Long topicId, boolean force) {
        return sendGuideViaTelegramAccount(null, guideId, targetGroupId, topicId, force);
    }

    @Transactional
    public GuidePublicationResultResponse sendGuideViaTelegramAccount(Long ownerUserId, Long guideId, Long targetGroupId, Long topicId, boolean force) {
        SendOutcome outcome = sendOne(ownerUserId, guideId, targetGroupId, topicId, force, false, false);
        return toResult(1, List.of(outcome));
    }

    @Transactional
    public GuidePublicationResultResponse sendSelected(List<Long> guideIds, boolean force, boolean dryRun) {
        return sendSelected(null, guideIds, force, dryRun);
    }

    @Transactional
    public GuidePublicationResultResponse sendSelected(Long ownerUserId, List<Long> guideIds, boolean force, boolean dryRun) {
        List<Long> ids = guideIds == null ? List.of() : guideIds.stream().filter(Objects::nonNull).distinct().toList();
        List<SendOutcome> outcomes = ids.stream()
                .map(id -> sendOne(ownerUserId, id, null, null, force, dryRun, false))
                .toList();
        return toResult(ids.size(), outcomes);
    }

    @Transactional
    public GuidePublicationResultResponse sendAllUnsent(int limit, boolean dryRun) {
        return sendAllUnsent(null, limit, dryRun);
    }

    @Transactional
    public GuidePublicationResultResponse sendAllUnsent(Long ownerUserId, int limit, boolean dryRun) {
        int boundedLimit = Math.max(1, Math.min(limit, 500));
        List<Long> ids = (ownerUserId != null ? guideRepository.findByOwnerUserId(ownerUserId) : guideRepository.findAll()).stream()
                .filter(this::isGuideContent)
                .filter(guide -> !wasGuideSent(ownerUserId, guide.getId()))
                .limit(boundedLimit)
                .map(GuideEntity::getId)
                .toList();
        List<SendOutcome> outcomes = ids.stream()
                .map(id -> sendOne(ownerUserId, id, null, null, false, dryRun, false))
                .toList();
        return toResult(ids.size(), outcomes);
    }

    @Transactional
    public GuidePublicationResultResponse sendTest() {
        return sendTest(null);
    }

    @Transactional
    public GuidePublicationResultResponse sendTest(Long ownerUserId) {
        GuidePublicationSettingsEntity settings = getOrCreateSettings(ownerUserId);
        GroupEntity targetGroup = resolveTargetGroup(settings, null, ownerUserId);
        Long chatId = resolveChatId(settings, targetGroup);
        Long topicId = settings.getTargetTopicId();
        if (ownerUserId != null && (targetGroup == null || targetGroup.getAccountId() == null)) {
            return new GuidePublicationResultResponse(1, 0, 1, 0);
        }
        try {
            if (targetGroup != null && targetGroup.getAccountId() != null) {
                telegramTdlibService.sendTextMessage(targetGroup.getAccountId(), chatId, topicId, "NeuroInfoGrinder: test publication message");
            } else {
                telegramTdlibService.sendTextMessage(chatId, topicId, "NeuroInfoGrinder: test publication message");
            }
            return new GuidePublicationResultResponse(1, 1, 0, 0);
        } catch (RuntimeException exception) {
            return new GuidePublicationResultResponse(1, 0, 0, 1);
        }
    }

    @Transactional(readOnly = true)
    public List<GuidePublicationLogResponse> getLogs(Long guideId) {
        return getLogs(null, guideId);
    }

    @Transactional(readOnly = true)
    public List<GuidePublicationLogResponse> getLogs(Long ownerUserId, Long guideId) {
        if (ownerUserId != null && guideId != null) {
            requireGuide(ownerUserId, guideId);
        }
        List<GuidePublicationLogEntity> logs = ownerUserId != null
                ? guideId == null
                    ? logRepository.findByOwnerUserIdOrderByCreatedAtDesc(ownerUserId)
                    : logRepository.findByGuideIdAndOwnerUserIdOrderByCreatedAtDesc(guideId, ownerUserId)
                : guideId == null
                    ? logRepository.findAll()
                    : logRepository.findByGuideIdOrderByCreatedAtDesc(guideId);
        return logs.stream().map(this::toLogResponse).toList();
    }

    @Transactional
    public void autoSendAfterGuideCreated(GuideEntity guide) {
        GuidePublicationSettingsEntity settings = getOrCreateSettings(guide.getOwnerUserId());
        if (!Boolean.TRUE.equals(settings.getEnabled())) {
            return;
        }
        try {
            sendOne(guide.getOwnerUserId(), guide.getId(), settings.getTargetGroupId(), settings.getTargetTopicId(), false, false, true);
        } catch (RuntimeException ignored) {
            // Publication failures are already written to guide_publication_log and must not break generation.
        }
    }

    @Transactional(readOnly = true)
    public String latestPublicationStatus(Long guideId) {
        return logRepository.findTopByGuideIdOrderByCreatedAtDesc(guideId)
                .map(GuidePublicationLogEntity::getStatus)
                .orElse("NOT_SENT");
    }

    private SendOutcome sendOne(Long ownerUserId, Long guideId, Long overrideGroupId, Long overrideTopicId, boolean force, boolean dryRun, boolean autoMode) {
        GuideEntity guide = requireGuide(ownerUserId, guideId);
        if (!isGuideContent(guide)) {
            return SendOutcome.SKIPPED;
        }
        Long effectiveOwnerUserId = ownerUserId != null ? ownerUserId : guide.getOwnerUserId();
        GuidePublicationSettingsEntity settings = getOrCreateSettings(effectiveOwnerUserId);
        GroupEntity targetGroup = resolveTargetGroup(settings, overrideGroupId, effectiveOwnerUserId);
        Long chatId = resolveChatId(settings, targetGroup);
        Long topicId = overrideTopicId != null ? overrideTopicId : settings.getTargetTopicId();

        String skipReason = validateBeforeSend(guide, settings, targetGroup, chatId, force, autoMode, effectiveOwnerUserId);
        if (skipReason != null) {
            if (!dryRun) {
                saveLog(guide, settings, targetGroup, chatId, topicId, "SKIPPED", null, skipReason);
            }
            return SendOutcome.SKIPPED;
        }
        if (dryRun) {
            return SendOutcome.SENT;
        }

        try {
            List<String> chunks = splitMessage(buildMessageText(guide, settings));
            Long firstMessageId = null;
            for (String chunk : chunks) {
                long messageId = targetGroup != null && targetGroup.getAccountId() != null
                    ? telegramTdlibService.sendTextMessage(targetGroup.getAccountId(), chatId, topicId, chunk)
                    : telegramTdlibService.sendTextMessage(chatId, topicId, chunk);
                if (firstMessageId == null) {
                    firstMessageId = messageId;
                }
            }
            saveLog(guide, settings, targetGroup, chatId, topicId, "SENT", firstMessageId, null);
            guide.setStatus("PUBLISHED");
            guide.setPublishedAt(Instant.now());
            guideRepository.save(guide);
            return SendOutcome.SENT;
        } catch (RuntimeException exception) {
            saveLog(guide, settings, targetGroup, chatId, topicId, "FAILED", null, sanitizeError(exception));
            return SendOutcome.FAILED;
        }
    }

    private boolean isGuideContent(GuideEntity guide) {
        return guide == null
            || guide.getContentType() == null
            || guide.getContentType().isBlank()
            || "GUIDE".equalsIgnoreCase(guide.getContentType());
    }

    private String validateBeforeSend(
        GuideEntity guide,
        GuidePublicationSettingsEntity settings,
        GroupEntity targetGroup,
        Long chatId,
        boolean force,
        boolean autoMode,
        Long ownerUserId
    ) {
        if (!"TDLIB_ACCOUNT".equals(settings.getMode())) {
            return "Only TDLIB_ACCOUNT mode is supported in MVP";
        }
        if (ownerUserId != null && !Objects.equals(ownerUserId, guide.getOwnerUserId())) {
            return "Guide does not belong to current user";
        }
        if (targetGroup == null && chatId == null) {
            return "Publication target is not configured";
        }
        if (ownerUserId != null && targetGroup == null) {
            return "Owner-scoped publication requires a saved target group";
        }
        if (ownerUserId != null && !Objects.equals(ownerUserId, targetGroup.getOwnerUserId())) {
            return "Target group does not belong to current user";
        }
        if (ownerUserId != null && targetGroup.getAccountId() == null) {
            return "Target group has no Telegram account";
        }
        if (targetGroup != null && !Boolean.TRUE.equals(targetGroup.getEnabled())) {
            return "Target group is disabled";
        }
        if (chatId == null) {
            return "Target Telegram chat ID is not configured";
        }
        if (!force && logRepository.existsByGuideIdAndStatus(guide.getId(), "SENT")) {
            return "Guide was already sent";
        }
        if (!isValidTitle(guide.getTitle())) {
            return "Guide title is empty or unsafe";
        }
        if (isBlank(guide.getContentMarkdown()) && isBlank(guide.getContent())) {
            return "Guide content is empty";
        }
        if ("FAILED".equals(guide.getStatus()) || !isBlank(guide.getGenerationError())) {
            return "Failed guides are not sent";
        }
        if (!parseStatuses(settings.getSendOnlyStatuses()).contains(guide.getStatus())) {
            return "Guide status is not allowed for publication";
        }
        if (autoMode && guide.getConfidence() != null && guide.getConfidence() < settings.getMinConfidence()) {
            return "Guide confidence is below publication threshold";
        }
        if (autoMode && guide.getConfidence() == null) {
            return "Guide confidence is missing";
        }
        return null;
    }

    private GuidePublicationSettingsEntity getOrCreateSettings() {
        return getOrCreateSettings(null);
    }

    private GuidePublicationSettingsEntity getOrCreateSettings(Long ownerUserId) {
        if (ownerUserId != null) {
            return settingsRepository.findFirstByOwnerUserIdOrderByIdAsc(ownerUserId)
                .orElseGet(() -> {
                    GuidePublicationSettingsEntity settings = new GuidePublicationSettingsEntity();
                    settings.setOwnerUserId(ownerUserId);
                    return settingsRepository.save(settings);
                });
        }
        return settingsRepository.findFirstByOrderByIdAsc()
            .orElseGet(() -> settingsRepository.save(new GuidePublicationSettingsEntity()));
    }

    private GroupEntity resolveTargetGroup(GuidePublicationSettingsEntity settings, Long overrideGroupId, Long ownerUserId) {
        Long groupId = overrideGroupId != null ? overrideGroupId : settings.getTargetGroupId();
        if (groupId == null) {
            return null;
        }
        return resolveTargetGroupById(ownerUserId, groupId);
    }

    private GroupEntity resolveTargetGroupById(Long ownerUserId, Long groupId) {
        return (ownerUserId != null
                ? groupRepository.findByIdAndOwnerUserId(groupId, ownerUserId)
                : groupRepository.findById(groupId))
            .orElseThrow(() -> new IllegalArgumentException("Target group not found: " + groupId));
    }

    private Long resolveChatId(GuidePublicationSettingsEntity settings, GroupEntity group) {
        if (group != null) {
            return group.getTelegramChatId();
        }
        return settings.getTargetTelegramChatId();
    }

    private String buildMessageText(GuideEntity guide, GuidePublicationSettingsEntity settings) {
        StringBuilder text = new StringBuilder();
        text.append(guide.getTitle().trim()).append("\n\n");
        text.append(!isBlank(guide.getContentMarkdown()) ? guide.getContentMarkdown().trim() : guide.getContent().trim());
        if (Boolean.TRUE.equals(settings.getAppendSourceLink())) {
            String sourceLink = resolveSourceLink(guide);
            if (!isBlank(sourceLink)) {
                text.append("\n\n").append("Источник: ").append(sourceLink);
            }
        }
        return text.toString();
    }

    private String resolveSourceLink(GuideEntity guide) {
        List<GuideSourceMessageEntity> sourceMessages = guideSourceMessageRepository.findByGuideId(guide.getId());
        Long rootMessageId = guide.getRootMessageId() != null
                ? guide.getRootMessageId()
                : sourceMessages.stream().map(GuideSourceMessageEntity::getMessageId).findFirst().orElse(null);
        if (rootMessageId == null) {
            return null;
        }
        MessageEntity message = messageRepository.findById(rootMessageId).orElse(null);
        GroupEntity group = groupRepository.findById(guide.getGroupId()).orElse(null);
        TelegramMessageLink link = TelegramMessageLinkBuilder.buildLink(group, message);
        return link.available() ? link.url() : null;
    }

    private List<String> splitMessage(String message) {
        if (message.length() <= TELEGRAM_SAFE_TEXT_LIMIT) {
            return List.of(message);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < message.length()) {
            int end = Math.min(start + TELEGRAM_SAFE_TEXT_LIMIT, message.length());
            int splitAt = message.lastIndexOf("\n\n", end);
            if (splitAt <= start + 500) {
                splitAt = message.lastIndexOf("\n", end);
            }
            if (splitAt <= start + 500) {
                splitAt = end;
            }
            chunks.add(message.substring(start, splitAt).trim());
            start = splitAt;
        }
        return chunks.stream().filter(chunk -> !chunk.isBlank()).toList();
    }

    private void saveLog(
        GuideEntity guide,
        GuidePublicationSettingsEntity settings,
        GroupEntity targetGroup,
        Long chatId,
        Long topicId,
        String status,
        Long telegramMessageId,
        String error
    ) {
        GuidePublicationLogEntity log = new GuidePublicationLogEntity();
        log.setOwnerUserId(guide.getOwnerUserId());
        log.setGuideId(guide.getId());
        log.setTargetMode(settings.getMode());
        log.setTargetGroupId(targetGroup != null ? targetGroup.getId() : settings.getTargetGroupId());
        log.setTargetTelegramChatId(chatId);
        log.setTargetTopicId(topicId);
        log.setStatus(status);
        log.setTelegramMessageId(telegramMessageId);
        log.setError(error);
        log.setSentAt("SENT".equals(status) ? Instant.now() : null);
        logRepository.save(log);
    }

    private GuideEntity requireGuide(Long ownerUserId, Long guideId) {
        return (ownerUserId != null
                ? guideRepository.findByIdAndOwnerUserId(guideId, ownerUserId)
                : guideRepository.findById(guideId))
            .orElseThrow(() -> new IllegalArgumentException("Guide not found: " + guideId));
    }

    private boolean wasGuideSent(Long ownerUserId, Long guideId) {
        return ownerUserId != null
            ? logRepository.existsByGuideIdAndOwnerUserIdAndStatus(guideId, ownerUserId, "SENT")
            : logRepository.existsByGuideIdAndStatus(guideId, "SENT");
    }

    private GuidePublicationResultResponse toResult(int requested, List<SendOutcome> outcomes) {
        int sent = (int) outcomes.stream().filter(outcome -> outcome == SendOutcome.SENT).count();
        int skipped = (int) outcomes.stream().filter(outcome -> outcome == SendOutcome.SKIPPED).count();
        int failed = (int) outcomes.stream().filter(outcome -> outcome == SendOutcome.FAILED).count();
        return new GuidePublicationResultResponse(requested, sent, skipped, failed);
    }

    private GuidePublicationSettingsResponse toSettingsResponse(GuidePublicationSettingsEntity settings) {
        return new GuidePublicationSettingsResponse(
            settings.getEnabled(),
            settings.getMode(),
            settings.getTargetGroupId(),
            settings.getTargetTelegramChatId(),
            settings.getTargetTopicId(),
            settings.getAppendSourceLink(),
            settings.getMinConfidence(),
            parseStatuses(settings.getSendOnlyStatuses()).stream().toList(),
            settings.getFormat(),
            settings.getCreatedAt(),
            settings.getUpdatedAt()
        );
    }

    private GuidePublicationLogResponse toLogResponse(GuidePublicationLogEntity log) {
        return new GuidePublicationLogResponse(
            log.getId(),
            log.getGuideId(),
            log.getTargetMode(),
            log.getTargetGroupId(),
            log.getTargetTelegramChatId(),
            log.getTargetTopicId(),
            log.getStatus(),
            log.getTelegramMessageId(),
            log.getError(),
            log.getCreatedAt(),
            log.getSentAt()
        );
    }

    private Set<String> parseStatuses(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_SEND_STATUSES;
        }
        return normalizeStatuses(List.of(value.split(",")));
    }

    private Set<String> normalizeStatuses(List<String> statuses) {
        LinkedHashSet<String> normalized = statuses.stream()
                .filter(Objects::nonNull)
                .map(status -> status.trim().toUpperCase(Locale.ROOT))
                .filter(status -> !status.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return normalized.isEmpty() ? DEFAULT_SEND_STATUSES : normalized;
    }

    private boolean isValidTitle(String title) {
        if (isBlank(title)) {
            return false;
        }
        String trimmed = title.trim();
        return !"{".equals(trimmed) && !trimmed.startsWith("{");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String sanitizeError(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }

    private enum SendOutcome {
        SENT,
        SKIPPED,
        FAILED
    }
}
