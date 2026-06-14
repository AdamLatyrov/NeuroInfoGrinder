package com.larbcorp.neuroinfogrinder.domain.questions.dto;

public record TokenDailyResponse(
    String date,
    long tokens,
    double cost
) {}
