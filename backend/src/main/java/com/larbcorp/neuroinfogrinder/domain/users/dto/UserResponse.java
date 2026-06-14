package com.larbcorp.neuroinfogrinder.domain.users.dto;

import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String role,
        Boolean hidden,
        Instant createdAt,
        Instant updatedAt
) {
}
