package com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TaskQueueEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskQueueRepository extends JpaRepository<TaskQueueEntity, Long> {

    List<TaskQueueEntity> findByStatusOrderByPriorityDescCreatedAtAsc(String status);

    long countByStatus(String status);
}
