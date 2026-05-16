package com.prodigalgal.xaigateway.infra.config.web;

import com.prodigalgal.xaigateway.gateway.core.error.GatewayRuleMatchedException;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebFluxTest(controllers = GlobalApiExceptionHandlerProbeController.class)
@Import({
        PermitAllSecurityTestConfig.class,
        GlobalApiExceptionHandler.class,
        TraceIdWebFilter.class
})
class GlobalApiExceptionHandlerTests {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldReturnOpenAiErrorEnvelopeForOpenAiProtocolPath() {
        webTestClient.post()
                .uri("/v1/chat/completions")
                .header(TraceIdWebFilter.REQUEST_ID_HEADER, "req-client-1")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().valueEquals(TraceIdWebFilter.REQUEST_ID_HEADER, "req-client-1")
                .expectHeader().valueEquals(TraceIdWebFilter.TRACE_ID_HEADER, "req-client-1")
                .expectBody()
                .jsonPath("$.error.message").isEqualTo("model 不能为空。")
                .jsonPath("$.error.type").isEqualTo("invalid_request_error")
                .jsonPath("$.error.param").doesNotExist()
                .jsonPath("$.error.code").isEqualTo("invalid_argument")
                .jsonPath("$.code").doesNotExist();
    }

    @Test
    void shouldKeepGatewayErrorEnvelopeForAnthropicCompatibleMessagesPath() {
        webTestClient.post()
                .uri("/v1/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().exists(TraceIdWebFilter.REQUEST_ID_HEADER)
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_ARGUMENT")
                .jsonPath("$.message").isEqualTo("messages 请求体不能为空。")
                .jsonPath("$.traceId").exists()
                .jsonPath("$.error").doesNotExist();
    }

    @Test
    void shouldReturnOpenAiRateLimitHeadersForLocalRateLimitError() {
        webTestClient.post()
                .uri("/v1/chat/completions/rate-limit")
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "60")
                .expectHeader().valueEquals("x-ratelimit-remaining-requests", "0")
                .expectHeader().valueEquals("x-ratelimit-remaining-tokens", "0")
                .expectHeader().valueEquals("x-ratelimit-reset-requests", "60s")
                .expectHeader().valueEquals("x-ratelimit-reset-tokens", "60s")
                .expectBody()
                .jsonPath("$.error.message").isEqualTo("当前 DistributedKey 已超过 RPM 限制。")
                .jsonPath("$.error.type").isEqualTo("rate_limit_error")
                .jsonPath("$.error.code").isEqualTo("rate_limit_exceeded");
    }

    @Test
    void shouldReturnOpenAiRateLimitHeadersForRuleMatched429() {
        webTestClient.post()
                .uri("/v1/responses/rule-rate-limit")
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "60")
                .expectHeader().valueEquals("x-ratelimit-remaining-requests", "0")
                .expectHeader().valueEquals("x-ratelimit-reset-requests", "60s")
                .expectBody()
                .jsonPath("$.error.message").isEqualTo("route policy rate limited")
                .jsonPath("$.error.type").isEqualTo("rate_limit_error")
                .jsonPath("$.error.code").isEqualTo("rate_limit_exceeded");
    }

}

@RestController
class GlobalApiExceptionHandlerProbeController {

    @PostMapping("/v1/chat/completions")
    String openAiChat() {
        throw new IllegalArgumentException("model 不能为空。");
    }

    @PostMapping("/v1/chat/completions/rate-limit")
    String openAiRateLimit() {
        throw new IllegalArgumentException("当前 DistributedKey 已超过 RPM 限制。");
    }

    @PostMapping("/v1/responses/rule-rate-limit")
    String openAiRuleMatchedRateLimit() {
        throw new GatewayRuleMatchedException(429, "rate_limit_exceeded", "route policy rate limited");
    }

    @PostMapping("/v1/messages")
    String anthropicMessages() {
        throw new IllegalArgumentException("messages 请求体不能为空。");
    }
}
