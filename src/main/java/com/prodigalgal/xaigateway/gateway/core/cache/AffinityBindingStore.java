package com.prodigalgal.xaigateway.gateway.core.cache;

import java.time.Duration;

public interface AffinityBindingStore {

    String get(String key);

    void put(String key, String value, Duration ttl);

    void invalidateIfMatches(String key, String expectedValue);
}
