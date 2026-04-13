package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
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
        InteropCapabilityLevel declaredLevel = declaredLevel(siteProfile.getSiteKind(), snapshot, true, true, true, true, true, feature);
        InteropCapabilityLevel implementedLevel = siteLevelImplementedLevel(siteProfile.getSiteKind(), feature);
        InteropCapabilityLevel effectiveLevel = minimumLevel(declaredLevel, implementedLevel);

        java.util.ArrayList<String> blockedReasons = new java.util.ArrayList<>();
        java.util.ArrayList<String> lossReasons = new java.util.ArrayList<>();
        if (effectiveLevel == InteropCapabilityLevel.UNSUPPORTED) {
            if (declaredLevel == InteropCapabilityLevel.UNSUPPORTED) {
                blockedReasons.add(feature.wireName() + " 当前站点声明不支持。");
            }
            if (implementedLevel == InteropCapabilityLevel.UNSUPPORTED) {
                blockedReasons.add(feature.wireName() + " 当前实现尚未落地。");
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
                report.lossReasons(),
                report.blockedReasons(),
                report.executionKind(),
                report.upstreamObjectMode()
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
            if (declaredLevel == InteropCapabilityLevel.UNSUPPORTED) {
                blockedReasons.add(feature.wireName() + " 当前站点声明不支持。");
            }
            if (modelLevel == InteropCapabilityLevel.UNSUPPORTED) {
                blockedReasons.add(feature.wireName() + " 当前模型不支持。");
            }
            if (implementedLevel == InteropCapabilityLevel.UNSUPPORTED) {
                blockedReasons.add(feature.wireName() + " 当前实现尚未落地。");
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
            case DEEPSEEK, GROK, MISTRAL, COHERE, TOGETHER, FIREWORKS, OPENROUTER, OPENAI_COMPATIBLE_GENERIC -> ProviderType.OPENAI_COMPATIBLE;
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
            case TOOLS -> siteKind == UpstreamSiteKind.OLLAMA_DIRECT
                    ? supportsTools ? InteropCapabilityLevel.NATIVE : InteropCapabilityLevel.UNSUPPORTED
                    : supportsChat ? InteropCapabilityLevel.NATIVE : InteropCapabilityLevel.UNSUPPORTED;
            case IMAGE_INPUT -> switch (siteKind) {
                case OLLAMA_DIRECT -> supportsImageInput ? InteropCapabilityLevel.NATIVE : InteropCapabilityLevel.UNSUPPORTED;
                case OPENAI_DIRECT, OPENAI_COMPATIBLE_GENERIC, DEEPSEEK, GROK, MISTRAL, TOGETHER, FIREWORKS,
                        OPENROUTER, ANTHROPIC_DIRECT, GEMINI_DIRECT, VERTEX_AI -> InteropCapabilityLevel.NATIVE;
                case AZURE_OPENAI -> InteropCapabilityLevel.LOSSY;
                default -> InteropCapabilityLevel.UNSUPPORTED;
            };
            case FILE_INPUT -> switch (siteKind) {
                case OPENAI_DIRECT, OPENAI_COMPATIBLE_GENERIC, DEEPSEEK, GROK, MISTRAL, TOGETHER, FIREWORKS,
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
            case AUDIO_TRANSCRIPTION, AUDIO_TRANSLATION, AUDIO_SPEECH ->
                    hasSnapshotCapability(snapshot, SiteCapabilitySnapshotEntity::isSupportsAudio)
                            && supportsUpstreamAudio(siteKind)
                            ? InteropCapabilityLevel.NATIVE
                            : InteropCapabilityLevel.UNSUPPORTED;
            case IMAGE_GENERATION, IMAGE_EDIT, IMAGE_VARIATION ->
                    hasSnapshotCapability(snapshot, SiteCapabilitySnapshotEntity::isSupportsImages)
                            && supportsUpstreamImages(siteKind)
                            ? InteropCapabilityLevel.NATIVE
                            : InteropCapabilityLevel.UNSUPPORTED;
            case MODERATION -> hasSnapshotCapability(snapshot, SiteCapabilitySnapshotEntity::isSupportsModeration)
                    && supportsUpstreamModeration(siteKind)
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case UPLOAD_CREATE -> hasSnapshotCapability(snapshot, SiteCapabilitySnapshotEntity::isSupportsUploads)
                    && supportsUpstreamAsyncObjects(siteKind)
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case BATCH_CREATE -> hasSnapshotCapability(snapshot, SiteCapabilitySnapshotEntity::isSupportsBatches)
                    && supportsUpstreamAsyncObjects(siteKind)
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case TUNING_CREATE -> hasSnapshotCapability(snapshot, SiteCapabilitySnapshotEntity::isSupportsTuning)
                    && supportsUpstreamAsyncObjects(siteKind)
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case REALTIME_CLIENT_SECRET -> hasSnapshotCapability(snapshot, SiteCapabilitySnapshotEntity::isSupportsRealtime)
                    && supportsUpstreamAsyncObjects(siteKind)
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
            case OPENAI_DIRECT, OPENAI_COMPATIBLE_GENERIC, DEEPSEEK, GROK, MISTRAL, TOGETHER, FIREWORKS,
                    OPENROUTER, COHERE, GEMINI_DIRECT, AZURE_OPENAI -> true;
            default -> false;
        };
    }

    private boolean supportsUpstreamAudio(UpstreamSiteKind siteKind) {
        return supportsOpenAiStyleResources(siteKind);
    }

    private boolean supportsUpstreamImages(UpstreamSiteKind siteKind) {
        return supportsOpenAiStyleResources(siteKind);
    }

    private boolean supportsUpstreamModeration(UpstreamSiteKind siteKind) {
        return supportsOpenAiStyleResources(siteKind);
    }

    private boolean supportsOpenAiStyleResources(UpstreamSiteKind siteKind) {
        return switch (siteKind) {
            case OPENAI_DIRECT, OPENAI_COMPATIBLE_GENERIC, DEEPSEEK, GROK, MISTRAL, COHERE, TOGETHER, FIREWORKS, OPENROUTER -> true;
            default -> false;
        };
    }

    private boolean supportsUpstreamFileObjects(UpstreamSiteKind siteKind) {
        return switch (siteKind) {
            case OPENAI_DIRECT, OPENAI_COMPATIBLE_GENERIC, OPENROUTER, TOGETHER, FIREWORKS, DEEPSEEK, GROK, MISTRAL, COHERE -> true;
            default -> false;
        };
    }

    private boolean supportsUpstreamAsyncObjects(UpstreamSiteKind siteKind) {
        return siteKind == UpstreamSiteKind.OPENAI_DIRECT;
    }

    private String upstreamObjectMode(TranslationResourceType resourceType, ExecutionKind executionKind) {
        if (executionKind == ExecutionKind.BLOCKED) {
            return "blocked";
        }
        return switch (resourceType) {
            case FILE, UPLOAD, BATCH, TUNING, REALTIME, RESPONSE -> "upstream_object_with_local_lineage";
            case CHAT, EMBEDDING, AUDIO, IMAGE, MODERATION -> executionKind == ExecutionKind.NATIVE
                    ? "direct_upstream_execution"
                    : "translated_execution";
            default -> "direct_upstream_execution";
        };
    }
}
