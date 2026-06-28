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

    List<GroupEntity> findByOwnerUserIdAndEnabledTrue(Long ownerUserId);

    long countByAccountId(Long accountId);

    long countByAccountIdAndOwnerUserId(Long accountId, Long ownerUserId);

    Page<GroupEntity> findByEnabledAndAccountId(Boolean enabled, Long accountId, Pageable pageable);

    Optional<GroupEntity> findByTelegramChatId(Long telegramChatId);

    Optional<GroupEntity> findByTelegramChatIdAndOwnerUserId(Long telegramChatId, Long ownerUserId);

    Optional<GroupEntity> findByAccountIdAndTelegramChatId(Long accountId, Long telegramChatId);

    Optional<GroupEntity> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    List<GroupEntity> findByTelegramChatIdIn(Collection<Long> telegramChatIds);

    List<GroupEntity> findByTelegramChatIdInAndOwnerUserId(Collection<Long> telegramChatIds, Long ownerUserId);
}
