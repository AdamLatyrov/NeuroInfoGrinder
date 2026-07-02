package com.larbcorp.neuroinfogrinder2.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.larbcorp.neuroinfogrinder2.replay.classicalml.RouteIntelligenceDecision;
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
    private static final ObjectMapper JSON = new ObjectMapper();

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

    @Test
    void narrowChecklistJudgeRejectAllowsLightweightMaterial() {
        ReplayV2Service service = serviceWithCount(0L);
        ObjectNode judge = JSON.createObjectNode();
        judge.put("decision", "reject");
        judge.put("reason", "Достаточно для узкого чеклиста, но недостаточно для полноценного standalone материала.");

        boolean allowed = service.shouldAllowLightweightMaterialAfterJudgeReject(judge, route("GUIDE", false), 0.85, 4);

        assertThat(allowed).isTrue();
    }

    @Test
    void contextualReferenceJudgeRejectAllowsLightweightReference() {
        ReplayV2Service service = serviceWithCount(0L);
        ObjectNode judge = JSON.createObjectNode();
        judge.put("decision", "reject");
        judge.put("reason", "Useful GitHub resource/reference, but too small for a standalone guide.");

        boolean allowed = service.shouldAllowLightweightMaterialAfterJudgeReject(judge, route("REFERENCE", false), 0.65, 2);

        assertThat(allowed).isTrue();
    }

    @Test
    void blankRouteNarrowChecklistJudgeRejectAllowsHighScoreCluster() {
        ReplayV2Service service = serviceWithCount(0L);
        ObjectNode judge = JSON.createObjectNode();
        judge.put("decision", "reject");
        judge.put("summary", "Этого достаточно для узкого чеклиста, но недостаточно для полноценного самостоятельного материала.");

        boolean allowed = service.shouldAllowLightweightMaterialAfterJudgeReject(judge, route("", false), 0.73, 4);

        assertThat(allowed).isTrue();
    }

    @Test
    void missingRouteDecisionNarrowChecklistJudgeRejectAllowsHighScoreCluster() {
        ReplayV2Service service = serviceWithCount(0L);
        ObjectNode judge = JSON.createObjectNode();
        judge.put("decision", "reject");
        judge.put("summary", "Этого достаточно для узкого чеклиста, но недостаточно для полноценного самостоятельного материала.");

        boolean allowed = service.shouldAllowLightweightMaterialAfterJudgeReject(judge, null, 0.73, 4);

        assertThat(allowed).isTrue();
    }

    @Test
    void noMaterialRouteNarrowChecklistJudgeRejectAllowsHighScoreCluster() {
        ReplayV2Service service = serviceWithCount(0L);
        ObjectNode judge = JSON.createObjectNode();
        judge.put("decision", "reject");
        judge.put("summary", "Этого достаточно для узкого чеклиста, но недостаточно для полноценного самостоятельного материала.");

        boolean allowed = service.shouldAllowLightweightMaterialAfterJudgeReject(judge, route("NO_MATERIAL", true), 0.73, 4);

        assertThat(allowed).isTrue();
    }

    @Test
    void modelhubEnvelopeNarrowChecklistJudgeRejectAllowsHighScoreCluster() throws Exception {
        ReplayV2Service service = serviceWithCount(0L);
        ObjectNode judge = JSON.createObjectNode();
        judge.putArray("choices").addObject().putObject("message").put("content", "{\"decision\":\"reject\",\"summary\":\"Этого достаточно для узкого чеклиста, но недостаточно для полноценного самостоятельного материала.\"}");

        boolean allowed = service.shouldAllowLightweightMaterialAfterJudgeReject(judge, null, 0.73, 4);

        assertThat(allowed).isTrue();
    }

    @Test
    void blankRouteReferenceJudgeRejectAllowsContextualResourceCluster() {
        ReplayV2Service service = serviceWithCount(0L);
        ObjectNode judge = JSON.createObjectNode();
        judge.put("decision", "reject");
        judge.put("summary", "Предоставлен ресурс GitHub Playwright и краткое описание reference context.");

        boolean allowed = service.shouldAllowLightweightMaterialAfterJudgeReject(judge, route("", false), 0.59, 2);

        assertThat(allowed).isTrue();
    }

    @Test
    void riskSensitiveJudgeRejectDoesNotAllowLightweightMaterial() {
        ReplayV2Service service = serviceWithCount(0L);
        ObjectNode judge = JSON.createObjectNode();
        judge.put("decision", "reject");
        judge.put("reason", "Safety risk: psychedelic advice requires manual review.");

        boolean allowed = service.shouldAllowLightweightMaterialAfterJudgeReject(judge, route("GUIDE", false), 0.90, 3);

        assertThat(allowed).isFalse();
    }

    @Test
    void hardBlockedRouteDoesNotAllowLightweightMaterial() {
        ReplayV2Service service = serviceWithCount(0L);
        ObjectNode judge = JSON.createObjectNode();
        judge.put("decision", "reject");
        judge.put("reason", "Достаточно для узкого чеклиста.");

        boolean allowed = service.shouldAllowLightweightMaterialAfterJudgeReject(judge, route("GUIDE", true, "RISK_SENSITIVE_MANUAL_REVIEW"), 0.90, 3);

        assertThat(allowed).isFalse();
    }

    @Test
    void genericBlockedRouteCanStillAllowLightweightMaterialAfterJudgeReject() {
        ReplayV2Service service = serviceWithCount(0L);
        ObjectNode judge = JSON.createObjectNode();
        judge.put("decision", "reject");
        judge.put("reason", "Достаточно для узкого чеклиста.");

        boolean allowed = service.shouldAllowLightweightMaterialAfterJudgeReject(judge, route("GUIDE", true), 0.90, 3);

        assertThat(allowed).isTrue();
    }

    private static ReplayV2Service serviceWithCount(long count) {
        return serviceWithCounts(count, 0L);
    }

    private static ReplayV2Service serviceWithCounts(long first, long second) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(Class.class), any(Object[].class))).thenReturn(first, second);
        Environment environment = mock(Environment.class);
        when(environment.getProperty("MODELHUB_API_KEY")).thenReturn(null);
        PipelineSettingsService settings = mock(PipelineSettingsService.class);
        when(settings.getInt("classicalMlRoutingEnabled", 0)).thenReturn(1);
        return new ReplayV2Service(jdbc, new ObjectMapper(), mock(ModelWorkerClient.class), mock(ModelhubProviderGateway.class), environment, settings);
    }

    private static RouteIntelligenceDecision route(String route, boolean blocked) {
        return route(route, blocked, null);
    }

    private static RouteIntelligenceDecision route(String route, boolean blocked, String policyFlag) {
        ArrayNode emptyArray = JSON.createArrayNode();
        ArrayNode flags = JSON.createArrayNode();
        if (policyFlag != null) flags.add(policyFlag);
        ObjectNode reasonBundle = JSON.createObjectNode();
        reasonBundle.put("materialRoute", route);
        return new RouteIntelligenceDecision("CLUSTER", "1", route, "DISCUSSION_SEGMENT", "TEST", "HIGH", true, blocked, emptyArray, flags, reasonBundle);
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
