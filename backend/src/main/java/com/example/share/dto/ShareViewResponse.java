package com.example.share.dto;

import java.time.Instant;

public record ShareViewResponse(
        boolean passwordRequired,
        String type,
        String content,
        String filename,
        String contentType,
        Long fileSize,
        Instant expiresAt,
        Boolean passwordProtected
) {
    public static ShareViewResponse locked(Instant expiresAt) {
        return new ShareViewResponse(true, null, null, null, null, null, expiresAt, true);
    }

    public static ShareViewResponse text(String content, Instant expiresAt, boolean passwordProtected) {
        return new ShareViewResponse(false, "TEXT", content, null, null, null, expiresAt, passwordProtected);
    }

    public static ShareViewResponse file(
            String filename,
            String contentType,
            long fileSize,
            Instant expiresAt,
            boolean passwordProtected
    ) {
        return new ShareViewResponse(
                false,
                "FILE",
                null,
                filename,
                contentType,
                fileSize,
                expiresAt,
                passwordProtected);
    }
}
