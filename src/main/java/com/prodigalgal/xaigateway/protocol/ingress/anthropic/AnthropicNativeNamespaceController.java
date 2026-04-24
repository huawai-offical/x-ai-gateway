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
            @RequestBody AnthropicMessagesRequest request) {
        return messagesController.createMessage(apiKey, request);
    }

    @RequestMapping("/**")
    public ResponseEntity<?> unsupported() {
        return ResponseEntity.status(501).body(java.util.Map.of(
                "error", "NATIVE_PATH_UNSUPPORTED",
                "message", "该 Anthropic native path 尚未显式兼容；请使用 /anthropic/v1/messages 或查看 /admin/native-compatibility/matrix。"
        ));
    }
}
