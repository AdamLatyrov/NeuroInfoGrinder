package com.larbcorp.neuroinfogrinder2.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.larbcorp.neuroinfogrinder2.replay.classicalml.RouteIntelligenceDecision;
import com.larbcorp.neuroinfogrinder2.settings.PipelineSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReplayV2ServiceMeasurementGovernanceTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void disabledClassicalRoutingKeepsShadowTraceNonAuthoritative() {
        PipelineSettingsService settings = mock(PipelineSettingsService.class);
        when(settings.getInt("classicalMlRoutingEnabled", 0)).thenReturn(0);
        ReplayV2Service service = service(settings);

        boolean blocked = service.shouldApplyClassicalRouteBlock(hardBlockedRoute());

        assertFalse(blocked);
    }

    @Test
    void enabledClassicalRoutingAppliesHardPolicyBlock() {
        PipelineSettingsService settings = mock(PipelineSettingsService.class);
        when(settings.getInt("classicalMlRoutingEnabled", 0)).thenReturn(1);
        ReplayV2Service service = service(settings);

        boolean blocked = service.shouldApplyClassicalRouteBlock(hardBlockedRoute());

        assertTrue(blocked);
    }

    @Test
    void unreadyTrainingDataBlocksClassifierTraining() {
        boolean allowed = ReplayV2Service.isClassifierTrainingAllowed(Map.of(
            "readyForClassifierV1", false,
            "humanReviewedExamples", 0,
            "strongExamples", 0
        ));

        assertFalse(allowed);
    }

    @Test
    void readyTrainingDataAllowsClassifierTraining() {
        boolean allowed = ReplayV2Service.isClassifierTrainingAllowed(Map.of(
            "readyForClassifierV1", true,
            "humanReviewedExamples", 600,
            "strongExamples", 600
        ));

        assertTrue(allowed);
    }

    private static RouteIntelligenceDecision hardBlockedRoute() {
        ArrayNode flags = JSON.createArrayNode().add("RISK_SENSITIVE_MANUAL_REVIEW");
        return new RouteIntelligenceDecision(
            "MESSAGE", "1", "NO_MATERIAL", "REJECT", "RISK_REQUIRES_MANUAL_REVIEW",
            "HIGH", false, true, JSON.createArrayNode(), flags, JSON.createObjectNode()
        );
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
