package com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<MessageEntity, Long> {

 Page<MessageEntity> findByGroupIdAndProcessingStatus(Long groupId, String status, Pageable pageable);

 Page<MessageEntity> findByGroupIdAndTopicId(Long groupId, Long topicId, Pageable pageable);

 Page<MessageEntity> findByGroupIdAndTopicIdAndProcessingStatus(Long groupId, Long topicId, String status, Pageable pageable);

 Page<MessageEntity> findByGroupId(Long groupId, Pageable pageable);

    long countByGroupIdAndMessageDateAfter(Long groupId, Instant since);

    List<MessageEntity> findByGroupIdAndReplyToMessageId(Long groupId, Long replyToMessageId);

    List<MessageEntity> findByGroupIdAndReplyToMessageIdIn(Long groupId, List<Long> replyToMessageIds);

    List<MessageEntity> findByGroupIdAndTelegramMessageIdIn(Long groupId, List<Long> telegramMessageIds);

    List<MessageEntity> findByGroupIdAndMessageDateAfter(Long groupId, Instant since);

    List<MessageEntity> findByGroupIdAndMessageDateBetweenOrderByMessageDateAsc(Long groupId, Instant from, Instant to);

    List<MessageEntity> findByProcessingStatus(String status);

    Optional<MessageEntity> findByGroupIdAndTelegramMessageId(Long groupId, Long telegramMessageId);

    Optional<MessageEntity> findByIdAndGroupId(Long id, Long groupId);

    Optional<MessageEntity> findFirstByClassificationContextHashAndUpdatedAtAfterOrderByUpdatedAtDesc(
        String classificationContextHash,
        Instant updatedAfter
    );

    List<MessageEntity> findByGuideId(Long guideId);

    List<MessageEntity> findTop500ByGroupIdAndTopicIdIsNotNullAndTopicNameIsNotNullOrderByMessageDateDesc(Long groupId);

    boolean existsByGroupIdAndTelegramMessageId(Long groupId, Long telegramMessageId);

    /** Find messages with given processing statuses, ordered by date for queue processing. */
    Page<MessageEntity> findByProcessingStatusInOrderByMessageDateAsc(List<String> statuses, Pageable pageable);

    Page<MessageEntity> findByGroupIdInAndProcessingStatusInOrderByMessageDateAsc(
        List<Long> groupIds,
        List<String> statuses,
        Pageable pageable
    );

    Page<MessageEntity> findByGroupIdInAndProcessingStatusIn(
        List<Long> groupIds,
        List<String> statuses,
        Pageable pageable
    );

    List<MessageEntity> findByGroupIdInAndProcessingStatusIn(
        List<Long> groupIds,
        List<String> statuses
    );

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

 Page<MessageEntity> findByGroupIdInAndProcessingStatusInAndMessageDateAfter(
 List<Long> groupIds, List<String> statuses, Instant from, Pageable pageable);

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
}
