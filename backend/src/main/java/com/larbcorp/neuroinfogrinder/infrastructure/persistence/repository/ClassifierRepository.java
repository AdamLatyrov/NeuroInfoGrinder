package com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.ClassifierEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClassifierRepository extends JpaRepository<ClassifierEntity, Long> {

    List<ClassifierEntity> findByStatus(String status);

    List<ClassifierEntity> findByStatusOrderByClassifierOrderAsc(String status);

    List<ClassifierEntity> findByType(String type);
}
