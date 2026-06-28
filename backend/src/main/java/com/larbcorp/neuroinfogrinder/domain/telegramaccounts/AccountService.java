package com.larbcorp.neuroinfogrinder.domain.telegramaccounts;

import com.larbcorp.neuroinfogrinder.domain.telegramaccounts.dto.AccountResponse;
import com.larbcorp.neuroinfogrinder.domain.telegramaccounts.dto.CreateAccountRequest;
import com.larbcorp.neuroinfogrinder.domain.telegramaccounts.dto.SubmitCodeRequest;
import com.larbcorp.neuroinfogrinder.domain.telegramaccounts.dto.SubmitPasswordRequest;
import com.larbcorp.neuroinfogrinder.domain.telegramaccounts.dto.UpdateProxyRequest;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TelegramAccountEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TelegramAccountRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.telegram.TelegramTdlibService;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramAuthStateResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final TelegramAccountRepository accountRepository;
    private final GroupRepository groupRepository;
    private final TelegramTdlibService telegramTdlibService;

    public AccountService(
            TelegramAccountRepository accountRepository,
            GroupRepository groupRepository,
            TelegramTdlibService telegramTdlibService
    ) {
        this.accountRepository = accountRepository;
        this.groupRepository = groupRepository;
        this.telegramTdlibService = telegramTdlibService;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAll(Long ownerUserId) {
        return accountRepository.findByOwnerUserIdOrderByCreatedAtAsc(ownerUserId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AccountResponse getById(Long ownerUserId, Long id) {
        TelegramAccountEntity account = accountRepository.findByIdAndOwnerUserId(id, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
        return toResponse(account);
    }

    @Transactional
    public AccountResponse create(Long ownerUserId, CreateAccountRequest request) {
        TelegramAccountEntity account = accountRepository.findByPhoneAndOwnerUserId(request.phone(), ownerUserId)
                .orElseGet(TelegramAccountEntity::new);
        account.setOwnerUserId(ownerUserId);
        account.setPhone(request.phone());
        account = accountRepository.save(account);
        TelegramAuthStateResponse authState = telegramTdlibService.submitPhoneNumber(account.getId(), request.phone());
        applyAuthState(account, authState);
        account = accountRepository.save(account);
        return toResponse(account);
    }

    @Transactional
    public AccountResponse submitCode(Long ownerUserId, Long id, SubmitCodeRequest request) {
        TelegramAccountEntity account = accountRepository.findByIdAndOwnerUserId(id, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
        TelegramAuthStateResponse authState = telegramTdlibService.submitCode(account.getId(), request.code());
        applyAuthState(account, authState);
        account = accountRepository.save(account);
        return toResponse(account);
    }

    @Transactional
    public AccountResponse submitPassword(Long ownerUserId, Long id, SubmitPasswordRequest request) {
        TelegramAccountEntity account = accountRepository.findByIdAndOwnerUserId(id, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
        TelegramAuthStateResponse authState = telegramTdlibService.submitPassword(account.getId(), request.password());
        applyAuthState(account, authState);
        account = accountRepository.save(account);
        return toResponse(account);
    }

    @Transactional
    public AccountResponse updateProxy(Long ownerUserId, Long id, UpdateProxyRequest request) {
        TelegramAccountEntity account = accountRepository.findByIdAndOwnerUserId(id, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
        if (request.proxyType() != null) {
            account.setProxyType(request.proxyType());
        }
        if (request.proxyHost() != null) {
            account.setProxyHost(request.proxyHost());
        }
        if (request.proxyPort() != null) {
            account.setProxyPort(request.proxyPort());
        }
        if (request.proxyUsername() != null) {
            account.setProxyUsername(request.proxyUsername());
        }
        if (request.proxyPassword() != null) {
            account.setProxyPasswordEncrypted(request.proxyPassword());
        }
        account = accountRepository.save(account);
        return toResponse(account);
    }

    @Transactional
    public AccountResponse reconnect(Long ownerUserId, Long id) {
        TelegramAccountEntity account = accountRepository.findByIdAndOwnerUserId(id, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
        account.setStatus("CONNECTED");
        account.setLastError(null);
        account = accountRepository.save(account);
        return toResponse(account);
    }

    @Transactional
    public void delete(Long ownerUserId, Long id) {
        TelegramAccountEntity account = accountRepository.findByIdAndOwnerUserId(id, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
        if (!account.getOwnerUserId().equals(ownerUserId)) {
            throw new IllegalArgumentException("Account not found: " + id);
        }
        accountRepository.deleteById(account.getId());
    }

    private void applyAuthState(TelegramAccountEntity account, TelegramAuthStateResponse authState) {
        account.setStatus(mapAccountStatus(authState));
        account.setLastError(authState.lastError());
    }

    private String mapAccountStatus(TelegramAuthStateResponse authState) {
        if (authState.ready()) {
            return "CONNECTED";
        }
        if (authState.waitPassword()) {
            return "WAITING_PASSWORD";
        }
        if (authState.waitCode()) {
            return "WAITING_CODE";
        }
        if (authState.waitPhoneNumber()) {
            return "DISCONNECTED";
        }
        if (authState.lastError() != null && !authState.lastError().isBlank()) {
            return "ERROR";
        }
        return "DISCONNECTED";
    }

    private AccountResponse toResponse(TelegramAccountEntity account) {
        AccountResponse.ProxyInfo proxy = null;
        if (account.getProxyType() != null || account.getProxyHost() != null) {
            proxy = new AccountResponse.ProxyInfo(
                    account.getProxyType(),
                    account.getProxyHost(),
                    account.getProxyPort(),
                    account.getProxyUsername(),
                    account.getProxyPasswordEncrypted() != null
            );
        }
        return new AccountResponse(
                account.getId(),
                account.getTelegramUserId(),
                account.getUsername(),
                account.getPhone(),
                account.getFirstName(),
                account.getLastName(),
                account.getStatus(),
                proxy,
                (int) groupRepository.countByAccountIdAndOwnerUserId(account.getId(), account.getOwnerUserId()),
                0L,
                account.getLastSyncAt()
        );
    }
}
