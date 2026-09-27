package com.example.share.dto;

import java.time.Instant;

public record ShareCreatedResponse(
        String token,
        String shareUrl,
        String managementToken,
        Instant expiresAt,
        boolean passwordProtected
) {
}
