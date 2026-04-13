package com.prodigalgal.xaigateway.gateway.core.auth;

import java.time.Duration;

public interface RateLimitStore {

    long get(String key);

    long increment(String key, long amount, Duration ttl);

    long decrement(String key);
}
