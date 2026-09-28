package com.example.share.dto;

import java.time.Instant;

public record ShareViewResponse(
        boolean passwordRequired,
        String name,
        String shareUrl,
        String type,
        String content,
        String filename,
        String extension,
        String contentType,
        Long fileSize,
        Instant expiresAt,
        Boolean passwordProtected
) {
    public static ShareViewResponse locked(String name, String shareUrl, String type, Instant expiresAt) {
        return new ShareViewResponse(true, name, shareUrl, type, null, null, null, null, null, expiresAt, true);
    }

    public static ShareViewResponse text(String name, String shareUrl, String content, Instant expiresAt, boolean passwordProtected) {
        return new ShareViewResponse(
                false, name, shareUrl, "TEXT", content, null, null, null, null, expiresAt, passwordProtected);
    }

    public static ShareViewResponse file(
            String name,
            String shareUrl,
            String filename,
            String extension,
            String contentType,
            long fileSize,
            Instant expiresAt,
            boolean passwordProtected
    ) {
        return new ShareViewResponse(
                false, name, shareUrl, "FILE", null, filename, extension, contentType, fileSize, expiresAt, passwordProtected);
    }
}
