package com.example.share.service;

import com.example.share.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenServiceTest {

    private final AccessTokenService tokens = new AccessTokenService(properties());

    @Test
    void acceptsAFreshTokenForTheSameShare() {
        String token = tokens.issue("a8Kx92LmP4xZ", java.time.Instant.now().plusSeconds(60));
        assertThat(tokens.isValid(token, "a8Kx92LmP4xZ", java.time.Instant.now())).isTrue();
    }

    @Test
    void rejectsExpiredTamperedAndCrossShareTokens() {
        String token = tokens.issue("a8Kx92LmP4xZ", java.time.Instant.now().minusSeconds(5));
        assertThat(tokens.isValid(token, "a8Kx92LmP4xZ", java.time.Instant.now())).isFalse();

        String fresh = tokens.issue("a8Kx92LmP4xZ", java.time.Instant.now().plusSeconds(60));
        assertThat(tokens.isValid(fresh, "otherToken11", java.time.Instant.now())).isFalse();
        assertThat(tokens.isValid(fresh.substring(0, fresh.length() - 2) + "aa", "a8Kx92LmP4xZ", java.time.Instant.now()))
                .isFalse();
        assertThat(tokens.isValid("management-token", "a8Kx92LmP4xZ", java.time.Instant.now())).isFalse();
    }

    private static AppProperties properties() {
        return new AppProperties(
                new AppProperties.Storage("./data/uploads"),
                new AppProperties.Share(1024, 1000, Duration.ofMinutes(15), 60_000, "15,60"),
                new AppProperties.Security("dev-only-access-token-secret-change-me"),
                new AppProperties.RateLimit(20, 10),
                new AppProperties.Cors("http://localhost:5173"));
    }
}
