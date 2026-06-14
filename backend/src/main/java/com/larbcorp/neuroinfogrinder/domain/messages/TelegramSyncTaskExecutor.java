package com.larbcorp.neuroinfogrinder.domain.messages;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class TelegramSyncTaskExecutor {

    @Value("${telegram.tdlib.sync.max-parallelism:2}")
    private int maxParallelism;

    @Value("${telegram.tdlib.sync.queue-capacity:4}")
    private int queueCapacity;

    private ThreadPoolExecutor executor;

    @PostConstruct
    void initialize() {
        int normalizedParallelism = Math.max(1, maxParallelism);
        int normalizedQueueCapacity = Math.max(0, queueCapacity);
        BlockingQueue<Runnable> queue = normalizedQueueCapacity == 0
                ? new SynchronousQueue<>()
                : new ArrayBlockingQueue<>(normalizedQueueCapacity);

        executor = new ThreadPoolExecutor(
                normalizedParallelism,
                normalizedParallelism,
                30L,
                TimeUnit.SECONDS,
                queue,
                new SyncThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public boolean execute(String taskName, Runnable runnable) {
        try {
            executor.execute(runnable);
            return true;
        } catch (RejectedExecutionException exception) {
            log.warn(
                    "Telegram sync executor saturated; skipping task {} (active={}, queued={}, remainingQueue={})",
                    taskName,
                    getActiveCount(),
                    getQueueSize(),
                    getRemainingQueueCapacity()
            );
            return false;
        }
    }

    public int getActiveCount() {
        return executor.getActiveCount();
    }

    public int getQueueSize() {
        return executor.getQueue().size();
    }

    public int getRemainingQueueCapacity() {
        return executor.getQueue().remainingCapacity();
    }

    @PreDestroy
    void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private static final class SyncThreadFactory implements ThreadFactory {

        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "telegram-sync-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
