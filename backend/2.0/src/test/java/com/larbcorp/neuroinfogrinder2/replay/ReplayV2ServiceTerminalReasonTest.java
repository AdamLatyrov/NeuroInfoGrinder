package com.larbcorp.neuroinfogrinder2.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder2.settings.PipelineSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReplayV2ServiceTerminalReasonTest {
    @Test
    void terminalReasonWithNoRunMessagesReturnsNoRunMessages() throws Exception {
        ReplayV2Service service = serviceWithCount(0L);

        String reason = terminalReason(service, List.of(), List.of(), List.of(), 7L);

        assertThat(reason).isEqualTo("NO_RUN_MESSAGES");
    }

    @Test
    void terminalReasonWithNoEmbeddingsReturnsNoEmbeddingArtifacts() throws Exception {
        ReplayV2Service service = serviceWithCounts(3L, 0L);

        String reason = terminalReason(service, List.of(), List.of(), List.of(), 7L);

        assertThat(reason).isEqualTo("NO_EMBEDDING_ARTIFACTS");
    }

    @Test
    void terminalReasonWithNoEligibleEmbeddingInputReturnsNoMaterialCandidates() throws Exception {
        ReplayV2Service service = serviceWithCounts(3L, 1L);

        String reason = terminalReason(service, List.of(), List.of(), List.of(), 7L);

        assertThat(reason).isEqualTo("NO_MATERIAL_CANDIDATES");
    }

    @Test
    void terminalReasonWithNoClustersOrSingleCandidatesReturnsNoMaterialCandidates() throws Exception {
        ReplayV2Service service = serviceWithCount(3L);
        Object embedding = embedding(1L, 11L);

        String reason = terminalReason(service, List.of(embedding), List.of(), List.of(), 7L);

        assertThat(reason).isEqualTo("NO_MATERIAL_CANDIDATES");
    }

    private static ReplayV2Service serviceWithCount(long count) {
        return serviceWithCounts(count, 0L);
    }

    private static ReplayV2Service serviceWithCounts(long first, long second) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(Class.class), any(Object[].class))).thenReturn(first, second);
        Environment environment = mock(Environment.class);
        when(environment.getProperty("MODELHUB_API_KEY")).thenReturn(null);
        return new ReplayV2Service(jdbc, new ObjectMapper(), mock(ModelWorkerClient.class), mock(ModelhubProviderGateway.class), environment, mock(PipelineSettingsService.class));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String terminalReason(ReplayV2Service service, List embeddings, List clusters, List singleCandidates, long runId) throws Exception {
        Method method = ReplayV2Service.class.getDeclaredMethod("terminalReason", List.class, List.class, List.class, long.class);
        method.setAccessible(true);
        return (String) method.invoke(service, embeddings, clusters, singleCandidates, runId);
    }

    private static Object embedding(long id, long messageId) throws Exception {
        Class<?> type = Class.forName("com.larbcorp.neuroinfogrinder2.replay.ReplayV2Service$Embedding");
        Constructor<?> constructor = type.getDeclaredConstructor(long.class, long.class, List.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(id, messageId, List.of(0.1, 0.2), "BAAI/bge-m3");
    }
}
