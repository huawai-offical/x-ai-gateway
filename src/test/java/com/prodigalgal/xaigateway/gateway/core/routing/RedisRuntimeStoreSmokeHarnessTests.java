package com.prodigalgal.xaigateway.gateway.core.routing;

import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.smoke.SmokeHarnessSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisRuntimeStoreSmokeHarnessTests {

    @Test
    void shouldShareRuntimeStateThroughRedisWhenSmokeEnabled() {
        Assumptions.assumeTrue(
                SmokeHarnessSupport.enabled("XAG_SMOKE_REDIS"),
                "设置 XAG_SMOKE_REDIS=true 后才执行真实 Redis smoke。"
        );
        LettuceConnectionFactory factory = connectionFactory();
        try {
            StringRedisTemplate redisTemplate = new StringRedisTemplate(factory);
            redisTemplate.afterPropertiesSet();
            GatewayProperties properties = new GatewayProperties();
            String prefix = "xag:smoke:routing-runtime:" + UUID.randomUUID();
            properties.getRouting().getRuntimeStore().setKeyPrefix(prefix);
            RedisRoutingPolicyRuntimeStore firstStore = new RedisRoutingPolicyRuntimeStore(redisTemplate, properties);
            RedisRoutingPolicyRuntimeStore secondStore = new RedisRoutingPolicyRuntimeStore(redisTemplate, properties);
            String runtimeKey = "policy:9001:credential:7001";
            Instant now = Instant.now();

            firstStore.reset(runtimeKey);
            RoutingPolicyRateWindowState first = firstStore.incrementRateWindow(
                    runtimeKey,
                    9001L,
                    "credential:7001",
                    now,
                    Duration.ofMinutes(1)
            );
            RoutingPolicyRateWindowState second = secondStore.incrementRateWindow(
                    runtimeKey,
                    9001L,
                    "credential:7001",
                    now.plusSeconds(1),
                    Duration.ofMinutes(1)
            );
            firstStore.recordFailure(
                    runtimeKey,
                    9001L,
                    "credential:7001",
                    1,
                    Duration.ofSeconds(1),
                    "smoke-upstream-503",
                    now
            );
            Optional<RoutingPolicyCircuitState> opened = secondStore.findCircuitState(runtimeKey);
            RoutingPolicyCircuitState halfOpen = secondStore.markHalfOpen(runtimeKey, now.plusSeconds(2));
            RoutingPolicyCircuitState competingHalfOpen = firstStore.markHalfOpen(runtimeKey, now.plusSeconds(2));

            assertEquals(1, first.counter());
            assertEquals(2, second.counter());
            assertTrue(opened.isPresent());
            assertEquals("OPEN", opened.get().state());
            assertNotNull(halfOpen);
            assertEquals("HALF_OPEN", halfOpen.state());
            assertNull(competingHalfOpen);

            secondStore.reset(runtimeKey);
            SmokeHarnessSupport.writeReport(
                    "redis-runtime-store",
                    "- redisHost: " + SmokeHarnessSupport.env("XAG_SMOKE_REDIS_HOST", "REDIS_HOST", "localhost") + "\n"
                            + "- runtimeKey: " + runtimeKey + "\n"
                            + "- rateWindowCount: " + second.counter() + "\n"
                            + "- circuitState: " + opened.get().state() + "\n"
                            + "- halfOpenLock: verified\n"
            );
        } finally {
            factory.destroy();
        }
    }

    private LettuceConnectionFactory connectionFactory() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                SmokeHarnessSupport.env("XAG_SMOKE_REDIS_HOST", "REDIS_HOST", "localhost"),
                SmokeHarnessSupport.envInt("XAG_SMOKE_REDIS_PORT", "REDIS_PORT", 6379)
        );
        configuration.setDatabase(SmokeHarnessSupport.envInt("XAG_SMOKE_REDIS_DATABASE", "REDIS_DATABASE", 0));
        String password = SmokeHarnessSupport.env("XAG_SMOKE_REDIS_PASSWORD", "REDIS_PASSWORD", "");
        if (!password.isBlank()) {
            configuration.setPassword(RedisPassword.of(password));
        }
        LettuceConnectionFactory factory = new LettuceConnectionFactory(configuration);
        factory.afterPropertiesSet();
        return factory;
    }
}
