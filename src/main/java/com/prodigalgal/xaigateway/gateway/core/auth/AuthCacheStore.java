package com.prodigalgal.xaigateway.gateway.core.auth;

import java.time.Duration;
import java.util.Optional;

public interface AuthCacheStore {

    Optional<DistributedKeyAuthSnapshot> get(String keyPrefix);

    void put(DistributedKeyAuthSnapshot snapshot, Duration ttl);

    void invalidate(String keyPrefix);
}
