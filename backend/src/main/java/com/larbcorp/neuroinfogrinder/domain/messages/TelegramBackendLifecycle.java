package com.larbcorp.neuroinfogrinder.domain.messages;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TelegramAccountEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TelegramAccountRepository;
import com.larbcorp.neuroinfogrinder.telegram.TelegramTdlibService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBackendLifecycle {

    private static final String CONNECTED_STATUS = "CONNECTED";

    private final TelegramTdlibService telegramTdlibService;
    private final TelegramAccountRepository telegramAccountRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeTdlibListener() {
        List<TelegramAccountEntity> connectedAccounts;
        try {
            connectedAccounts = telegramAccountRepository.findByStatus(CONNECTED_STATUS);
        } catch (Exception exception) {
            log.warn("TDLib connected account lookup failed, falling back to legacy listener warmup: {}", exception.getMessage());
            warmLegacyListener();
            return;
        }

        if (connectedAccounts.isEmpty()) {
            warmLegacyListener();
            return;
        }

        int initialized = 0;
        for (TelegramAccountEntity account : connectedAccounts) {
            Long accountId = account.getId();
            if (accountId == null) {
                continue;
            }
            try {
                telegramTdlibService.getAuthorizationState(accountId);
                initialized++;
            } catch (Exception exception) {
                log.warn("TDLib backend listener initialization deferred for account {}: {}", accountId, exception.getMessage());
            }
        }
        log.info(
                "TDLib backend listener warmup completed for {} of {} connected account(s)",
                initialized,
                connectedAccounts.size()
        );
    }

    private void warmLegacyListener() {
        try {
            telegramTdlibService.getAuthorizationState();
            log.info("TDLib backend legacy listener initialized");
        } catch (Exception exception) {
            log.warn("TDLib backend legacy listener initialization deferred: {}", exception.getMessage());
        }
    }
}
