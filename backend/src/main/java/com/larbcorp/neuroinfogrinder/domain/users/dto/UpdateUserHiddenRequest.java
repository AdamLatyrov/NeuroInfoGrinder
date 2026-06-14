package com.larbcorp.neuroinfogrinder.domain.users.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserHiddenRequest(
        @NotNull
        Boolean hidden
) {
}
