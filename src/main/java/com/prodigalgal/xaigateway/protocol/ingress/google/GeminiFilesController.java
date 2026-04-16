package com.prodigalgal.xaigateway.protocol.ingress.google;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping
public class GeminiFilesController {

    private static final String API_KEY_HEADER = "x-goog-api-key";

    private final DistributedKeyAuthenticationService distributedKeyAuthenticationService;
    private final GatewayFileService gatewayFileService;
    private final GeminiFilesEncoder geminiFilesEncoder;
    private final ObjectMapper objectMapper;

    public GeminiFilesController(
            DistributedKeyAuthenticationService distributedKeyAuthenticationService,
            GatewayFileService gatewayFileService,
            GeminiFilesEncoder geminiFilesEncoder,
            ObjectMapper objectMapper) {
        this.distributedKeyAuthenticationService = distributedKeyAuthenticationService;
        this.gatewayFileService = gatewayFileService;
        this.geminiFilesEncoder = geminiFilesEncoder;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/upload/v1beta/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<JsonNode> upload(
            @RequestHeader(value = API_KEY_HEADER, required = false) String headerApiKey,
            @RequestParam(value = "key", required = false) String queryApiKey,
            @RequestPart("file") FilePart file,
            @RequestPart(value = "metadata", required = false) String metadata) {
        AuthenticatedDistributedKey distributedKey = authenticate(headerApiKey, queryApiKey);
        return gatewayFileService.createGoogleNativeFile(distributedKey.id(), file, null, extractDisplayName(metadata))
                .map(geminiFilesEncoder::encode);
    }

    @GetMapping("/v1beta/files")
    public JsonNode list(
            @RequestHeader(value = API_KEY_HEADER, required = false) String headerApiKey,
            @RequestParam(value = "key", required = false) String queryApiKey) {
        AuthenticatedDistributedKey distributedKey = authenticate(headerApiKey, queryApiKey);
        return geminiFilesEncoder.encodeList(gatewayFileService.listGoogleNativeFiles(distributedKey.id()));
    }

    @GetMapping({"/v1beta/files/{fileName}", "/v1beta/files/files/{fileName}"})
    public JsonNode get(
            @RequestHeader(value = API_KEY_HEADER, required = false) String headerApiKey,
            @RequestParam(value = "key", required = false) String queryApiKey,
            @PathVariable String fileName) {
        AuthenticatedDistributedKey distributedKey = authenticate(headerApiKey, queryApiKey);
        return geminiFilesEncoder.encode(gatewayFileService.getGoogleNativeFile(normalizeFileName(fileName), distributedKey.id()));
    }

    @DeleteMapping({"/v1beta/files/{fileName}", "/v1beta/files/files/{fileName}"})
    public ResponseEntity<Void> delete(
            @RequestHeader(value = API_KEY_HEADER, required = false) String headerApiKey,
            @RequestParam(value = "key", required = false) String queryApiKey,
            @PathVariable String fileName) {
        AuthenticatedDistributedKey distributedKey = authenticate(headerApiKey, queryApiKey);
        gatewayFileService.deleteGoogleNativeFile(normalizeFileName(fileName), distributedKey.id());
        return ResponseEntity.noContent().build();
    }

    private AuthenticatedDistributedKey authenticate(String headerApiKey, String queryApiKey) {
        String token = StringUtils.hasText(headerApiKey) ? headerApiKey : queryApiKey;
        return distributedKeyAuthenticationService.authenticateRawToken(token);
    }

    private String normalizeFileName(String fileName) {
        return fileName.startsWith("files/") ? fileName : "files/" + fileName;
    }

    private String extractDisplayName(String metadata) {
        if (!StringUtils.hasText(metadata)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(metadata);
            String direct = node.path("displayName").asText(null);
            if (StringUtils.hasText(direct)) {
                return direct;
            }
            String nested = node.path("file").path("displayName").asText(null);
            return StringUtils.hasText(nested) ? nested : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
