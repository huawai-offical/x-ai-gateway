package com.prodigalgal.xaigateway.portal.application;

import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayUnauthorizedException;
import com.prodigalgal.xaigateway.infra.persistence.entity.AuditLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserPasskeyCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.AuditLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserPasskeyCredentialRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserRepository;
import com.prodigalgal.xaigateway.portal.api.PortalCaptchaChallengeResponse;
import com.prodigalgal.xaigateway.portal.api.PortalEmailVerificationConfirmRequest;
import com.prodigalgal.xaigateway.portal.api.PortalEmailVerificationStartResponse;
import com.prodigalgal.xaigateway.portal.api.PortalPasskeyAssertionFinishRequest;
import com.prodigalgal.xaigateway.portal.api.PortalPasskeyAssertionStartRequest;
import com.prodigalgal.xaigateway.portal.api.PortalPasskeyAssertionStartResponse;
import com.prodigalgal.xaigateway.portal.api.PortalPasskeyCredentialResponse;
import com.prodigalgal.xaigateway.portal.api.PortalPasskeyRegistrationFinishRequest;
import com.prodigalgal.xaigateway.portal.api.PortalPasskeyRegistrationStartRequest;
import com.prodigalgal.xaigateway.portal.api.PortalPasskeyRegistrationStartResponse;
import com.prodigalgal.xaigateway.portal.api.PortalRegistrationPolicyRequest;
import com.prodigalgal.xaigateway.portal.api.PortalRegistrationPolicyResponse;
import com.prodigalgal.xaigateway.portal.api.PortalSecurityStatusResponse;
import com.prodigalgal.xaigateway.portal.api.PortalTotpSetupResponse;
import com.prodigalgal.xaigateway.portal.api.PortalTotpVerifyRequest;
import java.nio.ByteBuffer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.WebSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class PortalSecurityService {

    private static final String PORTAL_USER_ID_SESSION_KEY = "portalUserId";
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);
    private static final Duration EMAIL_VERIFICATION_TTL = Duration.ofMinutes(15);
    private static final Duration PASSKEY_CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final int TOTP_STEP_SECONDS = 30;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final String DEFAULT_RP_ID = "x-ai-gateway.local";
    private static final String DEFAULT_ORIGIN = "https://x-ai-gateway.local";

    private final GatewayUserRepository gatewayUserRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final GatewayUserPasskeyCredentialRepository passkeyCredentialRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Challenge> captchaChallenges = new ConcurrentHashMap<>();
    private final Map<String, Challenge> emailVerifications = new ConcurrentHashMap<>();
    private final Map<String, PasskeyChallenge> passkeyChallenges = new ConcurrentHashMap<>();
    private volatile RegistrationPolicy registrationPolicy = new RegistrationPolicy(List.of(), false, Set.of(), false, Instant.now());

    @Autowired
    public PortalSecurityService(
            GatewayUserRepository gatewayUserRepository,
            CredentialCryptoService credentialCryptoService,
            GatewayUserPasskeyCredentialRepository passkeyCredentialRepository,
            AuditLogRepository auditLogRepository) {
        this.gatewayUserRepository = gatewayUserRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.passkeyCredentialRepository = passkeyCredentialRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public PortalSecurityService(
            GatewayUserRepository gatewayUserRepository,
            CredentialCryptoService credentialCryptoService) {
        this(gatewayUserRepository, credentialCryptoService, null, null);
    }

    public PortalCaptchaChallengeResponse createCaptchaChallenge() {
        int left = secureRandom.nextInt(9) + 1;
        int right = secureRandom.nextInt(9) + 1;
        String challengeId = "cap_" + UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plus(CAPTCHA_TTL);
        captchaChallenges.put(challengeId, new Challenge(String.valueOf(left + right), null, expiresAt));
        return new PortalCaptchaChallengeResponse(challengeId, left + " + " + right + " = ?", expiresAt);
    }

    public void verifyCaptcha(String challengeId, String answer) {
        if (challengeId == null || challengeId.isBlank() || answer == null || answer.isBlank()) {
            return;
        }
        Challenge challenge = captchaChallenges.remove(challengeId.trim());
        if (challenge == null || challenge.expiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("验证码已过期或不存在。");
        }
        if (!challenge.secret().equals(answer.trim())) {
            throw new IllegalArgumentException("验证码错误。");
        }
    }

    @Transactional(readOnly = true)
    public PortalSecurityStatusResponse status(WebSession session) {
        return toStatus(requireCurrentUser(session));
    }

    public PortalRegistrationPolicyResponse registrationPolicy() {
        return toPolicyResponse(registrationPolicy);
    }

    public PortalRegistrationPolicyResponse updateRegistrationPolicy(PortalRegistrationPolicyRequest request) {
        List<String> domains = normalizeDomains(request == null ? null : request.allowedEmailDomains());
        Set<String> inviteCodes = normalizeInviteCodes(request == null ? null : request.inviteCodes());
        registrationPolicy = new RegistrationPolicy(
                domains,
                request != null && Boolean.TRUE.equals(request.inviteCodeRequired()),
                inviteCodes,
                request != null && Boolean.TRUE.equals(request.emailVerificationRequiredForKeyCreation()),
                Instant.now()
        );
        audit(null, "REGISTRATION_POLICY_UPDATED", "SUCCESS", "{\"domains\":" + domains.size() + ",\"inviteRequired\":" + registrationPolicy.inviteCodeRequired() + "}");
        return toPolicyResponse(registrationPolicy);
    }

    public void verifyRegistrationPolicy(String email, String inviteCode) {
        RegistrationPolicy policy = registrationPolicy;
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!policy.allowedEmailDomains().isEmpty() && !domainAllowed(normalizedEmail, policy.allowedEmailDomains())) {
            audit(null, "REGISTRATION_POLICY_REJECTED", "FAILED", "{\"email\":\"" + normalizedEmail + "\",\"reason\":\"domain\"}");
            throw new IllegalArgumentException("当前邮箱域名不允许注册。");
        }
        if (policy.inviteCodeRequired()) {
            String normalizedCode = inviteCode == null ? "" : inviteCode.trim().toUpperCase(Locale.ROOT);
            if (normalizedCode.isBlank() || !policy.inviteCodes().contains(normalizedCode)) {
                audit(null, "REGISTRATION_POLICY_REJECTED", "FAILED", "{\"email\":\"" + normalizedEmail + "\",\"reason\":\"invite\"}");
                throw new IllegalArgumentException("注册需要有效邀请码。");
            }
        }
    }

    public void assertKeyCreationAllowed(GatewayUserEntity user) {
        if (registrationPolicy.emailVerificationRequiredForKeyCreation() && user.getEmailVerifiedAt() == null) {
            audit(user, "KEY_CREATION_REJECTED", "FAILED", "{\"reason\":\"email_not_verified\"}");
            throw new GatewayUnauthorizedException("需要先完成邮箱验证后才能创建 API Key。");
        }
    }

    @Transactional(readOnly = true)
    public int passkeyCountForUser(Long userId) {
        if (userId == null || passkeyCredentialRepository == null) {
            return 0;
        }
        return Math.toIntExact(passkeyCredentialRepository.countByUser_IdAndActiveTrue(userId));
    }

    public PortalEmailVerificationStartResponse startEmailVerification(WebSession session) {
        GatewayUserEntity user = requireCurrentUser(session);
        String verificationId = "email_" + UUID.randomUUID().toString().replace("-", "");
        String code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
        Instant expiresAt = Instant.now().plus(EMAIL_VERIFICATION_TTL);
        emailVerifications.put(verificationId, new Challenge(code, user.getId(), expiresAt));
        return new PortalEmailVerificationStartResponse(verificationId, code, expiresAt);
    }

    public PortalSecurityStatusResponse confirmEmailVerification(
            WebSession session,
            PortalEmailVerificationConfirmRequest request) {
        GatewayUserEntity user = requireCurrentUser(session);
        Challenge challenge = emailVerifications.remove(request.verificationId().trim());
        if (challenge == null || challenge.expiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("邮箱验证码已过期或不存在。");
        }
        if (!user.getId().equals(challenge.userId())) {
            throw new GatewayUnauthorizedException("无权使用该邮箱验证码。");
        }
        if (!challenge.secret().equals(request.verificationCode().trim())) {
            throw new IllegalArgumentException("邮箱验证码错误。");
        }
        user.setEmailVerifiedAt(Instant.now());
        GatewayUserEntity saved = gatewayUserRepository.save(user);
        audit(saved, "EMAIL_VERIFIED", "SUCCESS", "{}");
        return toStatus(saved);
    }

    public PortalTotpSetupResponse setupTotp(WebSession session) {
        GatewayUserEntity user = requireCurrentUser(session);
        String secret = generateBase32Secret();
        user.setTotpSecretCiphertext(credentialCryptoService.encrypt(secret));
        user.setTotpEnabled(false);
        user.setTotpVerifiedAt(null);
        gatewayUserRepository.save(user);
        String label = urlEncode("x-ai-gateway:" + user.getEmail());
        String issuer = urlEncode("x-ai-gateway");
        return new PortalTotpSetupResponse(
                secret,
                "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + issuer + "&digits=6&period=30"
        );
    }

    public PortalSecurityStatusResponse enableTotp(WebSession session, PortalTotpVerifyRequest request) {
        GatewayUserEntity user = requireCurrentUser(session);
        verifyTotpForUser(user, request.code());
        user.setTotpEnabled(true);
        user.setTotpVerifiedAt(Instant.now());
        GatewayUserEntity saved = gatewayUserRepository.save(user);
        audit(saved, "TOTP_ENABLED", "SUCCESS", "{}");
        return toStatus(saved);
    }

    public PortalSecurityStatusResponse disableTotp(WebSession session, PortalTotpVerifyRequest request) {
        GatewayUserEntity user = requireCurrentUser(session);
        if (user.isTotpEnabled()) {
            verifyTotpForUser(user, request.code());
        }
        user.setTotpEnabled(false);
        user.setTotpVerifiedAt(null);
        user.setTotpSecretCiphertext(null);
        GatewayUserEntity saved = gatewayUserRepository.save(user);
        audit(saved, "TOTP_DISABLED", "SUCCESS", "{}");
        return toStatus(saved);
    }

    @Transactional(readOnly = true)
    public List<PortalPasskeyCredentialResponse> listPasskeys(WebSession session) {
        GatewayUserEntity user = requireCurrentUser(session);
        return requirePasskeyRepository()
                .findAllByUser_IdAndActiveTrueOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toPasskeyResponse)
                .toList();
    }

    public PortalPasskeyRegistrationStartResponse startPasskeyRegistration(
            WebSession session,
            PortalPasskeyRegistrationStartRequest request) {
        GatewayUserEntity user = requireCurrentUser(session);
        String challengeId = "pkr_" + UUID.randomUUID().toString().replace("-", "");
        String challenge = randomBase64Url(32);
        String credentialName = defaultString(request == null ? null : request.credentialName(), "Passkey");
        Instant expiresAt = Instant.now().plus(PASSKEY_CHALLENGE_TTL);
        passkeyChallenges.put(challengeId, new PasskeyChallenge(
                "webauthn.create",
                user.getId(),
                challenge,
                DEFAULT_RP_ID,
                DEFAULT_ORIGIN,
                List.of(),
                expiresAt
        ));
        audit(user, "PASSKEY_REGISTRATION_CHALLENGE_CREATED", "SUCCESS", "{\"challengeId\":\"" + challengeId + "\"}");
        return new PortalPasskeyRegistrationStartResponse(
                challengeId,
                challenge,
                DEFAULT_RP_ID,
                DEFAULT_ORIGIN,
                String.valueOf(user.getId()),
                user.getEmail(),
                credentialName,
                expiresAt
        );
    }

    public PortalPasskeyCredentialResponse finishPasskeyRegistration(
            WebSession session,
            PortalPasskeyRegistrationFinishRequest request) {
        GatewayUserEntity user = requireCurrentUser(session);
        PasskeyChallenge challenge = consumePasskeyChallenge(request.challengeId(), "webauthn.create");
        if (!user.getId().equals(challenge.userId())) {
            throw new GatewayUnauthorizedException("Passkey challenge 不属于当前用户。");
        }
        verifyClientData(request.clientDataJson(), "webauthn.create", challenge);
        PublicKey publicKey = parsePublicKey(request.publicKeyPem());
        if (requirePasskeyRepository().findByCredentialIdAndActiveTrue(required(request.credentialId(), "credentialId")).isPresent()) {
            throw new IllegalArgumentException("Passkey credentialId 已存在。");
        }

        GatewayUserPasskeyCredentialEntity entity = new GatewayUserPasskeyCredentialEntity();
        entity.setUser(user);
        entity.setCredentialId(request.credentialId().trim());
        entity.setCredentialName(defaultString(request.credentialName(), "Passkey"));
        entity.setPublicKeyPem(request.publicKeyPem().trim());
        entity.setRpId(challenge.rpId());
        entity.setOrigin(challenge.origin());
        entity.setTransportsJson(writeJson(normalizePlainList(request.transports())));
        entity.setMetadataJson(writeJson(Map.of(
                "publicKeyAlgorithm", publicKey.getAlgorithm(),
                "registeredAt", Instant.now().toString()
        )));
        entity.setActive(true);
        GatewayUserPasskeyCredentialEntity saved = requirePasskeyRepository().save(entity);
        audit(user, "PASSKEY_REGISTERED", "SUCCESS", "{\"credentialId\":\"" + saved.getCredentialId() + "\"}");
        return toPasskeyResponse(saved);
    }

    public PortalPasskeyAssertionStartResponse startPasskeyAssertion(PortalPasskeyAssertionStartRequest request) {
        String email = request == null ? null : request.email();
        GatewayUserEntity user = gatewayUserRepository.findByEmailIgnoreCase(email == null ? "" : email.trim().toLowerCase(Locale.ROOT))
                .filter(GatewayUserEntity::isActive)
                .orElseThrow(() -> new GatewayUnauthorizedException("未找到可用 Passkey 用户。"));
        List<String> credentialIds = requirePasskeyRepository()
                .findAllByUser_IdAndActiveTrueOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(GatewayUserPasskeyCredentialEntity::getCredentialId)
                .toList();
        if (credentialIds.isEmpty()) {
            throw new GatewayUnauthorizedException("当前用户尚未注册 Passkey。");
        }
        String challengeId = "pka_" + UUID.randomUUID().toString().replace("-", "");
        String challenge = randomBase64Url(32);
        Instant expiresAt = Instant.now().plus(PASSKEY_CHALLENGE_TTL);
        passkeyChallenges.put(challengeId, new PasskeyChallenge(
                "webauthn.get",
                user.getId(),
                challenge,
                DEFAULT_RP_ID,
                DEFAULT_ORIGIN,
                credentialIds,
                expiresAt
        ));
        audit(user, "PASSKEY_ASSERTION_CHALLENGE_CREATED", "SUCCESS", "{\"challengeId\":\"" + challengeId + "\"}");
        return new PortalPasskeyAssertionStartResponse(challengeId, challenge, DEFAULT_RP_ID, DEFAULT_ORIGIN, credentialIds, expiresAt);
    }

    public GatewayUserEntity finishPasskeyAssertion(PortalPasskeyAssertionFinishRequest request) {
        PasskeyChallenge challenge = consumePasskeyChallenge(request.challengeId(), "webauthn.get");
        GatewayUserPasskeyCredentialEntity credential = requirePasskeyRepository()
                .findByCredentialIdAndActiveTrue(required(request.credentialId(), "credentialId"))
                .orElseThrow(() -> new GatewayUnauthorizedException("Passkey credential 不存在。"));
        if (!credential.getUser().getId().equals(challenge.userId())) {
            throw new GatewayUnauthorizedException("Passkey challenge 不属于当前凭证。");
        }
        if (!challenge.allowedCredentialIds().contains(credential.getCredentialId())) {
            throw new GatewayUnauthorizedException("Passkey credential 不在本次 challenge 允许列表。");
        }
        verifyClientData(request.clientDataJson(), "webauthn.get", challenge);
        verifyAssertionSignature(credential, request.authenticatorDataBase64(), request.clientDataJson(), request.signatureBase64());
        credential.setSignCount(credential.getSignCount() + 1);
        credential.setLastUsedAt(Instant.now());
        GatewayUserPasskeyCredentialEntity saved = requirePasskeyRepository().save(credential);
        audit(saved.getUser(), "PASSKEY_ASSERTED", "SUCCESS", "{\"credentialId\":\"" + saved.getCredentialId() + "\"}");
        return saved.getUser();
    }

    public List<PortalPasskeyCredentialResponse> deletePasskey(WebSession session, Long id) {
        GatewayUserEntity user = requireCurrentUser(session);
        GatewayUserPasskeyCredentialEntity credential = requirePasskeyRepository()
                .findByUser_IdAndIdAndActiveTrue(user.getId(), id)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定 Passkey。"));
        credential.setActive(false);
        requirePasskeyRepository().save(credential);
        audit(user, "PASSKEY_DELETED", "SUCCESS", "{\"credentialId\":\"" + credential.getCredentialId() + "\"}");
        return listPasskeys(session);
    }

    public void verifyLoginTotpIfRequired(GatewayUserEntity user, String code) {
        if (user == null || !user.isTotpEnabled()) {
            return;
        }
        verifyTotpForUser(user, code);
    }

    public String currentTotpCodeForTests(GatewayUserEntity user, Instant instant) {
        return totpCode(decryptTotpSecret(user), instant.getEpochSecond() / TOTP_STEP_SECONDS);
    }

    private void verifyTotpForUser(GatewayUserEntity user, String code) {
        if (code == null || code.isBlank()) {
            throw new GatewayUnauthorizedException("需要 TOTP 验证码。");
        }
        String secret = decryptTotpSecret(user);
        long window = Instant.now().getEpochSecond() / TOTP_STEP_SECONDS;
        for (long offset = -1; offset <= 1; offset++) {
            if (totpCode(secret, window + offset).equals(code.trim())) {
                return;
            }
        }
        throw new GatewayUnauthorizedException("TOTP 验证码错误。");
    }

    private String decryptTotpSecret(GatewayUserEntity user) {
        if (user.getTotpSecretCiphertext() == null || user.getTotpSecretCiphertext().isBlank()) {
            throw new IllegalStateException("当前用户尚未设置 TOTP。");
        }
        return credentialCryptoService.decrypt(user.getTotpSecretCiphertext());
    }

    private String totpCode(String base32Secret, long counter) {
        try {
            byte[] key = decodeBase32(base32Secret);
            byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(counterBytes);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成 TOTP 验证码。", exception);
        }
    }

    private String generateBase32Secret() {
        byte[] random = new byte[20];
        secureRandom.nextBytes(random);
        return encodeBase32(random);
    }

    private String encodeBase32(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                builder.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1f));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            builder.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1f));
        }
        return builder.toString();
    }

    private byte[] decodeBase32(String secret) {
        int buffer = 0;
        int bitsLeft = 0;
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        for (char raw : secret.toUpperCase(Locale.ROOT).toCharArray()) {
            if (raw == '=') {
                break;
            }
            int value = BASE32_ALPHABET.indexOf(raw);
            if (value < 0) {
                continue;
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output.write((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return output.toByteArray();
    }

    private GatewayUserEntity requireCurrentUser(WebSession session) {
        Object raw = session.getAttributes().get(PORTAL_USER_ID_SESSION_KEY);
        Long userId = null;
        if (raw instanceof Long value) {
            userId = value;
        } else if (raw instanceof Number value) {
            userId = value.longValue();
        } else if (raw instanceof String value && !value.isBlank()) {
            userId = Long.parseLong(value);
        }
        if (userId == null) {
            throw new GatewayUnauthorizedException("请先登录用户门户。");
        }
        return gatewayUserRepository.findById(userId)
                .filter(GatewayUserEntity::isActive)
                .orElseThrow(() -> new GatewayUnauthorizedException("门户会话已失效，请重新登录。"));
    }

    private PortalSecurityStatusResponse toStatus(GatewayUserEntity user) {
        int passkeyCount = passkeyCredentialRepository == null
                ? 0
                : Math.toIntExact(passkeyCredentialRepository.countByUser_IdAndActiveTrue(user.getId()));
        return new PortalSecurityStatusResponse(
                user.getEmailVerifiedAt() != null,
                user.getEmailVerifiedAt(),
                user.isTotpEnabled(),
                user.getTotpVerifiedAt(),
                passkeyCount > 0,
                passkeyCount,
                registrationPolicy.emailVerificationRequiredForKeyCreation()
        );
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private GatewayUserPasskeyCredentialRepository requirePasskeyRepository() {
        if (passkeyCredentialRepository == null) {
            throw new IllegalStateException("Passkey repository 尚未配置。");
        }
        return passkeyCredentialRepository;
    }

    private PasskeyChallenge consumePasskeyChallenge(String challengeId, String expectedType) {
        if (challengeId == null || challengeId.isBlank()) {
            throw new IllegalArgumentException("Passkey challengeId 不能为空。");
        }
        PasskeyChallenge challenge = passkeyChallenges.remove(challengeId.trim());
        if (challenge == null || challenge.expiresAt().isBefore(Instant.now())) {
            throw new GatewayUnauthorizedException("Passkey challenge 已过期或不存在。");
        }
        if (!expectedType.equals(challenge.type())) {
            throw new GatewayUnauthorizedException("Passkey challenge 类型不匹配。");
        }
        return challenge;
    }

    private void verifyClientData(String clientDataJson, String expectedType, PasskeyChallenge challenge) {
        try {
            JsonNode root = objectMapper.readTree(required(clientDataJson, "clientDataJson"));
            String type = root.path("type").asText(null);
            String challengeText = root.path("challenge").asText(null);
            String origin = root.path("origin").asText(null);
            if (!expectedType.equals(type)) {
                throw new GatewayUnauthorizedException("Passkey clientData type 不匹配。");
            }
            if (!challenge.challenge().equals(challengeText)) {
                throw new GatewayUnauthorizedException("Passkey challenge 校验失败。");
            }
            if (!challenge.origin().equals(origin)) {
                throw new GatewayUnauthorizedException("Passkey origin 校验失败。");
            }
        } catch (GatewayUnauthorizedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Passkey clientDataJson 不是合法 JSON。", exception);
        }
    }

    private void verifyAssertionSignature(
            GatewayUserPasskeyCredentialEntity credential,
            String authenticatorDataBase64,
            String clientDataJson,
            String signatureBase64) {
        try {
            byte[] authenticatorData = decodeBase64(authenticatorDataBase64);
            byte[] clientDataHash = MessageDigest.getInstance("SHA-256")
                    .digest(clientDataJson.getBytes(StandardCharsets.UTF_8));
            ByteBuffer signedPayload = ByteBuffer.allocate(authenticatorData.length + clientDataHash.length);
            signedPayload.put(authenticatorData);
            signedPayload.put(clientDataHash);
            PublicKey publicKey = parsePublicKey(credential.getPublicKeyPem());
            Signature signature = Signature.getInstance("RSA".equalsIgnoreCase(publicKey.getAlgorithm()) ? "SHA256withRSA" : "SHA256withECDSA");
            signature.initVerify(publicKey);
            signature.update(signedPayload.array());
            if (!signature.verify(decodeBase64(signatureBase64))) {
                throw new GatewayUnauthorizedException("Passkey 签名校验失败。");
            }
        } catch (GatewayUnauthorizedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GatewayUnauthorizedException("Passkey assertion 校验失败。");
        }
    }

    private PublicKey parsePublicKey(String publicKeyPem) {
        String normalized = required(publicKeyPem, "publicKeyPem")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] encoded = Base64.getDecoder().decode(normalized);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(encoded);
        try {
            return KeyFactory.getInstance("EC").generatePublic(spec);
        } catch (Exception ignored) {
            try {
                return KeyFactory.getInstance("RSA").generatePublic(spec);
            } catch (Exception exception) {
                throw new IllegalArgumentException("Passkey publicKeyPem 不合法。", exception);
            }
        }
    }

    private byte[] decodeBase64(String value) {
        String raw = required(value, "base64");
        try {
            return Base64.getDecoder().decode(raw);
        } catch (IllegalArgumentException ignored) {
            return Base64.getUrlDecoder().decode(raw);
        }
    }

    private String randomBase64Url(int bytes) {
        byte[] random = new byte[bytes];
        secureRandom.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private PortalPasskeyCredentialResponse toPasskeyResponse(GatewayUserPasskeyCredentialEntity entity) {
        return new PortalPasskeyCredentialResponse(
                entity.getId(),
                entity.getCredentialId(),
                entity.getCredentialName(),
                entity.getRpId(),
                entity.getOrigin(),
                readStringList(entity.getTransportsJson()),
                entity.getSignCount(),
                entity.getLastUsedAt(),
                entity.getCreatedAt()
        );
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            for (int index = 0; index < root.size(); index++) {
                String value = root.get(index).asText(null);
                if (value != null && !value.isBlank()) {
                    values.add(value.trim());
                }
            }
            return List.copyOf(values);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<String> normalizePlainList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<String> normalizeDomains(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT).replaceFirst("^@", ""))
                .distinct()
                .toList();
    }

    private Set<String> normalizeInviteCodes(List<String> values) {
        if (values == null) {
            return Set.of();
        }
        Set<String> codes = new HashSet<>();
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .forEach(codes::add);
        return Set.copyOf(codes);
    }

    private boolean domainAllowed(String email, List<String> allowedDomains) {
        int at = email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) {
            return false;
        }
        String domain = email.substring(at + 1);
        return allowedDomains.stream().anyMatch(allowed -> domain.equals(allowed) || domain.endsWith("." + allowed));
    }

    private PortalRegistrationPolicyResponse toPolicyResponse(RegistrationPolicy policy) {
        return new PortalRegistrationPolicyResponse(
                policy.allowedEmailDomains(),
                policy.inviteCodeRequired(),
                !policy.inviteCodes().isEmpty(),
                policy.emailVerificationRequiredForKeyCreation(),
                policy.updatedAt()
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空。");
        }
        return value.trim();
    }

    private void audit(GatewayUserEntity user, String action, String status, String detailJson) {
        if (auditLogRepository == null) {
            return;
        }
        AuditLogEntity entity = new AuditLogEntity();
        entity.setAuditType("PORTAL_SECURITY");
        entity.setAction(action);
        entity.setTargetType(user == null ? "registration_policy" : "gateway_user");
        entity.setTargetId(user == null || user.getId() == null ? "portal" : String.valueOf(user.getId()));
        entity.setStatus(status);
        entity.setActor(user == null ? "system" : user.getEmail());
        entity.setPath("/portal/auth/security");
        entity.setDetailJson(detailJson == null || detailJson.isBlank() ? "{}" : detailJson);
        auditLogRepository.save(entity);
    }

    private record Challenge(String secret, Long userId, Instant expiresAt) {
    }

    private record PasskeyChallenge(
            String type,
            Long userId,
            String challenge,
            String rpId,
            String origin,
            List<String> allowedCredentialIds,
            Instant expiresAt) {
    }

    private record RegistrationPolicy(
            List<String> allowedEmailDomains,
            boolean inviteCodeRequired,
            Set<String> inviteCodes,
            boolean emailVerificationRequiredForKeyCreation,
            Instant updatedAt) {
    }
}
