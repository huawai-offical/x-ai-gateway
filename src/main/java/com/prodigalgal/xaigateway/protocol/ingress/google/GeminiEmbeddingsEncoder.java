package com.prodigalgal.xaigateway.protocol.ingress.google;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class GeminiEmbeddingsEncoder {

    private final ObjectMapper objectMapper;

    public GeminiEmbeddingsEncoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode encodeSingle(JsonNode responseBody) {
        JsonNode embedding = firstEmbedding(responseBody);
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode output = body.putObject("embedding");
        output.set("values", embedding.deepCopy());
        return body;
    }

    public ObjectNode encodeBatch(JsonNode responseBody) {
        ArrayNode embeddings = responseBody != null && responseBody.path("data").isArray()
                ? (ArrayNode) responseBody.path("data")
                : objectMapper.createArrayNode();
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode output = body.putArray("embeddings");
        for (JsonNode item : embeddings) {
            JsonNode embedding = item.path("embedding");
            if (!embedding.isArray()) {
                throw new IllegalStateException("batch embeddings 响应缺少 embedding 数组。");
            }
            output.addObject().set("values", embedding.deepCopy());
        }
        return body;
    }

    private JsonNode firstEmbedding(JsonNode responseBody) {
        JsonNode data = responseBody == null ? null : responseBody.path("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            throw new IllegalStateException("embeddings 响应缺少 data。");
        }
        JsonNode embedding = data.get(0).path("embedding");
        if (!embedding.isArray()) {
            throw new IllegalStateException("embeddings 响应缺少 embedding 数组。");
        }
        return embedding;
    }
}
