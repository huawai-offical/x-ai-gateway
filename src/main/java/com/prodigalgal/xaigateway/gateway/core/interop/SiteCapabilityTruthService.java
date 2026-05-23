package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.execution.ExecutionBackendDecision;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.gateway.core.site.UpstreamSitePolicyService;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteCapabilitySnapshotEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteCapabilitySnapshotRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SiteCapabilityTruthService {

    private final UpstreamSitePolicyService upstreamSitePolicyService;
    private final SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository;
    private final ExecutionSupportMatrixService executionSupportMatrixService;

    public SiteCapabilityTruthService(
            UpstreamSitePolicyService upstreamSitePolicyService,
            SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository) {
        this(upstreamSitePolicyService, siteCapabilitySnapshotRepository, new ExecutionSupportMatrixService());
    }

    @Autowired
    public SiteCapabilityTruthService(
            UpstreamSitePolicyService upstreamSitePolicyService,
            SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository,
            ExecutionSupportMatrixService executionSupportMatrixService) {
        this.upstreamSitePolicyService = upstreamSitePolicyService;
        this.siteCapabilitySnapshotRepository = siteCapabilitySnapshotRepository;
        this.executionSupportMatrixService = executionSupportMatrixService;
    }

    public InteropCapabilityLevel capabilityLevel(CatalogCandidateView candidate, InteropFeature feature) {
        if (candidate == null || feature == null) {
            return InteropCapabilityLevel.UNSUPPORTED;
        }
        return resolve(
                candidate,
                new GatewayRequestSemantics(
                        TranslationResourceType.UNKNOWN,
                        TranslationOperation.UNKNOWN,
                        List.of(feature),
                        true
                )
        ).overallEffectiveLevel();
    }

    public boolean supportsFeature(
            UpstreamSiteProfileEntity siteProfile,
            SiteCapabilitySnapshotEntity snapshot,
            InteropFeature feature) {
        return resolve(siteProfile, snapshot, feature).effectiveLevel() != InteropCapabilityLevel.UNSUPPORTED;
    }

    public CapabilityResolution resolve(
            UpstreamSiteProfileEntity siteProfile,
            SiteCapabilitySnapshotEntity snapshot,
            InteropFeature feature) {
        if (siteProfile == null || feature == null) {
            return unsupportedResolution(feature, "未找到站点档案。");
        }
        UpstreamSiteKind siteKind = siteProfile.getSiteKind();
        InteropCapabilityLevel declaredLevel = declaredLevel(
                siteKind,
                snapshot,
                siteSupportsChat(siteKind),
                siteSupportsTools(siteKind),
                siteSupportsImageInput(siteKind),
                siteSupportsEmbeddings(siteKind),
                siteSupportsThinking(siteKind),
                feature
        );
        InteropCapabilityLevel implementedLevel = siteLevelImplementedLevel(siteProfile.getSiteKind(), feature);
        InteropCapabilityLevel effectiveLevel = minimumLevel(declaredLevel, implementedLevel);

        java.util.ArrayList<String> blockedReasons = new java.util.ArrayList<>();
        java.util.ArrayList<String> lossReasons = new java.util.ArrayList<>();
        if (effectiveLevel == InteropCapabilityLevel.UNSUPPORTED) {
            String providerSpecificReason = providerSpecificBlockedReason(siteProfile.getSiteKind(), feature);
            if (providerSpecificReason != null) {
                blockedReasons.add(providerSpecificReason);
            } else {
                if (declaredLevel == InteropCapabilityLevel.UNSUPPORTED) {
                    blockedReasons.add(feature.wireName() + " 当前站点声明不支持。");
                }
                if (implementedLevel == InteropCapabilityLevel.UNSUPPORTED) {
                    blockedReasons.add(feature.wireName() + " 当前实现尚未落地。");
                }
            }
        } else if (effectiveLevel == InteropCapabilityLevel.EMULATED) {
            lossReasons.add(feature.wireName() + " 以 emulated 执行。");
        } else if (effectiveLevel == InteropCapabilityLevel.LOSSY) {
            lossReasons.add(feature.wireName() + " 以 lossy 执行。");
        }

        return new CapabilityResolution(
                feature,
                declaredLevel,
                InteropCapabilityLevel.NATIVE,
                implementedLevel,
                effectiveLevel,
                List.copyOf(blockedReasons),
                List.copyOf(lossReasons)
        );
    }

    public CapabilityResolutionReport resolve(
            CatalogCandidateView candidate,
            GatewayRequestSemantics semantics) {
        if (candidate == null) {
            return new CapabilityResolutionReport(
                    Map.of(),
                    InteropCapabilityLevel.UNSUPPORTED,
                    InteropCapabilityLevel.UNSUPPORTED,
                    InteropCapabilityLevel.UNSUPPORTED,
                    ExecutionKind.BLOCKED,
                    "blocked",
                    List.of("未命中可用候选。"),
                    List.of()
            );
        }
        if (semantics == null || semantics.requiredFeatures() == null || semantics.requiredFeatures().isEmpty()) {
            return new CapabilityResolutionReport(
                    Map.of(),
                    InteropCapabilityLevel.NATIVE,
                    InteropCapabilityLevel.NATIVE,
                    InteropCapabilityLevel.NATIVE,
                    ExecutionKind.NATIVE,
                    upstreamObjectMode(TranslationResourceType.UNKNOWN, ExecutionKind.NATIVE),
                    List.of(),
                    List.of()
            );
        }

        SiteCapabilitySnapshotEntity snapshot = candidate.siteProfileId() == null
                ? null
                : siteCapabilitySnapshotRepository.findBySiteProfile_Id(candidate.siteProfileId()).orElse(null);
        Map<String, CapabilityResolution> featureResolutions = new LinkedHashMap<>();
        java.util.ArrayList<String> blockedReasons = new java.util.ArrayList<>();
        java.util.ArrayList<String> lossReasons = new java.util.ArrayList<>();
        InteropCapabilityLevel overallDeclaredLevel = InteropCapabilityLevel.NATIVE;
        InteropCapabilityLevel overallImplementedLevel = InteropCapabilityLevel.NATIVE;
        InteropCapabilityLevel overallEffectiveLevel = InteropCapabilityLevel.NATIVE;

        for (InteropFeature feature : semantics.requiredFeatures()) {
            CapabilityResolution resolution = resolve(candidate, snapshot, semantics, feature);
            featureResolutions.put(feature.wireName(), resolution);
            overallDeclaredLevel = minimumLevel(overallDeclaredLevel, resolution.declaredLevel());
            overallImplementedLevel = minimumLevel(overallImplementedLevel, resolution.implementedLevel());
            overallEffectiveLevel = minimumLevel(overallEffectiveLevel, resolution.effectiveLevel());
            blockedReasons.addAll(resolution.blockedReasons());
            lossReasons.addAll(resolution.lossReasons());
        }

        ExecutionKind executionKind = !blockedReasons.isEmpty()
                ? ExecutionKind.BLOCKED
                : overallEffectiveLevel == InteropCapabilityLevel.NATIVE
                ? ExecutionKind.NATIVE
                : overallEffectiveLevel == InteropCapabilityLevel.EMULATED
                ? ExecutionKind.EMULATED
                : ExecutionKind.TRANSLATED;

        return new CapabilityResolutionReport(
                Map.copyOf(featureResolutions),
                overallDeclaredLevel,
                overallImplementedLevel,
                overallEffectiveLevel,
                executionKind,
                upstreamObjectMode(semantics.resourceType(), executionKind),
                List.copyOf(blockedReasons),
                List.copyOf(lossReasons)
        );
    }

    public FeatureCompatibilityReport evaluate(
            CatalogCandidateView candidate,
            GatewayRequestSemantics semantics) {
        CapabilityResolutionReport report = resolve(candidate, semantics);
        Map<String, InteropCapabilityLevel> featureLevels = new LinkedHashMap<>();
        report.featureResolutions().forEach((key, value) -> featureLevels.put(key, value.effectiveLevel()));
        return new FeatureCompatibilityReport(
                Map.copyOf(featureLevels),
                report.overallEffectiveLevel(),
                SupportStatus.fromLevel(report.overallEffectiveLevel(), report.blockedReasons()),
                executionSupportMatrixService.degradationLevel(report.overallEffectiveLevel(), report.blockedReasons()),
                report.lossReasons(),
                report.blockedReasons(),
                report.executionKind(),
                report.upstreamObjectMode()
        );
    }

    public SurfaceCompatibilityReport evaluateSurface(
            UpstreamSiteProfileEntity siteProfile,
            SiteCapabilitySnapshotEntity snapshot,
            GatewayRequestSemantics semantics,
            ExecutionBackendDecision backendDecision) {
        if (siteProfile == null || semantics == null) {
            return new SurfaceCompatibilityReport(
                    Map.of(),
                    InteropCapabilityLevel.UNSUPPORTED,
                    List.of("未找到站点档案。"),
                    List.of()
            );
        }

        Map<String, CapabilityResolution> featureResolutions = new LinkedHashMap<>();
        java.util.ArrayList<String> blockedReasons = new java.util.ArrayList<>();
        java.util.ArrayList<String> lossReasons = new java.util.ArrayList<>();
        InteropCapabilityLevel executionCapabilityLevel = InteropCapabilityLevel.NATIVE;

        for (InteropFeature feature : semantics.requiredFeatures()) {
            CapabilityResolution resolution = resolve(siteProfile, snapshot, feature);
            featureResolutions.put(feature.wireName(), resolution);
            executionCapabilityLevel = minimumLevel(executionCapabilityLevel, resolution.effectiveLevel());
            blockedReasons.addAll(resolution.blockedReasons());
            lossReasons.addAll(resolution.lossReasons());
        }

        if (semantics.requiredFeatures().isEmpty()) {
            executionCapabilityLevel = InteropCapabilityLevel.NATIVE;
        }

        List<String> distinctBlockedReasons = blockedReasons.stream()
                .distinct()
                .toList();
        List<String> distinctLossReasons = lossReasons.stream()
                .distinct()
                .toList();

        if (supportsGatewayOrchestrationSurface(siteProfile, backendDecision, semantics)) {
            return new SurfaceCompatibilityReport(
                    Map.copyOf(featureResolutions),
                    InteropCapabilityLevel.NATIVE,
                    List.of(),
                    List.of()
            );
        }

        return new SurfaceCompatibilityReport(
                Map.copyOf(featureResolutions),
                executionCapabilityLevel,
                executionCapabilityLevel == InteropCapabilityLevel.UNSUPPORTED ? distinctBlockedReasons : List.of(),
                distinctLossReasons
        );
    }

    private CapabilityResolution resolve(
            CatalogCandidateView candidate,
            SiteCapabilitySnapshotEntity snapshot,
            GatewayRequestSemantics semantics,
            InteropFeature feature) {
        if (candidate == null || feature == null) {
            return unsupportedResolution(feature, "未命中可用候选。");
        }
        InteropCapabilityLevel declaredLevel = declaredLevel(
                candidate.siteKind(),
                snapshot,
                candidate.supportsChat(),
                candidate.supportsTools(),
                candidate.supportsImageInput(),
                candidate.supportsEmbeddings(),
                candidate.supportsThinking(),
                feature
        );
        InteropCapabilityLevel modelLevel = modelLevel(candidate, feature);
        InteropCapabilityLevel implementedLevel = executionSupportMatrixService.implementedLevel(candidate, semantics, feature);
        InteropCapabilityLevel effectiveLevel = minimumLevel(minimumLevel(declaredLevel, modelLevel), implementedLevel);

        java.util.ArrayList<String> blockedReasons = new java.util.ArrayList<>();
        java.util.ArrayList<String> lossReasons = new java.util.ArrayList<>();
        if (effectiveLevel == InteropCapabilityLevel.UNSUPPORTED) {
            String providerSpecificReason = providerSpecificBlockedReason(candidate.siteKind(), feature);
            if (providerSpecificReason != null) {
                blockedReasons.add(providerSpecificReason);
            } else {
                if (declaredLevel == InteropCapabilityLevel.UNSUPPORTED) {
                    blockedReasons.add(feature.wireName() + " 当前站点声明不支持。");
                }
                if (modelLevel == InteropCapabilityLevel.UNSUPPORTED) {
                    blockedReasons.add(feature.wireName() + " 当前模型不支持。");
                }
                if (implementedLevel == InteropCapabilityLevel.UNSUPPORTED) {
                    blockedReasons.add(feature.wireName() + " 当前实现尚未落地。");
                }
            }
        } else if (effectiveLevel == InteropCapabilityLevel.EMULATED) {
            lossReasons.add(feature.wireName() + " 以 emulated 执行。");
        } else if (effectiveLevel == InteropCapabilityLevel.LOSSY) {
            lossReasons.add(feature.wireName() + " 以 lossy 执行。");
        }

        return new CapabilityResolution(
                feature,
                declaredLevel,
                modelLevel,
                implementedLevel,
                effectiveLevel,
                List.copyOf(blockedReasons),
                List.copyOf(lossReasons)
        );
    }

    private InteropCapabilityLevel modelLevel(CatalogCandidateView candidate, InteropFeature feature) {
        if (candidate == null || feature == null) {
            return InteropCapabilityLevel.UNSUPPORTED;
        }
        return switch (feature) {
            case CHAT_TEXT -> candidate.supportsChat() ? InteropCapabilityLevel.NATIVE : InteropCapabilityLevel.UNSUPPORTED;
            case TOOLS -> candidate.supportsTools() ? InteropCapabilityLevel.NATIVE : InteropCapabilityLevel.UNSUPPORTED;
            case IMAGE_INPUT -> candidate.supportsImageInput() ? InteropCapabilityLevel.NATIVE : InteropCapabilityLevel.UNSUPPORTED;
            case EMBEDDINGS -> candidate.supportsEmbeddings() ? InteropCapabilityLevel.NATIVE : InteropCapabilityLevel.UNSUPPORTED;
            case REASONING -> candidate.supportsThinking() ? InteropCapabilityLevel.NATIVE : InteropCapabilityLevel.UNSUPPORTED;
            default -> InteropCapabilityLevel.NATIVE;
        };
    }

    private InteropCapabilityLevel siteLevelImplementedLevel(UpstreamSiteKind siteKind, InteropFeature feature) {
        CatalogCandidateView candidate = new CatalogCandidateView(
                -1L,
                "site",
                providerTypeFor(siteKind),
                null,
                providerFamilyFor(siteKind),
                null,
                siteKind,
                null,
                null,
                null,
                null,
                "site",
                "site",
                List.of(),
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                null,
                InteropCapabilityLevel.NATIVE
        );
        return executionSupportMatrixService.implementedLevel(
                candidate,
                new GatewayRequestSemantics(TranslationResourceType.UNKNOWN, TranslationOperation.UNKNOWN, List.of(feature), true),
                feature
        );
    }

    private CapabilityResolution unsupportedResolution(InteropFeature feature, String reason) {
        return new CapabilityResolution(
                feature,
                InteropCapabilityLevel.UNSUPPORTED,
                InteropCapabilityLevel.UNSUPPORTED,
                InteropCapabilityLevel.UNSUPPORTED,
                InteropCapabilityLevel.UNSUPPORTED,
                List.of(reason),
                List.of()
        );
    }

    private InteropCapabilityLevel minimumLevel(InteropCapabilityLevel left, InteropCapabilityLevel right) {
        if (left == InteropCapabilityLevel.UNSUPPORTED || right == InteropCapabilityLevel.UNSUPPORTED) {
            return InteropCapabilityLevel.UNSUPPORTED;
        }
        if (left == InteropCapabilityLevel.LOSSY || right == InteropCapabilityLevel.LOSSY) {
            return InteropCapabilityLevel.LOSSY;
        }
        if (left == InteropCapabilityLevel.EMULATED || right == InteropCapabilityLevel.EMULATED) {
            return InteropCapabilityLevel.EMULATED;
        }
        return InteropCapabilityLevel.NATIVE;
    }

    private ProviderFamily providerFamilyFor(UpstreamSiteKind siteKind) {
        return upstreamSitePolicyService.policy(siteKind).providerFamily();
    }

    private ProviderType providerTypeFor(UpstreamSiteKind siteKind) {
        return switch (siteKind) {
            case OPENAI_DIRECT, AZURE_OPENAI -> ProviderType.OPENAI_DIRECT;
            case DEEPSEEK, QWEN, MOONSHOT, SILICONFLOW, VOLCENGINE, MINIMAX, DIFY, GROK, MISTRAL, COHERE, JINA,
                    TOGETHER, FIREWORKS, OPENROUTER, PERPLEXITY, OPENAI_COMPATIBLE_GENERIC -> ProviderType.OPENAI_COMPATIBLE;
            case ANTHROPIC_DIRECT -> ProviderType.ANTHROPIC_DIRECT;
            case GEMINI_DIRECT, VERTEX_AI -> ProviderType.GEMINI_DIRECT;
            case OLLAMA_DIRECT -> ProviderType.OLLAMA_DIRECT;
        };
    }

    private InteropCapabilityLevel declaredLevel(
            UpstreamSiteKind siteKind,
            SiteCapabilitySnapshotEntity snapshot,
            boolean supportsChat,
            boolean supportsTools,
            boolean supportsImageInput,
            boolean supportsEmbeddings,
            boolean supportsThinking,
            InteropFeature feature) {
        UpstreamSitePolicyService.SitePolicy policy = upstreamSitePolicyService.policy(siteKind);
        if (policy.blockedReason() != null) {
            return InteropCapabilityLevel.UNSUPPORTED;
        }

        return switch (feature) {
            case CHAT_TEXT -> supportsChat ? InteropCapabilityLevel.NATIVE : InteropCapabilityLevel.UNSUPPORTED;
            case TOOLS -> supportsTools ? InteropCapabilityLevel.NATIVE : InteropCapabilityLevel.UNSUPPORTED;
            case IMAGE_INPUT -> switch (siteKind) {
                case OLLAMA_DIRECT -> supportsImageInput ? InteropCapabilityLevel.NATIVE : InteropCapabilityLevel.UNSUPPORTED;
                case OPENAI_DIRECT, OPENAI_COMPATIBLE_GENERIC, DEEPSEEK, QWEN, MOONSHOT, SILICONFLOW, VOLCENGINE,
                        MINIMAX, GROK, MISTRAL, TOGETHER, FIREWORKS,
                        OPENROUTER, ANTHROPIC_DIRECT, GEMINI_DIRECT, VERTEX_AI -> InteropCapabilityLevel.NATIVE;
                case AZURE_OPENAI -> InteropCapabilityLevel.LOSSY;
                default -> InteropCapabilityLevel.UNSUPPORTED;
            };
            case FILE_INPUT -> switch (siteKind) {
                case OPENAI_DIRECT, OPENAI_COMPATIBLE_GENERIC, DEEPSEEK, QWEN, MOONSHOT, SILICONFLOW, VOLCENGINE,
                        MINIMAX, GROK, MISTRAL, TOGETHER, FIREWORKS,
                        OPENROUTER, GEMINI_DIRECT, VERTEX_AI -> InteropCapabilityLevel.NATIVE;
                case ANTHROPIC_DIRECT -> InteropCapabilityLevel.EMULATED;
                default -> InteropCapabilityLevel.UNSUPPORTED;
            };
            case FILE_OBJECT -> hasSnapshotCapability(snapshot, SiteCapabilitySnapshotEntity::isSupportsFiles)
                    && supportsUpstreamFileObjects(siteKind)
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case REASONING -> supportsThinking ? InteropCapabilityLevel.NATIVE : InteropCapabilityLevel.UNSUPPORTED;
            case RESPONSE_OBJECT -> policy.supportedProtocols().contains("responses")
                    ? InteropCapabilityLevel.EMULATED
                    : InteropCapabilityLevel.UNSUPPORTED;
            case EMBEDDINGS -> supportsEmbeddings
                    && hasSnapshotCapability(snapshot, SiteCapabilitySnapshotEntity::isSupportsEmbeddings)
                    && supportsUpstreamEmbeddings(siteKind)
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case AUDIO_TRANSCRIPTION, AUDIO_SPEECH ->
                    hasSnapshotCapability(snapshot, SiteCapabilitySnapshotEntity::isSupportsAudio)
                            && supportsUpstreamAudio(siteKind)
                            ? InteropCapabilityLevel.NATIVE
                            : InteropCapabilityLevel.UNSUPPORTED;
            case AUDIO_TRANSLATION ->
                    hasSnapshotCapability(snapshot, SiteCapabilitySnapshotEntity::isSupportsAudio)
                            && supportsOpenAiStyleResources(siteKind)
                            ? InteropCapabilityLevel.NATIVE
                            : InteropCapabilityLevel.UNSUPPORTED;
            case IMAGE_GENERATION ->
                    hasSnapshotCapability(snapshot, SiteCapabilitySnapshotEntity::isSupportsImages)
                            && supportsUpstreamImageGeneration(siteKind)
                            ? InteropCapabilityLevel.NATIVE
                            : InteropCapabilityLevel.UNSUPPORTED;
            case IMAGE_EDIT ->
                    hasSnapshotCapability(snapshot, SiteCapabilitySnapshotEntity::isSupportsImages)
                            && supportsUpstreamImageEdit(siteKind)
                            ? InteropCapabilityLevel.NATIVE
                            : InteropCapabilityLevel.UNSUPPORTED;
            case IMAGE_VARIATION ->
                    hasSnapshotCapability(snapshot, SiteCapabilitySnapshotEntity::isSupportsImages)
                            && supportsOpenAiStyleResources(siteKind)
                            ? InteropCapabilityLevel.NATIVE
                            : InteropCapabilityLevel.UNSUPPORTED;
            case MODERATION -> hasSnapshotCapability(snapshot, SiteCapabilitySnapshotEntity::isSupportsModeration)
                    && supportsUpstreamModeration(siteKind)
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case UPLOAD_CREATE -> hasSnapshotCapability(snapshot, SiteCapabilitySnapshotEntity::isSupportsUploads)
                    && supportsUpstreamUploads(siteKind)
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case RERANK -> supportsUpstreamRerank(siteKind)
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case VIDEO_GENERATION, MUSIC_GENERATION, ASYNC_TASK -> supportsUpstreamMedia(siteKind)
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case WEB_SEARCH -> supportsUpstreamWebSearch(siteKind)
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
        };
    }

    private boolean hasSnapshotCapability(
            SiteCapabilitySnapshotEntity snapshot,
            java.util.function.Predicate<SiteCapabilitySnapshotEntity> predicate) {
        return snapshot != null && predicate.test(snapshot);
    }

    private boolean supportsUpstreamEmbeddings(UpstreamSiteKind siteKind) {
        return switch (siteKind) {
            case OPENAI_DIRECT, OPENAI_COMPATIBLE_GENERIC, DEEPSEEK, QWEN, MOONSHOT, SILICONFLOW, VOLCENGINE,
                    MINIMAX, GROK, MISTRAL, TOGETHER, FIREWORKS, OPENROUTER, COHERE, JINA,
                    GEMINI_DIRECT, VERTEX_AI, AZURE_OPENAI -> true;
            default -> false;
        };
    }

    private boolean supportsUpstreamAudio(UpstreamSiteKind siteKind) {
        return supportsOpenAiStyleResources(siteKind) || supportsGoogleGenAiSite(siteKind);
    }

    private boolean supportsUpstreamImageGeneration(UpstreamSiteKind siteKind) {
        return supportsOpenAiStyleResources(siteKind) || supportsGoogleGenAiSite(siteKind);
    }

    private boolean supportsUpstreamImageEdit(UpstreamSiteKind siteKind) {
        return supportsOpenAiStyleResources(siteKind) || supportsGoogleGenAiSite(siteKind);
    }

    private boolean supportsUpstreamModeration(UpstreamSiteKind siteKind) {
        return supportsOpenAiStyleResources(siteKind) || supportsGoogleGenAiSite(siteKind);
    }

    private boolean supportsOpenAiStyleResources(UpstreamSiteKind siteKind) {
        return switch (siteKind) {
            case OPENAI_DIRECT, OPENAI_COMPATIBLE_GENERIC, DEEPSEEK, QWEN, MOONSHOT, SILICONFLOW, VOLCENGINE,
                    MINIMAX, GROK, MISTRAL, COHERE, TOGETHER, FIREWORKS, OPENROUTER -> true;
            default -> false;
        };
    }

    private boolean supportsUpstreamFileObjects(UpstreamSiteKind siteKind) {
        return switch (siteKind) {
            case OPENAI_DIRECT, OPENAI_COMPATIBLE_GENERIC, OPENROUTER, TOGETHER, FIREWORKS, DEEPSEEK, QWEN,
                    MOONSHOT, SILICONFLOW, VOLCENGINE, MINIMAX, MISTRAL,
                    ANTHROPIC_DIRECT, GEMINI_DIRECT, VERTEX_AI -> true;
            default -> false;
        };
    }

    private boolean supportsUpstreamUploads(UpstreamSiteKind siteKind) {
        return siteKind == UpstreamSiteKind.OPENAI_DIRECT || supportsOpenAiStyleFileLifecycleResources(siteKind);
    }

    private boolean supportsOpenAiStyleFileLifecycleResources(UpstreamSiteKind siteKind) {
        return switch (siteKind) {
            case OPENAI_COMPATIBLE_GENERIC, DEEPSEEK, QWEN, MOONSHOT, SILICONFLOW, VOLCENGINE, MINIMAX, MISTRAL,
                    TOGETHER, FIREWORKS, OPENROUTER -> true;
            default -> false;
        };
    }

    private boolean supportsUpstreamRerank(UpstreamSiteKind siteKind) {
        return siteKind == UpstreamSiteKind.COHERE || siteKind == UpstreamSiteKind.JINA;
    }

    private boolean supportsUpstreamWebSearch(UpstreamSiteKind siteKind) {
        return siteKind == UpstreamSiteKind.OPENAI_DIRECT || siteKind == UpstreamSiteKind.PERPLEXITY;
    }

    private boolean supportsUpstreamMedia(UpstreamSiteKind siteKind) {
        return siteKind == UpstreamSiteKind.OPENAI_DIRECT || siteKind == UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC;
    }

    private boolean siteSupportsChat(UpstreamSiteKind siteKind) {
        return switch (siteKind) {
            case JINA -> false;
            default -> true;
        };
    }

    private boolean siteSupportsTools(UpstreamSiteKind siteKind) {
        return switch (siteKind) {
            case DIFY, JINA -> false;
            default -> siteSupportsChat(siteKind);
        };
    }

    private boolean siteSupportsImageInput(UpstreamSiteKind siteKind) {
        return switch (siteKind) {
            case DIFY, JINA, COHERE, PERPLEXITY, OLLAMA_DIRECT -> false;
            default -> true;
        };
    }

    private boolean siteSupportsEmbeddings(UpstreamSiteKind siteKind) {
        return upstreamSitePolicyService.policy(siteKind).supportsEmbeddings();
    }

    private boolean siteSupportsThinking(UpstreamSiteKind siteKind) {
        return switch (siteKind) {
            case DIFY, JINA, COHERE, PERPLEXITY, OLLAMA_DIRECT -> false;
            default -> siteSupportsChat(siteKind);
        };
    }

    private boolean supportsGoogleGenAiSite(UpstreamSiteKind siteKind) {
        return siteKind == UpstreamSiteKind.GEMINI_DIRECT || siteKind == UpstreamSiteKind.VERTEX_AI;
    }

    private boolean supportsGatewayOrchestrationSurface(
            UpstreamSiteProfileEntity siteProfile,
            ExecutionBackendDecision backendDecision,
            GatewayRequestSemantics semantics) {
        if (siteProfile == null || backendDecision == null || semantics == null) {
            return false;
        }
        if (backendDecision.preferredBackend() != ExecutionBackend.ORCHESTRATION) {
            return false;
        }
        return switch (siteProfile.getSiteKind()) {
            case GEMINI_DIRECT, VERTEX_AI -> semantics.resourceType() == TranslationResourceType.FILE
                    || semantics.resourceType() == TranslationResourceType.UPLOAD;
            case ANTHROPIC_DIRECT -> semantics.resourceType() == TranslationResourceType.FILE;
            default -> false;
        };
    }

    private String providerSpecificBlockedReason(UpstreamSiteKind siteKind, InteropFeature feature) {
        return switch (siteKind) {
            case GEMINI_DIRECT -> switch (feature) {
                case UPLOAD_CREATE ->
                        "Gemini Files API 存在，但不等价于 OpenAI /v1/uploads 的 create/parts/complete/cancel contract，因此仅开放 gateway-local orchestration surface。";
                case AUDIO_TRANSLATION ->
                        "Gemini 当前没有等价 OpenAI /v1/audio/translations 的稳定资源端点，因此当前不开放。";
                case IMAGE_VARIATION ->
                        "Gemini 当前没有等价 OpenAI /v1/images/variations 的稳定资源端点，因此当前不开放。";
                default -> null;
            };
            case ANTHROPIC_DIRECT -> switch (feature) {
                case EMBEDDINGS ->
                        "Anthropic 当前没有稳定的原生 embeddings API，因此当前不开放。";
                case AUDIO_TRANSCRIPTION, AUDIO_TRANSLATION, AUDIO_SPEECH ->
                        "Anthropic 当前没有稳定的原生 audio API，因此当前不开放。";
                case IMAGE_GENERATION, IMAGE_EDIT, IMAGE_VARIATION ->
                        "Anthropic 当前没有稳定的原生 image API，因此当前不开放。";
                case MODERATION ->
                        "Anthropic 当前没有稳定的原生 moderation API，因此当前不开放。";
                case UPLOAD_CREATE ->
                        "Anthropic Files API beta 不等价于 OpenAI /v1/uploads 的 create/parts/complete/cancel contract，因此当前不开放。";
                default -> null;
            };
            case DIFY -> switch (feature) {
                case FILE_OBJECT ->
                        "Dify 的 OpenAI-compatible surface 只视作 workflow/chat 入口，不把 file object lifecycle 视为稳定上游契约。";
                case UPLOAD_CREATE ->
                        "Dify 不暴露与 OpenAI /v1/uploads 等价的稳定对象生命周期，因此当前不开放。";
                case RERANK ->
                        "Dify 当前在本仓库仅作为 workflow/chat compatible preset，不把 rerank 标记为稳定 native 能力。";
                default -> null;
            };
            case OPENAI_COMPATIBLE_GENERIC, DEEPSEEK, QWEN, MOONSHOT, SILICONFLOW, VOLCENGINE, MINIMAX, MISTRAL,
                    TOGETHER, FIREWORKS, OPENROUTER -> switch (feature) {
                case FILE_OBJECT ->
                        "OpenAI-compatible 站点只有在 capability snapshot 声明 supports_files=true 时才开放 files 编排；当前站点未声明可用。";
                case UPLOAD_CREATE ->
                        "OpenAI-compatible 站点只有在 capability snapshot 声明 supports_uploads=true 时才开放 uploads 编排；当前站点未声明可用。";
                default -> null;
            };
            case GROK -> switch (feature) {
                case FILE_OBJECT ->
                        "xAI Files API 存在，但当前 gateway 不把它泛化为 OpenAI object lifecycle。";
                case UPLOAD_CREATE ->
                        "xAI 当前不开放与 OpenAI /v1/uploads 等价的稳定对象生命周期。";
                default -> null;
            };
            case COHERE -> switch (feature) {
                case FILE_OBJECT ->
                        "Cohere compatibility API 当前不视为稳定 file object lifecycle provider。";
                case UPLOAD_CREATE ->
                        "Cohere 当前不开放与 OpenAI /v1/uploads 等价的稳定对象生命周期。";
                default -> null;
            };
            case PERPLEXITY -> switch (feature) {
                case FILE_OBJECT ->
                        "Perplexity 当前作为 web-grounded chat/search provider，不视为 file object lifecycle provider。";
                case UPLOAD_CREATE ->
                        "Perplexity 当前不开放与 OpenAI /v1/uploads 等价的稳定对象生命周期。";
                default -> null;
            };
            case JINA -> switch (feature) {
                case FILE_OBJECT ->
                        "Jina 当前仅在本仓库中冻结为 embeddings/rerank provider，不把 file object lifecycle 视作稳定契约。";
                case UPLOAD_CREATE ->
                        "Jina 当前仅在本仓库中冻结为 embeddings/rerank provider，不开放 uploads object lifecycle。";
                default -> null;
            };
            default -> null;
        };
    }

    private String upstreamObjectMode(TranslationResourceType resourceType, ExecutionKind executionKind) {
        if (executionKind == ExecutionKind.BLOCKED) {
            return "blocked";
        }
        return switch (resourceType) {
            case FILE, UPLOAD, RESPONSE, VIDEO, MUSIC, TASK -> "upstream_object_with_local_lineage";
            case CHAT, EMBEDDING, AUDIO, IMAGE, MODERATION, RERANK, WEB_SEARCH -> executionKind == ExecutionKind.NATIVE
                    ? "direct_upstream_execution"
                    : "translated_execution";
            default -> "direct_upstream_execution";
        };
    }
}
