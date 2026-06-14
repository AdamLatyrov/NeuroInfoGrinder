package com.larbcorp.neuroinfogrinder.telegram.model;

import jakarta.validation.constraints.NotBlank;

public record TelegramAuthRequest(
        @NotBlank String value
) {
}
