package com.larbcorp.neuroinfogrinder2.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder2.replay.ModelWorkerClient;
import com.larbcorp.neuroinfogrinder2.replay.ReplayV2Service;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineLiveServiceRunStartTest {
    @Test
    void startLiveAutoRunDelegatesLiveRunToRealReplayProcessor() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ModelWorkerClient worker = mock(ModelWorkerClient.class);
        Environment environment = mock(Environment.class);
        ReplayV2Service replay = mock(ReplayV2Service.class);
        List<String> sqlStatements = new ArrayList<>();
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.ResultSetExtractor.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    sqlStatements.add(sql);
                    ResultSet rs = mock(ResultSet.class);
                    if (sql.contains("pipeline_message_intake")) {
                        when(rs.next()).thenReturn(false);
                        org.springframework.jdbc.core.ResultSetExtractor<?> extractor = invocation.getArgument(1);
                        return extractor.extractData(rs);
                    }
                    when(rs.next()).thenReturn(true, false);
                    when(rs.getLong("id")).thenReturn(2L);
                    when(rs.getLong("dataset_id")).thenReturn(10L);
                    when(rs.getLong("batch_id")).thenReturn(1L);
                    org.springframework.jdbc.core.ResultSetExtractor<?> extractor = invocation.getArgument(1);
                    return extractor.extractData(rs);
                });
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of(439L));
        when(replay.runExistingLiveAutoRun(2L)).thenReturn(new ReplayV2Service.ReplayRun(
                2L, 10L, "live auto", null, "LIVE_AUTO_RAW_MESSAGES", "COMPLETED", null, null, 1, 1, 0, java.math.BigDecimal.ZERO, "NO_MATERIAL_CANDIDATES"
        ));
        when(jdbc.update(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            sqlStatements.add(invocation.getArgument(0));
            return 1;
        });

        PipelineLiveService service = new PipelineLiveService(jdbc, new ObjectMapper(), worker, environment, replay);

        service.startLiveAutoRun(2L);

        verify(replay).runExistingLiveAutoRun(2L);
        assertThat(sqlStatements).noneSatisfy(sql -> assertThat(sql).contains("NO_CLUSTERS"));
        assertThat(sqlStatements).noneSatisfy(sql -> assertThat(sql).contains("stage_id IN ('normalization', 'cleanup', 'dedupe', 'rule_signals', 'bootstrap_classification')"));
        verify(worker, never()).health();
    }

    @Test
    void workerOkWaitingStageWarningDoesNotClaimWorkerArtifacts() throws Exception {
        PipelineLiveService service = new PipelineLiveService(mock(JdbcTemplate.class), new ObjectMapper(), mock(ModelWorkerClient.class), mock(Environment.class), mock(ReplayV2Service.class));
        Method warningForStage = PipelineLiveService.class.getDeclaredMethod("warningForStage", String.class, long.class, long.class, long.class, long.class, boolean.class, String.class);
        warningForStage.setAccessible(true);

        String warning = (String) warningForStage.invoke(service, "embeddings", 0L, 1445L, 0L, 0L, true, "OK");

        assertThat(warning).doesNotContain("worker/replay artifacts");
        assertThat(warning).doesNotContain("Worker недоступен");
    }

    @Test
    void publicDiscoverySourceIsRejectedBeforeChatLookup() {
        PipelineLiveService service = new PipelineLiveService(mock(JdbcTemplate.class), new ObjectMapper(), mock(ModelWorkerClient.class), mock(Environment.class), mock(ReplayV2Service.class));

        String reason = service.processingRejection(1L, 100500L, null, "PUBLIC_SEARCH");

        assertThat(reason).isEqualTo("PUBLIC_DISCOVERY_NOT_ALLOWED");
    }

    @Test
    void inactiveDialogIsRejectedEvenWhenChatHasRawHistory() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.ResultSetExtractor.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.next()).thenReturn(true, false);
                    when(rs.getBoolean(1)).thenReturn(false);
                    org.springframework.jdbc.core.ResultSetExtractor<?> extractor = invocation.getArgument(1);
                    return extractor.extractData(rs);
                });
        PipelineLiveService service = new PipelineLiveService(jdbc, new ObjectMapper(), mock(ModelWorkerClient.class), mock(Environment.class), mock(ReplayV2Service.class));

        String reason = service.processingRejection(1L, 100500L, null, "TELEGRAM_LIVE");

        assertThat(reason).isEqualTo("CHAT_NOT_ACTIVE_MEMBER");
    }

    @Test
    void explicitEnabledActiveDialogCanProcess() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        List<Boolean> answers = new ArrayList<>(List.of(true, true));
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.ResultSetExtractor.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.next()).thenReturn(true, false);
                    when(rs.getBoolean(1)).thenReturn(answers.remove(0));
                    org.springframework.jdbc.core.ResultSetExtractor<?> extractor = invocation.getArgument(1);
                    return extractor.extractData(rs);
                });
        PipelineLiveService service = new PipelineLiveService(jdbc, new ObjectMapper(), mock(ModelWorkerClient.class), mock(Environment.class), mock(ReplayV2Service.class));

        assertThat(service.processingRejection(1L, 100500L, null, "TELEGRAM_LIVE")).isNull();
    }

    @Test
    void enabledActiveDialogWithoutExplicitAutoSettingCanProcess() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.ResultSetExtractor.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.next()).thenReturn(true, false);
                    when(rs.getBoolean(1)).thenReturn(true);
                    org.springframework.jdbc.core.ResultSetExtractor<?> extractor = invocation.getArgument(1);
                    return extractor.extractData(rs);
                });
        PipelineLiveService service = new PipelineLiveService(jdbc, new ObjectMapper(), mock(ModelWorkerClient.class), mock(Environment.class), mock(ReplayV2Service.class));

        assertThat(service.processingRejection(1L, 100500L, null, "TELEGRAM_LIVE")).isNull();
    }

    @Test
    void backfillFromRawWithoutTopicDoesNotUseUntypedNullTopicParameter() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        List<String> sqlStatements = new ArrayList<>();
        List<Integer> argumentCounts = new ArrayList<>();
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    sqlStatements.add(invocation.getArgument(0));
                    argumentCounts.add(invocation.getArguments().length - 2);
                    return List.of();
                });
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.ResultSetExtractor.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.next()).thenReturn(false);
                    org.springframework.jdbc.core.ResultSetExtractor<?> extractor = invocation.getArgument(1);
                    return extractor.extractData(rs);
                });
        PipelineLiveService service = new PipelineLiveService(jdbc, new ObjectMapper(), mock(ModelWorkerClient.class), mock(Environment.class), mock(ReplayV2Service.class));

        service.backfillFromRaw(new PipelineLiveService.IntakeBackfillRequest(1L, 100500L, null, 50, true));

        assertThat(sqlStatements).anySatisfy(sql -> assertThat(sql).doesNotContain("? IS NULL"));
        assertThat(argumentCounts).contains(3);
    }
}
