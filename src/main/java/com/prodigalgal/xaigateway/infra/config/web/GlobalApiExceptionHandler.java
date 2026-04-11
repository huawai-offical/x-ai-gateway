package com.prodigalgal.xaigateway.infra.config.web;

import com.prodigalgal.xaigateway.gateway.core.auth.GatewayUnauthorizedException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

@Order
@RestControllerAdvice
public class GlobalApiExceptionHandler {

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
        return ResponseEntity.badRequest().body(buildError("INVALID_ARGUMENT", exception.getMessage(), exchange));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(
            Exception exception,
            ServerWebExchange exchange) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError("INTERNAL_ERROR", exception.getMessage(), exchange));
    }

    private ApiErrorResponse buildError(String code, String message, ServerWebExchange exchange) {
        Object traceId = exchange.getAttribute(TraceIdWebFilter.TRACE_ID_ATTRIBUTE);
        return new ApiErrorResponse(code, message, traceId == null ? null : traceId.toString());
    }
}
