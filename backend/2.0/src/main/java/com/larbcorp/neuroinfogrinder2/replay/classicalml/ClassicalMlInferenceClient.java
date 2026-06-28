package com.larbcorp.neuroinfogrinder2.replay.classicalml;

import com.fasterxml.jackson.databind.node.ObjectNode;

public interface ClassicalMlInferenceClient {
    ClassicalMlTriageResult inferMessage(String targetId, String text, ObjectNode featureStore);

    ClassicalMlTriageResult inferSegment(String targetId, String text, ObjectNode featureStore);

    ClassicalMlTriageResult inferCluster(String targetId, String text, ObjectNode featureStore);
}
