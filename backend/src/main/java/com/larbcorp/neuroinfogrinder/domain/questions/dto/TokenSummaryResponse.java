package com.larbcorp.neuroinfogrinder.domain.questions.dto;

import java.util.List;

public record TokenSummaryResponse(
    long totalTokensToday,
    double totalCostToday,
    List<TokenByProviderDto> tokensByProvider
) {}
