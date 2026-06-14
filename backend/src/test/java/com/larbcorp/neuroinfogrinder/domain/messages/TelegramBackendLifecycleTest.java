package com.larbcorp.neuroinfogrinder.domain.messages;

import com.larbcorp.neuroinfogrinder.telegram.TelegramTdlibService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TelegramBackendLifecycleTest {

    @Test
    void initializeTdlibListenerStartsTdlibOnApplicationReady() {
        TelegramTdlibService telegramTdlibService = mock(TelegramTdlibService.class);
        TelegramBackendLifecycle lifecycle = new TelegramBackendLifecycle(telegramTdlibService);

        lifecycle.initializeTdlibListener();

        verify(telegramTdlibService).getAuthorizationState();
    }

    @Test
    void initializeTdlibListenerSwallowsDeferredStartupFailures() {
        TelegramTdlibService telegramTdlibService = mock(TelegramTdlibService.class);
        doThrow(new RuntimeException("not ready")).when(telegramTdlibService).getAuthorizationState();
        TelegramBackendLifecycle lifecycle = new TelegramBackendLifecycle(telegramTdlibService);

        lifecycle.initializeTdlibListener();

        verify(telegramTdlibService).getAuthorizationState();
    }
}