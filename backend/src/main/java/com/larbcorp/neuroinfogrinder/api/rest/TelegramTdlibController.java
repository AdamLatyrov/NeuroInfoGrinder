package com.larbcorp.neuroinfogrinder.api.rest;

import com.larbcorp.neuroinfogrinder.domain.messages.TelegramRefreshCoordinator;
import com.larbcorp.neuroinfogrinder.domain.messages.TelegramTopicNames;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TelegramAccountEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TelegramAccountRepository;
import com.larbcorp.neuroinfogrinder.telegram.TelegramTdlibService;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramAuthRequest;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramAuthStateResponse;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramChatDto;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramMessageDto;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramMessagesBatchRequest;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramMessagesBatchResponse;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramTopicDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/v1/telegram")
public class TelegramTdlibController {

    private final TelegramTdlibService telegramTdlibService;
    private final TelegramRefreshCoordinator telegramRefreshCoordinator;
    private final GroupRepository groupRepository;
    private final MessageRepository messageRepository;
    private final TelegramAccountRepository telegramAccountRepository;

    public TelegramTdlibController(TelegramTdlibService telegramTdlibService,
                                   TelegramRefreshCoordinator telegramRefreshCoordinator,
                                   GroupRepository groupRepository,
                                   MessageRepository messageRepository,
                                   TelegramAccountRepository telegramAccountRepository) {
        this.telegramTdlibService = telegramTdlibService;
        this.telegramRefreshCoordinator = telegramRefreshCoordinator;
        this.groupRepository = groupRepository;
        this.messageRepository = messageRepository;
        this.telegramAccountRepository = telegramAccountRepository;
    }

    @GetMapping("/auth/state")
    public TelegramAuthStateResponse getAuthorizationState(@AuthenticationPrincipal Long ownerUserId,
                                                           @RequestParam(required = false) Long accountId) {
        TelegramAccountEntity account = resolveOwnedAccount(ownerUserId, accountId);
        return telegramTdlibService.getAuthorizationState(account.getId());
    }

    @PostMapping("/auth/phone")
    public TelegramAuthStateResponse submitPhone(@AuthenticationPrincipal Long ownerUserId,
                                                 @RequestParam(required = false) Long accountId,
                                                 @Valid @RequestBody TelegramAuthRequest request) {
        TelegramAccountEntity account = resolveOwnedAccount(ownerUserId, accountId);
        return telegramTdlibService.submitPhoneNumber(account.getId(), request.value());
    }

    @PostMapping("/auth/code")
    public TelegramAuthStateResponse submitCode(@AuthenticationPrincipal Long ownerUserId,
                                                @RequestParam(required = false) Long accountId,
                                                @Valid @RequestBody TelegramAuthRequest request) {
        TelegramAccountEntity account = resolveOwnedAccount(ownerUserId, accountId);
        return telegramTdlibService.submitCode(account.getId(), request.value());
    }

    @PostMapping("/auth/password")
    public TelegramAuthStateResponse submitPassword(@AuthenticationPrincipal Long ownerUserId,
                                                    @RequestParam(required = false) Long accountId,
                                                    @Valid @RequestBody TelegramAuthRequest request) {
        TelegramAccountEntity account = resolveOwnedAccount(ownerUserId, accountId);
        return telegramTdlibService.submitPassword(account.getId(), request.value());
    }

    @GetMapping("/chats")
    public List<TelegramChatDto> getChats(@AuthenticationPrincipal Long ownerUserId,
                                          @RequestParam(required = false) Long accountId,
                                          @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        TelegramAccountEntity account = resolveOwnedAccount(ownerUserId, accountId);
        return telegramTdlibService.getChats(account.getId(), limit);
    }

    @GetMapping("/chats/{chatId}/topics")
    public List<TelegramTopicDto> getTopics(
            @AuthenticationPrincipal Long ownerUserId,
            @PathVariable long chatId,
            @RequestParam(required = false) Long accountId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int limit
    ) {
        TelegramAccountEntity account = resolveOwnedAccount(ownerUserId, accountId);
        List<TelegramTopicDto> storedTopics = loadTopicsFromStoredMessages(ownerUserId, chatId, limit);
        List<TelegramTopicDto> cachedTopics = telegramTdlibService.getCachedTopics(account.getId(), chatId, limit);
        List<TelegramTopicDto> mergedTopics = mergeTopics(storedTopics, cachedTopics, limit);

        if (mergedTopics.size() < limit) {
            try {
                mergedTopics = mergeTopics(mergedTopics, telegramTdlibService.getTopics(account.getId(), chatId, limit), limit);
            } catch (RuntimeException exception) {
                telegramRefreshCoordinator.requestTopicWarmup(account.getId(), chatId, limit, "topics_endpoint");
            }
        }

        if (!mergedTopics.isEmpty()) {
            return mergedTopics;
        }

        try {
            return mergeTopics(mergedTopics, telegramTdlibService.getTopics(account.getId(), chatId, limit), limit);
        } catch (RuntimeException exception) {
            return mergedTopics;
        }
    }

