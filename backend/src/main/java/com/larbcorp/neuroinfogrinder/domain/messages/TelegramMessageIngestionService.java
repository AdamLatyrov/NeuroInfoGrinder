package com.larbcorp.neuroinfogrinder.domain.messages;

import com.larbcorp.neuroinfogrinder.domain.findings.PipelineEventBus;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.telegram.TelegramUpdateListener;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramMessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramMessageIngestionService implements TelegramUpdateListener {

    private final GroupRepository groupRepository;
    private final MessageRepository messageRepository;
    private final PipelineEventBus pipelineEventBus;

    @Override
    @Transactional
    public void onNewMessage(TelegramMessageDto message) {
        GroupEntity group = groupRepository.findByTelegramChatId(message.chatId())
            .orElseGet(() -> createUnknownGroup(message.chatId()));

        if (messageRepository.existsByGroupIdAndTelegramMessageId(group.getId(), message.id())) {
            return;
        }

        MessageEntity entity = new MessageEntity();
        entity.setTelegramMessageId(message.id());
        entity.setGroupId(group.getId());
        entity.setText(message.text());
        entity.setSenderName(message.senderName());
        entity.setSenderTelegramUserId(message.senderTelegramUserId());
        entity.setIsBot(message.isBot());
        entity.setReplyToMessageId(message.replyToMessageId() > 0 ? message.replyToMessageId() : null);
        entity.setTopicName(message.topicName());
        entity.setTopicId(message.messageThreadId() > 0 ? message.messageThreadId() : null);
        entity.setReplyCount(0);
        entity.setProcessingStatus("UNPROCESSED");
        entity.setMessageDate(Instant.ofEpochSecond(message.date()));
        entity = messageRepository.save(entity);

        group.setLastReadMessageId(message.id());
        group.setLastReadAt(Instant.now());
        groupRepository.save(group);

        log.info("Realtime Telegram message ingested: group={} chat={} message={}", group.getId(), message.chatId(), message.id());
        pipelineEventBus.publish(new PipelineEventBus.PipelineEvent(
            "MESSAGE_INGESTED",
            entity.getId(),
            group.getId(),
            "TELEGRAM_READ",
            "COMPLETED"
        ));
    }

    private GroupEntity createUnknownGroup(long chatId) {
        GroupEntity group = new GroupEntity();
        group.setTelegramChatId(chatId);
        group.setTitle("Telegram chat " + chatId);
        group.setForum(false);
        group.setEnabled(false);
        group.setLastReadMessageId(0L);
        return groupRepository.save(group);
    }
}
