package com.larbcorp.neuroinfogrinder.domain.questions.dto;

import java.util.List;
import java.util.Map;

public record TestPromptRequest(
    Map<String, String> variables,
    Long providerId,
    String chainSource,
    List<String> customChainMessages
) {}
