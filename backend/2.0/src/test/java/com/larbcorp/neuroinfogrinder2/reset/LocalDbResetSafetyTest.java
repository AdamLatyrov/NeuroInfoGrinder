package com.larbcorp.neuroinfogrinder2.reset;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalDbResetSafetyTest {
    @Test
    void deniesProductionProfileEvenForLocalLookingDatabase() {
        LocalDbResetSafety safety = new LocalDbResetSafety();

        var decision = safety.evaluate("jdbc:postgresql://localhost:5432/neuroinfogrinder2_dev", "production");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("profile");
    }

    @Test
    void deniesRemoteDatabaseHost() {
        LocalDbResetSafety safety = new LocalDbResetSafety();

        var decision = safety.evaluate("jdbc:postgresql://185.130.212.188:5432/neuroinfogrinder2_dev", "local");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("host");
    }
}
