package com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GuideRepository extends JpaRepository<GuideEntity, Long> {

    Page<GuideEntity> findByStatus(String status, Pageable pageable);

    Page<GuideEntity> findByGroupId(Long groupId, Pageable pageable);

    List<GuideEntity> findTop100ByGroupIdAndCreatedAtAfterOrderByCreatedAtDesc(Long groupId, java.time.Instant createdAtAfter);

    List<GuideEntity> findByOwnerUserId(Long ownerUserId);

    java.util.Optional<GuideEntity> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    long countByOwnerUserId(Long ownerUserId);

    Page<GuideEntity> findByStatusIn(List<String> statuses, Pageable pageable);

    List<GuideEntity> findByTopicClusterId(Long topicClusterId);

    List<GuideEntity> findByTopicClusterGuideCandidateId(Long topicClusterGuideCandidateId);

    long countByGroupId(Long groupId);

    long countByGroupIdAndOwnerUserId(Long groupId, Long ownerUserId);

    @Query("""
        select count(g)
        from GuideEntity g
        where g.status = 'FAILED'
           or (g.generationError is not null and g.generationError <> '')
        """)
    long countGenerationErrors();

    @Query("""
        select guide
        from GuideEntity guide
        where guide.ownerUserId = :ownerUserId
          and (guide.contentType is null or upper(guide.contentType) = 'GUIDE')
        """)
    List<GuideEntity> findOwnedGuidesForCleanup(@Param("ownerUserId") Long ownerUserId);

    @Query("""
        select guide
        from GuideEntity guide
        where guide.ownerUserId = :ownerUserId
          and guide.contentType is not null
          and upper(guide.contentType) <> 'GUIDE'
        """)
    List<GuideEntity> findOwnedMaterialsForCleanup(@Param("ownerUserId") Long ownerUserId);
}
