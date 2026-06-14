package com.larbcorp.neuroinfogrinder.domain.findings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.GuideDetailResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.GuideSummaryResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.LlmRequestDto;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.SourceMessageDto;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.UpdateGuideContentRequest;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.UpdateGuideStatusRequest;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideSourceMessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GuideService {

    private final GuideRepository guideRepository;
    private final GuideSourceMessageRepository guideSourceMessageRepository;
    private final MessageRepository messageRepository;
    private final GroupRepository groupRepository;
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

        Map<Long, MessageEntity> messageMap = sourceMsgs.isEmpty()
            ? Map.of()
            : messageRepository.findAllById(
                sourceMsgs.stream().map(GuideSourceMessageEntity::getMessageId).toList()
            ).stream().collect(Collectors.toMap(MessageEntity::getId, Function.identity()));

        MessageEntity rootMessage = resolveRootMessage(guide, sourceMsgs, messageMap);

        List<SourceMessageDto> sourceMessageDtos = sourceMsgs.stream()
            .sorted((left, right) -> compareSourceMessages(left, right, messageMap))
            .map(sm -> {
                MessageEntity msg = messageMap.get(sm.getMessageId());
                return new SourceMessageDto(
                    sm.getMessageId(),
                    msg != null ? msg.getTelegramMessageId() : null,
                    msg != null ? msg.getSenderName() : null,
                    msg != null ? msg.getText() : null,
                    sm.getUsedInPrompt(),
                    resolveRelation(rootMessage, msg),
                    msg != null ? msg.getReplyToMessageId() : null,
                    msg != null ? msg.getTopicId() : null,
                    msg != null ? msg.getTopicName() : null
                );
            })
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
            guide.getTitle(),
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

    public GuideSummaryResponse toSummaryResponse(GuideEntity guide) {
        GroupEntity group = groupRepository.findById(guide.getGroupId()).orElse(null);
        return new GuideSummaryResponse(
            guide.getId(),
            guide.getTitle(),
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
            .min((left, right) -> compareMessages(left, right))
            .orElse(null);
    }

    private int compareSourceMessages(
        GuideSourceMessageEntity left,
        GuideSourceMessageEntity right,
        Map<Long, MessageEntity> messageMap
    ) {
        MessageEntity leftMessage = messageMap.get(left.getMessageId());
        MessageEntity rightMessage = messageMap.get(right.getMessageId());
        return compareMessages(leftMessage, rightMessage);
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
}
