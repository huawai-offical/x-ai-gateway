package com.prodigalgal.xaigateway.admin.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class ProviderPricingSnapshotService {

    private static final Instant DEFAULT_VERIFIED_AT = Instant.parse("2026-05-14T00:00:00Z");

    public List<ProviderPricingSnapshotView> fromCatalog(ProviderCatalogSnapshot snapshot, Instant verifiedAt) {
        Instant safeVerifiedAt = verifiedAt == null ? DEFAULT_VERIFIED_AT : verifiedAt;
        return snapshot.presets().stream()
                .map(preset -> fromPreset(preset, safeVerifiedAt))
                .toList();
    }

    public List<ProviderPricingSnapshotView> productionEligible(List<ProviderPricingSnapshotView> snapshots, Instant now) {
        Instant safeNow = now == null ? Instant.now() : now;
        return snapshots.stream()
                .filter(snapshot -> snapshot.productionEligible()
                        && "APPROVED".equals(snapshot.approvalStatus())
                        && snapshot.effectiveAt() != null
                        && !snapshot.effectiveAt().isAfter(safeNow)
                        && (snapshot.supersededAt() == null || snapshot.supersededAt().isAfter(safeNow)))
                .toList();
    }

    public ProviderPricingSnapshotView approve(ProviderPricingSnapshotView snapshot, Instant effectiveAt) {
        Instant safeEffectiveAt = effectiveAt == null ? DEFAULT_VERIFIED_AT : effectiveAt;
        return new ProviderPricingSnapshotView(
                snapshot.providerCode(),
                snapshot.displayName(),
                snapshot.sourceKind(),
                snapshot.sourceRef(),
                snapshot.pricingMetadata(),
                snapshot.costProfile(),
                snapshot.snapshotVersion(),
                snapshot.checksum(),
                "APPROVED",
                snapshot.syncStatus(),
                snapshot.lastVerifiedAt(),
                safeEffectiveAt,
                snapshot.supersededAt(),
                "NO_DRIFT",
                true,
                snapshot.notes()
        );
    }

    private ProviderPricingSnapshotView fromPreset(ProviderPresetDefinition preset, Instant verifiedAt) {
        String sourceKind = sourceKind(preset.pricingMetadata());
        String checksum = checksum(preset);
        String approvalStatus = approvalStatus(sourceKind);
        boolean productionEligible = "APPROVED".equals(approvalStatus)
                && !"AGGREGATOR_PASS_THROUGH".equals(sourceKind)
                && !"MISSING_SOURCE".equals(sourceKind);
        Instant effectiveAt = productionEligible ? verifiedAt : null;
        return new ProviderPricingSnapshotView(
                preset.code(),
                preset.displayName(),
                sourceKind,
                preset.pricingMetadata() + "@" + preset.catalogSource(),
                preset.pricingMetadata(),
                preset.costProfile(),
                preset.catalogVersion() + ":" + preset.code() + ":" + checksum.substring(0, 12),
                checksum,
                approvalStatus,
                syncStatus(sourceKind),
                verifiedAt,
                effectiveAt,
                null,
                driftStatus(sourceKind, approvalStatus),
                productionEligible,
                notes(sourceKind)
        );
    }

    private String sourceKind(String pricingMetadata) {
        String normalized = pricingMetadata == null ? "" : pricingMetadata.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "MISSING_SOURCE";
        }
        if (normalized.startsWith("public-list-price")) {
            return "PUBLIC_PRICE_PAGE";
        }
        if (normalized.contains("aggregator")) {
            return "AGGREGATOR_PASS_THROUGH";
        }
        if (normalized.contains("provider-console") || normalized.contains("operator-configured")) {
            return "OPERATOR_CONFIGURED";
        }
        if (normalized.contains("metered") || normalized.contains("metadata-api")) {
            return "PROVIDER_METADATA_API";
        }
        return "CATALOG_METADATA";
    }

    private String approvalStatus(String sourceKind) {
        return switch (sourceKind) {
            case "PUBLIC_PRICE_PAGE", "PROVIDER_METADATA_API", "CATALOG_METADATA" -> "APPROVED";
            case "MISSING_SOURCE" -> "BLOCKED";
            default -> "PENDING_REVIEW";
        };
    }

    private String syncStatus(String sourceKind) {
        return switch (sourceKind) {
            case "PUBLIC_PRICE_PAGE" -> "PUBLIC_SOURCE_TRACKED";
            case "PROVIDER_METADATA_API" -> "PROVIDER_METADATA_TRACKED";
            case "AGGREGATOR_PASS_THROUGH" -> "AGGREGATOR_REVIEW_REQUIRED";
            case "OPERATOR_CONFIGURED" -> "OPERATOR_REVIEW_REQUIRED";
            case "MISSING_SOURCE" -> "MISSING_SOURCE";
            default -> "CATALOG_METADATA_TRACKED";
        };
    }

    private String driftStatus(String sourceKind, String approvalStatus) {
        if ("MISSING_SOURCE".equals(sourceKind)) {
            return "MISSING_SOURCE";
        }
        if (!"APPROVED".equals(approvalStatus)) {
            return "PENDING_REVIEW";
        }
        return "NO_DRIFT";
    }

    private String notes(String sourceKind) {
        return switch (sourceKind) {
            case "PUBLIC_PRICE_PAGE" -> "公开价格源已形成版本化 checksum，生产计费仅使用 approved/effective snapshot。";
            case "PROVIDER_METADATA_API" -> "provider metadata API 或云计量源已形成版本化 checksum。";
            case "AGGREGATOR_PASS_THROUGH" -> "聚合站价格由上游模型决定，必须人工确认具体模型后才能进入生产计费。";
            case "OPERATOR_CONFIGURED" -> "operator-configured/provider console 价格需要人工批准后才可生效。";
            case "MISSING_SOURCE" -> "缺少 pricing metadata，阻断生产计费引用。";
            default -> "catalog pricing metadata 已形成版本化 checksum。";
        };
    }

    private String checksum(ProviderPresetDefinition preset) {
        String canonical = String.join("|",
                safe(preset.code()),
                safe(preset.displayName()),
                safe(preset.catalogVersion()),
                safe(preset.pricingMetadata()),
                safe(preset.costProfile()),
                safe(preset.compatibilitySurface()),
                safe(preset.supportStrategy()),
                String.join(",", preset.modelFamilies())
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 缺少 SHA-256。", exception);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
