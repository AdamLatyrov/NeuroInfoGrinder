package com.larbcorp.neuroinfogrinder2.telegram.tdlib;

import org.drinkless.tdlib.TdApi;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class TdlibAuthStateMappingTest {
    @Test
    void mapsTdlibAuthStatesToStage1Statuses() throws Exception {
        Method method = TdlibClientManager2.class.getDeclaredMethod("accountStatus", TdApi.AuthorizationState.class);
        method.setAccessible(true);

        assertThat(method.invoke(nullManager(), new TdApi.AuthorizationStateWaitPhoneNumber())).isEqualTo("WAIT_PHONE");
        assertThat(method.invoke(nullManager(), new TdApi.AuthorizationStateWaitCode())).isEqualTo("WAIT_CODE");
        assertThat(method.invoke(nullManager(), new TdApi.AuthorizationStateWaitPassword())).isEqualTo("WAIT_PASSWORD");
        assertThat(method.invoke(nullManager(), new TdApi.AuthorizationStateReady())).isEqualTo("CONNECTED");
    }

    private TdlibClientManager2 nullManager() {
        return new TdlibClientManager2(null, null, null, null, null);
    }
}
