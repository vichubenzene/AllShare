package com.example.share.dto;

import java.time.Instant;

public record ShareCreatedResponse(
        String name,
        String type,
        String shareUrl,
        String managementToken,
        Instant expiresAt,
        boolean passwordProtected
) {
}
