package com.prodigalgal.xaigateway.protocol.ingress.google;

import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionResult;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class GeminiGenerateContentResourceEncoder {

    public GeminiGenerateContentResponse encodeImageGeneration(GatewayResourceExecutionResult result) {
        JsonNode data = result.responseJson() == null ? null : result.responseJson().path("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            throw new IllegalStateException("image generation 响应缺少 data。");
        }
        List<GeminiGenerateContentResponse.Part> parts = new ArrayList<>();
        for (JsonNode item : data) {
            String b64 = item.path("b64_json").asText(null);
            if (b64 == null || b64.isBlank()) {
                continue;
            }
            parts.add(new GeminiGenerateContentResponse.Part(
                    null,
                    null,
                    new GeminiGenerateContentResponse.InlineData("image/png", b64)
            ));
        }
        if (parts.isEmpty()) {
            throw new IllegalStateException("image generation 响应未返回有效图片。");
        }
        return response(parts);
    }

    public GeminiGenerateContentResponse encodeAudioSpeech(GatewayResourceExecutionResult result) {
        byte[] body = result.binaryResponse() == null ? null : result.binaryResponse().getBody();
        if (body == null || body.length == 0) {
            throw new IllegalStateException("audio speech 响应缺少音频数据。");
        }
        String mimeType = result.contentType();
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return response(List.of(new GeminiGenerateContentResponse.Part(
                null,
                null,
                new GeminiGenerateContentResponse.InlineData(mimeType, Base64.getEncoder().encodeToString(body))
        )));
    }

    private GeminiGenerateContentResponse response(List<GeminiGenerateContentResponse.Part> parts) {
        return new GeminiGenerateContentResponse(
                List.of(new GeminiGenerateContentResponse.Candidate(
                        new GeminiGenerateContentResponse.Content(parts, "model"),
                        "STOP"
                )),
                new GeminiGenerateContentResponse.UsageMetadata(0, 0, 0, 0, 0)
        );
    }
}
