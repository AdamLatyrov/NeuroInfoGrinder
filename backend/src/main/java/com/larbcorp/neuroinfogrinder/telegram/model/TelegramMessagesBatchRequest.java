package com.larbcorp.neuroinfogrinder.telegram.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record TelegramMessagesBatchRequest(
        @NotEmpty List<Long> chatIds,
        @Min(1) @Max(100) int limitPerChat
) {
}
