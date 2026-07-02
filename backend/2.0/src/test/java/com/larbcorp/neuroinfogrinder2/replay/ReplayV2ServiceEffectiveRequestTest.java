package com.larbcorp.neuroinfogrinder2.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.larbcorp.neuroinfogrinder2.settings.PipelineSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReplayV2ServiceEffectiveRequestTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void liveSnapshotOverridesGlobalBudgetWhileSettingsFillThresholds() {
        PipelineSettingsService settings = settings();
        ReplayV2Service service = service(settings);
        ObjectNode snapshot = JSON.createObjectNode();
        snapshot.put("maxProviderCalls", 10);
        snapshot.put("maxCostUsd", 1.0);

        ReplayV2Service.ReplayRequest resolved = service.resolveEffectiveRequest(
            new ReplayV2Service.ReplayRequest(42L, "live", null, null, null, null, null, null),
            snapshot
        );

        assertEquals(10, resolved.maxProviderCalls());
        assertEquals(1.0, resolved.maxEstimatedCostUsd());
        assertEquals(0.62, resolved.semanticSimilarityThreshold());
        assertEquals(0.65, resolved.minClusterScoreForJudge());
        assertEquals(0.72, resolved.minJudgeConfidenceForGeneration());
    }

    @Test
    void explicitRequestOverridesSnapshotAndGlobalSettings() {
        ReplayV2Service service = service(settings());
        ObjectNode snapshot = JSON.createObjectNode();
        snapshot.put("maxProviderCalls", 10);
        snapshot.put("maxCostUsd", 1.0);
        snapshot.put("semanticSimilarityThreshold", 0.62);

        ReplayV2Service.ReplayRequest resolved = service.resolveEffectiveRequest(
            new ReplayV2Service.ReplayRequest(42L, "manual", 100, 7, 0.5, 0.71, 0.61, 0.81),
            snapshot
        );

        assertEquals(100, resolved.maxMessages());
        assertEquals(7, resolved.maxProviderCalls());
        assertEquals(0.5, resolved.maxEstimatedCostUsd());
        assertEquals(0.71, resolved.semanticSimilarityThreshold());
        assertEquals(0.61, resolved.minClusterScoreForJudge());
        assertEquals(0.81, resolved.minJudgeConfidenceForGeneration());
    }

    private static PipelineSettingsService settings() {
        PipelineSettingsService settings = mock(PipelineSettingsService.class);
        when(settings.getInt("maxProviderCallsPerRun", 50)).thenReturn(30);
        when(settings.getDouble("maxCostUsdPerRun", 2.0)).thenReturn(2.0);
        when(settings.getDouble("semanticSimilarityThreshold", 0.66)).thenReturn(0.62);
        when(settings.getDouble("minClusterScoreForJudge", 0.65)).thenReturn(0.65);
        when(settings.getDouble("minJudgeConfidenceForGeneration", 0.72)).thenReturn(0.72);
        return settings;
    }

    private static ReplayV2Service service(PipelineSettingsService settings) {
        return new ReplayV2Service(
            mock(JdbcTemplate.class),
            JSON,
            mock(ModelWorkerClient.class),
            mock(ModelhubProviderGateway.class),
            mock(Environment.class),
            settings
        );
    }
}
