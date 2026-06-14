package com.larbcorp.neuroinfogrinder.domain.findings;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageContextBuilder {

    private static final Pattern QUESTION_PATTERN = Pattern.compile(
        "(?iu)(\\?|\\bwhere\\b|\\bhow\\b|\\bwhy\\b|\\bcan\\b|\\bwho knows\\b|"
            + "\\bгде\\b|\\bкак\\b|\\bподскажите\\b|\\bкто\\s+знает\\b|\\bможно\\s+ли\\b|\\bпочему\\b|"
            + "\\bчто\\s+делать\\b|\\bу\\s+кого\\s+есть\\b|\\bесть\\s+вариант\\b)"
    );
    private static final Pattern PRICE_OR_URL_PATTERN = Pattern.compile(
        "(?iu)(https?://\\S+|\\b\\d+\\s*(?:\\$|usd|eur|руб|₽|юан|yuan)\\b)"
    );
    private static final Pattern DEMAND_PATTERN = Pattern.compile(
        "(?iu)\\b(дорого|не\\s+работает|лимит\\w*|быстро\\s+заканчива\\w*|забанил\\w*|"
            + "не\\s+могу\\s+оплатить|ищу|надо|хочу|нужен|нужно|безлимит\\w*)\\b"
    );
    private static final Pattern PROVIDER_PATTERN = Pattern.compile(
        "(?iu)\\b(claude|chatgpt|gpt|openrouter|anthropic|codex|trae|antigravity|cursor|windsurf|gemini|api|"
            + "клауд|клод|чат\\s*гпт|гпт|опенроутер|антропик|кодекс|трае|курсор|гемини|апи)\\b"
    );

    private final MessageRepository messageRepository;

    @Value("${neuroinfogrinder.pipeline.classification-context-enabled:true}")
    private boolean contextEnabled;

    @Value("${neuroinfogrinder.pipeline.classification-context-window-minutes:10}")
    private int contextWindowMinutes;

    @Value("${neuroinfogrinder.pipeline.classification-context-max-messages:80}")
    private int contextMaxMessages;

    @Value("${neuroinfogrinder.pipeline.classification-context-max-chars:20000}")
    private int contextMaxChars;

    @Value("${neuroinfogrinder.pipeline.classification-context-reply-depth:1}")
    private int replyDepth;

    @Value("${neuroinfogrinder.pipeline.classification-anchor-min-score:3}")
    private int anchorMinScore;

    public MessageContextBundle buildContext(Long anchorMessageId) {
        MessageEntity anchor = messageRepository.findById(anchorMessageId)
            .orElseThrow(() -> new IllegalArgumentException("Message not found: " + anchorMessageId));
        return buildContext(anchor);
    }

    public MessageContextBundle buildContext(MessageEntity anchor) {
        int anchorScore = scoreAnchor(anchor);
        List<String> anchorSignals = collectAnchorSignals(anchor);

        if (!contextEnabled) {
            List<MessageEntity> onlyAnchor = List.of(anchor);
            return new MessageContextBundle(
                anchor,
                onlyAnchor,
                buildContextHash(anchor.getGroupId(), anchor.getTopicId(), List.of(anchor.getId())),
                anchorScore,
                anchorSignals,
                safeText(anchor).length()
            );
        }

        Map<Long, MessageEntity> collected = new LinkedHashMap<>();
        collected.put(anchor.getId(), anchor);

        List<MessageEntity> seeds = new ArrayList<>();
        seeds.add(anchor);
        collectParentBranch(anchor, seeds, collected, replyDepth);
        collectDirectReplies(anchor, seeds, collected, replyDepth);

        for (MessageEntity seed : List.copyOf(seeds)) {
            collectWindow(seed, anchor, collected);
        }

        List<MessageEntity> sorted = collected.values().stream()
            .sorted(Comparator.comparing(MessageEntity::getMessageDate).thenComparing(MessageEntity::getId))
            .toList();

        List<MessageEntity> limited = applyLimits(sorted);
        String contextHash = buildContextHash(
            anchor.getGroupId(),
            anchor.getTopicId(),
            limited.stream().map(MessageEntity::getId).sorted().toList()
        );
        int totalChars = limited.stream().mapToInt(msg -> safeText(msg).length()).sum();

        log.info(
            "Classification context built: anchorMessageId={} messageCount={} totalChars={} contextHash={}",
            anchor.getId(),
            limited.size(),
            totalChars,
            contextHash
        );

        return new MessageContextBundle(anchor, limited, contextHash, anchorScore, anchorSignals, totalChars);
    }

    public int scoreAnchor(MessageEntity message) {
        int score = 0;
        String text = safeText(message);
        if (QUESTION_PATTERN.matcher(text).find()) {
            score += 2;
        }
        if (message.getReplyToMessageId() != null || hasDirectReplies(message)) {
            score += 2;
        }
        if (PRICE_OR_URL_PATTERN.matcher(text).find()) {
            score += 2;
        }
        if (PROVIDER_PATTERN.matcher(text).find()) {
            score += 2;
        }
        if (isRelevantTopic(message)) {
            score += 1;
        }
        if (DEMAND_PATTERN.matcher(text).find()) {
            score += 1;
        }
        if (hasLocalActivity(message)) {
            score += 1;
        }
        return score;
    }

    public boolean isAnchorCandidate(MessageEntity message) {
        return scoreAnchor(message) >= anchorMinScore;
    }

    private List<String> collectAnchorSignals(MessageEntity message) {
        List<String> signals = new ArrayList<>();
        String text = safeText(message);
        if (QUESTION_PATTERN.matcher(text).find()) {
            signals.add("question");
        }
        if (message.getReplyToMessageId() != null) {
            signals.add("reply-to");
        }
        if (hasDirectReplies(message)) {
            signals.add("direct-replies");
        }
        if (PRICE_OR_URL_PATTERN.matcher(text).find()) {
            signals.add("url-or-price");
        }
        if (PROVIDER_PATTERN.matcher(text).find()) {
            signals.add("provider");
        }
        if (isRelevantTopic(message)) {
            signals.add("relevant-topic");
        }
        if (DEMAND_PATTERN.matcher(text).find()) {
            signals.add("pain-or-demand");
        }
        if (hasLocalActivity(message)) {
            signals.add("local-activity");
        }
        return List.copyOf(signals);
    }

    private void collectParentBranch(MessageEntity anchor, List<MessageEntity> seeds,
                                     Map<Long, MessageEntity> collected, int depth) {
        MessageEntity current = anchor;
        for (int i = 0; i < depth; i++) {
            if (current.getReplyToMessageId() == null) {
                return;
            }
            MessageEntity parent = messageRepository
                .findByGroupIdAndTelegramMessageId(current.getGroupId(), current.getReplyToMessageId())
                .orElse(null);
            if (parent == null || collected.putIfAbsent(parent.getId(), parent) != null) {
                return;
            }
            seeds.add(parent);
            current = parent;
        }
    }

    private void collectDirectReplies(MessageEntity anchor, List<MessageEntity> seeds,
                                      Map<Long, MessageEntity> collected, int depth) {
        Set<Long> currentTelegramIds = Set.of(anchor.getTelegramMessageId());
        for (int i = 0; i < depth; i++) {
            List<MessageEntity> replies = messageRepository.findByGroupIdAndReplyToMessageIdIn(
                anchor.getGroupId(),
                List.copyOf(currentTelegramIds)
            );
            if (replies.isEmpty()) {
                return;
            }
            Set<Long> nextTelegramIds = new LinkedHashSet<>();
            for (MessageEntity reply : replies) {
                if (collected.putIfAbsent(reply.getId(), reply) == null) {
                    seeds.add(reply);
                    nextTelegramIds.add(reply.getTelegramMessageId());
                }
            }
            currentTelegramIds = nextTelegramIds;
            if (currentTelegramIds.isEmpty()) {
                return;
            }
        }
    }

    private void collectWindow(MessageEntity seed, MessageEntity anchor, Map<Long, MessageEntity> collected) {
        Instant from = seed.getMessageDate().minusSeconds(contextWindowMinutes * 60L);
        Instant to = seed.getMessageDate().plusSeconds(contextWindowMinutes * 60L);
        List<MessageEntity> nearby = messageRepository.findByGroupIdAndMessageDateBetweenOrderByMessageDateAsc(
            anchor.getGroupId(),
            from,
            to
        );

        for (MessageEntity candidate : nearby) {
            if (isRelevantToAnchor(anchor, candidate, collected)) {
                collected.putIfAbsent(candidate.getId(), candidate);
            }
        }
    }

    private boolean isRelevantToAnchor(MessageEntity anchor, MessageEntity candidate, Map<Long, MessageEntity> collected) {
        if (!anchor.getGroupId().equals(candidate.getGroupId())) {
            return false;
        }
        if (anchor.getTopicId() != null && candidate.getTopicId() != null && anchor.getTopicId().equals(candidate.getTopicId())) {
            return true;
        }
        if (candidate.getReplyToMessageId() != null && collected.values().stream()
            .anyMatch(existing -> candidate.getReplyToMessageId().equals(existing.getTelegramMessageId()))) {
            return true;
        }
        if (collected.values().stream()
            .anyMatch(existing -> existing.getReplyToMessageId() != null
                && existing.getReplyToMessageId().equals(candidate.getTelegramMessageId()))) {
            return true;
        }
        if (anchor.getSenderTelegramUserId() != null
            && anchor.getSenderTelegramUserId().equals(candidate.getSenderTelegramUserId())) {
            return true;
        }
        return anchor.getTopicId() == null && candidate.getTopicId() == null;
    }

    private List<MessageEntity> applyLimits(List<MessageEntity> sorted) {
        List<MessageEntity> limited = new ArrayList<>();
        int chars = 0;
        for (MessageEntity message : sorted) {
            if (limited.size() >= contextMaxMessages) {
                break;
            }
            int candidateChars = safeText(message).length();
            if (!limited.isEmpty() && chars + candidateChars > contextMaxChars) {
                break;
            }
            limited.add(message);
            chars += candidateChars;
        }
        return limited;
    }

    private boolean hasDirectReplies(MessageEntity message) {
        return !messageRepository.findByGroupIdAndReplyToMessageId(message.getGroupId(), message.getTelegramMessageId()).isEmpty();
    }

    private boolean hasLocalActivity(MessageEntity message) {
        Instant from = message.getMessageDate().minusSeconds(contextWindowMinutes * 60L);
        Instant to = message.getMessageDate().plusSeconds(contextWindowMinutes * 60L);
        List<MessageEntity> nearby = messageRepository.findByGroupIdAndMessageDateBetweenOrderByMessageDateAsc(
            message.getGroupId(),
            from,
            to
        );
        long distinctSenders = nearby.stream()
            .map(MessageEntity::getSenderTelegramUserId)
            .filter(senderId -> senderId != null)
            .distinct()
            .count();
        return nearby.size() >= 3 || distinctSenders >= 2;
    }

    private boolean isRelevantTopic(MessageEntity message) {
        String topic = ((message.getTopicName() != null ? message.getTopicName() : "") + " "
            + (message.getText() != null ? message.getText() : "")).toLowerCase(Locale.ROOT);
        return topic.contains("claude")
            || topic.contains("chatgpt")
            || topic.contains("codex")
            || topic.contains("trae")
            || topic.contains("antigravity")
            || topic.contains("openrouter")
            || topic.contains("api")
            || topic.contains("tools")
            || topic.contains("payment")
            || topic.contains("клауд")
            || topic.contains("гпт")
            || topic.contains("оплата")
            || topic.contains("халява");
    }

    private String buildContextHash(Long groupId, Long topicId, List<Long> messageIds) {
        String raw = groupId + "|" + topicId + "|" + messageIds;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte item : hash) {
                hex.append(String.format("%02x", item));
            }
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private String safeText(MessageEntity message) {
        return message.getText() != null ? message.getText() : "";
    }
}
