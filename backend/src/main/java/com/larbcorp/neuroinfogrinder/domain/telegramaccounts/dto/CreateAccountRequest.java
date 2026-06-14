package com.larbcorp.neuroinfogrinder.domain.telegramaccounts.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAccountRequest(
        @NotBlank String phone
) {
}
