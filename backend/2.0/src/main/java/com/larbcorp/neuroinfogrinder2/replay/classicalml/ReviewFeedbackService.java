package com.larbcorp.neuroinfogrinder2.replay.classicalml;

import com.fasterxml.jackson.databind.JsonNode;

public interface ReviewFeedbackService {
    JsonNode buildStageFeedbackContext(String targetType, long targetId, JsonNode stageResults);
}
