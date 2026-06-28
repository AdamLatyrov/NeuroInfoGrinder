package com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuidePublicationLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GuidePublicationLogRepository extends JpaRepository<GuidePublicationLogEntity, Long> {

    List<GuidePublicationLogEntity> findByGuideIdOrderByCreatedAtDesc(Long guideId);

    List<GuidePublicationLogEntity> findByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);

    List<GuidePublicationLogEntity> findByGuideIdAndOwnerUserIdOrderByCreatedAtDesc(Long guideId, Long ownerUserId);

    Optional<GuidePublicationLogEntity> findTopByGuideIdOrderByCreatedAtDesc(Long guideId);

    boolean existsByGuideIdAndStatus(Long guideId, String status);

    boolean existsByGuideIdAndOwnerUserIdAndStatus(Long guideId, Long ownerUserId, String status);
}
