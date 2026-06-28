package com.larbcorp.neuroinfogrinder.domain.messages;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TelegramBackfillJobEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TelegramBackfillJobRepository;
import com.larbcorp.neuroinfogrinder.telegram.TelegramTdlibService;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramMessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBackfillJobService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_PAUSED_FLOOD_WAIT = "PAUSED_FLOOD_WAIT";
    public static final String STATUS_PAUSED_MANUAL = "PAUSED_MANUAL";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private static final Pattern FLOOD_WAIT_PATTERN = Pattern.compile("FLOOD_WAIT_?(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final int DEFAULT_BATCH_SIZE = 100;

    private final TelegramBackfillJobRepository jobRepository;
    private final TelegramTdlibService telegramTdlibService;
    private final TelegramMessageIngestionService ingestionService;

    @Value("${telegram.backfill.enabled:true}")
    private boolean backfillEnabled;

    @Value("${telegram.backfill.max-jobs-per-tick:2}")
    private int maxJobsPerTick;

    @Scheduled(
            fixedDelayString = "${telegram.backfill.fixed-delay-ms:5000}",
            initialDelayString = "${telegram.backfill.initial-delay-ms:10000}"
    )
    public void processRunnableJobs() {
        if (!backfillEnabled) {
            return;
        }
        List<TelegramBackfillJobEntity> jobs = jobRepository.findRunnableJobs(
                List.of(STATUS_PENDING, STATUS_RUNNING),
                Instant.now(),
                Pageable.ofSize(Math.max(1, maxJobsPerTick))
        );
        for (TelegramBackfillJobEntity job : jobs) {
            processJob(job.getId());
        }
    }

    public void processJob(Long jobId) {
        Optional<TelegramBackfillJobEntity> maybeJob = jobRepository.findById(jobId);
        if (maybeJob.isEmpty()) {
            return;
        }
        TelegramBackfillJobEntity job = maybeJob.get();
        if (!backfillEnabled || !isRunnable(job)) {
            return;
        }

        try {
            markRunning(job);
            int batchSize = normalizedBatchSize(job);
            List<TelegramMessageDto> messages = telegramTdlibService.getMessages(
                    job.getTelegramAccountId(),
                    job.getTelegramChatId(),
                    job.getFromMessageId() == null ? 0L : job.getFromMessageId(),
                    batchSize
            );
            List<TelegramMessageDto> selectedMessages = filterJobMessages(job, messages);
            for (TelegramMessageDto message : selectedMessages) {
                ingestionService.ingestBackfillMessage(job.getTelegramAccountId(), message);
            }
            updateCursor(job, selectedMessages);
            completeIfDone(job, messages, selectedMessages, batchSize);
            job.setLastError(null);
            jobRepository.save(job);
        } catch (Exception exception) {
            pauseOrFail(job, exception);
        }
    }

    @Transactional
    public TelegramBackfillJobEntity createJob(TelegramBackfillJobEntity job) {
        job.setStatus(STATUS_PENDING);
        job.setBatchSize(normalizedBatchSize(job));
        job.setMessagesFetched(job.getMessagesFetched() == null ? 0L : job.getMessagesFetched());
        return jobRepository.save(job);
    }

    @Transactional
    public Optional<TelegramBackfillJobEntity> cancel(Long ownerUserId, Long jobId) {
        return jobRepository.findByIdAndOwnerUserId(jobId, ownerUserId)
                .map(job -> {
                    job.setStatus(STATUS_CANCELLED);
                    job.setPausedUntil(null);
                    return jobRepository.save(job);
                });
    }

    @Transactional
    public Optional<TelegramBackfillJobEntity> resume(Long ownerUserId, Long jobId) {
        return jobRepository.findByIdAndOwnerUserId(jobId, ownerUserId)
                .map(job -> {
                    if (STATUS_CANCELLED.equals(job.getStatus()) || STATUS_COMPLETED.equals(job.getStatus())) {
                        return job;
                    }
                    job.setStatus(STATUS_PENDING);
                    job.setPausedUntil(null);
                    job.setFloodWaitSeconds(null);
                    job.setLastError(null);
                    return jobRepository.save(job);
                });
    }

    public List<TelegramBackfillJobEntity> listOwned(Long ownerUserId) {
        return jobRepository.findByOwnerUserIdOrderByUpdatedAtDesc(ownerUserId);
    }

    private void markRunning(TelegramBackfillJobEntity job) {
        if (!STATUS_RUNNING.equals(job.getStatus())) {
            job.setStatus(STATUS_RUNNING);
            jobRepository.save(job);
        }
    }

    private boolean isRunnable(TelegramBackfillJobEntity job) {
        if (!STATUS_PENDING.equals(job.getStatus()) && !STATUS_RUNNING.equals(job.getStatus())) {
            return false;
        }
        return job.getPausedUntil() == null || !job.getPausedUntil().isAfter(Instant.now());
    }

    private List<TelegramMessageDto> filterJobMessages(TelegramBackfillJobEntity job, List<TelegramMessageDto> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
                .filter(message -> job.getTopicId() == null || message.messageThreadId() == job.getTopicId())
                .filter(message -> job.getFromDate() == null || !Instant.ofEpochSecond(message.date()).isBefore(job.getFromDate()))
                .filter(message -> job.getToDate() == null || !Instant.ofEpochSecond(message.date()).isAfter(job.getToDate()))
                .sorted(Comparator.comparingLong(TelegramMessageDto::id).reversed())
                .limit(remainingLimit(job))
                .toList();
    }

    private void updateCursor(TelegramBackfillJobEntity job, List<TelegramMessageDto> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        long oldestMessageId = messages.stream()
                .mapToLong(TelegramMessageDto::id)
                .min()
                .orElse(job.getFromMessageId() == null ? 0L : job.getFromMessageId());
        job.setFromMessageId(oldestMessageId);
        job.setMessagesFetched((job.getMessagesFetched() == null ? 0L : job.getMessagesFetched()) + messages.size());
    }

    private void completeIfDone(
            TelegramBackfillJobEntity job,
            List<TelegramMessageDto> rawMessages,
            List<TelegramMessageDto> selectedMessages,
            int batchSize
    ) {
        boolean noMoreMessages = rawMessages == null || rawMessages.isEmpty();
        boolean maxReached = job.getMaxMessages() != null
                && (job.getMessagesFetched() == null ? 0L : job.getMessagesFetched()) >= job.getMaxMessages();
        if (noMoreMessages || selectedMessages.isEmpty() || maxReached) {
            job.setStatus(STATUS_COMPLETED);
        } else {
            job.setStatus(STATUS_RUNNING);
        }
    }

    private void pauseOrFail(TelegramBackfillJobEntity job, Exception exception) {
        int floodWaitSeconds = floodWaitSeconds(exception);
        if (floodWaitSeconds > 0) {
            job.setStatus(STATUS_PAUSED_FLOOD_WAIT);
            job.setFloodWaitSeconds(floodWaitSeconds);
            job.setPausedUntil(Instant.now().plusSeconds(floodWaitSeconds));
            job.setLastError("FLOOD_WAIT_" + floodWaitSeconds);
        } else {
            job.setStatus(STATUS_FAILED);
            job.setLastError(sanitize(exception.getMessage()));
        }
        jobRepository.save(job);
    }

    private int normalizedBatchSize(TelegramBackfillJobEntity job) {
        int value = job.getBatchSize() == null ? DEFAULT_BATCH_SIZE : job.getBatchSize();
        return Math.max(1, Math.min(value, 100));
    }

    private long remainingLimit(TelegramBackfillJobEntity job) {
        if (job.getMaxMessages() == null) {
            return Long.MAX_VALUE;
        }
        long fetched = job.getMessagesFetched() == null ? 0L : job.getMessagesFetched();
        return Math.max(0L, job.getMaxMessages() - fetched);
    }

    private int floodWaitSeconds(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                Matcher matcher = FLOOD_WAIT_PATTERN.matcher(message);
                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
            }
            current = current.getCause();
        }
        return 0;
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
