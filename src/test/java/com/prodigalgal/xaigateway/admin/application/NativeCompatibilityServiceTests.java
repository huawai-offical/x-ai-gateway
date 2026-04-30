package com.prodigalgal.xaigateway.admin.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeCompatibilityServiceTests {

    @Test
    void shouldExposeNativeCompatibilityMatrix() {
        NativeCompatibilityService service = new NativeCompatibilityService();

        var response = service.matrix();

        assertTrue(response.routes().stream().anyMatch(route -> route.protocol().equals("ollama") && route.path().equals("/ollama/api/chat")));
        assertTrue(response.routes().stream().anyMatch(route -> route.protocol().equals("anthropic") && route.status().equals("EXPLICIT_UNSUPPORTED")));
        assertTrue(response.routes().stream().anyMatch(route -> route.namespace().equals("/google/upload/v1beta") && route.path().equals("/google/upload/v1beta/files")));
        assertTrue(response.routes().stream().anyMatch(route -> route.namespace().equals("/v1beta") && route.status().equals("SUPPORTED_GOVERNED")));
        assertTrue(response.routes().stream().allMatch(route -> route.authenticated()));
    }
}
