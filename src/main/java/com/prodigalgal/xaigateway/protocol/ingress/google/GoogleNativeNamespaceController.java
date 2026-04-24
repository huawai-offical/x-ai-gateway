package com.prodigalgal.xaigateway.protocol.ingress.google;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/google/v1beta")
public class GoogleNativeNamespaceController {

    private final GeminiGenerateContentController generateContentController;

    public GoogleNativeNamespaceController(GeminiGenerateContentController generateContentController) {
        this.generateContentController = generateContentController;
    }

    @PostMapping("/models/{model}:generateContent")
    public ResponseEntity<?> generateContent(
            @PathVariable String model,
            @RequestHeader(value = "x-goog-api-key", required = false) String headerApiKey,
            @RequestParam(value = "key", required = false) String queryApiKey,
            @RequestBody GeminiGenerateContentRequest request) {
        return generateContentController.generateContent(model, headerApiKey, queryApiKey, request);
    }

    @PostMapping("/models/{model}:streamGenerateContent")
    public ResponseEntity<?> streamGenerateContent(
            @PathVariable String model,
            @RequestHeader(value = "x-goog-api-key", required = false) String headerApiKey,
            @RequestParam(value = "key", required = false) String queryApiKey,
            @RequestBody GeminiGenerateContentRequest request) {
        return generateContentController.streamGenerateContent(model, headerApiKey, queryApiKey, request);
    }

    @RequestMapping("/**")
    public ResponseEntity<?> unsupported() {
        return ResponseEntity.status(501).body(java.util.Map.of(
                "error", "NATIVE_PATH_UNSUPPORTED",
                "message", "该 Google native path 尚未显式兼容；请使用 /google/v1beta/models/{model}:generateContent 或查看 /admin/native-compatibility/matrix。"
        ));
    }
}
