package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ProviderCatalogMarketplaceStatusResponse;
import com.prodigalgal.xaigateway.admin.api.ProviderCatalogMarketplaceUpdateRequest;
import com.prodigalgal.xaigateway.admin.api.ProviderCatalogMarketplaceUpdateResponse;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class ProviderCatalogMarketplaceService {

    private final ProviderCatalogLoader providerCatalogLoader;
    private final ObjectMapper objectMapper;
    private final Path root;
    private final Path currentPath;
    private final Path previousPath;
    private final Path manifestPath;

    public ProviderCatalogMarketplaceService(
            ProviderCatalogLoader providerCatalogLoader,
            ObjectMapper objectMapper,
            GatewayProperties gatewayProperties) {
        this.providerCatalogLoader = providerCatalogLoader;
        this.objectMapper = objectMapper;
        this.root = Path.of(gatewayProperties.getStorage().getFileRoot()).toAbsolutePath()
                .resolve("provider-catalog-marketplace");
        this.currentPath = root.resolve("current.json");
        this.previousPath = root.resolve("previous.json");
        this.manifestPath = root.resolve("manifest.json");
    }

    public ProviderCatalogMarketplaceStatusResponse status() {
        ProviderCatalogSnapshot snapshot = providerCatalogLoader.load();
        boolean cached = Files.exists(currentPath) && snapshot.source().startsWith("marketplace-cache:");
        return new ProviderCatalogMarketplaceStatusResponse(
                snapshot.version(),
                snapshot.source(),
                cached ? "VERIFIED_CACHE" : "NOT_SIGNED",
                cached ? sha256(readStringOrEmpty(currentPath)) : null,
                snapshot.presets().size(),
                cached,
                Files.exists(previousPath),
                cached ? manifestUpdatedAt() : null,
                cached ? "当前使用 marketplace 缓存。" : "当前使用 classpath 或 builtin catalog。"
        );
    }

    public ProviderCatalogMarketplaceUpdateResponse update(ProviderCatalogMarketplaceUpdateRequest request) {
        String catalogJson = resolveCatalogJson(request);
        String source = source(request);
        String hash = sha256(catalogJson);
        String expectedSignature = hmacSha256(catalogJson, requiredText(request.signingKey(), "signingKey 不能为空。"));
        if (!secureEquals(normalizeSignature(request.signature()), expectedSignature)) {
            return rejected("SIGNATURE_FAILED", null, source, hash, "catalog 签名校验失败，已保留当前缓存。");
        }

        ProviderCatalogSnapshot snapshot;
        try {
            snapshot = providerCatalogLoader.loadFromJson(catalogJson, source);
        } catch (IllegalArgumentException exception) {
            return rejected("CATALOG_INVALID", null, source, hash, exception.getMessage());
        }

        boolean dryRun = Boolean.TRUE.equals(request.dryRun());
        if (!dryRun) {
            writeCache(catalogJson, snapshot, source, hash, request.signature());
        }
        return new ProviderCatalogMarketplaceUpdateResponse(
                dryRun ? "DRY_RUN_PASS" : "UPDATED",
                snapshot.version(),
                source,
                "VERIFIED",
                hash,
                snapshot.presets().size(),
                !dryRun,
                Files.exists(previousPath),
                Instant.now(),
                dryRun ? "签名和 catalog schema 已通过 dry-run 校验，未写入缓存。" : "已写入 marketplace current cache。"
        );
    }

    public ProviderCatalogMarketplaceUpdateResponse rollback() {
        if (!Files.exists(previousPath)) {
            return rejected("ROLLBACK_UNAVAILABLE", null, "marketplace-cache", null, "没有可用 previous cache。");
        }
        try {
            String previous = Files.readString(previousPath, StandardCharsets.UTF_8);
            ProviderCatalogSnapshot snapshot = providerCatalogLoader.loadFromJson(previous, "marketplace-cache:" + previousPath.toAbsolutePath());
            Files.createDirectories(root);
            if (Files.exists(currentPath)) {
                Files.copy(currentPath, root.resolve("rolled-back-from-" + Instant.now().toEpochMilli() + ".json"));
            }
            Files.copy(previousPath, currentPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            writeManifest(snapshot, "rollback", sha256(previous), "VERIFIED_ROLLBACK", null);
            return new ProviderCatalogMarketplaceUpdateResponse(
                    "ROLLED_BACK",
                    snapshot.version(),
                    "marketplace-cache:" + currentPath.toAbsolutePath(),
                    "VERIFIED_ROLLBACK",
                    sha256(previous),
                    snapshot.presets().size(),
                    true,
                    true,
                    Instant.now(),
                    "已恢复 previous cache 为 current。"
            );
        } catch (IOException | IllegalArgumentException exception) {
            return rejected("ROLLBACK_FAILED", null, "marketplace-cache", null, exception.getMessage());
        }
    }

    private void writeCache(
            String catalogJson,
            ProviderCatalogSnapshot snapshot,
            String source,
            String hash,
            String signature) {
        try {
            Files.createDirectories(root);
            if (Files.exists(currentPath)) {
                Files.copy(currentPath, previousPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(currentPath, catalogJson, StandardCharsets.UTF_8);
            writeManifest(snapshot, source, hash, "VERIFIED", signature);
        } catch (IOException exception) {
            throw new IllegalStateException("写入 marketplace catalog cache 失败。", exception);
        }
    }

    private void writeManifest(
            ProviderCatalogSnapshot snapshot,
            String source,
            String hash,
            String signatureStatus,
            String signature) throws IOException {
        Files.writeString(manifestPath, objectMapper.writeValueAsString(Map.of(
                "catalogVersion", snapshot.version(),
                "source", source,
                "catalogHash", hash,
                "signatureStatus", signatureStatus,
                "signature", signature == null ? "" : signature,
                "presetCount", snapshot.presets().size(),
                "updatedAt", Instant.now().toString()
        )), StandardCharsets.UTF_8);
    }

    private String resolveCatalogJson(ProviderCatalogMarketplaceUpdateRequest request) {
        if (request.catalogJson() != null && !request.catalogJson().isBlank()) {
            return request.catalogJson();
        }
        String remoteUrl = requiredText(request.remoteUrl(), "remoteUrl 或 catalogJson 至少需要提供一个。");
        try (var inputStream = URI.create(remoteUrl).toURL().openStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalArgumentException("拉取远程 provider catalog 失败。", exception);
        }
    }

    private String source(ProviderCatalogMarketplaceUpdateRequest request) {
        if (request.remoteUrl() != null && !request.remoteUrl().isBlank()) {
            return "remote:" + request.remoteUrl().trim();
        }
        return "inline-marketplace";
    }

    private ProviderCatalogMarketplaceUpdateResponse rejected(
            String status,
            String version,
            String source,
            String hash,
            String message) {
        return new ProviderCatalogMarketplaceUpdateResponse(
                status,
                version,
                source,
                status,
                hash,
                0,
                false,
                Files.exists(previousPath),
                Instant.now(),
                message
        );
    }

    private Instant manifestUpdatedAt() {
        if (!Files.exists(manifestPath)) {
            return null;
        }
        try {
            var node = objectMapper.readTree(Files.readString(manifestPath, StandardCharsets.UTF_8));
            String value = node.path("updatedAt").asText(null);
            return value == null || value.isBlank() ? null : Instant.parse(value);
        } catch (Exception exception) {
            return null;
        }
    }

    private String readStringOrEmpty(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "";
        }
    }

    private String requiredText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeSignature(String signature) {
        String value = requiredText(signature, "signature 不能为空。");
        return value.startsWith("sha256=") ? value.substring("sha256=".length()) : value;
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("计算 catalog hash 失败。", exception);
        }
    }

    private String hmacSha256(String payload, String signingKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("计算 catalog 签名失败。", exception);
        }
    }

    private boolean secureEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
