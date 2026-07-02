package com.larbcorp.neuroinfogrinder2.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoPipelineServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void onRawMessageStoredCreatesRunWithTypedMetadataJsonWhenTopicIdIsNull() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PipelineLiveService pipelineLiveService = mock(PipelineLiveService.class);
        List<String> queryForObjectSql = new ArrayList<>();
        List<Object[]> queryForObjectArgs = new ArrayList<>();

        when(jdbc.query(anyString(), any(ResultSetExtractor.class), any(Object[].class))).thenAnswer(invocation -> null);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("account_id")).thenReturn(1L);
            when(rs.getLong("telegram_chat_id")).thenReturn(100500L);
            when(rs.getLong("topic_id")).thenReturn(0L);
            when(rs.wasNull()).thenReturn(false, true);
            when(rs.getBoolean("enabled")).thenReturn(true);
            when(rs.getInt("debounce_seconds")).thenReturn(30);
            when(rs.getInt("batch_size")).thenReturn(1);
            when(rs.getInt("max_provider_calls")).thenReturn(5);
            when(rs.getBigDecimal("max_cost_usd")).thenReturn(new BigDecimal("0.25"));
            return List.of(mapper.mapRow(rs, 0));
        });
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            queryForObjectSql.add(sql);
            queryForObjectArgs.add(invocation.getArguments());
            if (sql.contains("INSERT INTO auto_pipeline_batches")) {
                return 77L;
            }
            if (sql.contains("INSERT INTO datasets")) {
                return 88L;
            }
            if (sql.contains("INSERT INTO replay_runs")) {
                return 99L;
            }
            return null;
        });
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);

        AutoPipelineService service = new AutoPipelineService(jdbc, pipelineLiveService, objectMapper);
        when(pipelineLiveService.canProcessChat(1L, 100500L, null, "TELEGRAM_LIVE")).thenReturn(true);

        service.onRawMessageStored(123L, 1L, 100500L, null);

        int datasetInsertIndex = queryForObjectSql.indexOf(queryForObjectSql.stream()
                .filter(sql -> sql.contains("INSERT INTO datasets"))
                .findFirst()
                .orElseThrow());
        Object[] datasetArgs = queryForObjectArgs.get(datasetInsertIndex);
        assertThat(queryForObjectSql.get(datasetInsertIndex)).contains("?::jsonb").doesNotContain("jsonb_build_object");
        JsonNode metadata = objectMapper.readTree((String) datasetArgs[4]);
        assertThat(metadata.get("batchId").asLong()).isEqualTo(77L);
        assertThat(metadata.get("accountId").asLong()).isEqualTo(1L);
        assertThat(metadata.get("telegramChatId").asLong()).isEqualTo(100500L);
        assertThat(metadata.get("topicId").isNull()).isTrue();
        assertThat(metadata.get("reason").asText()).isEqualTo("BATCH_SIZE_REACHED");
    }

    @Test
    void metadataJsonKeepsTopicIdWhenTopicScopeIsUsed() throws Exception {
        AutoPipelineService service = new AutoPipelineService(mock(JdbcTemplate.class), mock(PipelineLiveService.class), objectMapper);

        JsonNode metadata = objectMapper.readTree(service.metadataJson(10L, 1L, 100500L, 56463L, "DEBOUNCE_EXPIRED"));

        assertThat(metadata.get("topicId").asLong()).isEqualTo(56463L);
        assertThat(metadata.get("reason").asText()).isEqualTo("DEBOUNCE_EXPIRED");
    }

    @Test
    void onRawMessageStoredQueuesEnabledActiveChatWithoutExplicitAutoSetting() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PipelineLiveService pipelineLiveService = mock(PipelineLiveService.class);
        List<String> updates = new ArrayList<>();

        when(jdbc.query(anyString(), any(ResultSetExtractor.class), any(Object[].class))).thenAnswer(invocation -> null);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("INSERT INTO auto_pipeline_batches")) {
                return 77L;
            }
            return null;
        });
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.update(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            updates.add(invocation.getArgument(0));
            return 1;
        });
        when(pipelineLiveService.canProcessChat(1L, 100500L, null, "TELEGRAM_LIVE")).thenReturn(true);

        AutoPipelineService service = new AutoPipelineService(jdbc, pipelineLiveService, objectMapper);
        service.onRawMessageStored(123L, 1L, 100500L, null);

        assertThat(updates).anySatisfy(sql -> assertThat(sql).contains("INSERT INTO auto_pipeline_queue"));
        verify(pipelineLiveService, times(1)).canProcessChat(1L, 100500L, null, "TELEGRAM_LIVE");
    }

    @Test
    void onRawMessageStoredUsesChatLevelBatchScopeForTopicMessage() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PipelineLiveService pipelineLiveService = mock(PipelineLiveService.class);
        List<Object[]> batchInsertArgs = new ArrayList<>();

        when(jdbc.query(anyString(), any(ResultSetExtractor.class), any(Object[].class))).thenAnswer(invocation -> null);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("account_id")).thenReturn(1L);
            when(rs.getLong("telegram_chat_id")).thenReturn(100500L);
            when(rs.getLong("topic_id")).thenReturn(0L);
            when(rs.wasNull()).thenReturn(false, true);
            when(rs.getBoolean("enabled")).thenReturn(true);
            when(rs.getInt("debounce_seconds")).thenReturn(30);
            when(rs.getInt("batch_size")).thenReturn(10);
            when(rs.getInt("max_provider_calls")).thenReturn(5);
            when(rs.getBigDecimal("max_cost_usd")).thenReturn(new BigDecimal("0.25"));
            return List.of(mapper.mapRow(rs, 0));
        });
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("INSERT INTO auto_pipeline_batches")) {
                batchInsertArgs.add(Arrays.copyOfRange(invocation.getArguments(), 2, invocation.getArguments().length));
                return 77L;
            }
            return null;
        });
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(pipelineLiveService.canProcessChat(1L, 100500L, 56463L, "TELEGRAM_LIVE")).thenReturn(true);

        AutoPipelineService service = new AutoPipelineService(jdbc, pipelineLiveService, objectMapper);
        service.onRawMessageStored(123L, 1L, 100500L, 56463L);

        Object[] insertedBatchArgs = batchInsertArgs.stream()
                .filter(args -> args.length >= 4 && Long.valueOf(1L).equals(args[0]) && Long.valueOf(100500L).equals(args[1]))
                .findFirst()
                .orElseThrow();
        assertThat(insertedBatchArgs[2]).isNull();
    }

    @Test
    void startPendingLiveAutoRunsStartsCreatedRuns() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PipelineLiveService pipelineLiveService = mock(PipelineLiveService.class);
        when(jdbc.queryForList(anyString(), eq(Long.class))).thenReturn(List.of(10L));
        AutoPipelineService service = new AutoPipelineService(jdbc, pipelineLiveService, objectMapper);

        service.startPendingLiveAutoRuns();

        verify(pipelineLiveService).startLiveAutoRun(10L);
    }

    @Test
    void flushReadyBatchesUsesEffectiveChatLevelSettingForTopicBatches() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        List<String> sql = new ArrayList<>();
        when(jdbc.queryForList(anyString(), eq(Long.class))).thenAnswer(invocation -> {
            sql.add(invocation.getArgument(0));
            return List.of();
        });
        AutoPipelineService service = new AutoPipelineService(jdbc, mock(PipelineLiveService.class), objectMapper);

        service.flushReadyBatches();

        assertThat(sql).hasSize(1);
        assertThat(sql.get(0)).contains("s.topic_id IS NULL");
        assertThat(sql.get(0)).contains("s.topic_id IS NOT DISTINCT FROM b.topic_id");
        assertThat(sql.get(0)).doesNotContain("COALESCE(s.topic_id, -1) = COALESCE(b.topic_id, -1)");
    }

    @Test
    void flushReadyBatchesDoesNotCapDebounceToFiveSeconds() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        List<String> sql = new ArrayList<>();
        when(jdbc.queryForList(anyString(), eq(Long.class))).thenAnswer(invocation -> {
            sql.add(invocation.getArgument(0));
            return List.of();
        });
        AutoPipelineService service = new AutoPipelineService(jdbc, mock(PipelineLiveService.class), objectMapper);

        service.flushReadyBatches();

        assertThat(sql).hasSize(1);
        assertThat(sql.get(0)).contains("COALESCE(s.debounce_seconds, 30)");
        assertThat(sql.get(0)).doesNotContain("LEAST(COALESCE(s.debounce_seconds, 30), 5)");
    }
}
