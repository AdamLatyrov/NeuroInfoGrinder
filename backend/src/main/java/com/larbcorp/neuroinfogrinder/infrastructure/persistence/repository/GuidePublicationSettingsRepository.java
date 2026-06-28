package com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuidePublicationSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GuidePublicationSettingsRepository extends JpaRepository<GuidePublicationSettingsEntity, Long> {
    Optional<GuidePublicationSettingsEntity> findFirstByOrderByIdAsc();

    Optional<GuidePublicationSettingsEntity> findFirstByOwnerUserIdOrderByIdAsc(Long ownerUserId);
}
