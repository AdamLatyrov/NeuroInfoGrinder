package com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideSourceMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GuideSourceMessageRepository extends JpaRepository<GuideSourceMessageEntity, Long> {

    List<GuideSourceMessageEntity> findByGuideId(Long guideId);

    long countByGuideId(Long guideId);

    long countByGuideIdIn(List<Long> guideIds);

    @Modifying(clearAutomatically = true)
    @Query("delete from GuideSourceMessageEntity source where source.guideId in :guideIds")
    int deleteByGuideIdIn(@Param("guideIds") List<Long> guideIds);

    boolean existsByGuideIdAndMessageId(Long guideId, Long messageId);
}
