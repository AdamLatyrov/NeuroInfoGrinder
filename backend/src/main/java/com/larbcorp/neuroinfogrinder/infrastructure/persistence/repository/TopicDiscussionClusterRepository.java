package com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TopicDiscussionClusterEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TopicDiscussionClusterRepository extends JpaRepository<TopicDiscussionClusterEntity, Long> {

    List<TopicDiscussionClusterEntity> findByGroupIdAndTelegramTopicIdAndStatusInAndEndAtAfterOrderByEndAtDesc(
        Long groupId,
        Long telegramTopicId,
        List<String> statuses,
        Instant endAtAfter
    );

    List<TopicDiscussionClusterEntity> findByOwnerUserIdAndGroupIdAndTelegramTopicIdAndStatusInAndEndAtAfterOrderByEndAtDesc(
        Long ownerUserId,
        Long groupId,
        Long telegramTopicId,
        List<String> statuses,
        Instant endAtAfter
    );

    List<TopicDiscussionClusterEntity> findByGroupIdAndTelegramTopicIdIsNullAndTopicTitleAndStatusInAndEndAtAfterOrderByEndAtDesc(
        Long groupId,
        String topicTitle,
        List<String> statuses,
        Instant endAtAfter
    );

    List<TopicDiscussionClusterEntity> findByOwnerUserIdAndGroupIdAndTelegramTopicIdIsNullAndTopicTitleAndStatusInAndEndAtAfterOrderByEndAtDesc(
        Long ownerUserId,
        Long groupId,
        String topicTitle,
        List<String> statuses,
        Instant endAtAfter
    );

    Page<TopicDiscussionClusterEntity> findByStatusInOrderByUpdatedAtDesc(List<String> statuses, Pageable pageable);

    Page<TopicDiscussionClusterEntity> findByOwnerUserIdAndStatusInOrderByUpdatedAtDesc(
        Long ownerUserId,
        List<String> statuses,
        Pageable pageable
    );

    Page<TopicDiscussionClusterEntity> findByGroupIdAndStatusInOrderByUpdatedAtDesc(
        Long groupId,
        List<String> statuses,
        Pageable pageable
    );

    Page<TopicDiscussionClusterEntity> findByOwnerUserIdAndGroupIdAndStatusInOrderByUpdatedAtDesc(
        Long ownerUserId,
        Long groupId,
        List<String> statuses,
        Pageable pageable
    );

    @Query("select cluster.id from TopicDiscussionClusterEntity cluster where cluster.ownerUserId = :ownerUserId")
    List<Long> findIdsByOwnerUserId(@Param("ownerUserId") Long ownerUserId);

    long countByOwnerUserId(Long ownerUserId);

    @Modifying
    @Query("""
        update TopicDiscussionClusterEntity cluster
        set cluster.guideId = null
        where cluster.ownerUserId = :ownerUserId
          and cluster.guideId in :guideIds
        """)
    int clearGuideLinks(@Param("ownerUserId") Long ownerUserId, @Param("guideIds") List<Long> guideIds);

    @Modifying
    @Query("delete from TopicDiscussionClusterEntity cluster where cluster.ownerUserId = :ownerUserId")
    int deleteOwnedClusters(@Param("ownerUserId") Long ownerUserId);
}
