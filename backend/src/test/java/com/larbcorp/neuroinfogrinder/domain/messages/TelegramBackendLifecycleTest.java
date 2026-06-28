package com.larbcorp.neuroinfogrinder.domain.messages;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TelegramAccountEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TelegramAccountRepository;
import com.larbcorp.neuroinfogrinder.telegram.TelegramTdlibService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramBackendLifecycleTest {

    @Test
    void initializeTdlibListenerStartsTdlibForConnectedAccountsOnApplicationReady() {
        TelegramTdlibService telegramTdlibService = mock(TelegramTdlibService.class);
        TelegramAccountRepository telegramAccountRepository = mock(TelegramAccountRepository.class);
        when(telegramAccountRepository.findByStatus("CONNECTED")).thenReturn(List.of(account(1L), account(2L)));
        TelegramBackendLifecycle lifecycle = new TelegramBackendLifecycle(telegramTdlibService, telegramAccountRepository);

        lifecycle.initializeTdlibListener();

        verify(telegramTdlibService).getAuthorizationState(1L);
        verify(telegramTdlibService).getAuthorizationState(2L);
    }

    @Test
    void initializeTdlibListenerSwallowsDeferredStartupFailuresPerAccount() {
        TelegramTdlibService telegramTdlibService = mock(TelegramTdlibService.class);
        TelegramAccountRepository telegramAccountRepository = mock(TelegramAccountRepository.class);
        when(telegramAccountRepository.findByStatus("CONNECTED")).thenReturn(List.of(account(1L), account(2L)));
        doThrow(new RuntimeException("not ready")).when(telegramTdlibService).getAuthorizationState(1L);
        TelegramBackendLifecycle lifecycle = new TelegramBackendLifecycle(telegramTdlibService, telegramAccountRepository);

        lifecycle.initializeTdlibListener();

        verify(telegramTdlibService).getAuthorizationState(1L);
        verify(telegramTdlibService).getAuthorizationState(2L);
    }

    @Test
    void initializeTdlibListenerFallsBackToLegacyWhenNoConnectedAccountsExist() {
        TelegramTdlibService telegramTdlibService = mock(TelegramTdlibService.class);
        TelegramAccountRepository telegramAccountRepository = mock(TelegramAccountRepository.class);
        when(telegramAccountRepository.findByStatus("CONNECTED")).thenReturn(List.of());
        TelegramBackendLifecycle lifecycle = new TelegramBackendLifecycle(telegramTdlibService, telegramAccountRepository);

        lifecycle.initializeTdlibListener();

        verify(telegramTdlibService).getAuthorizationState();
    }

    private TelegramAccountEntity account(Long id) {
        TelegramAccountEntity account = new TelegramAccountEntity();
        account.setId(id);
        account.setPhone("+10000000000");
        account.setStatus("CONNECTED");
        return account;
    }
}
