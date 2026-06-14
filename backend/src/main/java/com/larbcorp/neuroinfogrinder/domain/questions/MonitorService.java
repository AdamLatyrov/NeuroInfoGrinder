package com.larbcorp.neuroinfogrinder.domain.questions;

import com.larbcorp.neuroinfogrinder.domain.questions.dto.GroupStatsResponse;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.GuidesByGroupResponse;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.QueueStatusResponse;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.TokenByProviderDto;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.TokenDailyResponse;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.TokenSummaryResponse;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.TopMessagesGroupResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.AiProviderEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.AiUsageLogEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TaskQueueEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.AiProviderRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.AiUsageLogRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuideRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TaskQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MonitorService {

    private final AiUsageLogRepository aiUsageLogRepository;
    private final TaskQueueRepository taskQueueRepository;
    private final GroupRepository groupRepository;
    private final MessageRepository messageRepository;
    private final GuideRepository guideRepository;
    private final AiProviderRepository aiProviderRepository;

    private final AtomicBoolean queuePaused = new AtomicBoolean(false);

    @Transactional(readOnly = true)
    public TokenSummaryResponse getTokenSummary() {
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant now = Instant.now();

        List<AiUsageLogEntity> logs = aiUsageLogRepository.findByCreatedAtBetween(startOfDay, now);

        long totalTokens = logs.stream().mapToLong(AiUsageLogEntity::getTotalTokens).sum();
        double totalCost = logs.stream().mapToDouble(AiUsageLogEntity::getEstimatedCostUsd).sum();

        List<TokenByProviderDto> byProvider = getTokensByProvider();

        return new TokenSummaryResponse(totalTokens, totalCost, byProvider);
    }

    @Transactional(readOnly = true)
    public List<TokenDailyResponse> getTokenDaily(String from, String to) {
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        Instant fromInstant = LocalDate.parse(from, fmt).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = LocalDate.parse(to, fmt).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<AiUsageLogEntity> logs = aiUsageLogRepository.findByCreatedAtBetween(fromInstant, toInstant);

        Map<LocalDate, List<AiUsageLogEntity>> byDate = logs.stream()
            .collect(Collectors.groupingBy(log ->
                LocalDate.ofInstant(log.getCreatedAt(), ZoneOffset.UTC)
            ));

        return byDate.entrySet().stream()
            .map(entry -> {
                long tokens = entry.getValue().stream().mapToLong(AiUsageLogEntity::getTotalTokens).sum();
                double cost = entry.getValue().stream().mapToDouble(AiUsageLogEntity::getEstimatedCostUsd).sum();
                return new TokenDailyResponse(entry.getKey().toString(), tokens, cost);
            })
            .sorted(Comparator.comparing(TokenDailyResponse::date))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<TokenByProviderDto> getTokensByProvider() {
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant now = Instant.now();

        List<AiUsageLogEntity> logs = aiUsageLogRepository.findByCreatedAtBetween(startOfDay, now);

        Map<Long, Long> tokensByProvider = logs.stream()
            .filter(l -> l.getProviderId() != null)
            .collect(Collectors.groupingBy(AiUsageLogEntity::getProviderId,
                Collectors.summingLong(AiUsageLogEntity::getTotalTokens)));

        Map<Long, Double> costByProvider = logs.stream()
            .filter(l -> l.getProviderId() != null)
            .collect(Collectors.groupingBy(AiUsageLogEntity::getProviderId,
                Collectors.summingDouble(AiUsageLogEntity::getEstimatedCostUsd)));

        Map<Long, String> providerNames = aiProviderRepository.findAll().stream()
            .collect(Collectors.toMap(AiProviderEntity::getId, AiProviderEntity::getName));

        return tokensByProvider.entrySet().stream()
            .map(entry -> new TokenByProviderDto(
                entry.getKey(),
                providerNames.getOrDefault(entry.getKey(), "Unknown"),
                entry.getValue(),
                costByProvider.getOrDefault(entry.getKey(), 0.0)
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public String exportTokensCsv() {
        List<AiUsageLogEntity> logs = aiUsageLogRepository.findAll();

        Map<Long, String> providerNames = aiProviderRepository.findAll().stream()
            .collect(Collectors.toMap(AiProviderEntity::getId, AiProviderEntity::getName));

        StringBuilder sb = new StringBuilder();
        sb.append("id,createdAt,taskType,providerId,providerName,model,guideId,inputTokens,outputTokens,totalTokens,estimatedCostUsd\n");

        for (AiUsageLogEntity log : logs) {
            sb.append(log.getId()).append(',')
              .append(log.getCreatedAt()).append(',')
              .append(log.getTaskType()).append(',')
              .append(log.getProviderId()).append(',')
              .append(providerNames.getOrDefault(log.getProviderId(), "")).append(',')
              .append(log.getModel() != null ? log.getModel() : "").append(',')
              .append(log.getGuideId() != null ? log.getGuideId() : "").append(',')
              .append(log.getInputTokens()).append(',')
              .append(log.getOutputTokens()).append(',')
              .append(log.getTotalTokens()).append(',')
              .append(log.getEstimatedCostUsd()).append('\n');
        }

        return sb.toString();
    }

    @Transactional(readOnly = true)
    public QueueStatusResponse getQueueStatus() {
        long queued = taskQueueRepository.countByStatus("QUEUED");
        long running = taskQueueRepository.countByStatus("RUNNING");
        long stuck = taskQueueRepository.countByStatus("STUCK");

        return new QueueStatusResponse(queued, running, stuck, queuePaused.get());
    }

    @Transactional(readOnly = true)
    public Page<TaskQueueEntity> getQueueHistory(Pageable pageable) {
        return taskQueueRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<TaskQueueEntity> getStuckTasks() {
        // Stuck = FAILED with exhausted retries or RUNNING for too long
        List<TaskQueueEntity> failed = taskQueueRepository
            .findByStatusOrderByPriorityDescCreatedAtAsc("FAILED").stream()
            .filter(t -> t.getRetryCount() >= t.getMaxRetries())
            .toList();

        List<TaskQueueEntity> stuck = taskQueueRepository
            .findByStatusOrderByPriorityDescCreatedAtAsc("STUCK");

        // Also find RUNNING tasks started more than 30 minutes ago
        Instant thirtyMinutesAgo = Instant.now().minusSeconds(30 * 60);
        List<TaskQueueEntity> longRunning = taskQueueRepository
            .findByStatusOrderByPriorityDescCreatedAtAsc("RUNNING").stream()
            .filter(t -> t.getStartedAt() != null && t.getStartedAt().isBefore(thirtyMinutesAgo))
            .toList();

        var result = new java.util.ArrayList<>(failed);
        result.addAll(stuck);
        result.addAll(longRunning);
        return result;
    }

    public void pauseQueue() {
        queuePaused.set(true);
    }

    public void resumeQueue() {
        queuePaused.set(false);
    }

    @Transactional
    public void clearQueue() {
        List<TaskQueueEntity> queued = taskQueueRepository
            .findByStatusOrderByPriorityDescCreatedAtAsc("QUEUED");
        taskQueueRepository.deleteAll(queued);
    }

    @Transactional(readOnly = true)
    public List<GroupStatsResponse> getGroupStats() {
        List<GroupEntity> groups = groupRepository.findByEnabledTrue();

        return groups.stream().map(group -> {
            long messagesRead = messageRepository.countByGroupIdAndMessageDateAfter(
                group.getId(), Instant.EPOCH);

            long guidesGenerated = guideRepository.findByGroupId(group.getId(),
                org.springframework.data.domain.Pageable.ofSize(1)).getTotalElements();

            long guidesPublished = guideRepository.findByStatusIn(
                List.of("PUBLISHED"), org.springframework.data.domain.Pageable.ofSize(1)).getTotalElements();

            // Chains built and error count are placeholders since no direct repo methods exist
            long chainsBuilt = 0;
            long errorCount = taskQueueRepository.findByStatusOrderByPriorityDescCreatedAtAsc("FAILED").stream()
                .filter(t -> t.getInputRefType() != null && t.getInputRefType().equals("GROUP")
                    && group.getId().equals(t.getInputRefId()))
                .count();

            // Scale published to just this group's guides
            long groupPublished = guideRepository.findByGroupId(group.getId(),
                org.springframework.data.domain.Pageable.ofSize(Integer.MAX_VALUE)).stream()
                .filter(g -> "PUBLISHED".equals(g.getStatus()))
                .count();

            return new GroupStatsResponse(
                group.getId(),
                group.getTitle(),
                messagesRead,
                chainsBuilt,
                guidesGenerated,
                groupPublished,
                errorCount
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<TopMessagesGroupResponse> getTopMessagesGroups(int limit) {
        List<GroupEntity> groups = groupRepository.findByEnabledTrue();
        Instant thirtyDaysAgo = Instant.now().minusSeconds(30L * 24 * 60 * 60);

        return groups.stream()
            .map(group -> {
                long msgCount = messageRepository.countByGroupIdAndMessageDateAfter(
                    group.getId(), thirtyDaysAgo);
                long messagesPerDay = msgCount / 30;
                return new TopMessagesGroupResponse(group.getId(), group.getTitle(), messagesPerDay);
            })
            .sorted(Comparator.comparingLong(TopMessagesGroupResponse::messagesPerDay).reversed())
            .limit(limit)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<GuidesByGroupResponse> getGuidesByGroup() {
        List<GroupEntity> groups = groupRepository.findByEnabledTrue();

        return groups.stream().map(group -> {
            List<GuideEntity> groupGuides = guideRepository.findByGroupId(group.getId(),
                org.springframework.data.domain.Pageable.ofSize(Integer.MAX_VALUE)).getContent();

            long count = groupGuides.size();
            double avgConfidence = groupGuides.stream()
                .filter(g -> g.getConfidence() != null)
                .mapToDouble(GuideEntity::getConfidence)
                .average()
                .orElse(0.0);

            return new GuidesByGroupResponse(group.getId(), group.getTitle(), count, avgConfidence);
        }).toList();
    }
}
