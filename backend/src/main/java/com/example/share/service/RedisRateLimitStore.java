package com.example.share.service;

import com.example.share.exception.ApiException;
import com.example.share.exception.ErrorCode;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class RedisRateLimitStore implements RateLimitStore {

    private static final String SCRIPT = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script;

    public RedisRateLimitStore(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>();
        this.script.setScriptText(SCRIPT);
        this.script.setResultType(Long.class);
    }

    @Override
    public long increment(String key, Duration window) {
        try {
            Long count = redis.execute(script, List.of(key), String.valueOf(window.toMillis()));
            if (count == null) {
                throw unavailable();
            }
            return count;
        } catch (DataAccessException ex) {
            throw unavailable();
        }
    }

    private static ApiException unavailable() {
        return new ApiException(
                ErrorCode.SERVICE_UNAVAILABLE,
                HttpStatus.SERVICE_UNAVAILABLE,
                "Service temporarily unavailable.");
    }
}
