package com.prodigalgal.xaigateway.protocol.ingress.google;

import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class GeminiFilesEncoder {

    private final ObjectMapper objectMapper;

    public GeminiFilesEncoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode encode(GatewayFileService.GoogleNativeFileView view) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", view.externalFileId());
        body.put("displayName", view.displayName());
        body.put("mimeType", view.mimeType());
        body.put("sizeBytes", Long.toString(view.response().bytes()));
        body.put("createTime", view.createdAt().toString());
        body.put("updateTime", view.updatedAt().toString());
        body.put("state", mapState(view.status()));
        return body;
    }

    public ObjectNode encodeList(List<GatewayFileService.GoogleNativeFileView> views) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode files = body.putArray("files");
        for (GatewayFileService.GoogleNativeFileView view : views) {
            files.add(encode(view));
        }
        return body;
    }

    private String mapState(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        return switch (status.trim().toLowerCase()) {
            case "processing" -> "PROCESSING";
            case "failed" -> "FAILED";
            case "deleted" -> "DELETED";
            default -> "ACTIVE";
        };
    }
}
