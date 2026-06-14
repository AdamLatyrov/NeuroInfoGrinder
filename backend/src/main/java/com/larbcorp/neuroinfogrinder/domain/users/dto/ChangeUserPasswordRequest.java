package com.larbcorp.neuroinfogrinder.domain.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeUserPasswordRequest(
        @NotBlank
        @Size(min = 4, max = 128)
        String password
) {
}
