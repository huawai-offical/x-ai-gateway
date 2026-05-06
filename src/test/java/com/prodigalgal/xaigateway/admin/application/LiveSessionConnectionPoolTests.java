package com.prodigalgal.xaigateway.admin.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveSessionConnectionPoolTests {

    @Test
    void shouldLimitConnectionsPerTenantAndKeepTenantsIsolated() {
        LiveSessionConnectionPool pool = new LiveSessionConnectionPool(
                2,
                Duration.ofMinutes(5),
                Clock.fixed(Instant.parse("2026-05-06T00:00:00Z"), ZoneOffset.UTC)
        );

        var first = pool.acquire("tenant:a", "live_a_1", "openai_realtime");
        pool.acquire("tenant:a", "live_a_2", "openai_realtime");
        pool.acquire("tenant:b", "live_b_1", "gemini_live");

        assertEquals(3, pool.activeCount());
        assertEquals(2, pool.activeCountForTenant("tenant:a"));
        assertEquals(1, pool.activeCountForTenant("tenant:b"));
        assertTrue(first.leaseId().startsWith("live_pool_"));
        assertThrows(IllegalStateException.class, () -> pool.acquire("tenant:a", "live_a_3", "openai_realtime"));
    }

    @Test
    void shouldReleaseCancelAndSweepExpiredLeases() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-06T00:00:00Z"), ZoneOffset.UTC);
        LiveSessionConnectionPool pool = new LiveSessionConnectionPool(2, Duration.ofSeconds(1), fixedClock);

        pool.acquire("tenant:a", "live_a_1", "openai_realtime");
        pool.acquire("tenant:a", "live_a_2", "openai_realtime");
        var released = pool.release("live_a_1");
        var cancelled = pool.cancel("live_a_2");

        assertEquals("RELEASED", released.state());
        assertEquals("CANCELLED", cancelled.state());
        assertEquals(0, pool.activeCount());

        MutableClock expiringClock = new MutableClock(Instant.parse("2026-05-06T00:00:00Z"));
        LiveSessionConnectionPool expiringPool = new LiveSessionConnectionPool(1, Duration.ofSeconds(1), expiringClock);
        expiringPool.acquire("tenant:a", "live_a_3", "openai_realtime");
        assertEquals(1, expiringPool.activeCount());
        expiringClock.advance(Duration.ofSeconds(2));
        assertEquals(1, expiringPool.sweepExpired());
        assertEquals(0, expiringPool.activeCount());
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
