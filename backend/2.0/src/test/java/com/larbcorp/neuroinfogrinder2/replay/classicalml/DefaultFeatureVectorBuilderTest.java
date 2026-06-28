package com.larbcorp.neuroinfogrinder2.replay.classicalml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultFeatureVectorBuilderTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final DefaultFeatureVectorBuilder builder = new DefaultFeatureVectorBuilder(mapper);

    @Test
    void buildsVersionedMessageFeatureStore() {
        ObjectNode structural = mapper.createObjectNode();
        structural.put("linkCount", 2);
        structural.put("hasCode", true);

        ObjectNode features = builder.buildMessageFeatures(
            7L,
            11L,
            "How to fix docker timeout?\n```bash\nmvn test\n```",
            structural,
            true,
            "CANDIDATE",
            mapper.createArrayNode(),
            mapper.createArrayNode()
        );

        assertThat(features.path("featureVersion").asText()).isEqualTo(FeatureVectorBuilder.FEATURE_VERSION);
        assertThat(features.path("targetType").asText()).isEqualTo("MESSAGE");
        assertThat(features.path("lexical").path("hasQuestionMark").asBoolean()).isTrue();
        assertThat(features.path("structural").path("linkCount").asInt()).isEqualTo(2);
    }

    @Test
    void buildsClusterFeatureStoreWithTopicAnalytics() {
        ObjectNode topicAnalytics = mapper.createObjectNode();
        topicAnalytics.put("importanceScore", 0.77);
        ObjectNode clusterScore = mapper.createObjectNode();
        clusterScore.put("final_score", 0.66);

        ObjectNode features = builder.buildClusterFeatures(
            9L,
            101L,
            "Macro: api",
            0.66,
            java.util.List.of(1L, 2L, 3L),
            topicAnalytics,
            clusterScore
        );

        assertThat(features.path("targetType").asText()).isEqualTo("CLUSTER");
        assertThat(features.path("memberCount").asInt()).isEqualTo(3);
        assertThat(features.path("topicAnalytics").path("importanceScore").asDouble()).isEqualTo(0.77);
        assertThat(features.path("clusterScoreCard").path("final_score").asDouble()).isEqualTo(0.66);
    }
}
