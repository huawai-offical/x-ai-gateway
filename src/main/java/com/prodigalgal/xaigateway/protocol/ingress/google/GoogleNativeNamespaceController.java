package com.prodigalgal.xaigateway.protocol.ingress.google;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/google/v1beta")
public class GoogleNativeNamespaceController {

    private final GeminiGenerateContentController generateContentController;
    private final GeminiEmbeddingsController embeddingsController;
    private final GeminiBatchesController batchesController;
    private final GeminiFilesController filesController;

    public GoogleNativeNamespaceController(
            GeminiGenerateContentController generateContentController,
            GeminiEmbeddingsController embeddingsController,
            GeminiBatchesController batchesController,
            GeminiFilesController filesController) {
        this.generateContentController = generateContentController;
        this.embeddingsController = embeddingsController;
        this.batchesController = batchesController;
        this.filesController = filesController;
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

    @PostMapping("/models/{model}:batchGenerateContent")
    public JsonNode createBatch(
            @PathVariable String model,
            @RequestHeader(value = "x-goog-api-key", required = false) String headerApiKey,
            @RequestParam(value = "key", required = false) String queryApiKey,
            @RequestBody JsonNode request) {
        return batchesController.createBatch(model, headerApiKey, queryApiKey, request);
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

    @GetMapping({"/batches/{batchName}", "/batches/batches/{batchName}"})
    public JsonNode getBatch(
            @RequestHeader(value = "x-goog-api-key", required = false) String headerApiKey,
            @RequestParam(value = "key", required = false) String queryApiKey,
            @PathVariable String batchName) {
        return batchesController.getBatch(headerApiKey, queryApiKey, batchName);
    }

    @PostMapping({"/batches/{batchName}:cancel", "/batches/batches/{batchName}:cancel"})
    public JsonNode cancelBatch(
            @RequestHeader(value = "x-goog-api-key", required = false) String headerApiKey,
            @RequestParam(value = "key", required = false) String queryApiKey,
            @PathVariable String batchName) {
        return batchesController.cancelBatch(headerApiKey, queryApiKey, batchName);
    }

    @RequestMapping("/**")
    public ResponseEntity<?> unsupported() {
        return ResponseEntity.status(501).body(java.util.Map.of(
                "error", "NATIVE_PATH_UNSUPPORTED",
                "message", "该 Google native path 尚未显式兼容；请使用 /google/v1beta/models/{model}:generateContent、/google/v1beta/files、/google/v1beta/batches 或查看 /admin/native-compatibility/matrix。"
        ));
    }
}
