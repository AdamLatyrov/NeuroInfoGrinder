package com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.AiProviderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiProviderRepository extends JpaRepository<AiProviderEntity, Long> {

    List<AiProviderEntity> findByStatus(String status);
}
