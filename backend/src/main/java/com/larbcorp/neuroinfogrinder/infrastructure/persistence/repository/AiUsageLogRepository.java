package com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.AiUsageLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLogEntity, Long> {

    List<AiUsageLogEntity> findByCreatedAtBetween(Instant from, Instant to);

    List<AiUsageLogEntity> findByProviderId(Long providerId);
}
