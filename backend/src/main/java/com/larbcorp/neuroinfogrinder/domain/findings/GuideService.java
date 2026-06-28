package com.larbcorp.neuroinfogrinder.domain.findings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.GuideDetailResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.GuideRegenerateResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.GuideSummaryResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.LlmRequestDto;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.SourceMessageDto;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.SourceMessageTextEntityDto;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.UpdateGuideContentRequest;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.UpdateGuideStatusRequest;
import com.larbcorp.neuroinfogrinder.domain.messages.TelegramMessageLink;
import com.larbcorp.neuroinfogrinder.domain.messages.TelegramMessageLinkBuilder;
import com.larbcorp.neuroinfogrinder.domain.messages.dto.MessageTextEntityDto;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideSourceMessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuideRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuideSourceMessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuidePublicationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GuideService {

    private static final Pattern PRODUCT_NAME_PATTERN = Pattern.compile(
        "(?iu)(?:название|name)\\s*[:：]\\s*([\\p{L}\\p{N}_.@/-][\\p{L}\\p{N}_.@/ -]{1,48})"
    );

    private final GuideRepository guideRepository;
    private final GuideSourceMessageRepository guideSourceMessageRepository;
    private final MessageRepository messageRepository;
    private final GroupRepository groupRepository;
    private final GuidePublicationLogRepository guidePublicationLogRepository;
    private final GuideGenerator guideGenerator;
    private final GuideUsefulnessScorer guideUsefulnessScorer;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<GuideEntity> getGuides(String status, Long groupId, Long providerId,
                                        String classifier, Double minConfidence,
                                        Double maxCost, Integer minUsefulness,
                                        Integer maxUsefulness, Pageable pageable) {
        return getGuides(null, status, groupId, providerId, classifier, minConfidence, maxCost, minUsefulness, maxUsefulness, pageable);
    }

    @Transactional(readOnly = true)
    public Page<GuideEntity> getGuides(Long ownerUserId, String status, Long groupId, Long providerId,
                                        String classifier, Double minConfidence,
                                        Double maxCost, Integer minUsefulness,
                                        Integer maxUsefulness, Pageable pageable) {
        return getMaterials(ownerUserId, "GUIDE", status, groupId, providerId, classifier,
            minConfidence, maxCost, minUsefulness, maxUsefulness, pageable);
    }

    @Transactional(readOnly = true)
    public Page<GuideEntity> getMaterials(Long ownerUserId, String contentType, String status, Long groupId, Long providerId,
                                          String classifier, Double minConfidence,
                                          Double maxCost, Integer minUsefulness,
                                          Integer maxUsefulness, Pageable pageable) {
        List<GuideEntity> all = ownerUserId != null
            ? guideRepository.findByOwnerUserId(ownerUserId)
            : guideRepository.findAll();
        Set<String> normalizedContentTypes = parseContentTypes(contentType);

        List<GuideEntity> filtered = all.stream()
            .filter(g -> normalizedContentTypes.isEmpty() || normalizedContentTypes.contains(contentTypeOf(g)))
            .filter(g -> status == null || status.equals(g.getStatus()))
            .filter(g -> groupId == null || groupId.equals(g.getGroupId()))
            .filter(g -> providerId == null || providerId.equals(g.getProviderId()))
            .filter(g -> classifier == null || classifier.equals(String.valueOf(g.getClassifierId())))
            .filter(g -> minConfidence == null || g.getConfidence() != null && g.getConfidence() >= minConfidence)
            .filter(g -> maxCost == null || g.getEstimatedCostUsd() != null && g.getEstimatedCostUsd() <= maxCost)
            .filter(g -> minUsefulness == null || resolveUsefulnessScore(g, null, null) >= minUsefulness)
            .filter(g -> maxUsefulness == null || resolveUsefulnessScore(g, null, null) <= maxUsefulness)
            .sorted(guideComparator(pageable))
            .toList();

        int start = (int) Math.min(pageable.getOffset(), filtered.size());
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<GuideEntity> pageContent = filtered.subList(start, end);

        return new PageImpl<>(pageContent, pageable, filtered.size());
    }

    @Transactional(readOnly = true)
    public GuideDetailResponse getGuideDetail(Long id) {
        return getGuideDetail(null, id);
    }

    @Transactional(readOnly = true)
    public GuideDetailResponse getGuideDetail(Long ownerUserId, Long id) {
        GuideEntity guide = getById(ownerUserId, id);
        if (!"GUIDE".equals(contentTypeOf(guide))) {
            throw new IllegalArgumentException("Guide not found: " + id);
        }
        return toDetailResponse(guide, id);
    }

    @Transactional(readOnly = true)
    public GuideDetailResponse getMaterialDetail(Long ownerUserId, Long id) {
        GuideEntity guide = getById(ownerUserId, id);
        return toDetailResponse(guide, id);
    }

    private GuideDetailResponse toDetailResponse(GuideEntity guide, Long id) {
        GroupEntity group = groupRepository.findById(guide.getGroupId()).orElse(null);

        List<GuideSourceMessageEntity> sourceMsgs = guideSourceMessageRepository.findByGuideId(id);
        Map<Long, MessageEntity> messageMap = loadMessageMap(sourceMsgs);
        MessageEntity rootMessage = resolveRootMessage(guide, sourceMsgs, messageMap);
        ClassifierResult classifierResult = rootMessage != null ? parseClassifierResult(rootMessage) : null;

        List<SourceMessageDto> sourceMessageDtos = sourceMsgs.stream()
            .sorted((left, right) -> compareSourceMessages(left, right, messageMap))
            .map(source -> toSourceMessageDto(source, messageMap.get(source.getMessageId()), group, rootMessage))
            .toList();

        LlmRequestDto llmRequest = new LlmRequestDto(
            guide.getProviderId(),
            guide.getModel(),
            guide.getPromptId(),
            guide.getPromptVersion(),
            guide.getInputTokens(),
            guide.getOutputTokens(),
            guide.getTotalTokens(),
            guide.getEstimatedCostUsd()
        );

        List<GuideEntity> ownerGuides = guide.getOwnerUserId() != null
            ? guideRepository.findByOwnerUserId(guide.getOwnerUserId())
            : guideRepository.findAll();

        List<Long> relatedGuideIds = ownerGuides.stream()
            .filter(g -> !g.getId().equals(id) && Objects.equals(g.getGroupId(), guide.getGroupId()))
            .map(GuideEntity::getId)
            .toList();

        List<Long> possibleDuplicateIds = ownerGuides.stream()
            .filter(g -> id.equals(g.getDuplicateOfId()))
            .map(GuideEntity::getId)
            .toList();

        String displayTitle = displayGuideTitle(guide);
        String displayTopicLabel = displayTopicLabel(guide, displayTitle);
        String displayTopicSummary = displaySummary(guide.getTopicSummary());
        String displayContentTitle = displayContentTitle(guide, displayTitle);
        String displayContentSummary = displaySummary(guide.getContentSummary());

        return new GuideDetailResponse(
            guide.getId(),
            displayTitle,
            guide.getGroupId(),
            group != null ? group.getTitle() : null,
            guide.getRootMessageId(),
            contentTypeOf(guide),
            guide.getContentSubtype(),
            displayTopicLabel,
            displayTopicSummary,
            displayContentTitle,
            displayContentSummary,
            guide.getNormalizedTopicKey(),
            guide.getContentQualityScore(),
            guide.getImportanceScore(),
            guide.getActionabilityScore(),
            guide.getNoveltyScore(),
            guide.getEvidenceScore(),
            guide.getRiskScore(),
            guide.getConfidenceScore(),
            guide.getNoiseScore(),
            guide.getRoutingReason(),
            guide.getSafetyCategory(),
            guide.getPublicationKind(),
            guide.getProviderId(),
            guide.getModel(),
            guide.getClassifierId(),
            guide.getPromptId(),
            guide.getPromptVersion(),
            guide.getStatus(),
            guide.getDuplicateOfId(),
            guide.getDuplicateScore(),
            guide.getConfidence(),
            resolveUsefulnessScore(guide, rootMessage, classifierResult),
            guide.getTotalTokens(),
            guide.getEstimatedCostUsd(),
            parseTags(guide.getTagsJson()),
            guide.getGenerationError(),
            guide.getRawResponse(),
            guide.getRegeneratedFromGuideId(),
            guide.getPublishedAt(),
            guide.getCreatedAt(),
            guide.getContent(),
            guide.getContentMarkdown(),
            sourceMessageDtos,
            llmRequest,
            relatedGuideIds,
            possibleDuplicateIds
        );
    }

    @Transactional
    public GuideRegenerateResponse regenerate(Long id) {
        return regenerate(null, id);
    }

    @Transactional
    public GuideRegenerateResponse regenerate(Long ownerUserId, Long id) {
        GuideEntity sourceGuide = getById(ownerUserId, id);
        Long guideId = sourceGuide.getId();
        Long previousProviderId = sourceGuide.getProviderId();
        String previousModel = sourceGuide.getModel();
        List<GuideSourceMessageEntity> sourceLinks = guideSourceMessageRepository.findByGuideId(id);
        Map<Long, MessageEntity> messageMap = loadMessageMap(sourceLinks);
        List<MessageEntity> chain = sourceLinks.stream()
            .map(GuideSourceMessageEntity::getMessageId)
            .map(messageMap::get)
            .filter(Objects::nonNull)
            .sorted(this::compareMessages)
            .toList();

        if (chain.isEmpty()) {
            throw new IllegalArgumentException("Guide has no source messages: " + id);
        }

        MessageEntity rootMessage = resolveRootMessage(sourceGuide, sourceLinks, messageMap);
        ClassifierResult classifierResult = parseClassifierResult(rootMessage);

        GuideContent regenerated = guideGenerator.generate(
            chain,
            classifierResult,
            sourceGuide.getPromptId(),
            rootMessage != null ? rootMessage.getId() : chain.get(0).getId()
        );

        sourceGuide.setTitle(displayGuideTitle(regenerated.title()));
        sourceGuide.setContent(regenerated.content());
        sourceGuide.setContentMarkdown(regenerated.contentMarkdown());
        sourceGuide.setRawResponse(regenerated.rawResponse());
        sourceGuide.setRootMessageId(rootMessage != null ? rootMessage.getId() : sourceGuide.getRootMessageId());
        sourceGuide.setProviderId(regenerated.providerId() != null ? regenerated.providerId() : sourceGuide.getProviderId());
        sourceGuide.setModel(regenerated.model() != null ? regenerated.model() : sourceGuide.getModel());
        sourceGuide.setStatus(regenerated.generationError() != null ? "FAILED" : "DRAFT");
        sourceGuide.setConfidence(regenerated.confidence());
        sourceGuide.setTagsJson(writeJson(regenerated.tags()));
        sourceGuide.setGenerationError(regenerated.generationError());
        sourceGuide.setRegeneratedFromGuideId(null);
        sourceGuide.setUsefulnessScore(guideUsefulnessScorer.score(sourceGuide, rootMessage, classifierResult));
        GuideEntity savedGuide = guideRepository.save(sourceGuide);

        log.info(
            "Regenerating guide #{} through provider router: previousProvider={}/{}, routedProvider={}/{}, generationError={}",
            savedGuide.getId(),
            previousProviderId,
            previousModel,
            regenerated.providerId(),
            regenerated.model(),
            regenerated.generationError()
        );

        return new GuideRegenerateResponse(guideId, savedGuide.getId(), savedGuide.getStatus());
    }

    @Transactional
    public GuideEntity updateStatus(Long id, String status) {
        return updateStatus(null, id, status);
    }

    @Transactional
    public GuideEntity updateStatus(Long ownerUserId, Long id, String status) {
        GuideEntity guide = getById(ownerUserId, id);
        guide.setStatus(status);
        return guideRepository.save(guide);
    }

    @Transactional
    public GuideEntity updateContent(Long id, UpdateGuideContentRequest request) {
        return updateContent(null, id, request);
    }

    @Transactional
    public GuideEntity updateContent(Long ownerUserId, Long id, UpdateGuideContentRequest request) {
        GuideEntity guide = getById(ownerUserId, id);
        guide.setTitle(request.title());
        guide.setContent(request.content());
        guide.setContentMarkdown(request.contentMarkdown());
        guide.setUsefulnessScore(guideUsefulnessScorer.score(guide, null, null));
        return guideRepository.save(guide);
    }

    @Transactional
    public GuideEntity markNotDuplicate(Long id) {
        return markNotDuplicate(null, id);
    }

    @Transactional
    public GuideEntity markNotDuplicate(Long ownerUserId, Long id) {
        GuideEntity guide = getById(ownerUserId, id);
        guide.setDuplicateOfId(null);
        guide.setDuplicateScore(null);
        return guideRepository.save(guide);
    }

    @Transactional
    public void delete(Long id) {
        delete(null, id);
    }

    @Transactional
    public void delete(Long ownerUserId, Long id) {
        GuideEntity guide = getById(ownerUserId, id);
        deleteGuideAndDetachMessages(guide);
    }

    @Transactional
    public int bulkDelete(List<Long> ids) {
        return bulkDelete(null, ids);
    }

    @Transactional
    public int bulkDelete(Long ownerUserId, List<Long> ids) {
        LinkedHashSet<Long> uniqueIds = ids == null
            ? new LinkedHashSet<>()
            : ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (uniqueIds.isEmpty()) {
            return 0;
        }

        List<GuideEntity> guides = guideRepository.findAllById(uniqueIds);
        if (ownerUserId != null && guides.stream().anyMatch(guide -> !ownerUserId.equals(guide.getOwnerUserId()))) {
            throw new IllegalArgumentException("One or more guides were not found");
        }
        guides.forEach(this::deleteGuideAndDetachMessages);
        return guides.size();
    }

    public GuideSummaryResponse toSummaryResponse(GuideEntity guide) {
        GroupEntity group = groupRepository.findById(guide.getGroupId()).orElse(null);
        int sourceCount = Math.toIntExact(guideSourceMessageRepository.countByGuideId(guide.getId()));
        String displayTitle = displayGuideTitle(guide);
        String displayTopicLabel = displayTopicLabel(guide, displayTitle);
        String displayTopicSummary = displaySummary(guide.getTopicSummary());
        String displayContentTitle = displayContentTitle(guide, displayTitle);
        String displayContentSummary = displaySummary(guide.getContentSummary());
        return new GuideSummaryResponse(
            guide.getId(),
            displayTitle,
            guide.getGroupId(),
            group != null ? group.getTitle() : null,
            guide.getRootMessageId(),
            contentTypeOf(guide),
            guide.getContentSubtype(),
            displayTopicLabel,
            displayTopicSummary,
            displayContentTitle,
            displayContentSummary,
            guide.getNormalizedTopicKey(),
            guide.getContentQualityScore(),
            guide.getImportanceScore(),
            guide.getActionabilityScore(),
            guide.getNoveltyScore(),
            guide.getEvidenceScore(),
            guide.getRiskScore(),
            guide.getConfidenceScore(),
            guide.getNoiseScore(),
            guide.getRoutingReason(),
            guide.getSafetyCategory(),
            guide.getPublicationKind(),
            guide.getProviderId(),
            guide.getModel(),
            guide.getClassifierId(),
            guide.getPromptId(),
            guide.getPromptVersion(),
            guide.getStatus(),
            guide.getDuplicateOfId(),
            guide.getDuplicateScore(),
            guide.getConfidence(),
            resolveUsefulnessScore(guide, null, null),
            guide.getTotalTokens(),
            guide.getEstimatedCostUsd(),
            parseTags(guide.getTagsJson()),
            guide.getGenerationError(),
            sourceCount,
            latestPublicationStatus(guide.getId()),
            guide.getPublishedAt(),
            guide.getCreatedAt()
        );
    }

    private String latestPublicationStatus(Long guideId) {
        var latest = guidePublicationLogRepository.findTopByGuideIdOrderByCreatedAtDesc(guideId);
        if (latest == null || latest.isEmpty()) {
            return "NOT_SENT";
        }
        return latest.get().getStatus();
    }

    private Map<Long, MessageEntity> loadMessageMap(List<GuideSourceMessageEntity> sourceMsgs) {
        return sourceMsgs.isEmpty()
            ? Map.of()
            : messageRepository.findAllById(sourceMsgs.stream().map(GuideSourceMessageEntity::getMessageId).toList())
                .stream()
                .collect(Collectors.toMap(MessageEntity::getId, Function.identity()));
    }

    private SourceMessageDto toSourceMessageDto(
        GuideSourceMessageEntity source,
        MessageEntity message,
        GroupEntity group,
        MessageEntity rootMessage
    ) {
        TelegramMessageLink telegramLink = TelegramMessageLinkBuilder.buildLink(group, message);
        return new SourceMessageDto(
            source.getMessageId(),
            message != null ? message.getGroupId() : null,
            group != null ? group.getTelegramChatId() : null,
            message != null ? message.getTelegramMessageId() : null,
            resolveSenderDisplayName(message),
            message != null ? message.getSenderUsername() : null,
            message != null ? message.getSenderTelegramUserId() : null,
            resolveSenderNameSource(message),
            message != null ? message.getText() : null,
            parseTextEntities(message != null ? message.getTextEntitiesJson() : null),
            source.getUsedInPrompt(),
            resolveRelation(rootMessage, message),
            message != null ? message.getReplyToMessageId() : null,
            message != null ? message.getTopicId() : null,
            message != null ? message.getTopicName() : null,
            message != null && message.getGroupId() != null ? "/groups?group=" + message.getGroupId() + "&message=" + message.getId() : null,
            telegramLink.url(),
            telegramLink.available(),
            telegramLink.reason()
        );
    }

    private String resolveSenderDisplayName(MessageEntity message) {
        if (message == null) {
            return null;
        }
        if (message.getSenderName() != null && !message.getSenderName().isBlank()) {
            return message.getSenderName();
        }
        if (message.getSenderUsername() != null && !message.getSenderUsername().isBlank()) {
            return "@" + message.getSenderUsername();
        }
        if (message.getSenderTelegramUserId() != null) {
            return "User " + message.getSenderTelegramUserId();
        }
        return "Unknown";
    }

    private String resolveSenderNameSource(MessageEntity message) {
        if (message == null) {
            return null;
        }
        if (message.getSenderName() != null && !message.getSenderName().isBlank()) {
            return "display_name";
        }
        if (message.getSenderUsername() != null && !message.getSenderUsername().isBlank()) {
            return "username";
        }
        if (message.getSenderTelegramUserId() != null) {
            return "telegram_user_id";
        }
        return "unknown";
    }

    private List<SourceMessageTextEntityDto> parseTextEntities(String textEntitiesJson) {
        if (textEntitiesJson == null || textEntitiesJson.isBlank()) {
            return List.of();
        }
        try {
            List<MessageTextEntityDto> entities = objectMapper.readValue(
                textEntitiesJson,
                new TypeReference<List<MessageTextEntityDto>>() {}
            );
            return entities.stream()
                .map(entity -> new SourceMessageTextEntityDto(
                    entity.type(),
                    entity.offset(),
                    entity.length(),
                    entity.url(),
                    entity.text()
                ))
                .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private ClassifierResult parseClassifierResult(MessageEntity rootMessage) {
        if (rootMessage != null
            && rootMessage.getClassifierResultJson() != null
            && !rootMessage.getClassifierResultJson().isBlank()) {
            try {
                return objectMapper.readValue(rootMessage.getClassifierResultJson(), ClassifierResult.class);
            } catch (Exception ignored) {
                // fall through
            }
        }
        return new ClassifierResult(
            rootMessage != null && rootMessage.getClassifierScore() != null ? rootMessage.getClassifierScore() : 0.0,
            true,
            List.of(),
            false,
            rootMessage != null ? List.of(rootMessage.getId()) : List.of(),
            rootMessage != null && rootMessage.getClassifierReason() != null
                ? rootMessage.getClassifierReason()
                : "Guide regenerated from stored source messages"
        );
    }

    private void deleteGuideAndDetachMessages(GuideEntity guide) {
        guideSourceMessageRepository.findByGuideId(guide.getId())
            .forEach(sm -> guideSourceMessageRepository.deleteById(sm.getId()));

        List<MessageEntity> linkedMessages = messageRepository.findByGuideId(guide.getId());
        linkedMessages.forEach(message -> {
            message.setGuideId(null);
            if ("GUIDE_FOUND".equals(message.getProcessingStatus())) {
                message.setProcessingStatus("SKIPPED");
            }
        });
        if (!linkedMessages.isEmpty()) {
            messageRepository.saveAll(linkedMessages);
        }

        guideRepository.deleteById(guide.getId());
    }

    private GuideEntity getById(Long id) {
        return getById(null, id);
    }

    private GuideEntity getById(Long ownerUserId, Long id) {
        return (ownerUserId != null ? guideRepository.findByIdAndOwnerUserId(id, ownerUserId) : guideRepository.findById(id))
            .orElseThrow(() -> new IllegalArgumentException("Guide not found: " + id));
    }

    private String contentTypeOf(GuideEntity guide) {
        if (guide == null || guide.getContentType() == null || guide.getContentType().isBlank()) {
            return "GUIDE";
        }
        return guide.getContentType().trim().toUpperCase();
    }

    private Set<String> parseContentTypes(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(contentType.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(String::toUpperCase)
            .collect(Collectors.toSet());
    }

    private MessageEntity resolveRootMessage(
        GuideEntity guide,
        List<GuideSourceMessageEntity> sourceMsgs,
        Map<Long, MessageEntity> messageMap
    ) {
        if (guide.getRootMessageId() != null) {
            return messageMap.get(guide.getRootMessageId());
        }
        return sourceMsgs.stream()
            .map(GuideSourceMessageEntity::getMessageId)
            .map(messageMap::get)
            .filter(Objects::nonNull)
            .min(this::compareMessages)
            .orElse(null);
    }

    private int compareSourceMessages(
        GuideSourceMessageEntity left,
        GuideSourceMessageEntity right,
        Map<Long, MessageEntity> messageMap
    ) {
        return compareMessages(messageMap.get(left.getMessageId()), messageMap.get(right.getMessageId()));
    }

    private int compareMessages(MessageEntity left, MessageEntity right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        int byDate = left.getMessageDate().compareTo(right.getMessageDate());
        if (byDate != 0) {
            return byDate;
        }
        return left.getId().compareTo(right.getId());
    }

    private String resolveRelation(MessageEntity root, MessageEntity candidate) {
        if (root == null || candidate == null) {
            return "UNKNOWN";
        }
        if (root.getId().equals(candidate.getId())) {
            return "ROOT";
        }
        if (root.getReplyToMessageId() != null
            && root.getReplyToMessageId().equals(candidate.getTelegramMessageId())) {
            return "PARENT_REPLY";
        }
        if (candidate.getReplyToMessageId() != null
            && candidate.getReplyToMessageId().equals(root.getTelegramMessageId())) {
            return "DIRECT_REPLY";
        }
        if (candidate.getReplyToMessageId() != null || root.getReplyToMessageId() != null) {
            return "THREAD_REPLY";
        }
        if (root.getTopicId() != null && root.getTopicId().equals(candidate.getTopicId())) {
            return "SAME_TOPIC_NEARBY";
        }
        if (Objects.equals(root.getSenderTelegramUserId(), candidate.getSenderTelegramUserId())
            && root.getSenderTelegramUserId() != null) {
            return "SAME_AUTHOR_NEARBY";
        }
        return "TIMELINE_NEARBY";
    }

    private List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private int resolveUsefulnessScore(GuideEntity guide, MessageEntity sourceMessage, ClassifierResult classifierResult) {
        if (guide.getUsefulnessScore() != null) {
            return guide.getUsefulnessScore();
        }
        return guideUsefulnessScorer.score(guide, sourceMessage, classifierResult);
    }

    private Comparator<GuideEntity> guideComparator(Pageable pageable) {
        Comparator<GuideEntity> comparator = null;
        Sort sort = pageable.getSort();
        for (Sort.Order order : sort) {
            Comparator<GuideEntity> propertyComparator = guideComparator(order);
            if (propertyComparator == null) {
                continue;
            }
            comparator = comparator == null ? propertyComparator : comparator.thenComparing(propertyComparator);
        }
        return comparator != null
            ? comparator.thenComparing(this::compareByIdDesc)
            : this::compareByCreatedAtDescThenIdDesc;
    }

    private Comparator<GuideEntity> guideComparator(Sort.Order order) {
        boolean ascending = order.isAscending();
        return switch (order.getProperty()) {
            case "createdAt" -> (left, right) -> compareNullable(left.getCreatedAt(), right.getCreatedAt(), ascending);
            case "updatedAt" -> (left, right) -> compareNullable(left.getUpdatedAt(), right.getUpdatedAt(), ascending);
            case "publishedAt" -> (left, right) -> compareNullable(left.getPublishedAt(), right.getPublishedAt(), ascending);
            case "id" -> (left, right) -> compareNullable(left.getId(), right.getId(), ascending);
            default -> null;
        };
    }

    private int compareByCreatedAtDescThenIdDesc(GuideEntity left, GuideEntity right) {
        int byCreatedAt = compareNullable(left.getCreatedAt(), right.getCreatedAt(), false);
        return byCreatedAt != 0 ? byCreatedAt : compareByIdDesc(left, right);
    }

    private int compareByIdDesc(GuideEntity left, GuideEntity right) {
        return compareNullable(left.getId(), right.getId(), false);
    }

    private <T extends Comparable<? super T>> int compareNullable(T left, T right, boolean ascending) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        int compared = left.compareTo(right);
        return ascending ? compared : -compared;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize guide metadata", exception);
        }
    }

    private String displayGuideTitle(GuideEntity guide) {
        if (guide == null) {
            return "Материал";
        }
        String sourceText = firstNonBlank(
            guide.getContentSummary(),
            guide.getTopicSummary(),
            guide.getContentMarkdown(),
            guide.getContent()
        );
        String inferred = inferTitleFromContent(sourceText);
        if (inferred != null && shouldPreferInferredTitle(inferred, guide)) {
            return inferred;
        }
        String explicit = firstSpecificTitle(
            guide.getContentTitle(),
            guide.getTopicLabel(),
            guide.getTitle()
        );
        if (explicit != null) {
            return explicit;
        }
        if (inferred != null) {
            return inferred;
        }
        return "GUIDE".equals(contentTypeOf(guide)) ? "Гайд #" + guide.getId() : "Материал #" + guide.getId();
    }

    private String displayGuideTitle(String title) {
        String cleaned = cleanDisplayText(title);
        return cleaned != null && !isGenericTitle(cleaned) ? cleaned : "Материал";
    }

    private String displayTopicLabel(GuideEntity guide, String displayTitle) {
        String value = cleanDisplayText(guide.getTopicLabel());
        return isGenericTitle(value) || fieldConflictsWithDisplayTitle(value, displayTitle) ? displayTitle : value;
    }

    private String displayContentTitle(GuideEntity guide, String displayTitle) {
        String value = cleanDisplayText(guide.getContentTitle());
        return isGenericTitle(value) || fieldConflictsWithDisplayTitle(value, displayTitle) ? displayTitle : value;
    }

    private String displaySummary(String value) {
        String cleaned = cleanDisplayText(value);
        return cleaned != null ? cleaned : value;
    }

    private String firstSpecificTitle(String... values) {
        for (String value : values) {
            String cleaned = cleanDisplayText(value);
            if (cleaned != null && !isGenericTitle(cleaned)) {
                return clampWords(cleaned, 14);
            }
        }
        return null;
    }

    private boolean shouldPreferInferredTitle(String inferred, GuideEntity guide) {
        String explicit = firstSpecificTitle(
            guide.getContentTitle(),
            guide.getTopicLabel(),
            guide.getTitle()
        );
        if (explicit == null) {
            return true;
        }
        String normalizedExplicit = normalizeDisplay(explicit);
        String normalizedInferred = normalizeDisplay(inferred);
        return normalizedExplicit.contains("droid") && normalizedInferred.contains("ramteamai")
            || normalizedExplicit.contains("glm") && normalizedInferred.contains("runic")
            || normalizedExplicit.contains("провайдер") && normalizedInferred.contains("verdent");
    }

    private boolean fieldConflictsWithDisplayTitle(String fieldValue, String displayTitle) {
        String normalizedField = normalizeDisplay(fieldValue);
        String normalizedTitle = normalizeDisplay(displayTitle);
        return normalizedTitle.contains("ramteamai") && !normalizedField.contains("ramteamai")
            || normalizedTitle.contains("runic") && !normalizedField.contains("runic")
            || normalizedTitle.contains("verdent.ai") && !normalizedField.contains("verdent.ai");
    }

    private String inferTitleFromContent(String value) {
        String cleaned = cleanDisplayText(value);
        if (cleaned == null || cleaned.isBlank()) {
            return null;
        }
        String normalized = normalizeDisplay(cleaned);
        Matcher productMatcher = PRODUCT_NAME_PATTERN.matcher(cleaned);
        if (productMatcher.find()) {
            String productName = productMatcher.group(1)
                .replaceFirst("(?iu)\\s+(что делает|для кого|github|как запустить).*$", "")
                .replaceAll("\\s+", " ")
                .trim();
            if (!productName.isBlank()) {
                if (normalized.contains("desktop") || normalized.contains("настольн") || normalized.contains("агент")) {
                    return productName + ": desktop-клиент для AI-агентов";
                }
                return productName;
            }
        }
        if (normalized.contains("verdent.ai") && normalized.contains("100 кредит")) {
            return "verdent.ai: расход кредитов на Claude Opus";
        }
        if (normalized.contains("runic") && (normalized.contains("генерац") || normalized.contains("изображ") || normalized.contains("фото"))) {
            return "Runic: пополнение в рублях и генерация изображений";
        }
        if (normalized.contains("runic") && (normalized.contains("новые модели") || normalized.contains("цены") || normalized.contains("openrouter"))) {
            return "Runic: новые модели и цены API";
        }
        if (normalized.contains("подписк") && normalized.contains("api") && normalized.contains("код")) {
            return "Подписка vs API для кодинга";
        }
        if (normalized.contains("итератив") && normalized.contains("планирован") && normalized.contains("gemini")) {
            return "Выбор AI-модели для стратегического планирования";
        }
        if (normalized.contains("smmplanner")) {
            return "SMMplanner для автопостинга Instagram";
        }
        if (normalized.contains("openrouter") && normalized.contains("кредит")) {
            return "OpenRouter и кредиты для AI-моделей";
        }
        return titleFromFirstSentence(cleaned);
    }

    private String titleFromFirstSentence(String value) {
        String cleaned = cleanDisplayText(value);
        if (cleaned == null || cleaned.isBlank()) {
            return null;
        }
        String sentence = cleaned
            .replaceAll("(?m)^#+\\s*", "")
            .replaceAll("\\s+", " ")
            .trim();
        int boundary = sentence.indexOf('.');
        int question = sentence.indexOf('?');
        int exclamation = sentence.indexOf('!');
        int end = sentence.length();
        for (int candidate : List.of(boundary, question, exclamation)) {
            if (candidate > 12) {
                end = Math.min(end, candidate);
            }
        }
        sentence = sentence.substring(0, Math.min(end, sentence.length())).trim();
        if (sentence.length() < 10 || isGenericTitle(sentence)) {
            return null;
        }
        return clampWords(sentence, 12);
    }

    private String cleanDisplayText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value
            .replaceAll("\\s+", " ")
            .trim();
        if ((cleaned.startsWith("{") && cleaned.endsWith("}"))
            || (cleaned.startsWith("[") && cleaned.endsWith("]"))) {
            return null;
        }
        cleaned = cleaned.replaceFirst("(?iu)^(FAQ|Риск|Полезно знать|Полезное|Новость|Обновление|Справка|Предупреждение)\\s*[:：]\\s*(?=Практическ)", "");
        cleaned = cleaned.replaceFirst("(?iu)^Практический гайд по теме кластера\\s*[:：-]?\\s*", "");
        cleaned = cleaned.replaceFirst("(?iu)^Гайд по теме кластера\\s*[:：-]?\\s*", "");
        cleaned = cleaned.replaceFirst("(?iu)^Practical guide by cluster topic\\s*[:：-]?\\s*", "");
        cleaned = cleaned.replaceFirst("(?iu)^Guide angle\\s*[:：-]?\\s*", "");
        return cleaned.isBlank() ? null : cleaned.trim();
    }

    private boolean isGenericTitle(String value) {
        String normalized = normalizeDisplay(value);
        if (normalized.isBlank()) {
            return true;
        }
        return normalized.equals("faq")
            || normalized.equals("полезное")
            || normalized.equals("практический гайд")
            || normalized.equals("гайд по теме")
            || normalized.equals("гайд по теме кластера")
            || normalized.equals("практический гайд по теме кластера")
            || normalized.contains("faq практический гайд")
            || normalized.contains("практический гайд по теме кластера")
            || normalized.contains("practical guide by cluster topic")
            || normalized.contains("topic summary")
            || normalized.contains("useful information");
    }

    private String normalizeDisplay(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}.@/$+-]+", " ")
            .trim()
            .replaceAll("\\s+", " ");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String clampWords(String value, int maxWords) {
        if (value == null) {
            return null;
        }
        String[] words = value.trim().split("\\s+");
        if (words.length <= maxWords) {
            return value.trim();
        }
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < maxWords; i++) {
            selected.add(words[i]);
        }
        return String.join(" ", selected);
    }
}
