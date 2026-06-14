package com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<GroupEntity, Long>, JpaSpecificationExecutor<GroupEntity> {

    Page<GroupEntity> findByEnabled(Boolean enabled, Pageable pageable);

    Page<GroupEntity> findByAccountId(Long accountId, Pageable pageable);

    List<GroupEntity> findByEnabledTrue();

    long countByAccountId(Long accountId);

    Page<GroupEntity> findByEnabledAndAccountId(Boolean enabled, Long accountId, Pageable pageable);

    Optional<GroupEntity> findByTelegramChatId(Long telegramChatId);

    List<GroupEntity> findByTelegramChatIdIn(Collection<Long> telegramChatIds);
}
