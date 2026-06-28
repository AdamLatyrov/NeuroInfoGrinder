package com.larbcorp.neuroinfogrinder.domain.findings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TopicClusterGuideCandidateEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TopicClusterMessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TopicDiscussionClusterEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TopicClusterGuideCandidateRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TopicClusterMessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TopicDiscussionClusterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TopicClusterService {

    private static final int TOPIC_EXPLAIN_MAX_MESSAGES = 500;
    private static final int TOPIC_CANDIDATES_DEFAULT_LIMIT = 500;
    private static final int TOPIC_CANDIDATES_MAX_LIMIT = 1000;
    private static final int MAX_CLUSTER_MESSAGES = 80;
    private static final int MAX_GUIDE_CANDIDATES_PER_CLUSTER = 3;
    private static final List<String> TOPIC_EXPLAIN_STATUSES = List.of(
        "UNPROCESSED", "QUEUED", "PROCESSING", "CLUSTERED", "CLASSIFIED", "SKIPPED", "GUIDE_FOUND", "ERROR", "CLEARED"
    );
    private static final List<String> TOPIC_CANDIDATE_STATUSES = List.of("CLUSTERED", "CLASSIFIED", "GUIDE_FOUND", "ERROR");
    private static final List<String> ACTIVE_CLUSTER_STATUSES = List.of("OPEN", "CANDIDATE", "CLASSIFIED");
    private static final double TOPIC_ASSIGNMENT_MIN_SIMILARITY = 0.04;
    private static final double TOPIC_ASSIGNMENT_RELATED_MIN_SIMILARITY = 0.02;
    private static final Pattern SENSITIVE_VALUE_PATTERN = Pattern.compile(
        "(?i)(api[_-]?key|authorization|bearer|token|secret|password|jwt)\\s*[:=]\\s*[^\\s,}]+"
    );
    private static final Pattern TOKEN_SPLIT_PATTERN = Pattern.compile("[^\\p{L}\\p{N}.+-]+");
    private static final Set<String> STOP_WORDS = Set.of(
        "the", "and", "for", "with", "this", "that", "you", "are", "как", "что", "это", "или",
        "для", "про", "при", "там", "тут", "если", "есть", "можно", "надо", "нужно", "где"
    );
    private static final Set<String> RISK_MARKERS = Set.of(
        "backdoor", "crack", "cracked", "cracking", "resale", "account resale", "grey market", "gray market",
        "passive income", "profit", "scanner", "iam", "hack", "bypass",
        "взлом", "кряк", "бекдор", "перепродажа", "серый", "серые аккаунты", "обход"
    );

    private final MessageRepository messageRepository;
    private final GroupRepository groupRepository;
    private final TopicDiscussionClusterRepository topicDiscussionClusterRepository;
    private final TopicClusterMessageRepository topicClusterMessageRepository;
    private final TopicClusterGuideCandidateRepository topicClusterGuideCandidateRepository;
    private final ObjectMapper objectMapper;

    @Value("${neuroinfogrinder.pipeline.topic-cluster.window-minutes:20}")
    private int clusterWindowMinutes;

    @Value("${neuroinfogrinder.pipeline.topic-cluster.semantic-similarity-threshold:0.12}")
    private double semanticSimilarityThreshold;

    @Transactional
    public ClusterUpdateResult upsertFromMessage(
        MessageEntity anchorMessage,
        List<MessageEntity> contextMessages,
        ClassifierResult messageClassification,
        SignalScore signalScore
    ) {
        List<MessageEntity> sourceMessages = normalizeSourceMessages(anchorMessage, contextMessages);
        String fingerprint = semanticFingerprint(sourceMessages, messageClassification);
        String semanticHash = semanticHash(
            anchorMessage.getGroupId(),
            anchorMessage.getTopicId(),
            topicTitle(anchorMessage),
            fingerprint
        );

        TopicDiscussionClusterEntity cluster = findMatchingCluster(anchorMessage, sourceMessages, messageClassification, semanticHash);
        boolean created = cluster == null;
        if (cluster == null) {
            cluster = new TopicDiscussionClusterEntity();
            cluster.setGroupId(anchorMessage.getGroupId());
            cluster.setOwnerUserId(anchorMessage.getOwnerUserId());
            cluster.setTelegramTopicId(anchorMessage.getTopicId());
            cluster.setTopicTitle(topicTitle(anchorMessage));
            cluster.setStartAt(anchorMessage.getMessageDate());
            cluster.setEndAt(anchorMessage.getMessageDate());
            cluster.setSemanticHash(semanticHash);
        }

        applySourceSummary(cluster, sourceMessages, messageClassification, signalScore);
        cluster.setClassificationStatus("PENDING");
        cluster = topicDiscussionClusterRepository.save(cluster);

        int linked = upsertSourceLinks(cluster, sourceMessages, messageClassification);
        String reason = created
            ? "Created topic cluster from group/topic/time window and semantic fingerprint"
            : "Updated active topic cluster by group/topic/time window and semantic similarity";
        return new ClusterUpdateResult(cluster, sourceMessages, created, linked, reason);
    }

    @Transactional
    public ClusterClassificationResult applyClusterClassification(
        TopicDiscussionClusterEntity cluster,
        ClassifierResult classifierResult,
        Long classifierId,
        List<MessageEntity> sourceMessages
    ) {
        cluster.setClassifierId(classifierId);
        cluster.setClassifierScore(classifierResult != null ? classifierResult.score() : null);
        cluster.setClassifierResultJson(safeToJson(classifierResult));
        cluster.setClassificationStatus(classifierResult != null && classifierResult.matched() ? "CLASSIFIED" : "SKIPPED");
        cluster.setStatus(classifierResult != null && classifierResult.matched() ? "CLASSIFIED" : "OPEN");
        cluster.setTopicLabel(topicLabel(sourceMessages, classifierResult));
        cluster.setTopicSummary(topicSummary(sourceMessages, classifierResult));
        cluster.setGuidePotentialScore(maxScore(
            classifierResult != null ? classifierResult.guidePotentialScore() : null,
            sourceMessages.stream().map(MessageEntity::getGuidePotentialScore).toList()
        ));
        cluster.setProblemSignalScore(maxScore(
            classifierResult != null ? classifierResult.problemSignalScore() : null,
            sourceMessages.stream().map(MessageEntity::getProblemSignalScore).toList()
        ));
        cluster.setSafetyCategory(inferSafetyCategory(sourceMessages, classifierResult));
        if (classifierResult != null && classifierResult.guideCandidate()) {
            cluster.setGuideGenerationStatus("CANDIDATE");
        }
        cluster = topicDiscussionClusterRepository.save(cluster);

        List<TopicClusterGuideCandidateEntity> candidates = refreshGuideCandidates(cluster, classifierResult, sourceMessages);
        return new ClusterClassificationResult(cluster, candidates);
    }

    @Transactional(readOnly = true)
    public List<MessageEntity> sourceMessages(TopicDiscussionClusterEntity cluster) {
        List<Long> messageIds = topicClusterMessageRepository.findByClusterId(cluster.getId()).stream()
            .map(TopicClusterMessageEntity::getMessageId)
            .toList();
        if (messageIds.isEmpty()) {
            return List.of();
        }
        return messageRepository.findByIdInOrderByMessageDateAsc(messageIds);
    }

    @Transactional(readOnly = true)
    public List<TopicClusterGuideCandidateEntity> guideCandidates(TopicDiscussionClusterEntity cluster) {
        return topicClusterGuideCandidateRepository.findByClusterId(cluster.getId());
    }

    @Transactional
    public void markGuideGenerated(
        TopicDiscussionClusterEntity cluster,
        TopicClusterGuideCandidateEntity candidate,
        Long guideId,
        boolean failed
    ) {
        cluster.setGuideId(guideId);
        cluster.setGuideGenerationStatus(failed ? "FAILED" : "GENERATED");
        topicDiscussionClusterRepository.save(cluster);

        if (candidate != null) {
            candidate.setGuideId(guideId);
            candidate.setStatus(failed ? "FAILED" : "GENERATED");
            topicClusterGuideCandidateRepository.save(candidate);
        }
    }

    @Transactional
    public void markGuideMerged(
        TopicDiscussionClusterEntity cluster,
        TopicClusterGuideCandidateEntity candidate,
        Long guideId
    ) {
        cluster.setGuideId(guideId);
        cluster.setGuideGenerationStatus("MERGED");
        topicDiscussionClusterRepository.save(cluster);

        if (candidate != null) {
            candidate.setGuideId(guideId);
            candidate.setStatus("MERGED");
            topicClusterGuideCandidateRepository.save(candidate);
        }
    }

    @Transactional
    public void markGuideSkipped(TopicDiscussionClusterEntity cluster, String status) {
        cluster.setGuideGenerationStatus(status);
        topicDiscussionClusterRepository.save(cluster);
    }

    @Transactional(readOnly = true)
    public TopicCandidatesResponse getTopicCandidates(
        String group,
        String topic,
        String from,
        String to,
        int windowMinutes,
        boolean clusterCandidateOnly,
        Integer minProblemSignalScore,
        Integer minPainScore,
        Integer minWillingnessToPayScore,
        Integer minGuidePotentialScore,
        int maxSpamScore,
        int limit
    ) {
        return getTopicCandidates(
            null,
            group,
            topic,
            from,
            to,
            windowMinutes,
            clusterCandidateOnly,
            minProblemSignalScore,
            minPainScore,
            minWillingnessToPayScore,
            minGuidePotentialScore,
            maxSpamScore,
            limit
        );
    }

    @Transactional(readOnly = true)
    public TopicCandidatesResponse getTopicCandidates(
        Long ownerUserId,
        String group,
        String topic,
        String from,
        String to,
        int windowMinutes,
        boolean clusterCandidateOnly,
        Integer minProblemSignalScore,
        Integer minPainScore,
        Integer minWillingnessToPayScore,
        Integer minGuidePotentialScore,
        int maxSpamScore,
        int limit
    ) {
        List<GroupEntity> enabledGroups = ownerUserId != null
            ? groupRepository.findByOwnerUserIdAndEnabledTrue(ownerUserId)
            : groupRepository.findByEnabledTrue();
        Map<Long, GroupEntity> groupsById = enabledGroups.stream()
            .collect(Collectors.toMap(GroupEntity::getId, Function.identity()));
        int effectiveWindowMinutes = Math.max(1, Math.min(windowMinutes, 120));
        if (groupsById.isEmpty()) {
            return new TopicCandidatesResponse(
                "topic_cluster",
                "No enabled groups found",
                null,
                null,
                effectiveWindowMinutes,
                0,
                0,
                List.of(),
                List.of("No database writes are performed")
            );
        }

        Instant fromInstant = parseDateOrInstant(from, true);
        Instant toInstant = parseDateOrInstant(to, false);
        if (fromInstant == null) {
            fromInstant = Instant.now().minusSeconds(7L * 24L * 3600L);
        }
        if (toInstant == null) {
            toInstant = Instant.now().plusSeconds(60L);
        }
        if (toInstant.isBefore(fromInstant)) {
            throw new IllegalArgumentException("to must be greater than or equal to from");
        }

        int safeLimit = Math.max(1, Math.min(limit, TOPIC_CANDIDATES_MAX_LIMIT));
        List<Long> groupIds = groupsById.keySet().stream()
            .filter(Objects::nonNull)
            .toList();
        Page<MessageEntity> page = messageRepository.findByGroupIdInAndProcessingStatusInAndMessageDateBetween(
            groupIds,
            TOPIC_CANDIDATE_STATUSES,
            fromInstant,
            toInstant,
            PageRequest.of(0, safeLimit)
        );

        List<MessageEntity> messages = page.getContent().stream()
            .filter(message -> matchesGroupFilter(message, groupsById, group, null))
            .filter(message -> matchesTopicFilter(message, topic, null))
            .filter(message -> matchesTopicCandidateFilters(
                message,
                clusterCandidateOnly,
                minProblemSignalScore,
                minPainScore,
                minWillingnessToPayScore,
                minGuidePotentialScore,
                maxSpamScore
            ))
            .sorted(Comparator.comparing(MessageEntity::getMessageDate).thenComparing(MessageEntity::getId))
            .toList();

        List<TopicCandidateItem> candidates = buildTopicCandidates(messages, groupsById, effectiveWindowMinutes);
        return new TopicCandidatesResponse(
            "topic_cluster",
            "Read-only virtual topic candidates grouped from existing classified/signal messages.",
            fromInstant,
            toInstant,
            effectiveWindowMinutes,
            messages.size(),
            candidates.size(),
            candidates,
            List.of(
                "No database writes are performed",
                "Messages remain evidence; each topic candidate is the review unit",
                "Persistent topic_discussion_clusters is the local cluster-first MVP model"
            )
        );
    }

    @Transactional(readOnly = true)
    public TopicExplainResponse explainTopics(
        String group,
        String topic,
        String from,
        String to,
        Long messageId,
        int windowMinutes
    ) {
        return explainTopics(null, group, topic, from, to, messageId, windowMinutes);
    }

    @Transactional(readOnly = true)
    public TopicExplainResponse explainTopics(
        Long ownerUserId,
        String group,
        String topic,
        String from,
        String to,
        Long messageId,
        int windowMinutes
    ) {
        List<GroupEntity> enabledGroups = ownerUserId != null
            ? groupRepository.findByOwnerUserIdAndEnabledTrue(ownerUserId)
            : groupRepository.findByEnabledTrue();
        if (enabledGroups.isEmpty()) {
            return emptyTopicExplain(windowMinutes, "No enabled groups");
        }

        Map<Long, GroupEntity> groupsById = enabledGroups.stream()
            .collect(Collectors.toMap(GroupEntity::getId, Function.identity()));
        List<Long> enabledGroupIds = enabledGroups.stream().map(GroupEntity::getId).toList();
        int effectiveWindowMinutes = Math.max(1, Math.min(windowMinutes, 120));

        MessageEntity anchor = null;
        Instant fromInstant = parseDateOrInstant(from, true);
        Instant toInstant = parseDateOrInstant(to, false);
        if (messageId != null) {
            anchor = ownerUserId != null
                ? messageRepository.findByIdAndOwnerUserId(messageId, ownerUserId).orElse(null)
                : messageRepository.findById(messageId).orElse(null);
            if (anchor != null) {
                fromInstant = anchor.getMessageDate().minusSeconds(effectiveWindowMinutes * 60L);
                toInstant = anchor.getMessageDate().plusSeconds(effectiveWindowMinutes * 60L);
            }
        }

        Page<MessageEntity> page;
        Pageable pageRequest = PageRequest.of(0, TOPIC_EXPLAIN_MAX_MESSAGES);
        if (fromInstant != null && toInstant != null) {
            page = messageRepository.findByGroupIdInAndProcessingStatusInAndMessageDateBetween(
                enabledGroupIds,
                TOPIC_EXPLAIN_STATUSES,
                fromInstant,
                toInstant,
                pageRequest
            );
        } else if (fromInstant != null) {
            page = messageRepository.findByGroupIdInAndProcessingStatusInAndMessageDateAfter(
                enabledGroupIds,
                TOPIC_EXPLAIN_STATUSES,
                fromInstant,
                pageRequest
            );
        } else {
            page = messageRepository.findByGroupIdInAndProcessingStatusIn(
                enabledGroupIds,
                TOPIC_EXPLAIN_STATUSES,
                pageRequest
            );
        }

        MessageEntity anchorMessage = anchor;
        List<MessageEntity> messages = page.getContent().stream()
            .filter(message -> matchesGroupFilter(message, groupsById, group, anchorMessage))
            .filter(message -> matchesTopicFilter(message, topic, anchorMessage))
            .sorted(Comparator.comparing(MessageEntity::getMessageDate).thenComparing(MessageEntity::getId))
            .toList();

        List<TopicCandidateItem> candidates = buildTopicCandidates(messages, groupsById, effectiveWindowMinutes);
        List<TopicExplainMessage> sourceMessages = messages.stream()
            .map(message -> toTopicExplainMessage(message, groupsById.get(message.getGroupId())))
            .toList();

        return new TopicExplainResponse(
            "cluster",
            "Local MVP uses topic/discussion clusters as the primary classification unit; this endpoint explains the grouping without extra writes.",
            fromInstant,
            toInstant,
            effectiveWindowMinutes,
            messages.size(),
            candidates,
            sourceMessages,
            List.of(
            "Diagnostic endpoint is read-only",
            "Old message-level /signals remains available as debug/internal compatibility"
        )
    );
    }

    @Transactional(readOnly = true)
    public Page<TopicClusterItem> getTopicClusters(
        Long groupId,
        List<String> statuses,
        Pageable pageable
    ) {
        return getTopicClusters(null, groupId, statuses, pageable);
    }

    @Transactional(readOnly = true)
    public Page<TopicClusterItem> getTopicClusters(
        Long ownerUserId,
        Long groupId,
        List<String> statuses,
        Pageable pageable
    ) {
        List<String> effectiveStatuses = statuses == null || statuses.isEmpty()
            ? List.of("OPEN", "CANDIDATE", "CLASSIFIED")
            : statuses;

        Page<TopicDiscussionClusterEntity> page = ownerUserId != null
            ? groupId == null
                ? topicDiscussionClusterRepository.findByOwnerUserIdAndStatusInOrderByUpdatedAtDesc(ownerUserId, effectiveStatuses, pageable)
                : topicDiscussionClusterRepository.findByOwnerUserIdAndGroupIdAndStatusInOrderByUpdatedAtDesc(ownerUserId, groupId, effectiveStatuses, pageable)
            : groupId == null
                ? topicDiscussionClusterRepository.findByStatusInOrderByUpdatedAtDesc(effectiveStatuses, pageable)
                : topicDiscussionClusterRepository.findByGroupIdAndStatusInOrderByUpdatedAtDesc(groupId, effectiveStatuses, pageable);

        List<TopicDiscussionClusterEntity> clusters = page.getContent();
        if (clusters.isEmpty()) {
            return Page.empty(pageable);
        }

        Map<Long, GroupEntity> groupsById = groupRepository.findAllById(
                clusters.stream().map(TopicDiscussionClusterEntity::getGroupId).distinct().toList()
            ).stream()
            .collect(Collectors.toMap(GroupEntity::getId, Function.identity()));
        List<Long> clusterIds = clusters.stream().map(TopicDiscussionClusterEntity::getId).toList();
        Map<Long, List<TopicClusterMessageEntity>> linksByCluster = topicClusterMessageRepository.findByClusterIdIn(clusterIds)
            .stream()
            .collect(Collectors.groupingBy(TopicClusterMessageEntity::getClusterId));
        List<Long> messageIds = linksByCluster.values().stream()
            .flatMap(List::stream)
            .map(TopicClusterMessageEntity::getMessageId)
            .distinct()
            .toList();
        Map<Long, MessageEntity> messagesById = messageIds.isEmpty()
            ? Map.of()
            : messageRepository.findAllById(messageIds).stream()
                .collect(Collectors.toMap(MessageEntity::getId, Function.identity()));
        Map<Long, List<TopicClusterGuideCandidateEntity>> candidatesByCluster = topicClusterGuideCandidateRepository
            .findByClusterIdIn(clusterIds)
            .stream()
            .collect(Collectors.groupingBy(TopicClusterGuideCandidateEntity::getClusterId));

        List<TopicClusterItem> items = clusters.stream()
            .map(cluster -> toTopicClusterItem(
                cluster,
                groupsById.get(cluster.getGroupId()),
                linksByCluster.getOrDefault(cluster.getId(), List.of()),
                messagesById,
                candidatesByCluster.getOrDefault(cluster.getId(), List.of())
            ))
            .toList();
        return new PageImpl<>(items, pageable, page.getTotalElements());
    }

    private TopicDiscussionClusterEntity findMatchingCluster(
        MessageEntity anchorMessage,
        List<MessageEntity> sourceMessages,
        ClassifierResult messageClassification,
        String semanticHash
    ) {
        Instant windowStart = anchorMessage.getMessageDate().minusSeconds(clusterWindowMinutes * 60L);
        List<TopicDiscussionClusterEntity> candidates = anchorMessage.getTopicId() != null
            ? anchorMessage.getOwnerUserId() != null
                ? topicDiscussionClusterRepository.findByOwnerUserIdAndGroupIdAndTelegramTopicIdAndStatusInAndEndAtAfterOrderByEndAtDesc(
                anchorMessage.getOwnerUserId(),
                anchorMessage.getGroupId(),
                anchorMessage.getTopicId(),
                ACTIVE_CLUSTER_STATUSES,
                windowStart
            )
                : topicDiscussionClusterRepository.findByGroupIdAndTelegramTopicIdAndStatusInAndEndAtAfterOrderByEndAtDesc(
                anchorMessage.getGroupId(),
                anchorMessage.getTopicId(),
                ACTIVE_CLUSTER_STATUSES,
                windowStart
            )
            : anchorMessage.getOwnerUserId() != null
                ? topicDiscussionClusterRepository.findByOwnerUserIdAndGroupIdAndTelegramTopicIdIsNullAndTopicTitleAndStatusInAndEndAtAfterOrderByEndAtDesc(
                anchorMessage.getOwnerUserId(),
                anchorMessage.getGroupId(),
                topicTitle(anchorMessage),
                ACTIVE_CLUSTER_STATUSES,
                windowStart
            )
                : topicDiscussionClusterRepository.findByGroupIdAndTelegramTopicIdIsNullAndTopicTitleAndStatusInAndEndAtAfterOrderByEndAtDesc(
                anchorMessage.getGroupId(),
                topicTitle(anchorMessage),
                ACTIVE_CLUSTER_STATUSES,
                windowStart
            );

        for (TopicDiscussionClusterEntity candidate : candidates) {
            if (semanticHash.equals(candidate.getSemanticHash())
                || semanticSimilarity(candidate, sourceMessages, messageClassification) >= semanticSimilarityThreshold) {
                return candidate;
            }
        }
        return null;
    }

    private void applySourceSummary(
        TopicDiscussionClusterEntity cluster,
        List<MessageEntity> sourceMessages,
        ClassifierResult classifierResult,
        SignalScore signalScore
    ) {
        Instant start = sourceMessages.stream()
            .map(MessageEntity::getMessageDate)
            .filter(Objects::nonNull)
            .min(Instant::compareTo)
            .orElse(cluster.getStartAt());
        Instant end = sourceMessages.stream()
            .map(MessageEntity::getMessageDate)
            .filter(Objects::nonNull)
            .max(Instant::compareTo)
            .orElse(cluster.getEndAt());

        cluster.setStartAt(minInstant(cluster.getStartAt(), start));
        cluster.setEndAt(maxInstant(cluster.getEndAt(), end));
        cluster.setStatus("OPEN");
        cluster.setTopicLabel(topicLabel(sourceMessages, classifierResult));
        cluster.setTopicSummary(topicSummary(sourceMessages, classifierResult));
        cluster.setGuidePotentialScore(maxScore(
            classifierResult != null ? classifierResult.guidePotentialScore() : null,
            sourceMessages.stream().map(MessageEntity::getGuidePotentialScore).toList()
        ));
        cluster.setProblemSignalScore(maxScore(
            classifierResult != null ? classifierResult.problemSignalScore() : signalProblemScore(signalScore),
            sourceMessages.stream().map(MessageEntity::getProblemSignalScore).toList()
        ));
        cluster.setSafetyCategory(inferSafetyCategory(sourceMessages, classifierResult));
    }

    private int upsertSourceLinks(
        TopicDiscussionClusterEntity cluster,
        List<MessageEntity> sourceMessages,
        ClassifierResult classifierResult
    ) {
        Set<Long> evidenceIds = classifierResult != null
            ? new HashSet<>(classifierResult.evidenceMessageIds())
            : Set.of();
        int linked = 0;
        for (MessageEntity sourceMessage : sourceMessages.stream().limit(MAX_CLUSTER_MESSAGES).toList()) {
            TopicClusterMessageEntity existing = topicClusterMessageRepository
                .findByMessageId(sourceMessage.getId())
                .orElse(null);
            if (existing != null) {
                if (existing.getClusterId().equals(cluster.getId())) {
                    existing.setRole(resolveMessageRole(sourceMessages.get(0), sourceMessage, evidenceIds));
                    existing.setContributionScore(contributionScore(sourceMessage));
                    topicClusterMessageRepository.save(existing);
                }
                continue;
            }

            TopicClusterMessageEntity link = new TopicClusterMessageEntity();
            link.setClusterId(cluster.getId());
            link.setMessageId(sourceMessage.getId());
            link.setRole(resolveMessageRole(sourceMessages.get(0), sourceMessage, evidenceIds));
            link.setContributionScore(contributionScore(sourceMessage));
            topicClusterMessageRepository.save(link);
            linked++;
        }
        return linked;
    }

    private List<TopicClusterGuideCandidateEntity> refreshGuideCandidates(
        TopicDiscussionClusterEntity cluster,
        ClassifierResult classifierResult,
        List<MessageEntity> sourceMessages
    ) {
        if (classifierResult == null || !classifierResult.matched() || !classifierResult.guideCandidate()) {
            return topicClusterGuideCandidateRepository.findByClusterId(cluster.getId());
        }
        List<Long> sourceIds = clusterSourceMessageIds(cluster, sourceMessages);
        List<TopicClusterGuideCandidateEntity> existing = topicClusterGuideCandidateRepository.findByClusterId(cluster.getId());
        if (!existing.isEmpty()) {
            String sourceIdsJson = safeToJson(sourceIds);
            return existing.stream()
                .map(candidate -> {
                    candidate.setSourceMessageIdsJson(sourceIdsJson);
                    if (candidate.getWhyThisCluster() == null || candidate.getWhyThisCluster().isBlank()) {
                        candidate.setWhyThisCluster(
                            "Cluster has " + sourceIds.size()
                                + " source messages in the same group/topic/time window with semantic overlap"
                        );
                    }
                    return topicClusterGuideCandidateRepository.save(candidate);
                })
                .toList();
        }

        List<String> angles = guideAngles(cluster, classifierResult);
        List<TopicClusterGuideCandidateEntity> created = new ArrayList<>();
        for (String angle : angles.stream().limit(MAX_GUIDE_CANDIDATES_PER_CLUSTER).toList()) {
            TopicClusterGuideCandidateEntity candidate = new TopicClusterGuideCandidateEntity();
            candidate.setClusterId(cluster.getId());
            candidate.setStatus(isSafetyRestricted(cluster) ? "SAFETY_REVIEW" : "PENDING");
            candidate.setGuideAngle(angle);
            candidate.setSafetyCategory(cluster.getSafetyCategory());
            candidate.setWhyThisCluster(
                "Cluster has " + sourceMessages.size()
                    + " source messages in the same group/topic/time window with semantic overlap"
            );
            candidate.setSourceMessageIdsJson(safeToJson(sourceIds));
            created.add(topicClusterGuideCandidateRepository.save(candidate));
        }
        return created;
    }

    private List<Long> clusterSourceMessageIds(
        TopicDiscussionClusterEntity cluster,
        List<MessageEntity> sourceMessages
    ) {
        LinkedHashSet<Long> sourceIds = new LinkedHashSet<>();
        if (cluster.getId() != null) {
            List<TopicClusterMessageEntity> links = topicClusterMessageRepository.findByClusterId(cluster.getId());
            if (links != null) {
                links.stream()
                    .map(TopicClusterMessageEntity::getMessageId)
                    .filter(Objects::nonNull)
                    .forEach(sourceIds::add);
            }
        }
        if (sourceMessages != null) {
            sourceMessages.stream()
                .map(MessageEntity::getId)
                .filter(Objects::nonNull)
                .forEach(sourceIds::add);
        }
        return List.copyOf(sourceIds);
    }

    private List<MessageEntity> normalizeSourceMessages(MessageEntity anchorMessage, List<MessageEntity> contextMessages) {
        Map<Long, MessageEntity> unique = new LinkedHashMap<>();
        unique.put(anchorMessage.getId(), anchorMessage);
        if (contextMessages != null) {
            contextMessages.stream()
                .filter(Objects::nonNull)
                .filter(message -> message.getId() != null)
                .filter(message -> Objects.equals(anchorMessage.getOwnerUserId(), message.getOwnerUserId()))
                .filter(message -> anchorMessage.getGroupId().equals(message.getGroupId()))
                .filter(message -> sameTopic(anchorMessage, message))
                .sorted(Comparator.comparing(MessageEntity::getMessageDate).thenComparing(MessageEntity::getId))
                .forEach(message -> unique.putIfAbsent(message.getId(), message));
        }
        return unique.values().stream()
            .sorted(Comparator.comparing(MessageEntity::getMessageDate).thenComparing(MessageEntity::getId))
            .limit(MAX_CLUSTER_MESSAGES)
            .toList();
    }

    private boolean sameTopic(MessageEntity anchor, MessageEntity candidate) {
        if (anchor.getTopicId() != null || candidate.getTopicId() != null) {
            return Objects.equals(anchor.getTopicId(), candidate.getTopicId());
        }
        return normalized(topicTitle(anchor)).equals(normalized(topicTitle(candidate)));
    }

    private List<String> guideAngles(TopicDiscussionClusterEntity cluster, ClassifierResult classifierResult) {
        if (isSafetyRestricted(cluster)) {
            return List.of("Defensive compliance/moderation/product-risk insight");
        }

        LinkedHashSet<String> angles = new LinkedHashSet<>();
        for (String category : classifierResult.categories()) {
            if (category != null && !category.isBlank()) {
                angles.add("Guide angle: " + category.trim());
            }
        }
        for (String label : classifierResult.labels()) {
            if (ClassificationLabels.PRACTICAL_GUIDE_CANDIDATE.equals(label)) {
                angles.add(firstNonBlank(cluster.getTopicLabel(), cluster.getTopicSummary(), "Практический сценарий из обсуждения"));
            } else if (ClassificationLabels.PAYMENT_WORKAROUND.equals(label)) {
                angles.add("Риски и варианты оплаты/доступа");
            } else if (ClassificationLabels.BUG_OR_LIMITATION.equals(label)) {
                angles.add("Разбор проблемы и workaround");
            }
        }
        if (angles.isEmpty()) {
            angles.add(firstNonBlank(cluster.getTopicLabel(), "Гайд по теме кластера"));
        }
        return List.copyOf(angles);
    }

    private TopicClusterItem toTopicClusterItem(
        TopicDiscussionClusterEntity cluster,
        GroupEntity group,
        List<TopicClusterMessageEntity> links,
        Map<Long, MessageEntity> messagesById,
        List<TopicClusterGuideCandidateEntity> candidates
    ) {
        List<TopicClusterSourceMessageItem> sourceMessages = links.stream()
            .sorted(Comparator.comparing(link -> {
                MessageEntity message = messagesById.get(link.getMessageId());
                return message != null ? message.getMessageDate() : Instant.EPOCH;
            }))
            .map(link -> toSourceMessageItem(link, messagesById.get(link.getMessageId())))
            .filter(Objects::nonNull)
            .toList();
        int hidden = Math.max(0, links.size() - sourceMessages.size());

        return new TopicClusterItem(
            cluster.getId(),
            cluster.getGroupId(),
            group != null ? group.getTitle() : null,
            cluster.getTelegramTopicId(),
            cluster.getTopicTitle(),
            cluster.getStartAt(),
            cluster.getEndAt(),
            cluster.getStatus(),
            cluster.getTopicLabel(),
            cluster.getTopicSummary(),
            cluster.getSemanticHash(),
            cluster.getGuidePotentialScore(),
            cluster.getProblemSignalScore(),
            cluster.getSafetyCategory(),
            cluster.getClassificationStatus(),
            cluster.getGuideGenerationStatus(),
            cluster.getGuideId(),
            sourceMessages,
            candidates.stream().map(this::toGuideCandidateItem).toList(),
            "Grouped by group/topic/time window plus semantic similarity/hash",
            hidden
        );
    }

    private TopicClusterSourceMessageItem toSourceMessageItem(TopicClusterMessageEntity link, MessageEntity message) {
        if (message == null) {
            return null;
        }
        return new TopicClusterSourceMessageItem(
            message.getId(),
            link.getRole(),
            link.getContributionScore(),
            message.getSenderName(),
            sanitize(truncate(message.getText(), 500)),
            message.getMessageDate(),
            message.getProcessingStatus(),
            message.getSignalScore(),
            message.getClassifierScore(),
            message.getGuidePotentialScore(),
            message.getProblemSignalScore()
        );
    }

    private TopicClusterGuideCandidateItem toGuideCandidateItem(TopicClusterGuideCandidateEntity candidate) {
        return new TopicClusterGuideCandidateItem(
            candidate.getId(),
            candidate.getClusterId(),
            candidate.getGuideId(),
            candidate.getStatus(),
            candidate.getGuideAngle(),
            candidate.getContentType(),
            candidate.getContentSubtype(),
            candidate.getTopicLabel(),
            candidate.getTopicSummary(),
            candidate.getContentTitle(),
            candidate.getContentSummary(),
            candidate.getNormalizedTopicKey(),
            candidate.getContentQualityScore(),
            candidate.getImportanceScore(),
            candidate.getActionabilityScore(),
            candidate.getNoveltyScore(),
            candidate.getEvidenceScore(),
            candidate.getRiskScore(),
            candidate.getConfidenceScore(),
            candidate.getNoiseScore(),
            candidate.getRoutingReason(),
            candidate.getPublicationKind(),
            candidate.getShouldCreateMaterial(),
            candidate.getShouldGenerateFullGuide(),
            candidate.getSafetyCategory(),
            candidate.getWhyThisCluster(),
            candidate.getSourceMessageIdsJson()
        );
    }

    private List<TopicCandidateItem> buildTopicCandidates(
        List<MessageEntity> messages,
        Map<Long, GroupEntity> groupsById,
        int windowMinutes
    ) {
        if (messages.isEmpty()) {
            return List.of();
        }

        Map<String, List<MessageEntity>> buckets = new LinkedHashMap<>();
        for (MessageEntity message : messages) {
            buckets.computeIfAbsent(topicBucketKey(message), ignored -> new ArrayList<>()).add(message);
        }

        List<TopicCandidateItem> candidates = new ArrayList<>();
        for (List<MessageEntity> bucket : buckets.values()) {
            List<MessageEntity> sorted = bucket.stream()
                .sorted(Comparator.comparing(MessageEntity::getMessageDate).thenComparing(MessageEntity::getId))
                .toList();
            List<TopicDraftCluster> draftClusters = new ArrayList<>();
            for (MessageEntity message : sorted) {
                Set<String> messageTokens = topicTokens(message);
                TopicDraftCluster bestCluster = bestTopicDraftCluster(draftClusters, message, messageTokens, windowMinutes);
                if (bestCluster == null) {
                    draftClusters.add(new TopicDraftCluster(message, messageTokens));
                } else {
                    bestCluster.add(message, messageTokens);
                }
            }
            draftClusters.stream()
                .map(cluster -> toTopicCandidate(cluster.messages, groupsById, windowMinutes))
                .forEach(candidates::add);
        }

        return candidates.stream()
            .sorted(Comparator.comparing(TopicCandidateItem::startAt))
            .toList();
    }

    private TopicCandidateItem toTopicCandidate(
        List<MessageEntity> messages,
        Map<Long, GroupEntity> groupsById,
        int windowMinutes
    ) {
        MessageEntity first = messages.get(0);
        MessageEntity last = messages.get(messages.size() - 1);
        GroupEntity group = groupsById.get(first.getGroupId());
        List<Long> sourceIds = messages.stream().map(MessageEntity::getId).toList();
        List<String> participants = messages.stream()
            .map(MessageEntity::getSenderName)
            .filter(value -> value != null && !value.isBlank())
            .map(this::sanitize)
            .distinct()
            .limit(12)
            .toList();
        List<Long> bestEvidence = messages.stream()
            .sorted(Comparator
                .comparingInt(this::messageEvidenceScore)
                .reversed()
                .thenComparing(MessageEntity::getMessageDate))
            .limit(5)
            .map(MessageEntity::getId)
            .toList();

        String summary = candidateSummary(messages);
        String label = firstNonBlank(summary, first.getTopicName(), first.getText(), "topic-" + first.getId());
        Long guideId = messages.stream()
            .map(MessageEntity::getGuideId)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
        String status = guideId != null
            ? "guide_created"
            : messages.stream().anyMatch(this::isTopicCandidateMessage) ? "candidate" : "observed";
        Integer guidePotentialScore = messages.stream()
            .map(MessageEntity::getGuidePotentialScore)
            .filter(Objects::nonNull)
            .max(Integer::compareTo)
            .orElse(null);

        return new TopicCandidateItem(
            topicBucketKey(first),
            first.getGroupId(),
            group != null ? group.getTitle() : null,
            first.getTopicId(),
            first.getTopicName(),
            first.getMessageDate(),
            last.getMessageDate(),
            sourceIds,
            participants,
            sanitize(summary),
            sanitize(label),
            semanticHash(first.getGroupId(), first.getTopicId(), topicTitle(first), semanticFingerprint(messages, null)),
            guidePotentialScore,
            inferSafetyCategory(messages, null),
            status,
            guideId,
            bestEvidence,
            guideAnglesFor(messages, inferSafetyCategory(messages, null)),
            messages.stream()
                .map(message -> toTopicExplainMessage(message, groupsById.get(message.getGroupId())))
                .toList(),
            "Grouped by same group/topic and message gaps <= " + windowMinutes + " minutes"
        );
    }

    private TopicDraftCluster bestTopicDraftCluster(
        List<TopicDraftCluster> clusters,
        MessageEntity message,
        Set<String> messageTokens,
        int windowMinutes
    ) {
        TopicDraftCluster best = null;
        double bestScore = 0.0;
        for (TopicDraftCluster cluster : clusters) {
            long gapSeconds = Math.abs(message.getMessageDate().getEpochSecond() - cluster.lastMessageAt.getEpochSecond());
            if (gapSeconds > windowMinutes * 60L) {
                continue;
            }
            double score = topicAssignmentScore(cluster, message, messageTokens, gapSeconds);
            if (score > bestScore) {
                best = cluster;
                bestScore = score;
            }
        }
        if (best == null) {
            return null;
        }
        boolean replyRelated = hasReplyContinuity(best, message);
        boolean participantRelated = best.participants.contains(participantKey(message));
        if (replyRelated
            || bestScore >= TOPIC_ASSIGNMENT_MIN_SIMILARITY
            || (participantRelated && bestScore >= TOPIC_ASSIGNMENT_RELATED_MIN_SIMILARITY)) {
            return best;
        }
        return null;
    }

    private double topicAssignmentScore(
        TopicDraftCluster cluster,
        MessageEntity message,
        Set<String> messageTokens,
        long gapSeconds
    ) {
        double score = jaccard(messageTokens, cluster.tokens);
        if (hasReplyContinuity(cluster, message)) {
            score += 0.35;
        }
        if (cluster.participants.contains(participantKey(message))) {
            score += 0.06;
        }
        if (gapSeconds <= 120L && !messageTokens.isEmpty() && cluster.tokens.stream().anyMatch(messageTokens::contains)) {
            score += 0.04;
        }
        return score;
    }

    private boolean hasReplyContinuity(TopicDraftCluster cluster, MessageEntity message) {
        Long replyTo = message.getReplyToMessageId();
        if (replyTo != null && cluster.telegramMessageIds.contains(replyTo)) {
            return true;
        }
        Long telegramMessageId = message.getTelegramMessageId();
        return telegramMessageId != null && cluster.replyToTelegramMessageIds.contains(telegramMessageId);
    }

    private String participantKey(MessageEntity message) {
        if (message.getSenderTelegramUserId() != null) {
            return "tg:" + message.getSenderTelegramUserId();
        }
        return "name:" + normalized(message.getSenderName());
    }

    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        int intersection = 0;
        for (String token : left) {
            if (right.contains(token)) {
                intersection++;
            }
        }
        int union = left.size() + right.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    private Set<String> topicTokens(MessageEntity message) {
        return semanticTokens(List.of(message), "");
    }

    private TopicExplainMessage toTopicExplainMessage(MessageEntity message, GroupEntity group) {
        return new TopicExplainMessage(
            message.getId(),
            message.getGroupId(),
            group != null ? group.getTitle() : null,
            message.getTopicId(),
            message.getTopicName(),
            message.getMessageDate(),
            sanitize(message.getSenderName()),
            sanitize(truncate(message.getText(), 500)),
            message.getProcessingStatus(),
            message.getClassifierScore(),
            sanitize(truncate(message.getClassifierReason(), 500)),
            sanitize(truncate(message.getClassifierResultJson(), 500)),
            sanitize(truncate(message.getRuleResultJson(), 500)),
            message.getSignalScore(),
            message.getProblemSignalScore(),
            message.getPainScore(),
            message.getUrgencyScore(),
            message.getWillingnessToPayScore(),
            message.getTechnicalDepthScore(),
            sanitize(truncate(message.getProblemStatement(), 500)),
            sanitize(truncate(message.getSolutionHint(), 500)),
            sanitize(truncate(message.getMentionedToolsJson(), 500)),
            sanitize(truncate(message.getMentionedPricesJson(), 500)),
            sanitize(truncate(message.getMentionedErrorsJson(), 500)),
            sanitize(truncate(message.getIntelligenceReason(), 500)),
            message.getGuidePotentialScore(),
            message.getClusterCandidate(),
            message.getSpamScore(),
            message.getGuideId(),
            message.getClassificationContextHash()
        );
    }

    private boolean matchesGroupFilter(
        MessageEntity message,
        Map<Long, GroupEntity> groupsById,
        String groupFilter,
        MessageEntity anchor
    ) {
        if (anchor != null && !anchor.getGroupId().equals(message.getGroupId())) {
            return false;
        }
        if (groupFilter == null || groupFilter.isBlank()) {
            return true;
        }
        String trimmed = groupFilter.trim();
        if (isLong(trimmed) && Long.parseLong(trimmed) == message.getGroupId()) {
            return true;
        }
        GroupEntity group = groupsById.get(message.getGroupId());
        String title = group != null && group.getTitle() != null ? group.getTitle() : "";
        return title.toLowerCase(Locale.ROOT).contains(trimmed.toLowerCase(Locale.ROOT));
    }

    private boolean matchesTopicFilter(MessageEntity message, String topicFilter, MessageEntity anchor) {
        if (anchor != null) {
            if (anchor.getTopicId() != null || message.getTopicId() != null) {
                return Objects.equals(anchor.getTopicId(), message.getTopicId());
            }
            return normalized(message.getTopicName()).equals(normalized(anchor.getTopicName()));
        }
        if (topicFilter == null || topicFilter.isBlank()) {
            return true;
        }
        String trimmed = topicFilter.trim();
        if (isLong(trimmed) && message.getTopicId() != null && Long.parseLong(trimmed) == message.getTopicId()) {
            return true;
        }
        return normalized(message.getTopicName()).contains(normalized(trimmed));
    }

    private boolean isTopicCandidateMessage(MessageEntity message) {
        return Boolean.TRUE.equals(message.getClusterCandidate())
            || "CLASSIFIED".equals(message.getProcessingStatus())
            || "CLUSTERED".equals(message.getProcessingStatus())
            || "GUIDE_FOUND".equals(message.getProcessingStatus())
            || message.getGuideId() != null;
    }

    private boolean matchesTopicCandidateFilters(
        MessageEntity message,
        boolean clusterCandidateOnly,
        Integer minProblemSignalScore,
        Integer minPainScore,
        Integer minWillingnessToPayScore,
        Integer minGuidePotentialScore,
        int maxSpamScore
    ) {
        if (score(message.getSpamScore()) >= maxSpamScore) {
            return false;
        }
        if (clusterCandidateOnly && !Boolean.TRUE.equals(message.getClusterCandidate())) {
            return false;
        }
        if (minProblemSignalScore != null && score(message.getProblemSignalScore()) < minProblemSignalScore) {
            return false;
        }
        if (minPainScore != null && score(message.getPainScore()) < minPainScore) {
            return false;
        }
        if (minWillingnessToPayScore != null
            && score(message.getWillingnessToPayScore()) < minWillingnessToPayScore) {
            return false;
        }
        return minGuidePotentialScore == null || score(message.getGuidePotentialScore()) >= minGuidePotentialScore;
    }

    private List<String> guideAnglesFor(List<MessageEntity> messages, String safetyCategory) {
        if ("risk_abuse_cyber_safety".equals(safetyCategory)) {
            return List.of("risk analysis", "defensive checklist", "moderation insight");
        }
        if ("spam_or_ad".equals(safetyCategory)) {
            return List.of("moderation insight", "source-quality review");
        }

        List<String> angles = new ArrayList<>();
        int guidePotential = messages.stream()
            .map(MessageEntity::getGuidePotentialScore)
            .filter(Objects::nonNull)
            .max(Integer::compareTo)
            .orElse(0);
        int pain = messages.stream()
            .map(MessageEntity::getPainScore)
            .filter(Objects::nonNull)
            .max(Integer::compareTo)
            .orElse(0);
        int willingnessToPay = messages.stream()
            .map(MessageEntity::getWillingnessToPayScore)
            .filter(Objects::nonNull)
            .max(Integer::compareTo)
            .orElse(0);
        if (guidePotential >= 60) {
            angles.add("practical guide");
        }
        if (pain >= 60) {
            angles.add("problem analysis");
        }
        if (willingnessToPay >= 50) {
            angles.add("market demand insight");
        }
        if (angles.isEmpty()) {
            angles.add("topic summary");
        }
        return angles.stream().distinct().limit(4).toList();
    }

    private final class TopicDraftCluster {
        private final List<MessageEntity> messages = new ArrayList<>();
        private final Set<String> tokens = new LinkedHashSet<>();
        private final Set<String> participants = new LinkedHashSet<>();
        private final Set<Long> telegramMessageIds = new LinkedHashSet<>();
        private final Set<Long> replyToTelegramMessageIds = new LinkedHashSet<>();
        private Instant lastMessageAt;

        private TopicDraftCluster(MessageEntity message, Set<String> messageTokens) {
            add(message, messageTokens);
        }

        private void add(MessageEntity message, Set<String> messageTokens) {
            messages.add(message);
            tokens.addAll(messageTokens);
            participants.add(participantKey(message));
            if (message.getTelegramMessageId() != null) {
                telegramMessageIds.add(message.getTelegramMessageId());
            }
            if (message.getReplyToMessageId() != null) {
                replyToTelegramMessageIds.add(message.getReplyToMessageId());
            }
            lastMessageAt = message.getMessageDate();
        }
    }

    private int messageEvidenceScore(MessageEntity message) {
        return score(message.getGuidePotentialScore())
            + score(message.getProblemSignalScore())
            + score(message.getPainScore())
            + score(message.getWillingnessToPayScore())
            + score(message.getTechnicalDepthScore())
            - score(message.getSpamScore());
    }

    private String candidateSummary(List<MessageEntity> messages) {
        return messages.stream()
            .map(message -> firstNonBlank(message.getProblemStatement(), message.getMeaningSummary(), message.getText()))
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .map(value -> truncate(value.replaceAll("\\s+", " "), 220))
            .orElse(null);
    }

    private String topicLabel(List<MessageEntity> messages, ClassifierResult classifierResult) {
        return truncate(firstNonBlank(
            classifierResult != null ? classifierResult.problemStatement() : null,
            classifierResult != null ? classifierResult.meaningSummary() : null,
            messages.isEmpty() ? null : messages.get(0).getProblemStatement(),
            messages.isEmpty() ? null : messages.get(0).getMeaningSummary(),
            messages.isEmpty() ? null : messages.get(0).getTopicName(),
            messages.isEmpty() ? null : messages.get(0).getText(),
            "topic-cluster"
        ), 512);
    }

    private String topicSummary(List<MessageEntity> messages, ClassifierResult classifierResult) {
        String summary = firstNonBlank(
            classifierResult != null ? classifierResult.meaningSummary() : null,
            classifierResult != null ? classifierResult.problemStatement() : null,
            candidateSummary(messages)
        );
        if (summary != null && !summary.isBlank()) {
            return truncate(summary, 2000);
        }
        return truncate(messages.stream()
            .map(MessageEntity::getText)
            .filter(text -> text != null && !text.isBlank())
            .limit(3)
            .collect(Collectors.joining(" / ")), 2000);
    }

    private String inferSafetyCategory(List<MessageEntity> messages, ClassifierResult classifierResult) {
        if (messages.stream().anyMatch(message -> score(message.getSpamScore()) >= 70)
            || classifierResult != null && score(classifierResult.spamScore()) >= 70) {
            return "spam_or_ad";
        }
        String combined = (
            (classifierResult != null ? firstNonBlank(
                classifierResult.problemStatement(),
                classifierResult.meaningSummary(),
                classifierResult.solutionHint(),
                ""
            ) : "")
                + " "
                + messages.stream()
                    .map(message -> firstNonBlank(message.getProblemStatement(), message.getMeaningSummary(), message.getText(), ""))
                    .collect(Collectors.joining(" "))
        ).toLowerCase(Locale.ROOT);
        if (RISK_MARKERS.stream().anyMatch(combined::contains)) {
            return "risk_abuse_cyber_safety";
        }
        return "normal";
    }

    private boolean isSafetyRestricted(TopicDiscussionClusterEntity cluster) {
        String category = cluster.getSafetyCategory();
        return category != null && !"normal".equals(category) && !"spam_or_ad".equals(category);
    }

    private double semanticSimilarity(
        TopicDiscussionClusterEntity candidate,
        List<MessageEntity> messages,
        ClassifierResult classifierResult
    ) {
        Set<String> left = semanticTokens(List.of(), candidate.getTopicLabel() + " " + candidate.getTopicSummary());
        Set<String> right = new LinkedHashSet<>(semanticTokens(messages, classifierText(classifierResult)));
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private String semanticFingerprint(List<MessageEntity> messages, ClassifierResult classifierResult) {
        return semanticTokens(messages, classifierText(classifierResult)).stream()
            .limit(8)
            .collect(Collectors.joining(" "));
    }

    private String classifierText(ClassifierResult classifierResult) {
        if (classifierResult == null) {
            return "";
        }
        return String.join(" ",
            firstNonBlank(classifierResult.meaningSummary(), ""),
            firstNonBlank(classifierResult.problemStatement(), ""),
            firstNonBlank(classifierResult.solutionHint(), ""),
            String.join(" ", classifierResult.mentionedTools()),
            String.join(" ", classifierResult.mentionedErrors()),
            String.join(" ", classifierResult.categories())
        );
    }

    private Set<String> semanticTokens(List<MessageEntity> messages, String extraText) {
        String combined = (
            extraText != null ? extraText : ""
        ) + " " + messages.stream()
            .map(message -> firstNonBlank(message.getProblemStatement(), message.getMeaningSummary(), message.getText(), ""))
            .collect(Collectors.joining(" "));
        String normalized = Normalizer.normalize(combined, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT);
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String raw : TOKEN_SPLIT_PATTERN.split(normalized)) {
            String token = raw.trim();
            if (token.length() < 3 || STOP_WORDS.contains(token)) {
                continue;
            }
            tokens.add(token.length() > 32 ? token.substring(0, 32) : token);
        }
        return tokens;
    }

    private String resolveMessageRole(MessageEntity anchor, MessageEntity sourceMessage, Set<Long> evidenceIds) {
        if (sourceMessage.getId().equals(anchor.getId())) {
            return "anchor";
        }
        if (evidenceIds.contains(sourceMessage.getId())) {
            return "evidence";
        }
        if (sourceMessage.getReplyToMessageId() != null || anchor.getReplyToMessageId() != null) {
            return "supporting";
        }
        return "context";
    }

    private Double contributionScore(MessageEntity message) {
        int raw = messageEvidenceScore(message);
        return Math.max(0.0, Math.min(1.0, raw / 300.0));
    }

    private Integer signalProblemScore(SignalScore signalScore) {
        return signalScore != null && signalScore.score() > 0.0
            ? (int) Math.round(signalScore.score() * 100)
            : null;
    }

    private String topicBucketKey(MessageEntity message) {
        String topicKey = message.getTopicId() != null
            ? "topic-id:" + message.getTopicId()
            : "topic-name:" + normalized(firstNonBlank(message.getTopicName(), "main"));
        return message.getGroupId() + "|" + topicKey;
    }

    private String topicTitle(MessageEntity message) {
        return firstNonBlank(message.getTopicName(), "main");
    }

    private String semanticHash(Long groupId, Long topicId, String topicTitle, String fingerprint) {
        String raw = groupId + "|" + topicId + "|" + normalized(topicTitle) + "|" + normalized(fingerprint);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < 12 && index < bytes.length; index++) {
                result.append(String.format("%02x", bytes[index]));
            }
            return result.toString();
        } catch (Exception exception) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private TopicExplainResponse emptyTopicExplain(int windowMinutes, String reason) {
        return new TopicExplainResponse(
            "cluster",
            reason,
            null,
            null,
            Math.max(1, Math.min(windowMinutes, 120)),
            0,
            List.of(),
            List.of(),
            List.of("No database writes are performed")
        );
    }

    private Instant parseDateOrInstant(String value, boolean isFrom) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            // Try date-only below.
        }
        try {
            LocalDate date = LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
            return isFrom
                ? date.atStartOfDay(ZoneOffset.UTC).toInstant()
                : date.atTime(23, 59, 59, 999999999).atZone(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException ignored) {
            // Throw a consistent API error below.
        }
        throw new IllegalArgumentException(
            "Invalid date format: '" + trimmed + "'. Expected ISO-8601 date-time or date-only value.");
    }

    private Instant minInstant(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isBefore(right) ? left : right;
    }

    private Instant maxInstant(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private Integer maxScore(Integer primary, List<Integer> values) {
        int max = primary != null ? primary : 0;
        for (Integer value : values) {
            if (value != null && value > max) {
                max = value;
            }
        }
        return max;
    }

    private int score(Integer value) {
        return value != null ? value : 0;
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String redacted = SENSITIVE_VALUE_PATTERN.matcher(value).replaceAll("$1=<redacted>");
        return redacted.replaceAll("\\s+", " ").trim();
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isLong(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String safeToJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return String.valueOf(value);
        }
    }

    public record ClusterUpdateResult(
        TopicDiscussionClusterEntity cluster,
        List<MessageEntity> sourceMessages,
        boolean created,
        int linkedMessages,
        String groupingReason
    ) {}

    public record ClusterClassificationResult(
        TopicDiscussionClusterEntity cluster,
        List<TopicClusterGuideCandidateEntity> guideCandidates
    ) {}

    public record TopicExplainResponse(
        String currentProcessingUnit,
        String explanation,
        Instant from,
        Instant to,
        int windowMinutes,
        int sourceMessageCount,
        List<TopicCandidateItem> topicCandidates,
        List<TopicExplainMessage> sourceMessages,
        List<String> caveats
    ) {}

    public record TopicCandidatesResponse(
        String processingUnit,
        String explanation,
        Instant from,
        Instant to,
        int windowMinutes,
        int sourceMessageCount,
        int topicCandidateCount,
        List<TopicCandidateItem> topicCandidates,
        List<String> caveats
    ) {}

    public record TopicCandidateItem(
        String topicKey,
        Long groupId,
        String groupTitle,
        Long topicId,
        String topicName,
        Instant startAt,
        Instant endAt,
        List<Long> sourceMessageIds,
        List<String> participants,
        String canonicalSummary,
        String topicLabel,
        String semanticHash,
        Integer guidePotentialScore,
        String riskSafetyCategory,
        String status,
        Long guideId,
        List<Long> bestEvidenceMessageIds,
        List<String> guideAngles,
        List<TopicExplainMessage> sourceMessages,
        String groupingReason
    ) {}

    public record TopicExplainMessage(
        Long messageId,
        Long groupId,
        String groupTitle,
        Long topicId,
        String topicName,
        Instant messageDate,
        String senderName,
        String sanitizedText,
        String processingStatus,
        Double classifierScore,
        String classifierReason,
        String classifierResultSummary,
        String ruleResultSummary,
        Double signalScore,
        Integer problemSignalScore,
        Integer painScore,
        Integer urgencyScore,
        Integer willingnessToPayScore,
        Integer technicalDepthScore,
        String problemStatement,
        String solutionHint,
        String mentionedToolsJson,
        String mentionedPricesJson,
        String mentionedErrorsJson,
        String intelligenceReason,
        Integer guidePotentialScore,
        Boolean clusterCandidate,
        Integer spamScore,
        Long guideId,
        String classificationContextHash
    ) {}

    public record TopicClusterItem(
        Long id,
        Long groupId,
        String groupTitle,
        Long topicId,
        String topicName,
        Instant startAt,
        Instant endAt,
        String status,
        String topicLabel,
        String topicSummary,
        String semanticHash,
        Integer guidePotentialScore,
        Integer problemSignalScore,
        String safetyCategory,
        String classificationStatus,
        String guideGenerationStatus,
        Long guideId,
        List<TopicClusterSourceMessageItem> sourceMessages,
        List<TopicClusterGuideCandidateItem> guideCandidates,
        String whyGroupedTogether,
        int hiddenByFilters
    ) {}

    public record TopicClusterSourceMessageItem(
        Long messageId,
        String role,
        Double contributionScore,
        String senderName,
        String sanitizedText,
        Instant messageDate,
        String processingStatus,
        Double signalScore,
        Double classifierScore,
        Integer guidePotentialScore,
        Integer problemSignalScore
    ) {}

    public record TopicClusterGuideCandidateItem(
        Long id,
        Long clusterId,
        Long guideId,
        String status,
        String guideAngle,
        String contentType,
        String contentSubtype,
        String topicLabel,
        String topicSummary,
        String contentTitle,
        String contentSummary,
        String normalizedTopicKey,
        Integer contentQualityScore,
        Integer importanceScore,
        Integer actionabilityScore,
        Integer noveltyScore,
        Integer evidenceScore,
        Integer riskScore,
        Integer confidenceScore,
        Integer noiseScore,
        String routingReason,
        String publicationKind,
        Boolean shouldCreateMaterial,
        Boolean shouldGenerateFullGuide,
        String safetyCategory,
        String whyThisCluster,
        String sourceMessageIdsJson
    ) {}
}
