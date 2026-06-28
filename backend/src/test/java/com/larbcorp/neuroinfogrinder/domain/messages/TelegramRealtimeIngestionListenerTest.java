package com.larbcorp.neuroinfogrinder.domain.messages;

import com.larbcorp.neuroinfogrinder.telegram.model.TelegramMessageDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TelegramRealtimeIngestionListenerTest {

    @Test
    void updateCallbackOffersMessageToBoundedWorkerQueueAndReturnsBeforePersistence() throws Exception {
        BlockingIngestionService ingestionService = new BlockingIngestionService();
        TelegramRealtimeIngestionListener listener = new TelegramRealtimeIngestionListener(ingestionService);
        ReflectionTestUtils.setField(listener, "ingestionEnabled", true);
        ReflectionTestUtils.setField(listener, "queueSize", 10);
        ReflectionTestUtils.setField(listener, "workerThreads", 1);
        ReflectionTestUtils.setField(listener, "offerTimeoutMs", 50L);
        listener.initialize();

        long startedAt = System.nanoTime();
        listener.onNewMessage(7L, message(9001L, -1001L));
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertThat(elapsedMs).isLessThan(100L);
        assertThat(ingestionService.started.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(ingestionService.accountId.get()).isEqualTo(7L);
        assertThat(listener.getSubmittedCount()).isEqualTo(1L);

        ingestionService.release.countDown();
        listener.shutdown();
    }

    @Test
    void ingestionDisabledDropsUpdateWithoutQueueingWork() {
        TelegramMessageIngestionService ingestionService = mock(TelegramMessageIngestionService.class);
        TelegramRealtimeIngestionListener listener = new TelegramRealtimeIngestionListener(ingestionService);
        ReflectionTestUtils.setField(listener, "ingestionEnabled", false);

        listener.onNewMessage(7L, message(9001L, -1001L));

        assertThat(listener.getSubmittedCount()).isZero();
        verify(ingestionService, never()).ingestRealtimeMessage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void fullQueueRejectsUpdateWithoutBlockingTdlibThreadIndefinitely() throws Exception {
        BlockingIngestionService ingestionService = new BlockingIngestionService();
        TelegramRealtimeIngestionListener listener = new TelegramRealtimeIngestionListener(ingestionService);
        ReflectionTestUtils.setField(listener, "ingestionEnabled", true);
        ReflectionTestUtils.setField(listener, "queueSize", 1);
        ReflectionTestUtils.setField(listener, "workerThreads", 1);
        ReflectionTestUtils.setField(listener, "offerTimeoutMs", 10L);
        listener.initialize();

        listener.onNewMessage(7L, message(1L, -1001L));
        assertThat(ingestionService.started.await(1, TimeUnit.SECONDS)).isTrue();
        listener.onNewMessage(7L, message(2L, -1001L));
        listener.onNewMessage(7L, message(3L, -1001L));

        assertThat(listener.getRejectedCount()).isGreaterThanOrEqualTo(1L);

        ingestionService.release.countDown();
        listener.shutdown();
    }

    private static TelegramMessageDto message(long messageId, long chatId) {
        return new TelegramMessageDto(
                messageId,
                chatId,
                0L,
                null,
                "MessageText",
                "hello",
                "Alice",
                "alice",
                501L,
                false,
                null,
                0L,
                Instant.parse("2026-06-20T10:00:00Z").getEpochSecond()
        );
    }

    private static final class BlockingIngestionService extends TelegramMessageIngestionService {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicReference<Long> accountId = new AtomicReference<>();

        private BlockingIngestionService() {
            super(null, null, null, null, null);
        }

        @Override
        public TelegramIngestionResult ingestRealtimeMessage(Long telegramAccountId, TelegramMessageDto message) {
            accountId.set(telegramAccountId);
            started.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return TelegramIngestionResult.SAVED;
        }
    }
}
