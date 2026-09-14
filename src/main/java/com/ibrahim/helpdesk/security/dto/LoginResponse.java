package com.ibrahim.helpdesk.security.dto;

import com.ibrahim.helpdesk.user.dto.UserResponse;

import java.time.Instant;

/**
 * @param accessToken send as {@code Authorization: Bearer <accessToken>}
 * @param tokenType   always {@code Bearer}
 * @param expiresAt   when the token stops being accepted
 * @param user        the authenticated user, so a client does not need a second call
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserResponse user
) {
}
