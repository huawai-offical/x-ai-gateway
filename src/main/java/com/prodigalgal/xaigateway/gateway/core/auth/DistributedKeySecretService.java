package com.prodigalgal.xaigateway.gateway.core.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class DistributedKeySecretService {

    private final SecureRandom secureRandom = new SecureRandom();

    public DistributedKeySecrets generate() {
        byte[] publicId = new byte[8];
        secureRandom.nextBytes(publicId);
        String keyPrefix = "sk-gw-" + HexFormat.of().formatHex(publicId);

        byte[] secretBytes = new byte[24];
        secureRandom.nextBytes(secretBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);

        String fullKey = keyPrefix + "." + secret;
        return new DistributedKeySecrets(
                keyPrefix,
                fullKey,
                hashSecret(secret),
                mask(fullKey)
        );
    }

    public String hashSecret(String secret) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境缺少 SHA-256。", exception);
        }
    }

    private String mask(String fullKey) {
        if (fullKey.length() <= 18) {
            return fullKey;
        }

        return fullKey.substring(0, 12) + "..." + fullKey.substring(fullKey.length() - 6);
    }
}
