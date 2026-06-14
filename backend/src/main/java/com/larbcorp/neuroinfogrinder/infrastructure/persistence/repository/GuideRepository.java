package com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GuideRepository extends JpaRepository<GuideEntity, Long> {

    Page<GuideEntity> findByStatus(String status, Pageable pageable);

    Page<GuideEntity> findByGroupId(Long groupId, Pageable pageable);

    Page<GuideEntity> findByStatusIn(List<String> statuses, Pageable pageable);

    long countByGroupId(Long groupId);
}
