package com.example.share.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Storage storage,
        Share share,
        Security security,
        RateLimit rateLimit,
        Cors cors,
        RequestLog requestLog
) {
    public record RequestLog(boolean enabled) {
    }

    public record Storage(String location) {
        public Path path() {
            return Path.of(location).toAbsolutePath().normalize();
        }
    }

    public record Share(
            long maxFileSize,
            int maxTextLength,
            Duration accessTokenTtl,
            long cleanupDelayMs,
            String allowedExpirations
    ) {
        public Set<Integer> allowedExpirationMinutes() {
            Set<Integer> values = new LinkedHashSet<>();
            if (allowedExpirations == null || allowedExpirations.isBlank()) {
                return values;
            }
            for (String part : allowedExpirations.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    values.add(Integer.valueOf(trimmed));
                }
            }
            return values;
        }
    }

    public record Security(String accessTokenSecret) {
    }

    public record RateLimit(int createPerMinute, int verifyPerMinute) {
    }

    public record Cors(String allowedOrigins) {
        public List<String> originList() {
            if (allowedOrigins == null || allowedOrigins.isBlank()) {
                return List.of();
            }
            return Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(origin -> !origin.isEmpty())
                    .toList();
        }
    }
}
