package com.larbcorp.neuroinfogrinder2.replay.classicalml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClassicalMlTriageResultTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializesStableFields() throws Exception {
        ObjectNode predictions = mapper.createObjectNode();
        predictions.put("meaningTopLabel", "QUESTION");
        ClassicalMlTriageResult result = new ClassicalMlTriageResult(
            "MESSAGE_PIPELINE",
            "42",
            mapper.createObjectNode(),
            mapper.createObjectNode(),
            mapper.createObjectNode(),
            "MEDIUM",
            false,
            null,
            "TRACE_ONLY",
            "classical-ml-bootstrap-v1",
            "classical-ml-v1",
            "bootstrap-reviewed-v0",
            14L,
            false,
            0.12,
            predictions,
            mapper.createArrayNode(),
            mapper.createObjectNode().put("margin", 0.31),
            0.72
        );

        String json = mapper.writeValueAsString(result);
        ClassicalMlTriageResult restored = mapper.readValue(json, ClassicalMlTriageResult.class);

        assertThat(json).contains("\"confidenceBand\":\"MEDIUM\"");
        assertThat(restored.targetId()).isEqualTo("42");
        assertThat(restored.recommendedAction()).isEqualTo("TRACE_ONLY");
        assertThat(restored.predictions().path("meaningTopLabel").asText()).isEqualTo("QUESTION");
        assertThat(restored.uncertainty().path("margin").asDouble()).isEqualTo(0.31);
        assertThat(restored.modelAgreement()).isEqualTo(0.72);
    }
}
