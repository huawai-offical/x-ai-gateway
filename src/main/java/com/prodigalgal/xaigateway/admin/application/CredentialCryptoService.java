package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class CredentialCryptoService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final GatewayProperties gatewayProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    private SecretKeySpec secretKeySpec;

    public CredentialCryptoService(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
    }

    @PostConstruct
    void init() {
        this.secretKeySpec = new SecretKeySpec(sha256(gatewayProperties.getSecurity().getEncryptionKey()), "AES");
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception exception) {
            throw new IllegalStateException("无法加密上游 API key。", exception);
        }
    }

    public String fingerprint(String plainText) {
        return HexFormat.of().formatHex(sha256(plainText));
    }

    public String decrypt(String ciphertextBase64) {
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertextBase64);
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plainBytes = cipher.doFinal(ciphertext);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("无法解密上游 API key。", exception);
        }
    }

    private byte[] sha256(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境缺少 SHA-256。", exception);
        }
    }
}
