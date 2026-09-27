package com.example.share.service;

import com.example.share.config.AppProperties;
import com.example.share.exception.ApiException;
import com.example.share.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimitService {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RateLimitStore store;
    private final TokenService tokens;
    private final int createLimit;
    private final int verifyLimit;

    public RateLimitService(RateLimitStore store, TokenService tokens, AppProperties properties) {
        this.store = store;
        this.tokens = tokens;
        this.createLimit = properties.rateLimit().createPerMinute();
        this.verifyLimit = properties.rateLimit().verifyPerMinute();
    }

    public void checkCreate(String clientKey) {
        String material = clientKey == null || clientKey.isBlank() ? "unknown" : clientKey;
        long count = store.increment("rl:create:" + tokens.sha256Hex(material), WINDOW);
        if (count > createLimit) {
            throw limited();
        }
    }

    public void checkVerify(String shareToken) {
        long count = store.increment("rl:verify:" + tokens.sha256Hex(shareToken), WINDOW);
        if (count > verifyLimit) {
            throw limited();
        }
    }

    private static ApiException limited() {
        return new ApiException(
                ErrorCode.RATE_LIMITED,
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests. Try again later.");
    }
}
