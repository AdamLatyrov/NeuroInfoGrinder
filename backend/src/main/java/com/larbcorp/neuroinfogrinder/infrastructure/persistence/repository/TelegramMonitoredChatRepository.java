package com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TelegramMonitoredChatEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TelegramMonitoredChatRepository extends JpaRepository<TelegramMonitoredChatEntity, Long> {

    @Query("""
        select chat from TelegramMonitoredChatEntity chat
        where ((:accountId is null and chat.telegramAccountId is null) or chat.telegramAccountId = :accountId)
          and chat.telegramChatId = :chatId
          and chat.enabled = true
          and chat.liveIngestionEnabled = true
          and (chat.topicId is null or chat.topicId = :topicId)
        order by case when chat.topicId = :topicId then 0 else 1 end
        """)
    List<TelegramMonitoredChatEntity> findLiveMonitoredChats(
            @Param("accountId") Long accountId,
            @Param("chatId") Long chatId,
            @Param("topicId") Long topicId,
            Pageable pageable
    );

    default Optional<TelegramMonitoredChatEntity> findLiveMonitoredChat(Long accountId, Long chatId, Long topicId) {
        return findLiveMonitoredChats(accountId, chatId, topicId, Pageable.ofSize(1)).stream().findFirst();
    }

    @Query("""
        select chat from TelegramMonitoredChatEntity chat
        where ((:accountId is null and chat.telegramAccountId is null) or chat.telegramAccountId = :accountId)
          and chat.telegramChatId = :chatId
          and chat.enabled = true
          and chat.backfillEnabled = true
          and (chat.topicId is null or chat.topicId = :topicId)
        order by case when chat.topicId = :topicId then 0 else 1 end
        """)
    List<TelegramMonitoredChatEntity> findBackfillMonitoredChats(
            @Param("accountId") Long accountId,
            @Param("chatId") Long chatId,
            @Param("topicId") Long topicId,
            Pageable pageable
    );

    default Optional<TelegramMonitoredChatEntity> findBackfillMonitoredChat(Long accountId, Long chatId, Long topicId) {
        return findBackfillMonitoredChats(accountId, chatId, topicId, Pageable.ofSize(1)).stream().findFirst();
    }

    List<TelegramMonitoredChatEntity> findByOwnerUserIdOrderByUpdatedAtDesc(Long ownerUserId);

    Optional<TelegramMonitoredChatEntity> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    @Query("""
        select chat from TelegramMonitoredChatEntity chat
        where ((:accountId is null and chat.telegramAccountId is null) or chat.telegramAccountId = :accountId)
          and chat.ownerUserId = :ownerUserId
          and chat.telegramChatId = :chatId
          and ((:topicId is null and chat.topicId is null) or chat.topicId = :topicId)
        """)
    Optional<TelegramMonitoredChatEntity> findOwnedChat(
            @Param("accountId") Long accountId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("chatId") Long chatId,
            @Param("topicId") Long topicId
    );
}
