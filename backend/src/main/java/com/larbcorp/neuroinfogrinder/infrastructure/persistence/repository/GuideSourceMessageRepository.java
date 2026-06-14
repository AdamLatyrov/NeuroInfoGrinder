package com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideSourceMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GuideSourceMessageRepository extends JpaRepository<GuideSourceMessageEntity, Long> {

    List<GuideSourceMessageEntity> findByGuideId(Long guideId);
}
