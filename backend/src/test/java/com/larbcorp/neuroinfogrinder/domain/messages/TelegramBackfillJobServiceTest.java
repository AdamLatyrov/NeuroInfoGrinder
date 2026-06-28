package com.larbcorp.neuroinfogrinder.domain.messages;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TelegramBackfillJobEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TelegramBackfillJobRepository;
import com.larbcorp.neuroinfogrinder.telegram.TelegramTdlibService;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramMessageDto;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramBackfillJobServiceTest {

    @Test
    void runnableBackfillJobReadsOnlySelectedChatAndPersistsFetchedMessages() {
        TelegramBackfillJobRepository jobRepository = mock(TelegramBackfillJobRepository.class);
        TelegramTdlibService tdlibService = mock(TelegramTdlibService.class);
        TelegramMessageIngestionService ingestionService = mock(TelegramMessageIngestionService.class);
        TelegramBackfillJobService service = new TelegramBackfillJobService(jobRepository, tdlibService, ingestionService);
        ReflectionTestUtils.setField(service, "backfillEnabled", true);
        ReflectionTestUtils.setField(service, "maxJobsPerTick", 1);

        TelegramBackfillJobEntity job = job(100L, 7L, 11L, -1001L, null, 0L, 50, null);
        when(jobRepository.findRunnableJobs(any(), any(), any(Pageable.class))).thenReturn(List.of(job));
        when(jobRepository.findById(100L)).thenReturn(Optional.of(job));
        when(tdlibService.getMessages(7L, -1001L, 0L, 50)).thenReturn(List.of(message(9001L, -1001L, 0L)));
        when(ingestionService.ingestBackfillMessage(7L, message(9001L, -1001L, 0L))).thenReturn(TelegramIngestionResult.SAVED);
        when(jobRepository.save(any(TelegramBackfillJobEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.processRunnableJobs();

        verify(tdlibService).getMessages(7L, -1001L, 0L, 50);
        verify(ingestionService).ingestBackfillMessage(7L, message(9001L, -1001L, 0L));
        assertThat(job.getStatus()).isEqualTo(TelegramBackfillJobService.STATUS_RUNNING);
        assertThat(job.getFromMessageId()).isEqualTo(9001L);
        assertThat(job.getMessagesFetched()).isEqualTo(1L);
    }

    @Test
    void floodWaitPausesBackfillJobWithResumeCursorIntact() {
        TelegramBackfillJobRepository jobRepository = mock(TelegramBackfillJobRepository.class);
        TelegramTdlibService tdlibService = mock(TelegramTdlibService.class);
        TelegramMessageIngestionService ingestionService = mock(TelegramMessageIngestionService.class);
        TelegramBackfillJobService service = new TelegramBackfillJobService(jobRepository, tdlibService, ingestionService);
        ReflectionTestUtils.setField(service, "backfillEnabled", true);
        ReflectionTestUtils.setField(service, "maxJobsPerTick", 1);

        TelegramBackfillJobEntity job = job(100L, 7L, 11L, -1001L, null, 12345L, 50, null);
        when(jobRepository.findRunnableJobs(any(), any(), any(Pageable.class))).thenReturn(List.of(job));
        when(jobRepository.findById(100L)).thenReturn(Optional.of(job));
        when(tdlibService.getMessages(7L, -1001L, 12345L, 50)).thenThrow(new RuntimeException("FLOOD_WAIT_42"));
        when(jobRepository.save(any(TelegramBackfillJobEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.processRunnableJobs();

        assertThat(job.getStatus()).isEqualTo(TelegramBackfillJobService.STATUS_PAUSED_FLOOD_WAIT);
        assertThat(job.getFromMessageId()).isEqualTo(12345L);
        assertThat(job.getFloodWaitSeconds()).isEqualTo(42);
        assertThat(job.getPausedUntil()).isAfter(Instant.now());
    }

    private static TelegramBackfillJobEntity job(
            Long id,
            Long accountId,
            Long ownerUserId,
            Long chatId,
            Long topicId,
            Long fromMessageId,
            Integer batchSize,
            Long maxMessages
    ) {
        TelegramBackfillJobEntity job = new TelegramBackfillJobEntity();
        job.setId(id);
        job.setTelegramAccountId(accountId);
        job.setOwnerUserId(ownerUserId);
        job.setTelegramChatId(chatId);
        job.setTopicId(topicId);
        job.setFromMessageId(fromMessageId);
        job.setBatchSize(batchSize);
        job.setMaxMessages(maxMessages);
        job.setStatus(TelegramBackfillJobService.STATUS_PENDING);
        job.setMessagesFetched(0L);
        return job;
    }

    private static TelegramMessageDto message(long messageId, long chatId, long topicId) {
        return new TelegramMessageDto(
                messageId,
                chatId,
                topicId,
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
}
