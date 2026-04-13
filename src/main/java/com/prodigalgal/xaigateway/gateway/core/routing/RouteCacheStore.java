package com.prodigalgal.xaigateway.gateway.core.routing;

import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import java.time.Duration;
import java.util.Optional;

public interface RouteCacheStore {

    Optional<RoutePlanSnapshot> get(Long distributedKeyId, String protocol, String requestPath, String requestedModel, GatewayRequestSemantics semantics);

    void put(Long distributedKeyId, String protocol, String requestPath, String requestedModel, GatewayRequestSemantics semantics, RoutePlanSnapshot snapshot, Duration ttl);

    void invalidate(Long distributedKeyId, String protocol, String requestPath, String requestedModel, GatewayRequestSemantics semantics);
}
