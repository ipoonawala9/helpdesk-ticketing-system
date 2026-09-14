package com.ibrahim.helpdesk.message.dto;

import com.ibrahim.helpdesk.user.dto.UserSummaryResponse;

import java.time.LocalDateTime;

/**
 * Safe view of a ticket message. {@code sender} is null if the author's
 * account has since been deleted.
 */
public record MessageResponse(
        Long id,
        Long ticketId,
        UserSummaryResponse sender,
        String content,
        LocalDateTime createdAt
) {
}
