package com.larbcorp.neuroinfogrinder.telegram.tdlib;

import com.larbcorp.neuroinfogrinder.config.TdlibProperties;
import org.drinkless.tdlib.TdApi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TdlibClientManagerProxyTest {

    @Test
    void buildsSocks5ProxyByDefault() {
        TdlibProperties.Proxy proxy = new TdlibProperties.Proxy();
        proxy.setUsername("user");
        proxy.setPassword("pass");

        TdApi.ProxyTypeSocks5 type = (TdApi.ProxyTypeSocks5) TdlibClientManager.buildProxyType(proxy);

        assertThat(type.username).isEqualTo("user");
        assertThat(type.password).isEqualTo("pass");
    }

    @Test
    void buildsHttpProxy() {
        TdlibProperties.Proxy proxy = new TdlibProperties.Proxy();
        proxy.setType("http");

        TdApi.ProxyTypeHttp type = (TdApi.ProxyTypeHttp) TdlibClientManager.buildProxyType(proxy);

        assertThat(type.httpOnly).isFalse();
    }

    @Test
    void requiresMtprotoSecret() {
        TdlibProperties.Proxy proxy = new TdlibProperties.Proxy();
        proxy.setType("mtproto");

        assertThatThrownBy(() -> TdlibClientManager.buildProxyType(proxy))
                .isInstanceOf(TdlibException.class)
                .hasMessageContaining("MTProto proxy secret");
    }
}
