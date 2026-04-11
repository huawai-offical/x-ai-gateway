package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileContent;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileResponse;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/v1/files")
public class OpenAiFilesController {

    private final DistributedKeyAuthenticationService distributedKeyAuthenticationService;
    private final GatewayFileService gatewayFileService;

    public OpenAiFilesController(
            DistributedKeyAuthenticationService distributedKeyAuthenticationService,
            GatewayFileService gatewayFileService) {
        this.distributedKeyAuthenticationService = distributedKeyAuthenticationService;
        this.gatewayFileService = gatewayFileService;
    }

    @GetMapping
    public List<GatewayFileResponse> list(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayFileService.listFiles(distributedKey.id());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<GatewayFileResponse> upload(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestPart("file") FilePart file,
            @RequestPart(value = "purpose", required = false) String purpose) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayFileService.createFile(distributedKey.id(), file, purpose);
    }

    @GetMapping("/{fileId}")
    public GatewayFileResponse get(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String fileId) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayFileService.getFile(fileId, distributedKey.id());
    }

    @GetMapping("/{fileId}/content")
    public ResponseEntity<byte[]> content(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String fileId) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        GatewayFileContent content = gatewayFileService.getFileContent(fileId, distributedKey.id());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + content.metadata().filename() + "\"")
                .body(content.bytes());
    }

    @DeleteMapping("/{fileId}")
    public void delete(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String fileId) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        gatewayFileService.deleteFile(fileId, distributedKey.id());
    }
}
