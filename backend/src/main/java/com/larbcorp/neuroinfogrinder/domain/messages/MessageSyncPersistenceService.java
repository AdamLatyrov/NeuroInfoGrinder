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

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageSyncPersistenceService {

    private final MessageRepository messageRepository;
    private final GroupRepository groupRepository;

    @Transactional
    public int persistSyncedMessages(Long groupId, List<TelegramMessageDto> messages) {
        long startedAt = System.currentTimeMillis();
        GroupEntity group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));

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
                if (repairMessageMetadata(existing, msg, msgDate)) {
                    repairedEntities.add(existing);
                    repaired++;
                } else {
                    skipped++;
                }
                continue;
            }
            MessageEntity entity = new MessageEntity();
            entity.setTelegramMessageId(msg.id());
            entity.setGroupId(groupId);
            entity.setText(msg.text());
            entity.setSenderName(msg.senderName());
            entity.setSenderUsername(msg.senderUsername());
            entity.setSenderTelegramUserId(msg.senderTelegramUserId());
            entity.setIsBot(msg.isBot());
            entity.setTextEntitiesJson(msg.textEntitiesJson());
            entity.setReplyToMessageId(msg.replyToMessageId() > 0 ? msg.replyToMessageId() : null);
            entity.setTopicName(msg.topicName());
            entity.setTopicId(msg.messageThreadId() > 0 ? msg.messageThreadId() : null);
            entity.setReplyCount(0);
            entity.setProcessingStatus("UNPROCESSED");
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
        return created + repaired;
    }

    private boolean repairMessageMetadata(MessageEntity entity, TelegramMessageDto msg, Instant msgDate) {
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
        if ((entity.getTopicName() == null || entity.getTopicName().isBlank()) && msg.topicName() != null && !msg.topicName().isBlank()) {
            entity.setTopicName(msg.topicName());
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

        Map<Long, MessageEntity> existingByTelegramMessageId = new HashMap<>();
        for (MessageEntity entity : messageRepository.findByGroupIdAndTelegramMessageIdIn(groupId, telegramMessageIds)) {
            existingByTelegramMessageId.put(entity.getTelegramMessageId(), entity);
        }
        return existingByTelegramMessageId;
    }
}
