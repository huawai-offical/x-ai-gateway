package com.prodigalgal.xaigateway.protocol.ingress.google;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/google/upload/v1beta")
public class GoogleNativeUploadNamespaceController {

    private final GeminiFilesController filesController;

    public GoogleNativeUploadNamespaceController(GeminiFilesController filesController) {
        this.filesController = filesController;
    }

    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<JsonNode> upload(
            @RequestHeader(value = "x-goog-api-key", required = false) String headerApiKey,
            @RequestParam(value = "key", required = false) String queryApiKey,
            @RequestPart("file") FilePart file,
            @RequestPart(value = "metadata", required = false) String metadata) {
        return filesController.upload(headerApiKey, queryApiKey, file, metadata);
    }

    @RequestMapping("/**")
    public ResponseEntity<?> unsupported() {
        return ResponseEntity.status(501).body(java.util.Map.of(
                "error", "NATIVE_PATH_UNSUPPORTED",
                "message", "该 Google upload native path 尚未显式兼容；当前支持 /google/upload/v1beta/files，更多路径请查看 /public/docs/compatibility。"
        ));
    }
}
