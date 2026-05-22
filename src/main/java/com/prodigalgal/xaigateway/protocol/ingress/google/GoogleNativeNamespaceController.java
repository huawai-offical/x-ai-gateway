package com.prodigalgal.xaigateway.protocol.ingress.google;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/google/v1beta")
public class GoogleNativeNamespaceController {

    private final GeminiGenerateContentController generateContentController;
    private final GeminiEmbeddingsController embeddingsController;
    private final GeminiFilesController filesController;

    public GoogleNativeNamespaceController(
            GeminiGenerateContentController generateContentController,
            GeminiEmbeddingsController embeddingsController,
            GeminiFilesController filesController) {
        this.generateContentController = generateContentController;
        this.embeddingsController = embeddingsController;
        this.filesController = filesController;
    }

    @PostMapping("/models/{model}:generateContent")
    public ResponseEntity<?> generateContent(
            @PathVariable String model,
            @RequestHeader(value = "x-goog-api-key", required = false) String headerApiKey,
            @RequestHeader(value = "X-AI-Gateway-Client-Family", required = false) String explicitClientFamily,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestParam(value = "key", required = false) String queryApiKey,
            @RequestBody GeminiGenerateContentRequest request) {
        return generateContentController.generateContent(model, headerApiKey, explicitClientFamily, userAgent, queryApiKey, request);
    }

    @PostMapping("/models/{model}:streamGenerateContent")
    public ResponseEntity<?> streamGenerateContent(
            @PathVariable String model,
            @RequestHeader(value = "x-goog-api-key", required = false) String headerApiKey,
            @RequestHeader(value = "X-AI-Gateway-Client-Family", required = false) String explicitClientFamily,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestParam(value = "key", required = false) String queryApiKey,
            @RequestBody GeminiGenerateContentRequest request) {
        return generateContentController.streamGenerateContent(model, headerApiKey, explicitClientFamily, userAgent, queryApiKey, request);
    }

    @PostMapping("/models/{model}:embedContent")
    public ResponseEntity<JsonNode> embedContent(
            @PathVariable String model,
            @RequestHeader(value = "x-goog-api-key", required = false) String headerApiKey,
            @RequestParam(value = "key", required = false) String queryApiKey,
            @RequestBody JsonNode request) {
        return embeddingsController.embedContent(model, headerApiKey, queryApiKey, request);
    }

    @PostMapping("/models/{model}:batchEmbedContents")
    public ResponseEntity<JsonNode> batchEmbedContents(
            @PathVariable String model,
            @RequestHeader(value = "x-goog-api-key", required = false) String headerApiKey,
            @RequestParam(value = "key", required = false) String queryApiKey,
            @RequestBody JsonNode request) {
        return embeddingsController.batchEmbedContents(model, headerApiKey, queryApiKey, request);
    }

    @GetMapping("/files")
    public JsonNode listFiles(
            @RequestHeader(value = "x-goog-api-key", required = false) String headerApiKey,
            @RequestParam(value = "key", required = false) String queryApiKey) {
        return filesController.list(headerApiKey, queryApiKey);
    }

    @GetMapping({"/files/{fileName}", "/files/files/{fileName}"})
    public JsonNode getFile(
            @RequestHeader(value = "x-goog-api-key", required = false) String headerApiKey,
            @RequestParam(value = "key", required = false) String queryApiKey,
            @PathVariable String fileName) {
        return filesController.get(headerApiKey, queryApiKey, fileName);
    }

    @DeleteMapping({"/files/{fileName}", "/files/files/{fileName}"})
    public ResponseEntity<Void> deleteFile(
            @RequestHeader(value = "x-goog-api-key", required = false) String headerApiKey,
            @RequestParam(value = "key", required = false) String queryApiKey,
            @PathVariable String fileName) {
        return filesController.delete(headerApiKey, queryApiKey, fileName);
    }

    @RequestMapping("/**")
    public ResponseEntity<?> unsupported() {
        return ResponseEntity.status(501).body(java.util.Map.of(
                "error", "NATIVE_PATH_UNSUPPORTED",
                "message", "该 Google native path 不属于当前 OpenAI 标准功能区；请使用 /google/v1beta/models/{model}:generateContent、embeddings、files，或查看 /public/docs/compatibility。"
        ));
    }
}
