package com.prodigalgal.xaigateway.infra.config.web;

import com.prodigalgal.xaigateway.admin.application.ErrorRuleService;
import com.prodigalgal.xaigateway.gateway.core.error.ErrorRuleMatchContext;
import com.prodigalgal.xaigateway.gateway.core.error.GatewayRuleMatchedException;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayUnauthorizedException;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

@Order
@RestControllerAdvice
public class GlobalApiExceptionHandler {

    private final ErrorRuleService errorRuleService;

    public GlobalApiExceptionHandler(ObjectProvider<ErrorRuleService> errorRuleService) {
        this.errorRuleService = errorRuleService.getIfAvailable();
    }

    @ExceptionHandler(ApiResourceNotFoundException.class)
    public ResponseEntity<?> handleNotFound(
            ApiResourceNotFoundException exception,
            ServerWebExchange exchange) {
        return buildResponse(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", exception.getMessage(), exchange);
    }

    @ExceptionHandler(GatewayUnauthorizedException.class)
    public ResponseEntity<?> handleUnauthorized(
            GatewayUnauthorizedException exception,
            ServerWebExchange exchange) {
        return buildResponse(HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED", exception.getMessage(), exchange);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(
            IllegalArgumentException exception,
            ServerWebExchange exchange) {
        if (isLocalRateLimitMessage(exception.getMessage())) {
            return applyRuleOrDefault(429, "RATE_LIMIT_EXCEEDED", exception.getMessage(), exchange, "GATEWAY");
        }
        return applyRuleOrDefault(400, "INVALID_ARGUMENT", exception.getMessage(), exchange, "GATEWAY");
    }

    @ExceptionHandler(GatewayRuleMatchedException.class)
    public ResponseEntity<?> handleRuleMatched(
            GatewayRuleMatchedException exception,
            ServerWebExchange exchange) {
        return buildResponse(exception.getStatus(), exception.getCode(), exception.getMessage(), exchange);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneric(
            Exception exception,
            ServerWebExchange exchange) {
        return applyRuleOrDefault(500, "INTERNAL_ERROR", exception.getMessage(), exchange, "GATEWAY");
    }

    private ResponseEntity<?> applyRuleOrDefault(
            int status,
            String code,
            String message,
            ServerWebExchange exchange,
            String scope) {
        ErrorRuleMatchContext context = new ErrorRuleMatchContext(
                null,
                protocolFromPath(exchange.getRequest().getPath().value()),
                null,
                exchange.getRequest().getPath().value(),
                status,
                code,
                scope,
                message
        );
        if (errorRuleService == null) {
            return buildResponse(status, code, message, exchange);
        }
        return errorRuleService.evaluate(context)
                .<ResponseEntity<?>>map(ruleException -> buildResponse(
                        ruleException.getStatus(),
                        ruleException.getCode(),
                        ruleException.getMessage(),
                        exchange))
                .orElseGet(() -> buildResponse(status, code, message, exchange));
    }

    private ResponseEntity<?> buildResponse(int status, String code, String message, ServerWebExchange exchange) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        applyOpenAiRateLimitHeaders(builder, status, exchange);
        return builder.body(buildError(status, code, message, exchange));
    }

    private void applyOpenAiRateLimitHeaders(
            ResponseEntity.BodyBuilder builder,
            int status,
            ServerWebExchange exchange) {
        if (status != HttpStatus.TOO_MANY_REQUESTS.value()
                || !OpenAiProtocolPathMatcher.isOpenAiProtocolPath(exchange.getRequest().getPath().value())) {
            return;
        }
        builder.header(HttpHeaders.RETRY_AFTER, "60");
        builder.header("x-ratelimit-remaining-requests", "0");
        builder.header("x-ratelimit-remaining-tokens", "0");
        builder.header("x-ratelimit-reset-requests", "60s");
        builder.header("x-ratelimit-reset-tokens", "60s");
    }

    private boolean isLocalRateLimitMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("rpm 限制")
                || normalized.contains("tpm 限制")
                || normalized.contains("并发限制")
                || normalized.contains("并发已满")
                || normalized.contains("限流")
                || normalized.contains("rate limit")
                || normalized.contains("rate_limited")
                || normalized.contains("too many requests");
    }

    private Object buildError(int status, String code, String message, ServerWebExchange exchange) {
        Object traceId = exchange.getAttribute(TraceIdWebFilter.TRACE_ID_ATTRIBUTE);
        String requestId = traceId == null ? null : traceId.toString();
        if (OpenAiProtocolPathMatcher.isOpenAiProtocolPath(exchange.getRequest().getPath().value())) {
            return new OpenAiApiErrorResponse(new OpenAiApiErrorResponse.Error(
                    message,
                    openAiErrorType(status),
                    null,
                    normalizeOpenAiErrorCode(code)
            ));
        }
        return new ApiErrorResponse(code, message, requestId);
    }

    private String openAiErrorType(int status) {
        if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
            return "authentication_error";
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return "rate_limit_error";
        }
        if (status >= 500) {
            return "server_error";
        }
        return "invalid_request_error";
    }

    private String normalizeOpenAiErrorCode(String code) {
        return code == null || code.isBlank() ? null : code.toLowerCase(Locale.ROOT);
    }

    private String protocolFromPath(String path) {
        if (path == null) {
            return "openai";
        }
        if (path.startsWith("/anthropic")) {
            return "anthropic_native";
        }
        if (path.startsWith("/google")) {
            return "google_native";
        }
        return "openai";
    }
}
