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
                new NativeCompatibilityRoute("google", "/google/v1beta", "POST", "/google/v1beta/models/{model}:embedContent", "ALIAS", true, "AUTH_GOVERNED", "命名空间别名复用现有 Gemini embedding 实现。"),
                new NativeCompatibilityRoute("google", "/google/v1beta", "POST", "/google/v1beta/models/{model}:batchEmbedContents", "ALIAS", true, "AUTH_GOVERNED", "命名空间别名复用现有 Gemini batch embedding 实现。"),
                new NativeCompatibilityRoute("google", "/google/v1beta", "POST", "/google/v1beta/models/{model}:batchGenerateContent", "ALIAS", true, "AUTH_GOVERNED", "命名空间别名复用现有 Gemini batch lifecycle 实现。"),
                new NativeCompatibilityRoute("google", "/google/v1beta", "GET", "/google/v1beta/files", "ALIAS", true, "AUTH_GOVERNED", "命名空间别名复用现有 Google native file list，实现本地目录治理。"),
                new NativeCompatibilityRoute("google", "/google/v1beta", "GET", "/google/v1beta/files/{fileName}", "ALIAS", true, "AUTH_GOVERNED", "命名空间别名复用现有 Google native file get，并保持 lineage 校验。"),
                new NativeCompatibilityRoute("google", "/google/v1beta", "DELETE", "/google/v1beta/files/{fileName}", "ALIAS", true, "AUTH_GOVERNED", "命名空间别名复用现有 Google native file delete，并保持 lineage 校验。"),
                new NativeCompatibilityRoute("google", "/google/v1beta", "GET", "/google/v1beta/batches/{batchName}", "ALIAS", true, "AUTH_GOVERNED", "命名空间别名复用现有 Gemini batch get，并保持 lineage 校验。"),
                new NativeCompatibilityRoute("google", "/google/v1beta", "POST", "/google/v1beta/batches/{batchName}:cancel", "ALIAS", true, "AUTH_GOVERNED", "命名空间别名复用现有 Gemini batch cancel，并保持 lineage 校验。"),
                new NativeCompatibilityRoute("google", "/google/upload/v1beta", "POST", "/google/upload/v1beta/files", "ALIAS", true, "AUTH_GOVERNED", "Google upload namespace 复用现有 `/upload/v1beta/files` 文件上传实现。"),
                new NativeCompatibilityRoute("google", "/upload/v1beta", "POST", "/upload/v1beta/files", "SUPPORTED", true, "AUTH_GOVERNED", "通用 Google upload namespace 原生支持文件上传。"),
                new NativeCompatibilityRoute("google", "/v1beta", "*", "/v1beta/**", "SUPPORTED_GOVERNED", true, "AUTH_GOVERNED", "通用 Gemini `/v1beta` 已支持 models/files/batches 已建模路径，未知路径显式拒绝。"),
                new NativeCompatibilityRoute("google", "/google/v1beta", "*", "/google/v1beta/**", "EXPLICIT_UNSUPPORTED", true, "AUTH_GOVERNED", "非显式支持路径返回兼容矩阵，不做未治理透明代理。"),
                new NativeCompatibilityRoute("google", "/google/upload/v1beta", "*", "/google/upload/v1beta/**", "EXPLICIT_UNSUPPORTED", true, "AUTH_GOVERNED", "非显式支持 upload path 返回兼容矩阵，不做未治理透明代理。")
        ));
    }
}
