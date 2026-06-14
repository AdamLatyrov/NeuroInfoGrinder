package com.larbcorp.neuroinfogrinder.security;

public record CurrentUserResponse(
        Long userId,
        String username,
        String role
) {
}
