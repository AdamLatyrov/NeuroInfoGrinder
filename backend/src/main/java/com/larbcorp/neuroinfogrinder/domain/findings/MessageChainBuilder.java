package com.larbcorp.neuroinfogrinder.domain.findings;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.ChainConfigEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.ChainConfigRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageChainBuilder {

    private final MessageRepository messageRepository;
    private final ChainConfigRepository chainConfigRepository;

    public List<MessageEntity> buildChain(Long rootMessageId) {
        MessageEntity root = messageRepository.findById(rootMessageId)
            .orElseThrow(() -> new IllegalArgumentException("Message not found: " + rootMessageId));

        ChainConfigEntity config = chainConfigRepository.findAll().stream()
            .findFirst()
            .orElseGet(this::defaultConfig);

        List<MessageEntity> chain = buildChain(
            root,
            Boolean.TRUE.equals(config.getIncludeReplies()),
            config.getTimeWindowMinutes(),
            config.getMaxMessagesPerChain()
        );

        log.debug("Built message chain of size {} for root message {}", chain.size(), rootMessageId);
        return chain;
    }

    /**
     * Finds messages within a time window of a group, given explicit config values.
     * Used when chain config comes from settings rather than chain_config table.
     */
    public List<MessageEntity> buildChain(Long rootMessageId, boolean includeReplies,
                                            int timeWindowMinutes, int maxMessages) {
        MessageEntity root = messageRepository.findById(rootMessageId)
            .orElseThrow(() -> new IllegalArgumentException("Message not found: " + rootMessageId));

        return buildChain(root, includeReplies, timeWindowMinutes, maxMessages);
    }

    private List<MessageEntity> buildChain(MessageEntity root, boolean includeReplies,
                                           int timeWindowMinutes, int maxMessages) {
        Map<Long, MessageEntity> collected = new LinkedHashMap<>();
        collected.put(root.getId(), root);

        collectAncestorBranch(root, collected);

        if (includeReplies) {
            collectReplyBranch(root, collected);
        }

        if (timeWindowMinutes > 0) {
            Instant windowStart = root.getMessageDate()
                .minusSeconds(timeWindowMinutes * 60L);
            Instant windowEnd = root.getMessageDate()
                .plusSeconds(timeWindowMinutes * 60L);

            List<MessageEntity> nearby = messageRepository
                .findByGroupIdAndMessageDateAfter(root.getGroupId(), windowStart);

            for (MessageEntity msg : nearby) {
                if (msg.getId().equals(root.getId()) || msg.getMessageDate().isAfter(windowEnd)) {
                    continue;
                }
                if (!isContextuallyRelated(root, msg, includeReplies)) {
                    continue;
                }
                collected.putIfAbsent(msg.getId(), msg);
            }
        }

        List<MessageEntity> chain = new ArrayList<>(collected.values());
        chain.sort(Comparator.comparing(MessageEntity::getMessageDate));

        if (chain.size() > maxMessages) {
            chain = chain.subList(0, maxMessages);
        }

        return chain;
    }

    private void collectAncestorBranch(MessageEntity root, Map<Long, MessageEntity> collected) {
        MessageEntity current = root;
        while (current.getReplyToMessageId() != null) {
            MessageEntity parent = messageRepository
                .findByGroupIdAndTelegramMessageId(current.getGroupId(), current.getReplyToMessageId())
                .orElse(null);
            if (parent == null || collected.putIfAbsent(parent.getId(), parent) != null) {
                break;
            }
            current = parent;
        }
    }

    private void collectReplyBranch(MessageEntity root, Map<Long, MessageEntity> collected) {
        List<Long> pendingTelegramIds = List.of(root.getTelegramMessageId());
        while (!pendingTelegramIds.isEmpty()) {
            List<MessageEntity> replies = messageRepository
                .findByGroupIdAndReplyToMessageIdIn(root.getGroupId(), pendingTelegramIds);
            if (replies.isEmpty()) {
                break;
            }

            List<Long> nextPending = new ArrayList<>();
            for (MessageEntity reply : replies) {
                if (collected.putIfAbsent(reply.getId(), reply) == null) {
                    nextPending.add(reply.getTelegramMessageId());
                }
            }
            pendingTelegramIds = nextPending;
        }
    }

    private boolean isContextuallyRelated(MessageEntity root, MessageEntity candidate, boolean includeReplies) {
        if (root.getTopicId() != null && candidate.getTopicId() != null
            && root.getTopicId().equals(candidate.getTopicId())) {
            return true;
        }
        if (root.getReplyToMessageId() != null && root.getReplyToMessageId().equals(candidate.getTelegramMessageId())) {
            return true;
        }
        if (includeReplies && root.getTelegramMessageId().equals(candidate.getReplyToMessageId())) {
            return true;
        }
        if (root.getSenderTelegramUserId() != null
            && root.getSenderTelegramUserId().equals(candidate.getSenderTelegramUserId())) {
            return true;
        }
        return root.getTopicId() == null && candidate.getReplyToMessageId() == null;
    }

    private ChainConfigEntity defaultConfig() {
        ChainConfigEntity config = new ChainConfigEntity();
        config.setIncludeReplies(true);
        config.setTimeWindowMinutes(5);
        config.setMinMessagesForProcessing(2);
        config.setMaxMessagesPerChain(20);
        return config;
    }
}
