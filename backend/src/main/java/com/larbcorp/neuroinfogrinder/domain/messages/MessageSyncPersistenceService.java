package com.larbcorp.neuroinfogrinder.domain.messages;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramMessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageSyncPersistenceService {

    private final MessageRepository messageRepository;
    private final GroupRepository groupRepository;

    @Transactional
    public int persistSyncedMessages(Long groupId, List<TelegramMessageDto> messages) {
        return persistSyncedMessagesDetailed(groupId, messages).changed();
    }

    @Transactional
    public MessageSyncPersistenceResult persistSyncedMessagesDetailed(Long groupId, List<TelegramMessageDto> messages) {
        long startedAt = System.currentTimeMillis();
        GroupEntity group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
        if (!Boolean.TRUE.equals(group.getEnabled())) {
            log.debug("Skipping Telegram sync persist for disabled group {}", groupId);
            return new MessageSyncPersistenceResult(0, 0, 0, 0, messages.size());
        }

        Instant monthAgo = Instant.now().minusSeconds(30L * 24L * 3600L);
        Map<Long, MessageEntity> existingByTelegramMessageId = loadExistingMessages(groupId, messages);
        List<MessageEntity> newEntities = new ArrayList<>();
        List<MessageEntity> repairedEntities = new ArrayList<>();
        int created = 0;
        int skipped = 0;
        int repaired = 0;
        int tooOld = 0;

        for (TelegramMessageDto msg : messages) {
            Instant msgDate = Instant.ofEpochSecond(msg.date());
            if (msgDate.isBefore(monthAgo)) {
                tooOld++;
                continue;
            }
            group.setLastReadMessageId(Math.max(group.getLastReadMessageId() == null ? 0L : group.getLastReadMessageId(), msg.id()));
            if (group.getLastReadAt() == null || msgDate.isAfter(group.getLastReadAt())) {
                group.setLastReadAt(msgDate);
            }
            MessageEntity existing = existingByTelegramMessageId.get(msg.id());
            if (existing != null) {
                if (repairMessageMetadata(group, existing, msg, msgDate)) {
                    repairedEntities.add(existing);
                    repaired++;
                } else {
                    skipped++;
                }
                continue;
            }
            MessageEntity entity = new MessageEntity();
            entity.setTelegramMessageId(msg.id());
            entity.setTelegramAccountId(group.getAccountId());
            entity.setTelegramChatId(msg.chatId());
            entity.setGroupId(groupId);
            entity.setOwnerUserId(group.getOwnerUserId());
            entity.setText(msg.text());
            entity.setSenderName(msg.senderName());
            entity.setSenderUsername(msg.senderUsername());
            entity.setSenderTelegramUserId(msg.senderTelegramUserId());
            entity.setIsBot(msg.isBot());
            entity.setTextEntitiesJson(msg.textEntitiesJson());
            entity.setReplyToMessageId(msg.replyToMessageId() > 0 ? msg.replyToMessageId() : null);
            entity.setTopicId(msg.messageThreadId() > 0 ? msg.messageThreadId() : null);
            entity.setTopicName(TelegramTopicNames.storedTopicName(group, msg.messageThreadId(), msg.topicName()));
            entity.setReplyCount(0);
            applyInitialProcessingStatus(entity, group);
            entity.setMessageDate(msgDate);
            newEntities.add(entity);
            created++;
        }

        if (!repairedEntities.isEmpty()) {
            messageRepository.saveAll(repairedEntities);
        }
        if (!newEntities.isEmpty()) {
            messageRepository.saveAll(newEntities);
        }
        if (created > 0 || repaired > 0 || !messages.isEmpty()) {
            groupRepository.save(group);
        }

        log.info(
                "Message sync persist for group {}: {} new, {} repaired, {} skipped (existing), {} too old, from {} TDLib messages in {} ms",
                groupId,
                created,
                repaired,
                skipped,
                tooOld,
                messages.size(),
                System.currentTimeMillis() - startedAt
        );
        return new MessageSyncPersistenceResult(created, repaired, skipped, tooOld, messages.size());
    }

    private boolean repairMessageMetadata(GroupEntity group, MessageEntity entity, TelegramMessageDto msg, Instant msgDate) {
        boolean changed = false;

        if (isPlaceholderSenderName(entity.getSenderName()) && !isPlaceholderSenderName(msg.senderName())) {
            entity.setSenderName(msg.senderName());
            changed = true;
        }
        if (entity.getSenderTelegramUserId() == null && msg.senderTelegramUserId() != null) {
            entity.setSenderTelegramUserId(msg.senderTelegramUserId());
            changed = true;
        }
        if ((entity.getSenderUsername() == null || entity.getSenderUsername().isBlank())
            && msg.senderUsername() != null && !msg.senderUsername().isBlank()) {
            entity.setSenderUsername(msg.senderUsername());
            changed = true;
        }
        Long topicId = entity.getTopicId() != null ? entity.getTopicId() : (msg.messageThreadId() > 0 ? msg.messageThreadId() : null);
        String topicName = TelegramTopicNames.storedTopicName(group, topicId == null ? 0L : topicId, msg.topicName());
        if ((entity.getTopicName() == null || entity.getTopicName().isBlank()) && topicName != null && !topicName.isBlank()) {
            entity.setTopicName(topicName);
            changed = true;
        }
        if (entity.getTopicId() == null && msg.messageThreadId() > 0) {
            entity.setTopicId(msg.messageThreadId());
            changed = true;
        }
        if (entity.getReplyToMessageId() == null && msg.replyToMessageId() > 0) {
            entity.setReplyToMessageId(msg.replyToMessageId());
            changed = true;
        }
        if (shouldRefreshText(entity.getText(), msg.text())) {
            entity.setText(msg.text());
            changed = true;
        }
        if ((entity.getTextEntitiesJson() == null || entity.getTextEntitiesJson().isBlank())
            && msg.textEntitiesJson() != null && !msg.textEntitiesJson().isBlank()) {
            entity.setTextEntitiesJson(msg.textEntitiesJson());
            changed = true;
        }
        if (entity.getMessageDate() == null) {
            entity.setMessageDate(msgDate);
            changed = true;
        }
        if (!Boolean.TRUE.equals(entity.getIsBot()) && msg.isBot()) {
            entity.setIsBot(true);
            changed = true;
        }

        return changed;
    }

    private void applyInitialProcessingStatus(MessageEntity entity, GroupEntity group) {
        if (Boolean.TRUE.equals(group.getEnabled())) {
            entity.setProcessingStatus("UNPROCESSED");
            return;
        }

        entity.setProcessingStatus("SKIPPED");
        entity.setClassifierReason("Group is disabled; skipped at ingestion");
    }

    private boolean isPlaceholderSenderName(String senderName) {
        return senderName == null || senderName.isBlank() || senderName.equals("Unknown") || senderName.startsWith("User ");
    }

    private boolean shouldRefreshText(String currentText, String refreshedText) {
        if (refreshedText == null || refreshedText.isBlank()) {
            return false;
        }
        if (currentText == null || currentText.isBlank()) {
            return true;
        }
        if (currentText.equals(refreshedText)) {
            return false;
        }

        boolean refreshedHasLink = refreshedText.contains("](")
                || refreshedText.contains("http://")
                || refreshedText.contains("https://");
        boolean currentHasLink = currentText.contains("](")
                || currentText.contains("http://")
                || currentText.contains("https://");

        return refreshedHasLink && !currentHasLink;
    }

    private Map<Long, MessageEntity> loadExistingMessages(Long groupId, List<TelegramMessageDto> messages) {
        if (messages.isEmpty()) {
            return Map.of();
        }

        List<Long> telegramMessageIds = messages.stream()
                .map(TelegramMessageDto::id)
                .distinct()
                .toList();

        GroupEntity group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
        Map<Long, MessageEntity> existingByTelegramMessageId = new HashMap<>();
        List<MessageEntity> existingMessages = group.getAccountId() != null && group.getTelegramChatId() != null
                ? telegramMessageIds.stream()
                        .map(messageId -> messageRepository
                                .findByTelegramAccountIdAndTelegramChatIdAndTelegramMessageId(
                                        group.getAccountId(),
                                        group.getTelegramChatId(),
                                        messageId
                                ))
                        .flatMap(Optional::stream)
                        .toList()
                : messageRepository.findByGroupIdAndTelegramMessageIdIn(groupId, telegramMessageIds);
        for (MessageEntity entity : existingMessages) {
            existingByTelegramMessageId.put(entity.getTelegramMessageId(), entity);
        }
        return existingByTelegramMessageId;
    }
}
