package com.larbcorp.neuroinfogrinder.domain.messages;

import com.larbcorp.neuroinfogrinder.telegram.TelegramTdlibService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBackendLifecycle {

    private final TelegramTdlibService telegramTdlibService;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeTdlibListener() {
        try {
            telegramTdlibService.getAuthorizationState();
            log.info("TDLib backend listener initialized");
        } catch (Exception exception) {
            log.warn("TDLib backend listener initialization deferred: {}", exception.getMessage());
        }
    }
}