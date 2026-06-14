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
    public List<AccountResponse> getAll() {
        return accountRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AccountResponse getById(Long id) {
        TelegramAccountEntity account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
        return toResponse(account);
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        TelegramAccountEntity account = accountRepository.findByPhone(request.phone())
                .orElseGet(TelegramAccountEntity::new);
        account.setPhone(request.phone());
        TelegramAuthStateResponse authState = telegramTdlibService.submitPhoneNumber(request.phone());
        applyAuthState(account, authState);
        account = accountRepository.save(account);
        return toResponse(account);
    }

    @Transactional
    public AccountResponse submitCode(Long id, SubmitCodeRequest request) {
        TelegramAccountEntity account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
        TelegramAuthStateResponse authState = telegramTdlibService.submitCode(request.code());
        applyAuthState(account, authState);
        account = accountRepository.save(account);
        return toResponse(account);
    }

    @Transactional
    public AccountResponse submitPassword(Long id, SubmitPasswordRequest request) {
        TelegramAccountEntity account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
        TelegramAuthStateResponse authState = telegramTdlibService.submitPassword(request.password());
        applyAuthState(account, authState);
        account = accountRepository.save(account);
        return toResponse(account);
    }

    @Transactional
    public AccountResponse updateProxy(Long id, UpdateProxyRequest request) {
        TelegramAccountEntity account = accountRepository.findById(id)
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
    public AccountResponse reconnect(Long id) {
        TelegramAccountEntity account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
        account.setStatus("CONNECTED");
        account.setLastError(null);
        account = accountRepository.save(account);
        return toResponse(account);
    }

    @Transactional
    public void delete(Long id) {
        if (!accountRepository.existsById(id)) {
            throw new IllegalArgumentException("Account not found: " + id);
        }
        accountRepository.deleteById(id);
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
                (int) groupRepository.countByAccountId(account.getId()),
                0L,
                account.getLastSyncAt()
        );
    }
}
