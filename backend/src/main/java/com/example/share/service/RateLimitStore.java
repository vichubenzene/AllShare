package com.example.share.service;

import java.time.Duration;

public interface RateLimitStore {

    long increment(String key, Duration window);
}
