package com.larbcorp.neuroinfogrinder2.telegram.tdlib;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class JdbcTdlibLiveUpdateGateway implements TdlibLiveUpdateSupervisor.Gateway {
    private final JdbcTemplate jdbc;
    private final TdlibClientManager2 tdlibClientManager;

    JdbcTdlibLiveUpdateGateway(JdbcTemplate jdbc, TdlibClientManager2 tdlibClientManager) {
        this.jdbc = jdbc;
        this.tdlibClientManager = tdlibClientManager;
    }

    @Override
    public List<TdlibLiveUpdateSupervisor.TelegramAccountState> operationalAccountStates() {
        return jdbc.query("""
                SELECT id, status
                FROM telegram_accounts
                WHERE status IN ('CONNECTED', 'READY')
                ORDER BY id
                """, (rs, rowNum) -> new TdlibLiveUpdateSupervisor.TelegramAccountState(
                rs.getLong("id"),
                rs.getString("status")
        ));
    }

    @Override
    public void ensureLiveUpdatesStarted(long accountId) {
        tdlibClientManager.ensureLiveUpdatesStarted(accountId);
    }
}
