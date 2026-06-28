package com.larbcorp.neuroinfogrinder2.replay.classicalml;

import com.fasterxml.jackson.databind.node.ObjectNode;

public interface StageDecisionEngine {
    StageDecisionSnapshot decideMessage(String targetId, String text, ObjectNode featureStore);

    StageDecisionSnapshot decideDiscussionSegment(String targetId, String text, ObjectNode featureStore);

    StageDecisionSnapshot decideCluster(String targetId, String text, ObjectNode featureStore);
}
