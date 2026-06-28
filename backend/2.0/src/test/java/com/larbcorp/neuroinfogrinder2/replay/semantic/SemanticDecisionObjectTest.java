package com.larbcorp.neuroinfogrinder2.replay.semantic;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticDecisionObjectTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shadowMessageDecisionSerializesAndDeserializes() throws Exception {
        SemanticDecisionObject decision = SemanticDecisionObject.shadowForMessage(
            42L,
            "account:1/chat:2/topic:3/thread:4",
            "FULL_REPLAY",
            mapper
        );

        String jsonPayload = mapper.writeValueAsString(decision);
        SemanticDecisionObject restored = mapper.readValue(jsonPayload, SemanticDecisionObject.class);

        assertThat(restored.schemaVersion()).isEqualTo(SemanticDecisionObject.SCHEMA_VERSION);
        assertThat(restored.decisionMode()).isEqualTo(SemanticDecisionObject.DecisionMode.SHADOW);
        assertThat(restored.messageId()).isEqualTo(42L);
        assertThat(restored.meaning()).containsExactly(SemanticDecisionObject.Meaning.UNKNOWN);
    }

    @Test
    void enumsUseStableStringSerialization() throws Exception {
        SemanticDecisionObject decision = SemanticDecisionObject.shadowForMessage(
            7L,
            "account:1/chat:2/topic:null/thread:null",
            "FULL_REPLAY",
            mapper
        );

        String jsonPayload = mapper.writeValueAsString(decision);

        assertThat(jsonPayload).contains("\"decisionMode\":\"SHADOW\"");
        assertThat(jsonPayload).contains("\"valueLevel\":\"NOT_GARBAGE_NO_MATERIAL\"");
        assertThat(jsonPayload).contains("\"uiReason\":\"LOW_CONFIDENCE_REVIEW\"");
        assertThat(jsonPayload).contains("\"materialRoute\":\"NO_MATERIAL\"");
    }

    @Test
    void shadowDefaultsBlockMaterialization() {
        SemanticDecisionObject decision = SemanticDecisionObject.shadowForDiscussionSegment(
            55L,
            java.util.List.of(1L, 2L),
            "account:1/chat:2/topic:3/thread:3",
            "REPLAY_DISCUSSION_SEGMENT",
            mapper
        );

        assertThat(decision.materializationAllowed()).isFalse();
        assertThat(decision.draftAllowed()).isFalse();
        assertThat(decision.noGenerationReason()).isEqualTo(SemanticDecisionObject.NO_GENERATION_REASON_TRACE_ONLY);
        assertThat(decision.generationBlockedBy()).contains(SemanticDecisionObject.BLOCKED_BY_SHADOW_MODE);
        assertThat(decision.materialId()).isNull();
        assertThat(decision.candidateId()).isNull();
    }
}
