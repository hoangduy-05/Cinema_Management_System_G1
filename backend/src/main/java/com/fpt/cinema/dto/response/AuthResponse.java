package com.fpt.cinema.dto.response;

import java.util.Set;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Long accountId,
        String username,
        String email,
        Set<String> roles
) {
    public AuthResponse {
        tokenType = tokenType == null || tokenType.isBlank() ? "Bearer" : tokenType;
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}
