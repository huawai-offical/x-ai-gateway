package com.prodigalgal.xaigateway.protocol.ingress.google;

import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class GeminiBatchesEncoder {

    private final ObjectMapper objectMapper;

    public GeminiBatchesEncoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode encode(GatewayAsyncResourceService.GoogleNativeBatchView view) {
        ObjectNode body = objectMapper.createObjectNode();
        String upstreamObjectId = view.metadata().path("upstream_object_id").asText(view.entity().getUpstreamObjectId());
        body.put("name", upstreamObjectId == null || upstreamObjectId.isBlank() ? "batches/" + view.entity().getResourceKey() : upstreamObjectId);
        body.put("model", view.responsePayload().path("model").asText(view.entity().getRequestModel()));
        body.put("state", mapState(view.responsePayload().path("status").asText(view.entity().getStatus())));
        body.put("createTime", view.entity().getCreatedAt().toString());
        body.put("updateTime", view.entity().getUpdatedAt().toString());
        if (view.responsePayload().hasNonNull("input_file_id")) {
            body.put("inputFile", view.responsePayload().path("input_file_id").asText());
        }
        return body;
    }

    private String mapState(String status) {
        if (status == null || status.isBlank()) {
            return "JOB_STATE_PENDING";
        }
        return switch (status.trim().toLowerCase()) {
            case "validating", "queued" -> "JOB_STATE_PENDING";
            case "running" -> "JOB_STATE_RUNNING";
            case "completed", "succeeded" -> "JOB_STATE_SUCCEEDED";
            case "cancelled" -> "JOB_STATE_CANCELLED";
            case "failed" -> "JOB_STATE_FAILED";
            default -> "JOB_STATE_PENDING";
        };
    }
}
