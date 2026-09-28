package com.example.share.service;

import com.example.share.config.AppProperties;
import com.example.share.exception.ApiException;
import com.example.share.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitServiceTest {

    @Test
    void blocksCreateAfterTheConfiguredLimitPerClient() {
        RateLimitService limits = service(2, 10);
        limits.checkCreate("10.0.0.8");
        limits.checkCreate("10.0.0.8");
        limits.checkCreate("10.1.1.1");

        assertThatThrownBy(() -> limits.checkCreate("10.0.0.8"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    void blocksPasswordAttemptsPerShare() {
        RateLimitService limits = service(20, 2);
        limits.checkVerify("share-a");
        limits.checkVerify("share-a");
        limits.checkVerify("share-b");

        assertThatThrownBy(() -> limits.checkVerify("share-a"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    private static RateLimitService service(int createLimit, int verifyLimit) {
        AppProperties properties = new AppProperties(
                new AppProperties.Storage("./data/uploads"),
                new AppProperties.Share(1024, 1000, Duration.ofMinutes(15), 60_000, "60"),
                new AppProperties.Security("dev-only-access-token-secret-change-me"),
                new AppProperties.RateLimit(createLimit, verifyLimit),
                new AppProperties.Cors("http://localhost:5173"),
                new AppProperties.RequestLog(false));
        return new RateLimitService(new MapRateLimitStore(), new TokenService(), properties);
    }

    private static final class MapRateLimitStore implements RateLimitStore {
        private final Map<String, long[]> windows = new HashMap<>();
        private final Clock clock = Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC);

        @Override
        public synchronized long increment(String key, Duration window) {
            long now = clock.millis();
            long[] state = windows.get(key);
            if (state == null || now >= state[1]) {
                state = new long[]{0, now + window.toMillis()};
                windows.put(key, state);
            }
            state[0]++;
            return state[0];
        }
    }
}
