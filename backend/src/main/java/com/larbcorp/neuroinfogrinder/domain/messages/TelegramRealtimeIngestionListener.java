package com.larbcorp.neuroinfogrinder.domain.messages;

import com.larbcorp.neuroinfogrinder.telegram.TelegramUpdateListener;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramMessageDto;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class TelegramRealtimeIngestionListener implements TelegramUpdateListener {

    private final TelegramMessageIngestionService ingestionService;

    @Value("${telegram.ingestion.enabled:true}")
    private boolean ingestionEnabled;

    @Value("${telegram.ingestion.queue-size:10000}")
    private int queueSize;

    @Value("${telegram.ingestion.worker-threads:2}")
    private int workerThreads;

    @Value("${telegram.ingestion.offer-timeout-ms:100}")
    private long offerTimeoutMs;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong submittedCount = new AtomicLong(0);
    private final AtomicLong rejectedCount = new AtomicLong(0);
    private final AtomicLong disabledDropCount = new AtomicLong(0);
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private BlockingQueue<RealtimeMessageEnvelope> queue;
    private Thread[] workers;

    public TelegramRealtimeIngestionListener(TelegramMessageIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostConstruct
    void initialize() {
        int normalizedQueueSize = Math.max(1, queueSize);
        int normalizedWorkers = Math.max(1, workerThreads);
        queue = new ArrayBlockingQueue<>(normalizedQueueSize);
        workers = new Thread[normalizedWorkers];
        running.set(true);
        AtomicInteger counter = new AtomicInteger(1);
        for (int i = 0; i < normalizedWorkers; i++) {
            Thread worker = new Thread(this::runWorker, "telegram-ingestion-" + counter.getAndIncrement());
            worker.setDaemon(true);
            worker.start();
            workers[i] = worker;
        }
        log.info("Telegram realtime ingestion queue initialized: queueSize={} workerThreads={}", normalizedQueueSize, normalizedWorkers);
    }

    @Override
    public void onNewMessage(TelegramMessageDto message) {
        onNewMessage(null, message);
    }

    @Override
    public void onNewMessage(Long telegramAccountId, TelegramMessageDto message) {
        if (!ingestionEnabled) {
            disabledDropCount.incrementAndGet();
            return;
        }
        if (message == null) {
            return;
        }
        ensureInitialized();
        try {
            boolean accepted = queue.offer(
                    new RealtimeMessageEnvelope(telegramAccountId, message),
                    Math.max(0L, offerTimeoutMs),
                    TimeUnit.MILLISECONDS
            );
            if (accepted) {
                submittedCount.incrementAndGet();
            } else {
                rejectedCount.incrementAndGet();
                log.warn(
                        "Telegram realtime ingestion queue full; dropping update account={} chat={} message={} queueSize={}",
                        telegramAccountId,
                        message.chatId(),
                        message.id(),
                        getQueueSize()
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            rejectedCount.incrementAndGet();
        }
    }

    public int getQueueSize() {
        return queue == null ? 0 : queue.size();
    }

    public long getSubmittedCount() {
        return submittedCount.get();
    }

    public long getRejectedCount() {
        return rejectedCount.get();
    }

    public long getDisabledDropCount() {
        return disabledDropCount.get();
    }

    public String getLastError() {
        return lastError.get();
    }

    @PreDestroy
    void shutdown() {
        running.set(false);
        if (workers != null) {
            for (Thread worker : workers) {
                if (worker != null) {
                    worker.interrupt();
                }
            }
        }
    }

    private void runWorker() {
        while (running.get()) {
            try {
                RealtimeMessageEnvelope envelope = queue.poll(500L, TimeUnit.MILLISECONDS);
                if (envelope == null) {
                    continue;
                }
                ingestionService.ingestRealtimeMessage(envelope.telegramAccountId(), envelope.message());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception exception) {
                lastError.set(sanitize(exception.getMessage()));
                log.warn("Telegram realtime ingestion worker failed: {}", sanitize(exception.getMessage()));
            }
        }
    }

    private void ensureInitialized() {
        if (queue == null) {
            initialize();
        }
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    private record RealtimeMessageEnvelope(Long telegramAccountId, TelegramMessageDto message) {
    }
}
