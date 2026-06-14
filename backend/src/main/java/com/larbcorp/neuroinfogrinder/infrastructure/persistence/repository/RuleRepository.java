package com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.RuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleRepository extends JpaRepository<RuleEntity, Long> {

    List<RuleEntity> findByStatusOrderByRuleOrderAsc(String status);

    List<RuleEntity> findAllByOrderByRuleOrderAsc();
}
