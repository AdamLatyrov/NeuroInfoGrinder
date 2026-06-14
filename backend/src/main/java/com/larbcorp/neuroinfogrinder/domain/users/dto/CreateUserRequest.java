package com.larbcorp.neuroinfogrinder.domain.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank
        @Size(min = 3, max = 64)
        String username,
        @NotBlank
        @Size(min = 4, max = 128)
        String password
) {
}
