package com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TelegramBackfillJobEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TelegramBackfillJobRepository extends JpaRepository<TelegramBackfillJobEntity, Long> {

    @Query("""
        select job from TelegramBackfillJobEntity job
        where job.status in :statuses
          and (job.pausedUntil is null or job.pausedUntil <= :now)
        order by job.createdAt asc
        """)
    List<TelegramBackfillJobEntity> findRunnableJobs(
            @Param("statuses") Collection<String> statuses,
            @Param("now") Instant now,
            Pageable pageable
    );

    List<TelegramBackfillJobEntity> findByOwnerUserIdOrderByUpdatedAtDesc(Long ownerUserId);

    Optional<TelegramBackfillJobEntity> findByIdAndOwnerUserId(Long id, Long ownerUserId);
}
