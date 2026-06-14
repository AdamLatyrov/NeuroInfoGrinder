package com.larbcorp.neuroinfogrinder.domain.messages;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramSyncTaskExecutorTest {

    private TelegramSyncTaskExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            ReflectionTestUtils.invokeMethod(executor, "shutdown");
        }
    }

    @Test
    void executeReturnsFalseWhenExecutorIsSaturated() throws InterruptedException {
        executor = new TelegramSyncTaskExecutor();
        ReflectionTestUtils.setField(executor, "maxParallelism", 1);
        ReflectionTestUtils.setField(executor, "queueCapacity", 0);
        ReflectionTestUtils.invokeMethod(executor, "initialize");

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        assertTrue(executor.execute("first", () -> {
            started.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }));
        assertTrue(started.await(1, TimeUnit.SECONDS));

        assertFalse(executor.execute("second", () -> {}));
        release.countDown();
    }
}
