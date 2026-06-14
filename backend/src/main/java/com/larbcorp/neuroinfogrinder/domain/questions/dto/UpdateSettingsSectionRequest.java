package com.larbcorp.neuroinfogrinder.domain.questions.dto;

import java.util.Map;

public record UpdateSettingsSectionRequest(
    String section,
    Map<String, Object> data
) {}
