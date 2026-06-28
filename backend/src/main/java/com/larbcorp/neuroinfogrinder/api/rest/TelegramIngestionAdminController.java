package com.larbcorp.neuroinfogrinder.api.rest;

import com.larbcorp.neuroinfogrinder.domain.messages.TelegramBackfillJobService;
import com.larbcorp.neuroinfogrinder.domain.messages.dto.TelegramBackfillJobRequest;
import com.larbcorp.neuroinfogrinder.domain.messages.dto.TelegramBackfillJobResponse;
import com.larbcorp.neuroinfogrinder.domain.messages.dto.TelegramMonitoredChatRequest;
import com.larbcorp.neuroinfogrinder.domain.messages.dto.TelegramMonitoredChatResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TelegramAccountEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TelegramBackfillJobEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TelegramMonitoredChatEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TelegramAccountRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TelegramMonitoredChatRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/telegram")
public class TelegramIngestionAdminController {

    private final TelegramMonitoredChatRepository monitoredChatRepository;
    private final TelegramBackfillJobService backfillJobService;
    private final TelegramAccountRepository telegramAccountRepository;

    public TelegramIngestionAdminController(
            TelegramMonitoredChatRepository monitoredChatRepository,
            TelegramBackfillJobService backfillJobService,
            TelegramAccountRepository telegramAccountRepository
    ) {
        this.monitoredChatRepository = monitoredChatRepository;
        this.backfillJobService = backfillJobService;
        this.telegramAccountRepository = telegramAccountRepository;
    }

    @GetMapping("/monitored-chats")
    public List<TelegramMonitoredChatResponse> listMonitoredChats(@AuthenticationPrincipal Long ownerUserId) {
        requireOwner(ownerUserId);
        return monitoredChatRepository.findByOwnerUserIdOrderByUpdatedAtDesc(ownerUserId).stream()
                .map(this::toMonitoredChatResponse)
                .toList();
    }

    @PostMapping("/monitored-chats")
    @ResponseStatus(HttpStatus.CREATED)
    public TelegramMonitoredChatResponse createMonitoredChat(
            @AuthenticationPrincipal Long ownerUserId,
            @Valid @RequestBody TelegramMonitoredChatRequest request
    ) {
        requireOwner(ownerUserId);
        TelegramAccountEntity account = resolveOwnedAccount(ownerUserId, request.accountId());
        TelegramMonitoredChatEntity entity = monitoredChatRepository
                .findOwnedChat(account.getId(), ownerUserId, requireChatId(request.chatId()), request.topicId())
                .orElseGet(TelegramMonitoredChatEntity::new);
        entity.setTelegramAccountId(account.getId());
        entity.setOwnerUserId(ownerUserId);
        applyMonitoredChatRequest(entity, request);
        return toMonitoredChatResponse(monitoredChatRepository.save(entity));
    }

