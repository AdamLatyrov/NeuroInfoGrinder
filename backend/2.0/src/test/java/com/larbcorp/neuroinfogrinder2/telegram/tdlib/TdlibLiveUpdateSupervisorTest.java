package com.larbcorp.neuroinfogrinder2.telegram.tdlib;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TdlibLiveUpdateSupervisorTest {
    @Test
    void maintainLiveUpdatesStartsOnlyOperationalAccounts() {
        RecordingGateway gateway = new RecordingGateway(List.of(
                new TdlibLiveUpdateSupervisor.TelegramAccountState(1L, "CONNECTED"),
                new TdlibLiveUpdateSupervisor.TelegramAccountState(2L, "READY"),
                new TdlibLiveUpdateSupervisor.TelegramAccountState(3L, "DISABLED"),
                new TdlibLiveUpdateSupervisor.TelegramAccountState(4L, "AUTH_REQUIRED"),
                new TdlibLiveUpdateSupervisor.TelegramAccountState(5L, "WAIT_CODE")
        ));
        TdlibLiveUpdateSupervisor supervisor = new TdlibLiveUpdateSupervisor(gateway);

        supervisor.maintainLiveUpdates();

        assertEquals(List.of(1L, 2L), gateway.startedAccountIds);
    }

    @Test
    void maintainLiveUpdatesContinuesWhenOneAccountFails() {
        RecordingGateway gateway = new RecordingGateway(List.of(
                new TdlibLiveUpdateSupervisor.TelegramAccountState(1L, "CONNECTED"),
                new TdlibLiveUpdateSupervisor.TelegramAccountState(2L, "READY")
        ));
        gateway.failingAccountId = 1L;
        TdlibLiveUpdateSupervisor supervisor = new TdlibLiveUpdateSupervisor(gateway);

        supervisor.maintainLiveUpdates();

        assertTrue(gateway.startedAccountIds.contains(2L));
    }

    private static final class RecordingGateway implements TdlibLiveUpdateSupervisor.Gateway {
        private final List<TdlibLiveUpdateSupervisor.TelegramAccountState> accounts;
        private final List<Long> startedAccountIds = new ArrayList<>();
        private Long failingAccountId;

        private RecordingGateway(List<TdlibLiveUpdateSupervisor.TelegramAccountState> accounts) {
            this.accounts = accounts;
        }

        @Override
        public List<TdlibLiveUpdateSupervisor.TelegramAccountState> operationalAccountStates() {
            return accounts;
        }

        @Override
        public void ensureLiveUpdatesStarted(long accountId) {
            startedAccountIds.add(accountId);
            if (Long.valueOf(accountId).equals(failingAccountId)) {
                throw new IllegalStateException("boom");
            }
        }
    }
}
