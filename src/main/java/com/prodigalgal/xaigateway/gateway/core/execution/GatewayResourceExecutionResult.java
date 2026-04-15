package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceResponse;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

public record GatewayResourceExecutionResult(
        ResponseEntity<JsonNode> jsonResponse,
        ResponseEntity<byte[]> binaryResponse,
        CanonicalResourceResponse canonicalResponse
) {

    public GatewayResourceExecutionResult {
        if (jsonResponse == null && binaryResponse == null) {
            throw new IllegalArgumentException("资源执行结果至少需要一个原始响应。");
        }
    }

    public static GatewayResourceExecutionResult json(
            ResponseEntity<JsonNode> response,
            CanonicalResourceResponse canonicalResponse) {
        return new GatewayResourceExecutionResult(response, null, canonicalResponse);
    }

    public static GatewayResourceExecutionResult binary(
            ResponseEntity<byte[]> response,
            CanonicalResourceResponse canonicalResponse) {
        return new GatewayResourceExecutionResult(null, response, canonicalResponse);
    }

    public boolean binary() {
        return binaryResponse != null;
    }

    public int statusCode() {
        return binary() ? binaryResponse.getStatusCode().value() : jsonResponse.getStatusCode().value();
    }

    public String contentType() {
        var headers = binary() ? binaryResponse.getHeaders() : jsonResponse.getHeaders();
        return headers.getContentType() == null ? null : headers.getContentType().toString();
    }

    public Integer binaryLength() {
        if (!binary() || binaryResponse.getBody() == null) {
            return null;
        }
        return binaryResponse.getBody().length;
    }

    public JsonNode responseJson() {
        return binary() ? null : jsonResponse.getBody();
    }
}
