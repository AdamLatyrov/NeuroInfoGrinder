package com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TopicClusterGuideCandidateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TopicClusterGuideCandidateRepository extends JpaRepository<TopicClusterGuideCandidateEntity, Long> {

    List<TopicClusterGuideCandidateEntity> findByClusterId(Long clusterId);

    List<TopicClusterGuideCandidateEntity> findByClusterIdIn(List<Long> clusterIds);

    long countByClusterIdIn(List<Long> clusterIds);

    @Modifying(clearAutomatically = true)
    @Query("delete from TopicClusterGuideCandidateEntity candidate where candidate.clusterId in :clusterIds")
    int deleteByClusterIdIn(@Param("clusterIds") List<Long> clusterIds);

    @Modifying
    @Query("""
        update TopicClusterGuideCandidateEntity candidate
        set candidate.guideId = null
        where candidate.guideId in :guideIds
        """)
    int clearGuideLinks(@Param("guideIds") List<Long> guideIds);
}
