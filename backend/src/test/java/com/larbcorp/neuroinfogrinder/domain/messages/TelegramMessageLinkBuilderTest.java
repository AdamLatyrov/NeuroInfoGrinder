package com.larbcorp.neuroinfogrinder.domain.messages;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramMessageLinkBuilderTest {

    @Test
    void buildsPublicUsernameLink() {
        GroupEntity group = new GroupEntity();
        group.setTelegramChatId(-1001234567890L);
        group.setUsername("testchannel");

        MessageEntity message = new MessageEntity();
        message.setTelegramMessageId(123L);

        assertThat(TelegramMessageLinkBuilder.build(group, message))
                .isEqualTo("https://t.me/testchannel/123");
    }

    @Test
    void buildsPrivateSupergroupLink() {
        GroupEntity group = new GroupEntity();
        group.setTelegramChatId(-1001234567890L);

        MessageEntity message = new MessageEntity();
        message.setTelegramMessageId(456L);

        assertThat(TelegramMessageLinkBuilder.build(group, message))
                .isEqualTo("https://t.me/c/1234567890/456");
    }

    @Test
    void doesNotBuildDirectChatLink() {
        GroupEntity group = new GroupEntity();
        group.setTelegramChatId(5701740052L);

        MessageEntity message = new MessageEntity();
        message.setTelegramMessageId(789L);

        assertThat(TelegramMessageLinkBuilder.build(group, message)).isNull();
        TelegramMessageLink link = TelegramMessageLinkBuilder.buildLink(group, message);
        assertThat(link.available()).isFalse();
        assertThat(link.reason()).isNotBlank();
    }
}
