package com.larbcorp.neuroinfogrinder.domain.messages;

import com.larbcorp.neuroinfogrinder.domain.findings.PipelineEventBus;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TelegramMonitoredChatEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TelegramAccountRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TelegramMonitoredChatRepository;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramMessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramMessageIngestionService {

    private final GroupRepository groupRepository;
    private final MessageRepository messageRepository;
    private final TelegramAccountRepository telegramAccountRepository;
    private final TelegramMonitoredChatRepository monitoredChatRepository;
    private final PipelineEventBus pipelineEventBus;

    @Value("${telegram.ingestion.enabled:true}")
    private boolean ingestionEnabled;

    @Value("${pipeline.processing.enabled:true}")
    private boolean pipelineProcessingEnabled;

    @Transactional
    public TelegramIngestionResult ingestRealtimeMessage(Long telegramAccountId, TelegramMessageDto message) {
        if (!ingestionEnabled) {
            return TelegramIngestionResult.IGNORED_DISABLED;
        }
        if (message == null || message.id() <= 0 || message.chatId() == 0L) {
            return TelegramIngestionResult.IGNORED_EMPTY;
        }

        Optional<TelegramMonitoredChatEntity> monitoredChat = monitoredChatRepository.findLiveMonitoredChat(
                telegramAccountId,
                message.chatId(),
                topicId(message)
        );
        if (monitoredChat.isEmpty()) {
            log.debug(
                    "Ignoring realtime Telegram message from unmonitored chat account={} chat={} message={}",
                    telegramAccountId,
                    message.chatId(),
                    message.id()
            );
            return TelegramIngestionResult.IGNORED_UNMONITORED_CHAT;
        }

        return persistMonitoredMessage(monitoredChat.get(), telegramAccountId, message, true);
    }

    @Transactional
    public TelegramIngestionResult ingestBackfillMessage(Long telegramAccountId, TelegramMessageDto message) {
        if (message == null || message.id() <= 0 || message.chatId() == 0L) {
            return TelegramIngestionResult.IGNORED_EMPTY;
        }

        Optional<TelegramMonitoredChatEntity> monitoredChat = monitoredChatRepository.findBackfillMonitoredChat(
                telegramAccountId,
                message.chatId(),
                topicId(message)
        );
        if (monitoredChat.isEmpty()) {
            return TelegramIngestionResult.IGNORED_UNMONITORED_CHAT;
        }

        return persistMonitoredMessage(monitoredChat.get(), telegramAccountId, message, false);
    }

    private TelegramIngestionResult persistMonitoredMessage(
            TelegramMonitoredChatEntity monitoredChat,
            Long callbackAccountId,
            TelegramMessageDto message,
            boolean live
    ) {
        Long effectiveAccountId = firstNonNull(monitoredChat.getTelegramAccountId(), callbackAccountId);
        Long topicId = topicId(message);
        GroupEntity group = findOrCreateGroup(monitoredChat, effectiveAccountId);

        Optional<MessageEntity> existing = findExistingMessage(effectiveAccountId, message.chatId(), message.id(), group.getId());
        if (existing.isPresent()) {
            return TelegramIngestionResult.DUPLICATE;
        }

        MessageEntity entity = new MessageEntity();
        entity.setTelegramAccountId(effectiveAccountId);
        entity.setTelegramChatId(message.chatId());
        entity.setTelegramMessageId(message.id());
        entity.setGroupId(group.getId());
        entity.setOwnerUserId(group.getOwnerUserId());
        entity.setText(message.text());
        entity.setSenderName(message.senderName());
        entity.setSenderUsername(message.senderUsername());
        entity.setSenderTelegramUserId(message.senderTelegramUserId());
        entity.setIsBot(message.isBot());
        entity.setTextEntitiesJson(message.textEntitiesJson());
        entity.setReplyToMessageId(message.replyToMessageId() > 0 ? message.replyToMessageId() : null);
        entity.setTopicId(topicId);
        entity.setTopicName(TelegramTopicNames.storedTopicName(group, message.messageThreadId(), message.topicName()));
        entity.setReplyCount(0);
        entity.setProcessingStatus(pipelineProcessingEnabled ? "QUEUED" : "UNPROCESSED");
        entity.setMessageDate(Instant.ofEpochSecond(message.date()));

        try {
            entity = messageRepository.save(entity);
        } catch (DataIntegrityViolationException exception) {
            log.debug(
                    "Duplicate Telegram message rejected by storage account={} chat={} message={}",
                    effectiveAccountId,
                    message.chatId(),
                    message.id()
            );
            return TelegramIngestionResult.DUPLICATE;
        }

        updateCursors(monitoredChat, group, message, entity.getMessageDate(), live);

        log.info(
                "Telegram message ingested: source={} group={} account={} chat={} message={}",
                live ? "realtime" : "backfill",
                group.getId(),
                effectiveAccountId,
                message.chatId(),
                message.id()
        );
        if (pipelineProcessingEnabled) {
            pipelineEventBus.publish(new PipelineEventBus.PipelineEvent(
                    "MESSAGE_INGESTED",
                    entity.getId(),
                    group.getId(),
                    live ? "TELEGRAM_REALTIME" : "TELEGRAM_BACKFILL",
                    "QUEUED"
            ));
        }
        return TelegramIngestionResult.SAVED;
    }

    private Optional<MessageEntity> findExistingMessage(
            Long telegramAccountId,
            Long telegramChatId,
            Long telegramMessageId,
            Long groupId
    ) {
        if (telegramAccountId != null && telegramChatId != null) {
            return messageRepository.findByTelegramAccountIdAndTelegramChatIdAndTelegramMessageId(
                    telegramAccountId,
                    telegramChatId,
                    telegramMessageId
            );
        }
        return messageRepository.findByGroupIdAndTelegramMessageId(groupId, telegramMessageId);
    }

    private GroupEntity findOrCreateGroup(TelegramMonitoredChatEntity monitoredChat, Long effectiveAccountId) {
        Optional<GroupEntity> existing = effectiveAccountId != null
                ? groupRepository.findByAccountIdAndTelegramChatId(effectiveAccountId, monitoredChat.getTelegramChatId())
                : groupRepository.findByTelegramChatIdAndOwnerUserId(
                        monitoredChat.getTelegramChatId(),
                        monitoredChat.getOwnerUserId()
                );
        return existing.map(group -> updateGroupFromMonitor(group, monitoredChat, effectiveAccountId))
                .orElseGet(() -> createGroupFromMonitor(monitoredChat, effectiveAccountId));
    }

    private GroupEntity updateGroupFromMonitor(
            GroupEntity group,
            TelegramMonitoredChatEntity monitoredChat,
            Long effectiveAccountId
    ) {
        boolean changed = false;
        if (group.getAccountId() == null && effectiveAccountId != null) {
            group.setAccountId(effectiveAccountId);
            changed = true;
        }
        if (group.getOwnerUserId() == null && monitoredChat.getOwnerUserId() != null) {
            group.setOwnerUserId(monitoredChat.getOwnerUserId());
            changed = true;
        }
        if (monitoredChat.getChatTitle() != null && !monitoredChat.getChatTitle().isBlank()
                && !monitoredChat.getChatTitle().equals(group.getTitle())) {
            group.setTitle(monitoredChat.getChatTitle());
            changed = true;
        }
        if (!Boolean.TRUE.equals(group.getEnabled()) && Boolean.TRUE.equals(monitoredChat.getEnabled())) {
            group.setEnabled(true);
            changed = true;
        }
        return changed ? groupRepository.save(group) : group;
    }

    private GroupEntity createGroupFromMonitor(TelegramMonitoredChatEntity monitoredChat, Long effectiveAccountId) {
        GroupEntity group = new GroupEntity();
        group.setTelegramChatId(monitoredChat.getTelegramChatId());
        group.setTitle(nonBlank(monitoredChat.getChatTitle(), "Telegram chat " + monitoredChat.getTelegramChatId()));
        group.setForum(false);
        group.setEnabled(Boolean.TRUE.equals(monitoredChat.getEnabled()));
        group.setAccountId(effectiveAccountId);
        group.setOwnerUserId(monitoredChat.getOwnerUserId());
        group.setLastReadMessageId(0L);
        return groupRepository.save(group);
    }

    private void updateCursors(
            TelegramMonitoredChatEntity monitoredChat,
            GroupEntity group,
            TelegramMessageDto message,
            Instant messageDate,
            boolean live
    ) {
        group.setLastReadMessageId(Math.max(group.getLastReadMessageId() == null ? 0L : group.getLastReadMessageId(), message.id()));
        if (group.getLastReadAt() == null || messageDate.isAfter(group.getLastReadAt())) {
            group.setLastReadAt(messageDate);
        }
        groupRepository.save(group);

        if (live) {
            monitoredChat.setLastLiveMessageId(message.id());
            monitoredChat.setLastLiveMessageAt(messageDate);
        } else {
            monitoredChat.setLastBackfillMessageId(message.id());
            monitoredChat.setBackfillStatus("RUNNING");
        }
        monitoredChat.setLastError(null);
        monitoredChatRepository.save(monitoredChat);
    }

    private Long resolveOwnerUserId(Long telegramAccountId) {
        if (telegramAccountId == null) {
            return null;
        }
        return telegramAccountRepository.findById(telegramAccountId)
                .map(account -> account.getOwnerUserId())
                .orElse(null);
    }

    private Long topicId(TelegramMessageDto message) {
        return message.messageThreadId() > 0 ? message.messageThreadId() : null;
    }

    private Long firstNonNull(Long left, Long right) {
        return left != null ? left : right;
    }

    private String nonBlank(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
