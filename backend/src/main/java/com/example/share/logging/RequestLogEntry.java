package com.example.share.logging;

import org.bson.Document;

import java.time.Instant;
import java.util.Date;

/**
 * One row in the MongoDB {@code request_logs} collection. Never add bodies, passwords, tokens, or headers
 * other than the user agent.
 */
public record RequestLogEntry(
        Instant timestamp,
        String method,
        String path,
        int status,
        String ip,
        String userAgent,
        String shareName,
        String action,
        String errorCode,
        long responseTimeMs
) {
    public Document toDocument() {
        Document document = new Document()
                .append("timestamp", Date.from(timestamp))
                .append("method", method)
                .append("path", path)
                .append("status", status)
                .append("ip", ip)
                .append("userAgent", userAgent)
                .append("action", action)
                .append("responseTimeMs", responseTimeMs);
        if (shareName != null) {
            document.append("shareName", shareName);
        }
        if (errorCode != null) {
            document.append("errorCode", errorCode);
        }
        return document;
    }
}
