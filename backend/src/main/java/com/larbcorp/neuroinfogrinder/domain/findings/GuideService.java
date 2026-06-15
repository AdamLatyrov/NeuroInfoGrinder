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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GuideService {

    private final GuideRepository guideRepository;
    private final GuideSourceMessageRepository guideSourceMessageRepository;
    private final MessageRepository messageRepository;
    private final GroupRepository groupRepository;
    private final AiProviderRepository aiProviderRepository;
    private final GuideGenerator guideGenerator;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<GuideEntity> getGuides(String status, Long groupId, Long providerId,
                                        String classifier, Double minConfidence,
                                        Double maxCost, Pageable pageable) {
        List<GuideEntity> all = guideRepository.findAll();

        List<GuideEntity> filtered = all.stream()
            .filter(g -> status == null || status.equals(g.getStatus()))
            .filter(g -> groupId == null || groupId.equals(g.getGroupId()))
            .filter(g -> providerId == null || providerId.equals(g.getProviderId()))
            .filter(g -> classifier == null || classifier.equals(String.valueOf(g.getClassifierId())))
            .filter(g -> minConfidence == null || g.getConfidence() != null && g.getConfidence() >= minConfidence)
            .filter(g -> maxCost == null || g.getEstimatedCostUsd() != null && g.getEstimatedCostUsd() <= maxCost)
            .toList();

        int start = (int) Math.min(pageable.getOffset(), filtered.size());
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<GuideEntity> pageContent = filtered.subList(start, end);

        return new PageImpl<>(pageContent, pageable, filtered.size());
    }

    @Transactional(readOnly = true)
    public GuideDetailResponse getGuideDetail(Long id) {
        GuideEntity guide = getById(id);
        GroupEntity group = groupRepository.findById(guide.getGroupId()).orElse(null);

        List<GuideSourceMessageEntity> sourceMsgs = guideSourceMessageRepository.findByGuideId(id);
        Map<Long, MessageEntity> messageMap = loadMessageMap(sourceMsgs);
        MessageEntity rootMessage = resolveRootMessage(guide, sourceMsgs, messageMap);

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

        List<Long> relatedGuideIds = guideRepository.findAll().stream()
            .filter(g -> !g.getId().equals(id) && Objects.equals(g.getGroupId(), guide.getGroupId()))
            .map(GuideEntity::getId)
            .toList();

        List<Long> possibleDuplicateIds = guideRepository.findAll().stream()
            .filter(g -> id.equals(g.getDuplicateOfId()))
            .map(GuideEntity::getId)
            .toList();

        return new GuideDetailResponse(
            guide.getId(),
            displayGuideTitle(guide),
            guide.getGroupId(),
            group != null ? group.getTitle() : null,
            guide.getRootMessageId(),
            guide.getProviderId(),
            guide.getModel(),
            guide.getClassifierId(),
            guide.getPromptId(),
            guide.getPromptVersion(),
            guide.getStatus(),
            guide.getDuplicateOfId(),
            guide.getDuplicateScore(),
            guide.getConfidence(),
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
        GuideEntity sourceGuide = getById(id);
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
        Long providerId = resolveProviderId(sourceGuide);

        GuideContent regenerated = guideGenerator.generate(
            chain,
            classifierResult,
            providerId,
            sourceGuide.getPromptId(),
            rootMessage != null ? rootMessage.getId() : chain.get(0).getId()
        );

        GuideEntity newGuide = new GuideEntity();
        newGuide.setTitle(displayGuideTitle(regenerated.title()));
        newGuide.setContent(regenerated.content());
        newGuide.setContentMarkdown(regenerated.contentMarkdown());
        newGuide.setRawResponse(regenerated.rawResponse());
        newGuide.setGroupId(sourceGuide.getGroupId());
        newGuide.setRootMessageId(rootMessage != null ? rootMessage.getId() : sourceGuide.getRootMessageId());
        newGuide.setProviderId(providerId);
        newGuide.setModel(resolveProviderModel(providerId, sourceGuide.getModel()));
        newGuide.setClassifierId(sourceGuide.getClassifierId());
        newGuide.setPromptId(sourceGuide.getPromptId());
        newGuide.setPromptVersion(sourceGuide.getPromptVersion());
        newGuide.setStatus(regenerated.generationError() != null ? "FAILED" : "DRAFT");
        newGuide.setDuplicateOfId(sourceGuide.getDuplicateOfId());
        newGuide.setDuplicateScore(sourceGuide.getDuplicateScore());
        newGuide.setConfidence(regenerated.confidence());
        newGuide.setTotalTokens(sourceGuide.getTotalTokens());
        newGuide.setEstimatedCostUsd(sourceGuide.getEstimatedCostUsd());
        newGuide.setTagsJson(writeJson(regenerated.tags()));
        newGuide.setGenerationError(regenerated.generationError());
        newGuide.setRegeneratedFromGuideId(sourceGuide.getId());
        newGuide = guideRepository.save(newGuide);

        for (GuideSourceMessageEntity sourceLink : sourceLinks) {
            GuideSourceMessageEntity copy = new GuideSourceMessageEntity();
            copy.setGuideId(newGuide.getId());
            copy.setMessageId(sourceLink.getMessageId());
            copy.setUsedInPrompt(sourceLink.getUsedInPrompt());
            guideSourceMessageRepository.save(copy);
        }

        return new GuideRegenerateResponse(sourceGuide.getId(), newGuide.getId(), newGuide.getStatus());
    }

    @Transactional
    public GuideEntity updateStatus(Long id, String status) {
        GuideEntity guide = getById(id);
        guide.setStatus(status);
        return guideRepository.save(guide);
    }

    @Transactional
    public GuideEntity updateContent(Long id, UpdateGuideContentRequest request) {
        GuideEntity guide = getById(id);
        guide.setTitle(request.title());
        guide.setContent(request.content());
        guide.setContentMarkdown(request.contentMarkdown());
        return guideRepository.save(guide);
    }

    @Transactional
    public GuideEntity markNotDuplicate(Long id) {
        GuideEntity guide = getById(id);
        guide.setDuplicateOfId(null);
        guide.setDuplicateScore(null);
        return guideRepository.save(guide);
    }

    @Transactional
    public void delete(Long id) {
        GuideEntity guide = getById(id);
        deleteGuideAndDetachMessages(guide);
    }

    @Transactional
    public int bulkDelete(List<Long> ids) {
        LinkedHashSet<Long> uniqueIds = ids == null
            ? new LinkedHashSet<>()
            : ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (uniqueIds.isEmpty()) {
            return 0;
        }

        List<GuideEntity> guides = guideRepository.findAllById(uniqueIds);
        guides.forEach(this::deleteGuideAndDetachMessages);
        return guides.size();
    }

    public GuideSummaryResponse toSummaryResponse(GuideEntity guide) {
        GroupEntity group = groupRepository.findById(guide.getGroupId()).orElse(null);
        return new GuideSummaryResponse(
            guide.getId(),
            displayGuideTitle(guide),
            guide.getGroupId(),
            group != null ? group.getTitle() : null,
            guide.getRootMessageId(),
            guide.getProviderId(),
            guide.getModel(),
            guide.getClassifierId(),
            guide.getPromptId(),
            guide.getPromptVersion(),
            guide.getStatus(),
            guide.getDuplicateOfId(),
            guide.getDuplicateScore(),
            guide.getConfidence(),
            guide.getTotalTokens(),
            guide.getEstimatedCostUsd(),
            parseTags(guide.getTagsJson()),
            guide.getGenerationError(),
            guide.getPublishedAt(),
            guide.getCreatedAt()
        );
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

    private Long resolveProviderId(GuideEntity sourceGuide) {
        if (sourceGuide.getProviderId() != null && aiProviderRepository.findById(sourceGuide.getProviderId()).isPresent()) {
            return sourceGuide.getProviderId();
        }
        return aiProviderRepository.findAll().stream()
            .filter(provider -> {
                String status = provider.getStatus();
                return "ACTIVE".equalsIgnoreCase(status)
                    || "HEALTHY".equalsIgnoreCase(status)
                    || "WARNING".equalsIgnoreCase(status);
            })
            .sorted(Comparator.comparing(AiProviderEntity::getId))
            .map(AiProviderEntity::getId)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No active AI provider found for guide regeneration"));
    }

    private String resolveProviderModel(Long providerId, String fallbackModel) {
        if (providerId == null) {
            return fallbackModel;
        }
        return aiProviderRepository.findById(providerId)
            .map(AiProviderEntity::getModel)
            .filter(model -> model != null && !model.isBlank())
            .orElse(fallbackModel);
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
        return guideRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Guide not found: " + id));
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize guide metadata", exception);
        }
    }

    private String displayGuideTitle(GuideEntity guide) {
        return displayGuideTitle(guide != null ? guide.getTitle() : null);
    }

    private String displayGuideTitle(String title) {
        if (title == null || title.isBlank()) {
            return "Untitled Guide";
        }
        String trimmed = title.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
            || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            return "Untitled Guide";
        }
        return trimmed;
    }
}
