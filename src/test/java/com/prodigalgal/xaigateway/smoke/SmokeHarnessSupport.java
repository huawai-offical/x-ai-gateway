package com.prodigalgal.xaigateway.smoke;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SmokeHarnessSupport {

    private SmokeHarnessSupport() {
    }

    public static boolean enabled(String name) {
        String value = System.getenv(name);
        return value != null && ("true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim()));
    }

    public static String env(String primary, String fallbackName, String fallbackValue) {
        String value = System.getenv(primary);
        if (value == null || value.isBlank()) {
            value = fallbackName == null ? null : System.getenv(fallbackName);
        }
        return value == null || value.isBlank() ? fallbackValue : value.trim();
    }

    public static int envInt(String primary, String fallbackName, int fallbackValue) {
        String value = env(primary, fallbackName, String.valueOf(fallbackValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallbackValue;
        }
    }

    public static List<String> envList(String... names) {
        Set<String> values = new LinkedHashSet<>();
        if (names == null) {
            return List.of();
        }
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            String raw = System.getenv(name);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            for (String item : raw.split("[,;\\s]+")) {
                if (item != null && !item.isBlank()) {
                    values.add(item.trim());
                }
            }
        }
        return List.copyOf(values);
    }

    public static String secretRef(String secret) {
        if (secret == null || secret.isBlank()) {
            return "sha256:blank";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 缺少 SHA-256。", exception);
        }
    }

    public static Path writeReport(String suite, String content) {
        try {
            Path root = Path.of("build", "reports", "xag-smoke").toAbsolutePath();
            Files.createDirectories(root);
            Path report = root.resolve(suite + ".md");
            Files.writeString(
                    report,
                    "# " + suite + " smoke report\n\n"
                            + "- generatedAt: " + Instant.now() + "\n\n"
                            + content,
                    StandardCharsets.UTF_8
            );
            System.out.println("x-ai-gateway smoke report: " + report);
            return report;
        } catch (IOException exception) {
            throw new IllegalStateException("无法写入 smoke 报告。", exception);
        }
    }
}
