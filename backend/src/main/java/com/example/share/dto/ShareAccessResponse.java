package com.example.share.dto;

import java.time.Instant;

public record ShareAccessResponse(
        String accessToken,
        Instant accessExpiresAt,
        ShareViewResponse share
) {
}
