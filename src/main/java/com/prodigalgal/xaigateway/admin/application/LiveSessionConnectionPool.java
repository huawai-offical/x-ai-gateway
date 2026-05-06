package com.prodigalgal.xaigateway.admin.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LiveSessionConnectionPool {

    private static final int DEFAULT_MAX_CONNECTIONS_PER_TENANT = 4;
    private static final Duration DEFAULT_LEASE_TTL = Duration.ofMinutes(30);

    private final Map<String, Lease> leasesBySessionKey = new LinkedHashMap<>();
    private final int maxConnectionsPerTenant;
    private final Duration leaseTtl;
    private final Clock clock;

    public LiveSessionConnectionPool() {
        this(DEFAULT_MAX_CONNECTIONS_PER_TENANT, DEFAULT_LEASE_TTL, Clock.systemUTC());
    }

    public LiveSessionConnectionPool(int maxConnectionsPerTenant, Duration leaseTtl, Clock clock) {
        if (maxConnectionsPerTenant <= 0) {
            throw new IllegalArgumentException("maxConnectionsPerTenant 必须大于 0。");
        }
        this.maxConnectionsPerTenant = maxConnectionsPerTenant;
        this.leaseTtl = leaseTtl == null || leaseTtl.isNegative() || leaseTtl.isZero() ? DEFAULT_LEASE_TTL : leaseTtl;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public synchronized Lease acquire(String tenantKey, String sessionKey, String protocol) {
        sweepExpired();
        Lease existing = leasesBySessionKey.get(sessionKey);
        if (existing != null && existing.active()) {
            return existing.touch(now(), leaseTtl);
        }
        String normalizedTenant = normalizeTenant(tenantKey);
        long activeForTenant = leasesBySessionKey.values().stream()
                .filter(Lease::active)
                .filter(lease -> lease.tenantKey().equals(normalizedTenant))
                .count();
        if (activeForTenant >= maxConnectionsPerTenant) {
            throw new IllegalStateException("Realtime 连接池已达到当前租户上限。");
        }
        Instant acquiredAt = now();
        Lease lease = new Lease(
                "live_pool_" + UUID.randomUUID().toString().replace("-", ""),
                sessionKey,
                normalizedTenant,
                protocol == null || protocol.isBlank() ? "unknown" : protocol.trim(),
                "ACTIVE",
                acquiredAt,
                acquiredAt,
                acquiredAt.plus(leaseTtl)
        );
        leasesBySessionKey.put(sessionKey, lease);
        return lease;
    }

    public synchronized Lease touch(String sessionKey) {
        Lease lease = leasesBySessionKey.get(sessionKey);
        if (lease == null || !lease.active()) {
            return lease;
        }
        Lease touched = lease.touch(now(), leaseTtl);
        leasesBySessionKey.put(sessionKey, touched);
        return touched;
    }

    public synchronized Lease release(String sessionKey) {
        Lease lease = leasesBySessionKey.get(sessionKey);
        if (lease == null) {
            return null;
        }
        Lease released = lease.withState("RELEASED", now());
        leasesBySessionKey.put(sessionKey, released);
        return released;
    }

    public synchronized Lease cancel(String sessionKey) {
        Lease lease = leasesBySessionKey.get(sessionKey);
        if (lease == null) {
            return null;
        }
        Lease cancelled = lease.withState("CANCELLED", now());
        leasesBySessionKey.put(sessionKey, cancelled);
        return cancelled;
    }

    public synchronized int sweepExpired() {
        Instant current = now();
        int swept = 0;
        for (Map.Entry<String, Lease> entry : new ArrayList<>(leasesBySessionKey.entrySet())) {
            Lease lease = entry.getValue();
            if (lease.active() && !lease.expiresAt().isAfter(current)) {
                leasesBySessionKey.put(entry.getKey(), lease.withState("EXPIRED", current));
                swept++;
            }
        }
        return swept;
    }

    public synchronized int activeCount() {
        sweepExpired();
        return (int) leasesBySessionKey.values().stream().filter(Lease::active).count();
    }

    public synchronized int activeCountForTenant(String tenantKey) {
        sweepExpired();
        String normalizedTenant = normalizeTenant(tenantKey);
        return (int) leasesBySessionKey.values().stream()
                .filter(Lease::active)
                .filter(lease -> lease.tenantKey().equals(normalizedTenant))
                .count();
    }

    public synchronized List<Lease> snapshot() {
        sweepExpired();
        return List.copyOf(leasesBySessionKey.values());
    }

    public int maxConnectionsPerTenant() {
        return maxConnectionsPerTenant;
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private String normalizeTenant(String tenantKey) {
        return tenantKey == null || tenantKey.isBlank() ? "tenant:anonymous" : tenantKey.trim();
    }

    public record Lease(
            String leaseId,
            String sessionKey,
            String tenantKey,
            String protocol,
            String state,
            Instant acquiredAt,
            Instant lastUsedAt,
            Instant expiresAt
    ) {

        boolean active() {
            return "ACTIVE".equals(state);
        }

        Lease touch(Instant now, Duration ttl) {
            return new Lease(leaseId, sessionKey, tenantKey, protocol, state, acquiredAt, now, now.plus(ttl));
        }

        Lease withState(String nextState, Instant now) {
            return new Lease(leaseId, sessionKey, tenantKey, protocol, nextState, acquiredAt, now, now);
        }
    }
}
