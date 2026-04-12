package com.prodigalgal.xaigateway.infra.config.web;

import com.prodigalgal.xaigateway.admin.application.ErrorRuleService;
import com.prodigalgal.xaigateway.gateway.core.error.ErrorRuleMatchContext;
import com.prodigalgal.xaigateway.gateway.core.error.GatewayRuleMatchedException;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayUnauthorizedException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
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
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            ApiResourceNotFoundException exception,
            ServerWebExchange exchange) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError("NOT_FOUND", exception.getMessage(), exchange));
    }

    @ExceptionHandler(GatewayUnauthorizedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(
            GatewayUnauthorizedException exception,
            ServerWebExchange exchange) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(buildError("UNAUTHORIZED", exception.getMessage(), exchange));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception,
            ServerWebExchange exchange) {
        return applyRuleOrDefault(400, "INVALID_ARGUMENT", exception.getMessage(), exchange, "GATEWAY");
    }

    @ExceptionHandler(GatewayRuleMatchedException.class)
    public ResponseEntity<ApiErrorResponse> handleRuleMatched(
            GatewayRuleMatchedException exception,
            ServerWebExchange exchange) {
        return ResponseEntity.status(exception.getStatus())
                .body(buildError(exception.getCode(), exception.getMessage(), exchange));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(
            Exception exception,
            ServerWebExchange exchange) {
        return applyRuleOrDefault(500, "INTERNAL_ERROR", exception.getMessage(), exchange, "GATEWAY");
    }

    private ResponseEntity<ApiErrorResponse> applyRuleOrDefault(
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
            return ResponseEntity.status(status).body(buildError(code, message, exchange));
        }
        return errorRuleService.evaluate(context)
                .map(ruleException -> ResponseEntity.status(ruleException.getStatus())
                        .body(buildError(ruleException.getCode(), ruleException.getMessage(), exchange)))
                .orElseGet(() -> ResponseEntity.status(status)
                        .body(buildError(code, message, exchange)));
    }

    private ApiErrorResponse buildError(String code, String message, ServerWebExchange exchange) {
        Object traceId = exchange.getAttribute(TraceIdWebFilter.TRACE_ID_ATTRIBUTE);
        return new ApiErrorResponse(code, message, traceId == null ? null : traceId.toString());
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
