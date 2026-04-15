package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalContentPart;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalPartType;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteCapabilitySnapshotEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import tools.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ExecutionBackendPolicyService {

    public ExecutionBackendDecision forSemantics(GatewayRequestSemantics semantics) {
        return new ExecutionBackendDecision(
                preferredBackendForSemantics(semantics),
                supportedBackendsForSemantics(semantics),
                backendReason(preferredBackendForSemantics(semantics), semantics, null, null)
        );
    }

    public ExecutionBackendDecision forCandidate(
            CatalogCandidateView candidate,
            GatewayRequestSemantics semantics,
            CanonicalRequest canonicalRequest,
            JsonNode requestBody) {
        if (candidate == null) {
            return forSemantics(semantics);
        }
        List<ExecutionBackend> supportedBackends = supportedBackends(candidate.providerType(), candidate.siteKind(), semantics);
        if (supportedBackends.isEmpty()) {
            return new ExecutionBackendDecision(ExecutionBackend.PASSTHROUGH, List.of(), "当前候选不存在可用执行后端。");
        }

        ExecutionBackend preferredBackend = preferredBackend(
                candidate.providerType(),
                candidate.siteKind(),
                semantics,
                canonicalRequest,
                requestBody,
                supportedBackends
        );
        String reason = backendReason(preferredBackend, semantics, canonicalRequest, requestBody);
        return new ExecutionBackendDecision(preferredBackend, supportedBackends, reason);
    }

    public ExecutionBackendDecision forSiteSurface(
            UpstreamSiteProfileEntity siteProfile,
            SiteCapabilitySnapshotEntity snapshot,
            TranslationResourceType resourceType,
            TranslationOperation operation) {
        GatewayRequestSemantics semantics = new GatewayRequestSemantics(resourceType, operation, List.of(), true);
        ProviderType providerType = providerTypeForSite(siteProfile == null ? null : siteProfile.getSiteKind());
        List<ExecutionBackend> supportedBackends = supportedBackends(providerType, siteProfile == null ? null : siteProfile.getSiteKind(), semantics);
        ExecutionBackend preferredBackend = supportedBackends.isEmpty()
                ? ExecutionBackend.PASSTHROUGH
                : preferredBackend(providerType, siteProfile == null ? null : siteProfile.getSiteKind(), semantics, null, null, supportedBackends);
        return new ExecutionBackendDecision(
                preferredBackend,
                supportedBackends,
                backendReason(preferredBackend, semantics, null, null)
        );
    }

    private List<ExecutionBackend> supportedBackends(
            ProviderType providerType,
            UpstreamSiteKind siteKind,
            GatewayRequestSemantics semantics) {
        if (providerType == null || siteKind == null) {
            return supportedBackendsForSemantics(semantics);
        }
        if (semantics == null) {
            return List.of();
        }
        if (semantics.resourceType() == TranslationResourceType.CHAT
                || semantics.resourceType() == TranslationResourceType.RESPONSE) {
            return switch (providerType) {
                case OPENAI_DIRECT, OPENAI_COMPATIBLE -> List.of(
                        ExecutionBackend.SPRING_AI,
                        ExecutionBackend.NATIVE
                );
                case ANTHROPIC_DIRECT, GEMINI_DIRECT -> List.of(
                        ExecutionBackend.SPRING_AI,
                        ExecutionBackend.NATIVE
                );
                case OLLAMA_DIRECT -> List.of(ExecutionBackend.NATIVE);
            };
        }
        if (semantics.resourceType() == TranslationResourceType.EMBEDDING) {
            return switch (providerType) {
                case OPENAI_DIRECT, OPENAI_COMPATIBLE, GEMINI_DIRECT -> List.of(ExecutionBackend.NATIVE);
                case ANTHROPIC_DIRECT, OLLAMA_DIRECT -> List.of();
            };
        }
        if (semantics.resourceType() == TranslationResourceType.AUDIO
                || semantics.resourceType() == TranslationResourceType.IMAGE
                || semantics.resourceType() == TranslationResourceType.MODERATION) {
            return List.of(ExecutionBackend.PASSTHROUGH);
        }
        if (semantics.resourceType() == TranslationResourceType.FILE
                || semantics.resourceType() == TranslationResourceType.UPLOAD
                || semantics.resourceType() == TranslationResourceType.BATCH
                || semantics.resourceType() == TranslationResourceType.TUNING
                || semantics.resourceType() == TranslationResourceType.REALTIME) {
            return List.of(ExecutionBackend.ORCHESTRATION);
        }
        return List.of();
    }

    private List<ExecutionBackend> supportedBackendsForSemantics(GatewayRequestSemantics semantics) {
        if (semantics == null) {
            return List.of();
        }
        return switch (semantics.resourceType()) {
            case EMBEDDING -> List.of(ExecutionBackend.NATIVE);
            case AUDIO, IMAGE, MODERATION -> List.of(ExecutionBackend.PASSTHROUGH);
            case FILE, UPLOAD, BATCH, TUNING, REALTIME -> List.of(ExecutionBackend.ORCHESTRATION);
            case CHAT, RESPONSE, UNKNOWN -> List.of();
        };
    }

    private ExecutionBackend preferredBackendForSemantics(GatewayRequestSemantics semantics) {
        List<ExecutionBackend> supportedBackends = supportedBackendsForSemantics(semantics);
        return supportedBackends.isEmpty() ? ExecutionBackend.PASSTHROUGH : supportedBackends.getFirst();
    }

    private ExecutionBackend preferredBackend(
            ProviderType providerType,
            UpstreamSiteKind siteKind,
            GatewayRequestSemantics semantics,
            CanonicalRequest canonicalRequest,
            JsonNode requestBody,
            List<ExecutionBackend> supportedBackends) {
        if (supportedBackends.size() == 1) {
            return supportedBackends.getFirst();
        }
        if (siteKind == UpstreamSiteKind.OLLAMA_DIRECT || providerType == ProviderType.OLLAMA_DIRECT) {
            return ExecutionBackend.NATIVE;
        }
        if (semantics.operation() == TranslationOperation.RESPONSE_CREATE) {
            return ExecutionBackend.NATIVE;
        }
        if (requiresNativeByFeature(semantics, canonicalRequest, requestBody)) {
            return supportedBackends.contains(ExecutionBackend.NATIVE)
                    ? ExecutionBackend.NATIVE
                    : supportedBackends.getFirst();
        }
        return supportedBackends.contains(ExecutionBackend.SPRING_AI)
                ? ExecutionBackend.SPRING_AI
                : supportedBackends.getFirst();
    }

    private boolean requiresNativeByFeature(
            GatewayRequestSemantics semantics,
            CanonicalRequest canonicalRequest,
            JsonNode requestBody) {
        if (semantics == null) {
            return false;
        }
        if (semantics.requiredFeatures().contains(InteropFeature.REASONING)
                || semantics.requiredFeatures().contains(InteropFeature.FILE_INPUT)
                || semantics.requiredFeatures().contains(InteropFeature.IMAGE_INPUT)) {
            return true;
        }
        boolean streaming = requestBody != null && requestBody.path("stream").asBoolean(false);
        boolean toolStreaming = streaming
                && (semantics.requiredFeatures().contains(InteropFeature.TOOLS)
                || canonicalRequest != null && canonicalRequest.tools() != null && !canonicalRequest.tools().isEmpty());
        if (toolStreaming) {
            return true;
        }
        if (canonicalRequest == null || canonicalRequest.messages() == null) {
            return false;
        }
        for (CanonicalMessage message : canonicalRequest.messages()) {
            if (message.parts() == null) {
                continue;
            }
            for (CanonicalContentPart part : message.parts()) {
                if (part.type() == CanonicalPartType.FILE || part.type() == CanonicalPartType.IMAGE) {
                    return true;
                }
            }
        }
        return false;
    }

    private String backendReason(
            ExecutionBackend backend,
            GatewayRequestSemantics semantics,
            CanonicalRequest canonicalRequest,
            JsonNode requestBody) {
        return switch (backend) {
            case SPRING_AI -> "默认聊天链使用 Spring AI executor。";
            case NATIVE -> nativeReason(semantics, canonicalRequest, requestBody);
            case ORCHESTRATION -> "当前对象生命周期接口采用统一 orchestration executor。";
            case PASSTHROUGH -> "当前资源接口采用高保真 passthrough/orchestration。";
        };
    }

    private String nativeReason(
            GatewayRequestSemantics semantics,
            CanonicalRequest canonicalRequest,
            JsonNode requestBody) {
        if (semantics == null) {
            return "当前路径优先使用 native executor。";
        }
        if (semantics.operation() == TranslationOperation.RESPONSE_CREATE) {
            return "responses 路径优先使用 native executor 以保留对象与事件语义。";
        }
        if (semantics.requiredFeatures().contains(InteropFeature.REASONING)) {
            return "当前请求包含 reasoning，优先使用 native executor。";
        }
        if (semantics.requiredFeatures().contains(InteropFeature.FILE_INPUT)
                || semantics.requiredFeatures().contains(InteropFeature.IMAGE_INPUT)) {
            return "当前请求包含文件或图片输入，优先使用 native executor。";
        }
        boolean streaming = requestBody != null && requestBody.path("stream").asBoolean(false);
        if (streaming && semantics.requiredFeatures().contains(InteropFeature.TOOLS)) {
            return "当前请求为 tools + stream 组合，优先使用 native executor。";
        }
        if (canonicalRequest != null && canonicalRequest.tools() != null && !canonicalRequest.tools().isEmpty() && streaming) {
            return "当前请求为 tools + stream 组合，优先使用 native executor。";
        }
        return "当前路径优先使用 native executor。";
    }

    public List<ExecutionBackend> supportedBackendsForSurface(
            TranslationResourceType resourceType,
            TranslationOperation operation,
            ProviderType providerType,
            UpstreamSiteKind siteKind) {
        return supportedBackends(providerType, siteKind, new GatewayRequestSemantics(resourceType, operation, List.of(), true));
    }

    public ExecutionBackend preferredBackendForSurface(
            TranslationResourceType resourceType,
            TranslationOperation operation,
            ProviderType providerType,
            UpstreamSiteKind siteKind) {
        List<ExecutionBackend> supported = supportedBackendsForSurface(resourceType, operation, providerType, siteKind);
        if (supported.isEmpty()) {
            return ExecutionBackend.PASSTHROUGH;
        }
        return preferredBackend(
                providerType,
                siteKind,
                new GatewayRequestSemantics(resourceType, operation, List.of(), true),
                null,
                null,
                supported
        );
    }

    public ProviderType providerTypeForSite(UpstreamSiteKind siteKind) {
        if (siteKind == null) {
            return ProviderType.OPENAI_COMPATIBLE;
        }
        return switch (siteKind) {
            case ANTHROPIC_DIRECT -> ProviderType.ANTHROPIC_DIRECT;
            case GEMINI_DIRECT, VERTEX_AI -> ProviderType.GEMINI_DIRECT;
            case OLLAMA_DIRECT -> ProviderType.OLLAMA_DIRECT;
            default -> ProviderType.OPENAI_COMPATIBLE;
        };
    }
}
