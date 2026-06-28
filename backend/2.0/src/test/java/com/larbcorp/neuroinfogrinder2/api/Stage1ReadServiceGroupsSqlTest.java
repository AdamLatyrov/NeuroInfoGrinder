package com.larbcorp.neuroinfogrinder2.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder2.telegram.model.TelegramTopicDto;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Stage1ReadServiceGroupsSqlTest {
    @Test
    void groupsWithMultipleFiltersSeparatesParametersAndOrderBy() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        List<String> sqlStatements = new ArrayList<>();
        when(jdbc.queryForObject(any(String.class), eq(Long.class), any(Object[].class))).thenAnswer(invocation -> {
            sqlStatements.add(invocation.getArgument(0));
            return 0L;
        });
        when(jdbc.query(any(String.class), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            sqlStatements.add(invocation.getArgument(0));
            return List.of();
        });
        Stage1ReadService service = new Stage1ReadService(jdbc, new ObjectMapper());

        service.groups(0, 20, true, "dev", 2L);

        assertThat(sqlStatements).anySatisfy(sql -> {
            assertThat(sql).contains("lower(c.title) LIKE ? ORDER BY");
            assertThat(sql).doesNotContain("?ORDER BY");
        });
    }

    @Test
    void messagesWithTopicFilterSeparatesParametersAndOrderBy() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        List<String> sqlStatements = new ArrayList<>();
        when(jdbc.queryForObject(any(String.class), any(RowMapper.class), any(Object[].class))).thenReturn(new Stage1ReadService.GroupDto(
                10L,
                20L,
                "Chat",
                null,
                "GROUP",
                null,
                false,
                true,
                1L,
                0L,
                0L,
                null,
                null,
                0L,
                0L,
                null,
                0L,
                false,
                false,
                "KNOWN_EMPTY",
                "DISPLAY_ONLY_NOT_ACTIVE",
                "DB_CACHE"
        ));
        when(jdbc.queryForObject(any(String.class), eq(Long.class), any(Object[].class))).thenAnswer(invocation -> {
            sqlStatements.add(invocation.getArgument(0));
            return 0L;
        });
        when(jdbc.query(any(String.class), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            sqlStatements.add(invocation.getArgument(0));
            return List.of();
        });
        Stage1ReadService service = new Stage1ReadService(jdbc, new ObjectMapper());

        service.messages(10L, 0, 20, 30L);

        assertThat(sqlStatements).anySatisfy(sql -> {
            assertThat(sql).contains("m.telegram_topic_id = ? ORDER BY");
            assertThat(sql).doesNotContain("?ORDER BY");
        });
    }

    @Test
    void topicsWithAccountFilterSeparatesParametersAndOrderBy() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        List<String> sqlStatements = new ArrayList<>();
        when(jdbc.query(any(String.class), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            sqlStatements.add(invocation.getArgument(0));
            return List.of();
        });
        Stage1ReadService service = new Stage1ReadService(jdbc, new ObjectMapper());

        service.topicsByTelegramChatId(1L, -100L);

        assertThat(sqlStatements).anySatisfy(sql -> {
            assertThat(sql).contains("account_id = ? ORDER BY");
            assertThat(sql).doesNotContain("?ORDER BY");
        });
    }

    @Test
    void deleteAccountRemovesAccountScopedRuntimeRowsBeforeSoftDeletingSession() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        List<String> sqlStatements = new ArrayList<>();
        when(jdbc.update(any(String.class), any(Object[].class))).thenAnswer(invocation -> {
            sqlStatements.add(invocation.getArgument(0));
            return 1;
        });
        Stage1ReadService service = new Stage1ReadService(jdbc, new ObjectMapper());

        service.deleteAccount(3L);

        assertThat(sqlStatements).hasSizeGreaterThan(1);
        assertThat(sqlStatements.get(0)).contains("DELETE FROM telegram_runtime_errors");
        assertThat(sqlStatements.get(sqlStatements.size() - 1)).contains("UPDATE telegram_accounts");
        assertThat(sqlStatements.get(sqlStatements.size() - 1)).contains("deleted_at");
        assertThat(sqlStatements.get(sqlStatements.size() - 1)).doesNotContain("DELETE FROM telegram_accounts");
        verify(jdbc, times(sqlStatements.size())).update(any(String.class), any(Object[].class));
    }

    @Test
    void updateAccountOperationalStatusUpdatesStatusHealthAndRuntimeEvent() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        List<String> sqlStatements = new ArrayList<>();
        when(jdbc.update(any(String.class), any(Object[].class))).thenAnswer(invocation -> {
            sqlStatements.add(invocation.getArgument(0));
            return 1;
        });
        when(jdbc.queryForObject(any(String.class), any(RowMapper.class), any(Object[].class))).thenReturn(new Stage1ReadService.AccountDto(
                3L,
                "***00",
                null,
                null,
                "Telegram account",
                null,
                "PAUSED",
                null,
                0L,
                0L,
                null
        ));
        Stage1ReadService service = new Stage1ReadService(jdbc, new ObjectMapper());

        service.updateAccountOperationalStatus(3L, "PAUSED", "TELEGRAM_ACCOUNT_PAUSED");

        assertThat(sqlStatements).anySatisfy(sql -> assertThat(sql).contains("UPDATE telegram_accounts"));
        assertThat(sqlStatements).anySatisfy(sql -> assertThat(sql).contains("INSERT INTO telegram_account_health"));
        assertThat(sqlStatements).anySatisfy(sql -> assertThat(sql).contains("INSERT INTO telegram_runtime_events"));
    }

    @Test
    void upsertTelegramTopicUpdatesRawMessageTopicTitlesForForumAndThreadIds() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        List<String> sqlStatements = new ArrayList<>();
        when(jdbc.update(any(String.class), any(Object[].class))).thenAnswer(invocation -> {
            sqlStatements.add(invocation.getArgument(0));
            return 1;
        });
        Stage1ReadService service = new Stage1ReadService(jdbc, new ObjectMapper());

        service.upsertTelegramTopic(1L, new TelegramTopicDto(-100L, 179L, 179L, "Codex", false));

        assertThat(sqlStatements).anySatisfy(sql -> assertThat(sql).contains("UPDATE raw_messages"));
        assertThat(sqlStatements).anySatisfy(sql -> {
            assertThat(sql).contains("topic_title");
            assertThat(sql).contains("forum_topic_id");
            assertThat(sql).contains("message_thread_id");
        });
    }

    @Test
    void reconcileTelegramTopicsLooksForPlaceholderTitlesAndUpdatesRawMessages() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        List<String> sqlStatements = new ArrayList<>();
        when(jdbc.queryForObject(any(String.class), eq(Long.class), any(Object[].class))).thenAnswer(invocation -> {
            sqlStatements.add(invocation.getArgument(0));
            return 0L;
        });
        when(jdbc.query(any(String.class), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            sqlStatements.add(invocation.getArgument(0));
            return List.of();
        });
        when(jdbc.update(any(String.class), any(Object[].class))).thenAnswer(invocation -> {
            sqlStatements.add(invocation.getArgument(0));
            return 0;
        });
        Stage1ReadService service = new Stage1ReadService(jdbc, new ObjectMapper());

        service.reconcileTelegramTopics(1L, -100L);

        assertThat(sqlStatements).anySatisfy(sql -> assertThat(sql).contains("title = 'Тема без названия'").contains("title LIKE 'Topic %'"));
        assertThat(sqlStatements).anySatisfy(sql -> assertThat(sql).contains("UPDATE raw_messages"));
    }
}
