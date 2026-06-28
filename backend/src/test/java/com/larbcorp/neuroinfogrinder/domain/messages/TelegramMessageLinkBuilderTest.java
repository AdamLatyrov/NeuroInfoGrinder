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
        group.setForum(false);

        MessageEntity message = new MessageEntity();
        message.setTelegramMessageId(123L);

        assertThat(TelegramMessageLinkBuilder.build(group, message))
                .isEqualTo("https://t.me/testchannel/123");
    }

    @Test
    void buildsPublicForumTopicLink() {
        GroupEntity group = new GroupEntity();
        group.setTelegramChatId(-1003854867646L);
        group.setUsername("vibedev_m");
        group.setForum(true);

        MessageEntity message = new MessageEntity();
        message.setTopicId(5L);
        message.setTelegramMessageId(33136L);

        assertThat(TelegramMessageLinkBuilder.build(group, message))
                .isEqualTo("https://t.me/vibedev_m/5/33136");
    }

    @Test
    void buildsPrivateSupergroupLink() {
        GroupEntity group = new GroupEntity();
        group.setTelegramChatId(-1001234567890L);
        group.setForum(false);

        MessageEntity message = new MessageEntity();
        message.setTelegramMessageId(456L);

        assertThat(TelegramMessageLinkBuilder.build(group, message))
                .isEqualTo("https://t.me/c/1234567890/456");
    }

    @Test
    void buildsPrivateForumTopicLink() {
        GroupEntity group = new GroupEntity();
        group.setTelegramChatId(-1003854867646L);
        group.setForum(true);

        MessageEntity message = new MessageEntity();
        message.setTopicId(5L);
        message.setTelegramMessageId(33136L);

        assertThat(TelegramMessageLinkBuilder.build(group, message))
                .isEqualTo("https://t.me/c/3854867646/5/33136");
    }

    @Test
    void buildsPrivateForumTopicLinkWhenInternalChatIdIsAlreadyNormalized() {
        GroupEntity group = new GroupEntity();
        group.setTelegramChatId(3854867646L);
        group.setForum(true);

        MessageEntity message = new MessageEntity();
        message.setTopicId(5L);
        message.setTelegramMessageId(33136L);

        assertThat(TelegramMessageLinkBuilder.build(group, message))
                .isEqualTo("https://t.me/c/3854867646/5/33136");
    }

    @Test
    void omitsTopicSegmentWhenForumTopicIdIsMissing() {
        GroupEntity group = new GroupEntity();
        group.setTelegramChatId(-1003854867646L);
        group.setForum(true);

        MessageEntity message = new MessageEntity();
        message.setTelegramMessageId(33136L);

        TelegramMessageLink link = TelegramMessageLinkBuilder.buildLink(group, message);

        assertThat(link.available()).isTrue();
        assertThat(link.url()).isEqualTo("https://t.me/c/3854867646/33136");
        assertThat(link.url()).doesNotContain("/null/");
    }

    @Test
    void doesNotUseCompositeMessageIdAsFinalSegment() {
        GroupEntity group = new GroupEntity();
        group.setTelegramChatId(-1003854867646L);
        group.setForum(true);

        MessageEntity message = new MessageEntity();
        message.setTopicId(5L);
        message.setTelegramMessageId(33136L);

        assertThat(TelegramMessageLinkBuilder.build(group, message))
                .doesNotContain("34744565760");
    }
}
