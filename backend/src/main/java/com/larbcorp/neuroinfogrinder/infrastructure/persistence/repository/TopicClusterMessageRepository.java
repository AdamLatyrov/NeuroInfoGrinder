package com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TopicClusterMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TopicClusterMessageRepository extends JpaRepository<TopicClusterMessageEntity, Long> {

    boolean existsByMessageId(Long messageId);

    Optional<TopicClusterMessageEntity> findByMessageId(Long messageId);

    Optional<TopicClusterMessageEntity> findByClusterIdAndMessageId(Long clusterId, Long messageId);

    List<TopicClusterMessageEntity> findByClusterId(Long clusterId);

    List<TopicClusterMessageEntity> findByClusterIdIn(List<Long> clusterIds);

    long countByClusterIdIn(List<Long> clusterIds);

    @Modifying(clearAutomatically = true)
    @Query("delete from TopicClusterMessageEntity message where message.clusterId in :clusterIds")
    int deleteByClusterIdIn(@Param("clusterIds") List<Long> clusterIds);
}
