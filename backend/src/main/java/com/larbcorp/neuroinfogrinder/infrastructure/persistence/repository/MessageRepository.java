package com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<MessageEntity, Long> {

 Page<MessageEntity> findByGroupIdAndProcessingStatus(Long groupId, String status, Pageable pageable);

 Page<MessageEntity> findByOwnerUserIdAndGroupIdAndProcessingStatus(Long ownerUserId, Long groupId, String status, Pageable pageable);

 Page<MessageEntity> findByGroupIdAndTopicId(Long groupId, Long topicId, Pageable pageable);

 Page<MessageEntity> findByOwnerUserIdAndGroupIdAndTopicId(Long ownerUserId, Long groupId, Long topicId, Pageable pageable);

 Page<MessageEntity> findByGroupIdAndTopicIdAndProcessingStatus(Long groupId, Long topicId, String status, Pageable pageable);

 Page<MessageEntity> findByOwnerUserIdAndGroupIdAndTopicIdAndProcessingStatus(Long ownerUserId, Long groupId, Long topicId, String status, Pageable pageable);

 Page<MessageEntity> findByGroupId(Long groupId, Pageable pageable);

 Page<MessageEntity> findByOwnerUserIdAndGroupId(Long ownerUserId, Long groupId, Pageable pageable);

    long countByGroupIdAndMessageDateAfter(Long groupId, Instant since);

    List<MessageEntity> findByGroupIdAndReplyToMessageId(Long groupId, Long replyToMessageId);

    List<MessageEntity> findByGroupIdAndReplyToMessageIdIn(Long groupId, List<Long> replyToMessageIds);

    List<MessageEntity> findByGroupIdAndTelegramMessageIdIn(Long groupId, List<Long> telegramMessageIds);

    List<MessageEntity> findByGroupIdAndMessageDateAfter(Long groupId, Instant since);

    List<MessageEntity> findByGroupIdAndMessageDateBetweenOrderByMessageDateAsc(Long groupId, Instant from, Instant to);

    List<MessageEntity> findByProcessingStatus(String status);

    Optional<MessageEntity> findByGroupIdAndTelegramMessageId(Long groupId, Long telegramMessageId);

    Optional<MessageEntity> findByTelegramAccountIdAndTelegramChatIdAndTelegramMessageId(
        Long telegramAccountId,
        Long telegramChatId,
        Long telegramMessageId
    );

    Optional<MessageEntity> findByIdAndGroupId(Long id, Long groupId);

    Optional<MessageEntity> findByIdAndGroupIdAndOwnerUserId(Long id, Long groupId, Long ownerUserId);

    Optional<MessageEntity> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    Optional<MessageEntity> findFirstByGroupIdOrderByMessageDateDesc(Long groupId);

    Optional<MessageEntity> findFirstByClassificationContextHashAndUpdatedAtAfterOrderByUpdatedAtDesc(
        String classificationContextHash,
        Instant updatedAfter
    );

    List<MessageEntity> findByGuideId(Long guideId);

    List<MessageEntity> findByOwnerUserIdAndGuideIdIn(Long ownerUserId, List<Long> guideIds);

    long countByOwnerUserId(Long ownerUserId);

    @Modifying
    @Query("delete from MessageEntity message where message.ownerUserId = :ownerUserId")
    int deleteOwnedMessages(@Param("ownerUserId") Long ownerUserId);

    @Modifying(clearAutomatically = true)
    @Query("""
        update MessageEntity message
        set message.guideId = null,
            message.processingStatus = case
                when message.processingStatus = 'GUIDE_FOUND' then 'SKIPPED'
                else message.processingStatus
            end
        where message.ownerUserId = :ownerUserId
          and message.guideId in :guideIds
        """)
    int clearGuideLinks(@Param("ownerUserId") Long ownerUserId, @Param("guideIds") List<Long> guideIds);

    List<MessageEntity> findTop500ByGroupIdAndTopicIdIsNotNullAndTopicNameIsNotNullOrderByMessageDateDesc(Long groupId);

    boolean existsByGroupIdAndTelegramMessageId(Long groupId, Long telegramMessageId);

    boolean existsByTelegramAccountIdAndTelegramChatIdAndTelegramMessageId(
        Long telegramAccountId,
        Long telegramChatId,
        Long telegramMessageId
    );

    /** Find messages with given processing statuses, ordered by date for queue processing. */
    Page<MessageEntity> findByProcessingStatusInOrderByMessageDateAsc(List<String> statuses, Pageable pageable);

    Page<MessageEntity> findByGroupIdInAndProcessingStatusInOrderByMessageDateAsc(
        List<Long> groupIds,
        List<String> statuses,
        Pageable pageable
    );

    Page<MessageEntity> findByOwnerUserIdAndGroupIdInAndProcessingStatusInOrderByMessageDateAsc(
        Long ownerUserId,
        List<Long> groupIds,
        List<String> statuses,
        Pageable pageable
    );

    Page<MessageEntity> findByGroupIdInAndProcessingStatusIn(
        List<Long> groupIds,
        List<String> statuses,
        Pageable pageable
    );

    Page<MessageEntity> findByOwnerUserIdAndGroupIdInAndProcessingStatusIn(
        Long ownerUserId,
        List<Long> groupIds,
        List<String> statuses,
        Pageable pageable
    );

    List<MessageEntity> findByGroupIdInAndProcessingStatusIn(
        List<Long> groupIds,
        List<String> statuses
    );

    List<MessageEntity> findByOwnerUserIdAndGroupIdInAndProcessingStatusIn(
        Long ownerUserId,
        List<Long> groupIds,
        List<String> statuses
    );

    @Query("""
        select message from MessageEntity message
        where message.id in :ids
        order by message.messageDate asc
        """)
    List<MessageEntity> findByIdInOrderByMessageDateAsc(@Param("ids") List<Long> ids);

    /** Find messages with pipeline scores for the results/tuning view. */
    Page<MessageEntity> findByProcessingStatusInAndSignalScoreIsNotNull(
        List<String> statuses, Pageable pageable);

    Page<MessageEntity> findByProcessingStatusInAndSignalScoreIsNotNullAndMessageDateAfter(
        List<String> statuses, Instant from, Pageable pageable);

    Page<MessageEntity> findByProcessingStatusIn(
        List<String> statuses, Pageable pageable);

 Page<MessageEntity> findByProcessingStatusInAndMessageDateAfter(
 List<String> statuses, Instant from, Pageable pageable);

 Page<MessageEntity> findByProcessingStatusInAndMessageDateBetween(
 List<String> statuses, Instant from, Instant to, Pageable pageable);

 Page<MessageEntity> findByGroupIdInAndProcessingStatusInAndMessageDateBetween(
 List<Long> groupIds, List<String> statuses, Instant from, Instant to, Pageable pageable);

 Page<MessageEntity> findByOwnerUserIdAndGroupIdInAndProcessingStatusInAndMessageDateBetween(
 Long ownerUserId, List<Long> groupIds, List<String> statuses, Instant from, Instant to, Pageable pageable);

 Page<MessageEntity> findByGroupIdInAndProcessingStatusInAndMessageDateAfter(
 List<Long> groupIds, List<String> statuses, Instant from, Pageable pageable);

 Page<MessageEntity> findByOwnerUserIdAndGroupIdInAndProcessingStatusInAndMessageDateAfter(
 Long ownerUserId, List<Long> groupIds, List<String> statuses, Instant from, Pageable pageable);

 /** Count messages by processing status. */
    long countByProcessingStatus(String status);

    long countByGroupIdInAndProcessingStatus(List<Long> groupIds, String status);

    long countByGroupIdInAndProcessingStatusAndMessageDateAfter(
        List<Long> groupIds,
        String status,
        Instant from
    );

    long countByGroupIdInAndProcessingStatusAndMessageDateBetween(
        List<Long> groupIds,
        String status,
        Instant from,
        Instant to
    );

    long countByGroupIdInAndGuideIdIsNotNull(List<Long> groupIds);

    long countByGroupIdInAndGuideIdIsNotNullAndMessageDateAfter(
        List<Long> groupIds,
        Instant from
    );

    long countByGroupIdInAndGuideIdIsNotNullAndMessageDateBetween(
        List<Long> groupIds,
        Instant from,
        Instant to
    );
}
