package com.larbcorp.neuroinfogrinder2.telegram.tdlib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TdlibLiveUpdateSupervisor {
    private static final Logger log = LoggerFactory.getLogger(TdlibLiveUpdateSupervisor.class);

    private final Gateway gateway;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public TdlibLiveUpdateSupervisor(Gateway gateway) {
        this.gateway = gateway;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        maintainLiveUpdates();
    }

    @Scheduled(fixedDelay = 60000)
    public void maintainLiveUpdates() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            for (TelegramAccountState account : gateway.operationalAccountStates()) {
                if (!isOperational(account.status())) {
                    continue;
                }
                try {
                    gateway.ensureLiveUpdatesStarted(account.accountId());
                } catch (RuntimeException error) {
                    log.warn("TDLib live update startup failed accountId={}", account.accountId(), error);
                }
            }
        } finally {
            running.set(false);
        }
    }

    private boolean isOperational(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.toUpperCase(Locale.ROOT);
        return "CONNECTED".equals(normalized) || "READY".equals(normalized);
    }

    interface Gateway {
        List<TelegramAccountState> operationalAccountStates();

        void ensureLiveUpdatesStarted(long accountId);
    }

    record TelegramAccountState(long accountId, String status) {
    }
}
