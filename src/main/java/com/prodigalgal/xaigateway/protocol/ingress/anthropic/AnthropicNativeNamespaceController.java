package com.prodigalgal.xaigateway.protocol.ingress.anthropic;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/anthropic/v1")
public class AnthropicNativeNamespaceController {

    private final AnthropicMessagesController messagesController;

    public AnthropicNativeNamespaceController(AnthropicMessagesController messagesController) {
        this.messagesController = messagesController;
    }

    @PostMapping("/messages")
    public ResponseEntity<?> messages(
            @RequestHeader("x-api-key") String apiKey,
            @RequestHeader(value = "X-AI-Gateway-Client-Family", required = false) String explicitClientFamily,
            @RequestHeader(value = "anthropic-beta", required = false) String anthropicBeta,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestBody AnthropicMessagesRequest request) {
        return messagesController.createMessage(apiKey, explicitClientFamily, anthropicBeta, userAgent, request);
    }

    @RequestMapping("/**")
    public ResponseEntity<?> unsupported() {
        return ResponseEntity.status(501).body(java.util.Map.of(
                "error", "NATIVE_PATH_UNSUPPORTED",
                "message", "该 Anthropic native path 不属于当前 OpenAI 标准功能区；请使用 /anthropic/v1/messages，或查看 /admin/native-compatibility/matrix。"
        ));
    }
}
