package com.larbcorp.neuroinfogrinder.domain.findings;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodically dequeues QUEUED messages and runs them through the pipeline.
 * Also picks up UNPROCESSED messages that haven't been processed yet.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueProcessor {

    private final MessageRepository messageRepository;
    private final GroupRepository groupRepository;
    private final PipelineService pipelineService;

    private boolean enabled = true;

    /**
     * Run every 5 seconds. Picks up to 5 messages per cycle.
     * Respects the enabled flag — can be paused via API.
     */
    @Scheduled(
        fixedDelayString = "${neuroinfogrinder.pipeline.queue-delay-ms:500}",
        initialDelayString = "${neuroinfogrinder.pipeline.queue-initial-delay-ms:1000}"
    )
    public void processQueue() {
        if (!enabled) {
            return;
        }

        List<Long> enabledGroupIds = groupRepository.findByEnabledTrue().stream()
            .map(group -> group.getId())
            .toList();

        if (enabledGroupIds.isEmpty()) {
            return;
        }

        List<MessageEntity> queued = messageRepository
            .findByGroupIdInAndProcessingStatusInOrderByMessageDateAsc(
                enabledGroupIds,
                List.of("QUEUED", "UNPROCESSED"),
                org.springframework.data.domain.Pageable.ofSize(20)
            ).getContent();

        if (queued.isEmpty()) {
            return;
        }

        log.info("QueueProcessor: processing {} messages", queued.size());

        for (MessageEntity msg : queued) {
            try {
                pipelineService.processMessage(msg.getId());
            } catch (Exception e) {
                log.error("QueueProcessor: failed to process message {}: {}", msg.getId(), e.getMessage(), e);
                messageRepository.findById(msg.getId()).ifPresent(message -> {
                    message.setProcessingStatus("SKIPPED");
                    message.setClassifierReason("Ошибка конвейера: " + e.getMessage());
                    messageRepository.save(message);
                });
            }
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        log.info("QueueProcessor enabled={}", enabled);
    }

    public boolean isEnabled() {
        return enabled;
    }
}
