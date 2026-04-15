package com.prodigalgal.xaigateway.gateway.core.execution;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalFileRef;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileContent;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileResponse;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class FileObjectGatewayResourceExecutor implements GatewayResourceExecutor {

    private final GatewayFileService gatewayFileService;
    private final ObjectMapper objectMapper;

    public FileObjectGatewayResourceExecutor(
            GatewayFileService gatewayFileService,
            ObjectMapper objectMapper) {
        this.gatewayFileService = gatewayFileService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExecutionBackend backend() {
        return ExecutionBackend.ORCHESTRATION;
    }

    @Override
    public boolean supports(CanonicalResourceRequest request, CatalogCandidateView candidate) {
        if (request == null || request.normalizedPath() == null) {
            return false;
        }
        return "/v1/files".equals(request.normalizedPath())
                || "/v1/files/{fileId}".equals(request.normalizedPath())
                || "/v1/files/{fileId}/content".equals(request.normalizedPath());
    }

    @Override
    public ResponseEntity<JsonNode> executeJson(
            GatewayResourceExecutionContext context,
            JsonNode requestBody,
            String defaultModel) {
        return switch (context.httpMethod()) {
            case "GET" -> executeGet(context);
            case "DELETE" -> executeDelete(context);
            default -> throw new IllegalArgumentException("当前文件对象执行仅支持 GET/DELETE/multipart create。");
        };
    }

    @Override
    public ResponseEntity<byte[]> executeBinary(
            GatewayResourceExecutionContext context,
            JsonNode requestBody,
            String defaultModel) {
        if (!"GET".equals(context.httpMethod()) || !"/v1/files/{fileId}/content".equals(context.normalizedPath())) {
            throw new IllegalArgumentException("当前文件对象二进制执行仅支持 /v1/files/{fileId}/content。");
        }
        String fileId = requirePathParam(context, "fileId");
        GatewayFileContent content = gatewayFileService.getFileContent(fileId, context.distributedKeyId());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + content.metadata().filename() + "\"")
                .body(content.bytes());
    }

    @Override
    public Mono<ResponseEntity<JsonNode>> executeMultipart(
            GatewayResourceExecutionContext context,
            String requestedModel,
            Map<String, String> formFields,
            Map<String, FilePart> files) {
        if (!"POST".equals(context.httpMethod()) || !"/v1/files".equals(context.normalizedPath())) {
            return Mono.error(new IllegalArgumentException("当前文件对象 multipart 执行仅支持 /v1/files create。"));
        }
        String purpose = formFields.get("purpose");
        FilePart rawFile = files.get("file");
        if (rawFile != null) {
            return gatewayFileService.createFile(
                            context.distributedKeyId(),
                            rawFile,
                            purpose,
                            context.selectionResult() == null ? null : context.selectionResult().selectedCandidate().candidate().credentialId()
                    )
                    .map(file -> ResponseEntity.ok(objectMapper.valueToTree(file)));
        }
        List<CanonicalFileRef> fileRefs = context.request().fileRefs();
        if (fileRefs == null || fileRefs.isEmpty()) {
            return Mono.error(new IllegalArgumentException("files create 缺少 file 或 fileRef。"));
        }
        CanonicalFileRef fileRef = fileRefs.getFirst();
        GatewayFileResponse created = gatewayFileService.createFileFromExisting(
                context.distributedKeyId(),
                fileRef.fileKey(),
                purpose,
                context.selectionResult() == null ? null : context.selectionResult().selectedCandidate().candidate().credentialId()
        );
        return Mono.just(ResponseEntity.ok(objectMapper.valueToTree(created)));
    }

    private ResponseEntity<JsonNode> executeGet(GatewayResourceExecutionContext context) {
        if ("/v1/files".equals(context.normalizedPath())) {
            return ResponseEntity.ok(objectMapper.valueToTree(gatewayFileService.listFiles(context.distributedKeyId())));
        }
        if ("/v1/files/{fileId}".equals(context.normalizedPath())) {
            String fileId = requirePathParam(context, "fileId");
            return ResponseEntity.ok(objectMapper.valueToTree(gatewayFileService.getFile(fileId, context.distributedKeyId())));
        }
        throw new IllegalArgumentException("当前文件对象 GET 路径不受支持。");
    }

    private ResponseEntity<JsonNode> executeDelete(GatewayResourceExecutionContext context) {
        if (!"/v1/files/{fileId}".equals(context.normalizedPath())) {
            throw new IllegalArgumentException("当前文件对象 DELETE 路径不受支持。");
        }
        String fileId = requirePathParam(context, "fileId");
        gatewayFileService.deleteFile(fileId, context.distributedKeyId());
        return ResponseEntity.ok(objectMapper.createObjectNode()
                .put("id", fileId)
                .put("object", "file.deleted")
                .put("deleted", true));
    }

    private String requirePathParam(GatewayResourceExecutionContext context, String key) {
        String value = context.request().pathParams().get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少路径参数：" + key);
        }
        return value;
    }
}
