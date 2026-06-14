package com.larbcorp.neuroinfogrinder.security;

public record AuthResponse(
        String token,
        Long userId,
        String username,
        String role
) {
}