    @GetMapping("/chats/{chatId}/messages")
    public List<TelegramMessageDto> getMessages(
            @AuthenticationPrincipal Long ownerUserId,
            @PathVariable long chatId,
            @RequestParam(required = false) Long accountId,
            @RequestParam(defaultValue = "0") long fromMessageId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        TelegramAccountEntity account = resolveOwnedAccount(ownerUserId, accountId);
        return telegramTdlibService.getMessages(account.getId(), chatId, fromMessageId, limit);
    }

    @PostMapping("/messages/batch")
    public TelegramMessagesBatchResponse getMessagesForChats(@AuthenticationPrincipal Long ownerUserId,
                                                             @RequestParam(required = false) Long accountId,
                                                             @Valid @RequestBody TelegramMessagesBatchRequest request) {
        TelegramAccountEntity account = resolveOwnedAccount(ownerUserId, accountId);
        return telegramTdlibService.getMessagesForChats(account.getId(), request.chatIds(), request.limitPerChat());
    }

    private List<TelegramTopicDto> loadTopicsFromStoredMessages(Long ownerUserId, long chatId, int limit) {
        return groupRepository.findByTelegramChatIdAndOwnerUserId(chatId, ownerUserId)
                .map(group -> {
                    List<MessageEntity> messages =
                            messageRepository.findTop500ByGroupIdAndTopicIdIsNotNullAndTopicNameIsNotNullOrderByMessageDateDesc(group.getId());

                    Map<Long, TelegramTopicDto> uniqueTopics = new LinkedHashMap<>();
                    for (MessageEntity message : messages) {
                        Long topicId = message.getTopicId();
                        String topicName = message.getTopicName();
                        if (topicId == null) {
                            continue;
                        }
                        String visibleTopicName = topicName;
                        if ((visibleTopicName == null || visibleTopicName.isBlank()) && topicId == 1L) {
                            visibleTopicName = TelegramTopicNames.GENERAL_TOPIC_NAME_RU;
                        }
                        if (visibleTopicName == null || visibleTopicName.isBlank()) {
                            continue;
                        }

                        uniqueTopics.putIfAbsent(topicId, new TelegramTopicDto(
                                chatId,
                                topicId,
                                topicId,
                                visibleTopicName,
                                isGeneralTopic(topicId, visibleTopicName)
                        ));
                    }

                    List<TelegramTopicDto> topics = new ArrayList<>(uniqueTopics.values());
                    topics.sort(Comparator
                            .comparing(TelegramTopicDto::general).reversed()
                            .thenComparing(TelegramTopicDto::name, String.CASE_INSENSITIVE_ORDER));

                    if (topics.size() > limit) {
                        return topics.subList(0, limit);
                    }
                    return topics;
                })
                .orElseGet(List::of);
    }

    private TelegramAccountEntity resolveOwnedAccount(Long ownerUserId, Long accountId) {
        if (ownerUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user is required");
        }
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

    private List<TelegramTopicDto> mergeTopics(List<TelegramTopicDto> left, List<TelegramTopicDto> right, int limit) {
        Map<Long, TelegramTopicDto> merged = new LinkedHashMap<>();
        for (TelegramTopicDto topic : left) {
            merged.putIfAbsent(topic.messageThreadId(), topic);
        }
        for (TelegramTopicDto topic : right) {
            merged.putIfAbsent(topic.messageThreadId(), topic);
        }

        List<TelegramTopicDto> topics = new ArrayList<>(merged.values());
        topics.sort(Comparator
                .comparing(TelegramTopicDto::general).reversed()
                .thenComparing(TelegramTopicDto::name, String.CASE_INSENSITIVE_ORDER));
        if (topics.size() > limit) {
            return topics.subList(0, limit);
        }
        return topics;
    }

    private boolean isGeneralTopic(Long topicId, String topicName) {
        String normalized = topicName.trim().toLowerCase();
        return topicId == 1L || "general".equals(normalized) || "основной".equals(normalized);
    }
}
