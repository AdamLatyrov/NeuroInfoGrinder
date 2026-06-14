package com.larbcorp.neuroinfogrinder.api.rest;

import com.larbcorp.neuroinfogrinder.domain.messages.TelegramRefreshCoordinator;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    public TelegramTdlibController(TelegramTdlibService telegramTdlibService,
                                   TelegramRefreshCoordinator telegramRefreshCoordinator,
                                   GroupRepository groupRepository,
                                   MessageRepository messageRepository) {
        this.telegramTdlibService = telegramTdlibService;
        this.telegramRefreshCoordinator = telegramRefreshCoordinator;
        this.groupRepository = groupRepository;
        this.messageRepository = messageRepository;
    }

    @GetMapping("/auth/state")
    public TelegramAuthStateResponse getAuthorizationState() {
        return telegramTdlibService.getAuthorizationState();
    }

    @PostMapping("/auth/phone")
    public TelegramAuthStateResponse submitPhone(@Valid @RequestBody TelegramAuthRequest request) {
        return telegramTdlibService.submitPhoneNumber(request.value());
    }

    @PostMapping("/auth/code")
    public TelegramAuthStateResponse submitCode(@Valid @RequestBody TelegramAuthRequest request) {
        return telegramTdlibService.submitCode(request.value());
    }

    @PostMapping("/auth/password")
    public TelegramAuthStateResponse submitPassword(@Valid @RequestBody TelegramAuthRequest request) {
        return telegramTdlibService.submitPassword(request.value());
    }

    @GetMapping("/chats")
    public List<TelegramChatDto> getChats(@RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return telegramTdlibService.getChats(limit);
    }

    @GetMapping("/chats/{chatId}/topics")
    public List<TelegramTopicDto> getTopics(
            @PathVariable long chatId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int limit
    ) {
        List<TelegramTopicDto> storedTopics = loadTopicsFromStoredMessages(chatId, limit);
        List<TelegramTopicDto> cachedTopics = telegramTdlibService.getCachedTopics(chatId, limit);
        List<TelegramTopicDto> mergedTopics = mergeTopics(storedTopics, cachedTopics, limit);

        if (mergedTopics.size() < limit) {
            try {
                mergedTopics = mergeTopics(mergedTopics, telegramTdlibService.getTopics(chatId, limit), limit);
            } catch (RuntimeException exception) {
                telegramRefreshCoordinator.requestTopicWarmup(chatId, limit, "topics_endpoint");
            }
        }

        if (!mergedTopics.isEmpty()) {
            return mergedTopics;
        }

        try {
            return mergeTopics(mergedTopics, telegramTdlibService.getTopics(chatId, limit), limit);
        } catch (RuntimeException exception) {
            return mergedTopics;
        }
    }

    @GetMapping("/chats/{chatId}/messages")
    public List<TelegramMessageDto> getMessages(
            @PathVariable long chatId,
            @RequestParam(defaultValue = "0") long fromMessageId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        return telegramTdlibService.getMessages(chatId, fromMessageId, limit);
    }

    @PostMapping("/messages/batch")
    public TelegramMessagesBatchResponse getMessagesForChats(@Valid @RequestBody TelegramMessagesBatchRequest request) {
        return telegramTdlibService.getMessagesForChats(request.chatIds(), request.limitPerChat());
    }

    private List<TelegramTopicDto> loadTopicsFromStoredMessages(long chatId, int limit) {
        return groupRepository.findByTelegramChatId(chatId)
                .map(group -> {
                    List<MessageEntity> messages =
                            messageRepository.findTop500ByGroupIdAndTopicIdIsNotNullAndTopicNameIsNotNullOrderByMessageDateDesc(group.getId());

                    Map<Long, TelegramTopicDto> uniqueTopics = new LinkedHashMap<>();
                    for (MessageEntity message : messages) {
                        Long topicId = message.getTopicId();
                        String topicName = message.getTopicName();
                        if (topicId == null || topicName == null || topicName.isBlank()) {
                            continue;
                        }

                        uniqueTopics.putIfAbsent(topicId, new TelegramTopicDto(
                                chatId,
                                topicId,
                                topicId,
                                topicName,
                                isGeneralTopic(topicId, topicName)
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
