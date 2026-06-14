package com.larbcorp.neuroinfogrinder.domain.chats.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BulkToggleRequest(
        @NotEmpty List<Long> groupIds,
        @NotNull boolean enabled
) {
}
