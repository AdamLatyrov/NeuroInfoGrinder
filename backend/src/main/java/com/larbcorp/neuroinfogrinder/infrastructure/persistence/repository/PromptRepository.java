package com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.PromptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromptRepository extends JpaRepository<PromptEntity, Long> {

    List<PromptEntity> findByType(String type);

    List<PromptEntity> findByStatus(String status);

    List<PromptEntity> findByTypeAndStatus(String type, String status);
}
