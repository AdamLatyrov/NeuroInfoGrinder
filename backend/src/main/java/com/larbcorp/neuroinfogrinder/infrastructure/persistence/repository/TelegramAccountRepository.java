package com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TelegramAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TelegramAccountRepository extends JpaRepository<TelegramAccountEntity, Long> {

    List<TelegramAccountEntity> findByStatus(String status);

    long countByStatus(String status);

    Optional<TelegramAccountEntity> findByPhone(String phone);
}
