package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.NativeCompatibilityResponse;
import com.prodigalgal.xaigateway.admin.api.NativeCompatibilityRoute;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NativeCompatibilityService {

    public NativeCompatibilityResponse matrix() {
        return new NativeCompatibilityResponse(List.of(
                new NativeCompatibilityRoute("ollama", "/ollama/api", "GET", "/ollama/api/tags", "SUPPORTED", true, "AUTH_GOVERNED", "列出当前 key 可访问模型，复用分发 Key 鉴权与模型目录。"),
                new NativeCompatibilityRoute("ollama", "/ollama/api", "POST", "/ollama/api/chat", "SUPPORTED", true, "AUTH_GOVERNED", "Ollama chat 请求转换为 canonical chat，响应转回 Ollama message 结构。"),
                new NativeCompatibilityRoute("anthropic", "/anthropic/v1", "POST", "/anthropic/v1/messages", "ALIAS", true, "AUTH_GOVERNED", "命名空间别名复用现有 `/v1/messages` 实现。"),
                new NativeCompatibilityRoute("anthropic", "/anthropic/v1", "*", "/anthropic/v1/**", "EXPLICIT_UNSUPPORTED", true, "AUTH_GOVERNED", "非显式支持路径返回兼容矩阵，不做未治理透明代理。"),
                new NativeCompatibilityRoute("google", "/google/v1beta", "POST", "/google/v1beta/models/{model}:generateContent", "ALIAS", true, "AUTH_GOVERNED", "命名空间别名复用现有 Gemini generateContent 实现。"),
                new NativeCompatibilityRoute("google", "/google/v1beta", "*", "/google/v1beta/**", "EXPLICIT_UNSUPPORTED", true, "AUTH_GOVERNED", "非显式支持路径返回兼容矩阵，不做未治理透明代理。")
        ));
    }
}