    @PatchMapping("/monitored-chats/{id}")
    public TelegramMonitoredChatResponse updateMonitoredChat(
            @AuthenticationPrincipal Long ownerUserId,
            @PathVariable Long id,
            @Valid @RequestBody TelegramMonitoredChatRequest request
    ) {
        requireOwner(ownerUserId);
        TelegramMonitoredChatEntity entity = monitoredChatRepository.findByIdAndOwnerUserId(id, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitored chat not found"));
        applyMonitoredChatPatch(entity, request);
        return toMonitoredChatResponse(monitoredChatRepository.save(entity));
    }

    @PostMapping("/backfill-jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public TelegramBackfillJobResponse createBackfillJob(
            @AuthenticationPrincipal Long ownerUserId,
            @Valid @RequestBody TelegramBackfillJobRequest request
    ) {
        requireOwner(ownerUserId);
        TelegramAccountEntity account = resolveOwnedAccount(ownerUserId, request.accountId());
        Long chatId = requireChatId(request.chatId());
        monitoredChatRepository.findOwnedChat(account.getId(), ownerUserId, chatId, request.topicId())
                .filter(chat -> Boolean.TRUE.equals(chat.getEnabled()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat is not monitored"));

        TelegramBackfillJobEntity job = new TelegramBackfillJobEntity();
        job.setTelegramAccountId(account.getId());
        job.setOwnerUserId(ownerUserId);
        job.setTelegramChatId(chatId);
        job.setTopicId(request.topicId());
        job.setFromMessageId(request.fromMessageId());
        job.setFromDate(request.fromDate());
        job.setToDate(request.toDate());
        job.setMaxMessages(request.maxMessages());
        job.setBatchSize(request.batchSize());
        return toBackfillJobResponse(backfillJobService.createJob(job));
    }

    @GetMapping("/backfill-jobs")
    public List<TelegramBackfillJobResponse> listBackfillJobs(@AuthenticationPrincipal Long ownerUserId) {
        requireOwner(ownerUserId);
        return backfillJobService.listOwned(ownerUserId).stream()
                .map(this::toBackfillJobResponse)
                .toList();
    }

    @PostMapping("/backfill-jobs/{id}/cancel")
    public TelegramBackfillJobResponse cancelBackfillJob(
            @AuthenticationPrincipal Long ownerUserId,
            @PathVariable Long id
    ) {
        requireOwner(ownerUserId);
        return backfillJobService.cancel(ownerUserId, id)
                .map(this::toBackfillJobResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Backfill job not found"));
    }

    @PostMapping("/backfill-jobs/{id}/resume")
    public TelegramBackfillJobResponse resumeBackfillJob(
            @AuthenticationPrincipal Long ownerUserId,
            @PathVariable Long id
    ) {
        requireOwner(ownerUserId);
        return backfillJobService.resume(ownerUserId, id)
                .map(this::toBackfillJobResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Backfill job not found"));
    }

    private void applyMonitoredChatRequest(TelegramMonitoredChatEntity entity, TelegramMonitoredChatRequest request) {
        entity.setTelegramChatId(requireChatId(request.chatId()));
        entity.setChatTitle(nonBlank(request.chatTitle(), "Telegram chat " + request.chatId()));
        entity.setChatType(nonBlank(request.chatType(), "GROUP"));
        entity.setTopicId(request.topicId());
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setLiveIngestionEnabled(request.liveIngestionEnabled() == null || request.liveIngestionEnabled());
        entity.setBackfillEnabled(Boolean.TRUE.equals(request.backfillEnabled()));
        if (entity.getBackfillStatus() == null) {
            entity.setBackfillStatus("IDLE");
        }
    }

    private void applyMonitoredChatPatch(TelegramMonitoredChatEntity entity, TelegramMonitoredChatRequest request) {
        if (request.chatTitle() != null) {
            entity.setChatTitle(nonBlank(request.chatTitle(), entity.getChatTitle()));
        }
        if (request.chatType() != null) {
            entity.setChatType(nonBlank(request.chatType(), entity.getChatType()));
        }
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
        if (request.liveIngestionEnabled() != null) {
            entity.setLiveIngestionEnabled(request.liveIngestionEnabled());
        }
        if (request.backfillEnabled() != null) {
            entity.setBackfillEnabled(request.backfillEnabled());
        }
    }

    private TelegramAccountEntity resolveOwnedAccount(Long ownerUserId, Long accountId) {
        if (accountId != null) {
            return telegramAccountRepository.findByIdAndOwnerUserId(accountId, ownerUserId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Telegram account not found"));
        }

        List<TelegramAccountEntity> accounts = telegramAccountRepository.findByOwnerUserIdOrderByCreatedAtAsc(ownerUserId);
        if (accounts.size() == 1) {
            return accounts.get(0);
        }
        if (accounts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No Telegram account connected for user");
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountId is required when user has multiple Telegram accounts");
    }

    private void requireOwner(Long ownerUserId) {
        if (ownerUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user is required");
        }
    }

    private Long requireChatId(Long chatId) {
        if (chatId == null || chatId == 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chatId is required");
        }
        return chatId;
    }

    private String nonBlank(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private TelegramMonitoredChatResponse toMonitoredChatResponse(TelegramMonitoredChatEntity entity) {
        return new TelegramMonitoredChatResponse(
                entity.getId(),
                entity.getTelegramAccountId(),
                entity.getOwnerUserId(),
                entity.getTelegramChatId(),
                entity.getChatTitle(),
                entity.getChatType(),
                entity.getTopicId(),
                entity.getEnabled(),
                entity.getLiveIngestionEnabled(),
                entity.getBackfillEnabled(),
                entity.getLastLiveMessageId(),
                entity.getLastLiveMessageAt(),
                entity.getLastBackfillMessageId(),
                entity.getBackfillStatus(),
                entity.getLastError(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private TelegramBackfillJobResponse toBackfillJobResponse(TelegramBackfillJobEntity entity) {
        return new TelegramBackfillJobResponse(
                entity.getId(),
                entity.getTelegramAccountId(),
                entity.getOwnerUserId(),
                entity.getTelegramChatId(),
                entity.getTopicId(),
                entity.getFromMessageId(),
                entity.getFromDate(),
                entity.getToDate(),
                entity.getMaxMessages(),
                entity.getBatchSize(),
                entity.getStatus(),
                entity.getPausedUntil(),
                entity.getFloodWaitSeconds(),
                entity.getMessagesFetched(),
                entity.getLastError(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
