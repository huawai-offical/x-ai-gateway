package com.prodigalgal.xaigateway.portal.application;

import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayUnauthorizedException;
import com.prodigalgal.xaigateway.infra.persistence.entity.AuditLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserPasskeyCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.AuditLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserPasskeyCredentialRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserRepository;
import com.prodigalgal.xaigateway.portal.api.PortalEmailVerificationConfirmRequest;
import com.prodigalgal.xaigateway.portal.api.PortalPasskeyAssertionFinishRequest;
import com.prodigalgal.xaigateway.portal.api.PortalPasskeyAssertionStartRequest;
import com.prodigalgal.xaigateway.portal.api.PortalPasskeyRegistrationFinishRequest;
import com.prodigalgal.xaigateway.portal.api.PortalPasskeyRegistrationStartRequest;
import com.prodigalgal.xaigateway.portal.api.PortalRegistrationPolicyRequest;
import com.prodigalgal.xaigateway.portal.api.PortalTotpVerifyRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.WebSession;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalSecurityServiceTests {

    @Test
    void shouldVerifyCaptchaAnswer() {
        PortalSecurityService service = service(Mockito.mock(GatewayUserRepository.class), Mockito.mock(CredentialCryptoService.class));

        var challenge = service.createCaptchaChallenge();
        service.verifyCaptcha(challenge.challengeId(), captchaAnswer(challenge.question()));

        var wrong = service.createCaptchaChallenge();
        assertThrows(IllegalArgumentException.class, () -> service.verifyCaptcha(wrong.challengeId(), "999"));
    }

    @Test
    void shouldConfirmEmailVerificationForCurrentUser() {
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        GatewayUserEntity user = user();
        WebSession session = loggedInSession(user);
        Mockito.when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        Mockito.when(userRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        PortalSecurityService service = service(userRepository, credentialCryptoService);

        var started = service.startEmailVerification(session);
        var status = service.confirmEmailVerification(
                session,
                new PortalEmailVerificationConfirmRequest(started.verificationId(), started.verificationCode())
        );

        assertTrue(status.emailVerified());
        assertTrue(user.getEmailVerifiedAt() != null);
    }

    @Test
    void shouldSetupEnableAndDisableTotp() {
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        GatewayUserEntity user = user();
        WebSession session = loggedInSession(user);
        Mockito.when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        Mockito.when(userRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(credentialCryptoService.encrypt(Mockito.anyString())).thenAnswer(invocation -> "cipher:" + invocation.getArgument(0));
        Mockito.when(credentialCryptoService.decrypt(Mockito.anyString())).thenAnswer(invocation -> {
            String ciphertext = invocation.getArgument(0);
            return ciphertext.substring("cipher:".length());
        });
        PortalSecurityService service = service(userRepository, credentialCryptoService);

        var setup = service.setupTotp(session);
        assertTrue(setup.otpauthUri().contains("otpauth://totp/"));
        assertFalse(user.isTotpEnabled());

        String code = service.currentTotpCodeForTests(user, Instant.now());
        var enabled = service.enableTotp(session, new PortalTotpVerifyRequest(code));
        assertTrue(enabled.totpEnabled());

        assertThrows(GatewayUnauthorizedException.class, () -> service.verifyLoginTotpIfRequired(user, "000000"));
        service.verifyLoginTotpIfRequired(user, service.currentTotpCodeForTests(user, Instant.now()));

        var disabled = service.disableTotp(session, new PortalTotpVerifyRequest(code));
        assertFalse(disabled.totpEnabled());
        assertTrue(user.getTotpSecretCiphertext() == null);
    }

    @Test
    void shouldRegisterAssertAndDeletePasskeyWithAudit() throws Exception {
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        GatewayUserPasskeyCredentialRepository passkeyRepository = Mockito.mock(GatewayUserPasskeyCredentialRepository.class);
        AuditLogRepository auditLogRepository = Mockito.mock(AuditLogRepository.class);
        GatewayUserEntity user = user();
        WebSession session = loggedInSession(user);
        AtomicReference<GatewayUserPasskeyCredentialEntity> savedCredential = new AtomicReference<>();
        Mockito.when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        Mockito.when(userRepository.findByEmailIgnoreCase("security@example.com")).thenReturn(Optional.of(user));
        Mockito.when(passkeyRepository.findByCredentialIdAndActiveTrue("cred-1"))
                .thenAnswer(invocation -> Optional.ofNullable(savedCredential.get()).filter(GatewayUserPasskeyCredentialEntity::isActive));
        Mockito.when(passkeyRepository.save(Mockito.any())).thenAnswer(invocation -> {
            GatewayUserPasskeyCredentialEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                ReflectionTestUtils.setField(entity, "id", 77L);
            }
            savedCredential.set(entity);
            return entity;
        });
        Mockito.when(passkeyRepository.findAllByUser_IdAndActiveTrueOrderByCreatedAtDesc(42L))
                .thenAnswer(invocation -> savedCredential.get() == null || !savedCredential.get().isActive() ? List.of() : List.of(savedCredential.get()));
        Mockito.when(passkeyRepository.findByUser_IdAndIdAndActiveTrue(42L, 77L))
                .thenAnswer(invocation -> Optional.ofNullable(savedCredential.get()).filter(GatewayUserPasskeyCredentialEntity::isActive));
        Mockito.when(auditLogRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        PortalSecurityService service = new PortalSecurityService(userRepository, credentialCryptoService, passkeyRepository, auditLogRepository);
        KeyPair keyPair = ecKeyPair();

        var registration = service.startPasskeyRegistration(session, new PortalPasskeyRegistrationStartRequest("工作电脑"));
        String createClientData = clientData("webauthn.create", registration.challenge(), registration.origin());
        var registered = service.finishPasskeyRegistration(session, new PortalPasskeyRegistrationFinishRequest(
                registration.challengeId(),
                "cred-1",
                "工作电脑",
                createClientData,
                publicKeyPem(keyPair),
                List.of("internal")
        ));

        var assertion = service.startPasskeyAssertion(new PortalPasskeyAssertionStartRequest("security@example.com"));
        String getClientData = clientData("webauthn.get", assertion.challenge(), assertion.origin());
        byte[] authenticatorData = "auth-data".getBytes(StandardCharsets.UTF_8);
        String signature = signAssertion(keyPair, authenticatorData, getClientData);
        GatewayUserEntity assertedUser = service.finishPasskeyAssertion(new PortalPasskeyAssertionFinishRequest(
                assertion.challengeId(),
                "cred-1",
                getClientData,
                Base64.getEncoder().encodeToString(authenticatorData),
                signature
        ));
        var remaining = service.deletePasskey(session, registered.id());

        assertTrue(registered.credentialId().equals("cred-1"));
        assertTrue(assertedUser.getId().equals(user.getId()));
        assertTrue(savedCredential.get().getSignCount() == 1L);
        assertTrue(remaining.isEmpty());
        assertFalse(savedCredential.get().isActive());
        Mockito.verify(auditLogRepository, Mockito.atLeast(4)).save(Mockito.any(AuditLogEntity.class));
    }

    @Test
    void shouldEnforceRegistrationPolicyAndEmailVerifiedKeyCreation() {
        AuditLogRepository auditLogRepository = Mockito.mock(AuditLogRepository.class);
        GatewayUserEntity user = user();
        PortalSecurityService service = new PortalSecurityService(
                Mockito.mock(GatewayUserRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                Mockito.mock(GatewayUserPasskeyCredentialRepository.class),
                auditLogRepository
        );

        var policy = service.updateRegistrationPolicy(new PortalRegistrationPolicyRequest(
                List.of("example.com"),
                true,
                List.of("INVITE-1"),
                true
        ));

        assertTrue(policy.inviteCodeRequired());
        assertTrue(policy.allowedRegistrationChannels().contains(PortalSecurityService.REGISTRATION_CHANNEL_PASSWORD));
        assertThrows(IllegalArgumentException.class, () -> service.verifyRegistrationPolicy("bad@test.com", "INVITE-1"));
        assertThrows(IllegalArgumentException.class, () -> service.verifyRegistrationPolicy("security@example.com", null));
        service.verifyRegistrationPolicy("security@example.com", "invite-1");
        assertThrows(GatewayUnauthorizedException.class, () -> service.assertKeyCreationAllowed(user));
        user.setEmailVerifiedAt(Instant.now());
        service.assertKeyCreationAllowed(user);
    }

    @Test
    void shouldEnforceRegistrationChannelsAndInviteChannel() {
        PortalSecurityService service = new PortalSecurityService(
                Mockito.mock(GatewayUserRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                Mockito.mock(GatewayUserPasskeyCredentialRepository.class),
                Mockito.mock(AuditLogRepository.class)
        );

        service.updateRegistrationPolicy(new PortalRegistrationPolicyRequest(
                List.of(),
                List.of(PortalSecurityService.REGISTRATION_CHANNEL_INVITE_CODE),
                false,
                List.of("INVITE-2"),
                false
        ));

        assertThrows(IllegalArgumentException.class, () -> service.verifyRegistrationPolicy(
                "security@example.com",
                null,
                PortalSecurityService.REGISTRATION_CHANNEL_PASSWORD
        ));
        assertThrows(IllegalArgumentException.class, () -> service.verifyRegistrationPolicy(
                "security@example.com",
                null,
                PortalSecurityService.REGISTRATION_CHANNEL_INVITE_CODE
        ));
        service.verifyRegistrationPolicy(
                "security@example.com",
                "invite-2",
                PortalSecurityService.REGISTRATION_CHANNEL_INVITE_CODE
        );
        assertThrows(IllegalArgumentException.class, () -> service.verifyRegistrationPolicy(
                "security@example.com",
                null,
                PortalSecurityService.REGISTRATION_CHANNEL_SOCIAL_OAUTH
        ));
        assertThrows(IllegalArgumentException.class, () -> {
            service.updateRegistrationPolicy(new PortalRegistrationPolicyRequest(
                    List.of(),
                    List.of(PortalSecurityService.REGISTRATION_CHANNEL_SOCIAL_OAUTH),
                    true,
                    null,
                    false
            ));
            service.verifyRegistrationPolicy(
                    "security@example.com",
                    null,
                    PortalSecurityService.REGISTRATION_CHANNEL_SOCIAL_OAUTH
            );
        });
        service.verifyRegistrationPolicy(
                "security@example.com",
                "invite-social",
                PortalSecurityService.REGISTRATION_CHANNEL_SOCIAL_OAUTH
        );

        service.updateRegistrationPolicy(new PortalRegistrationPolicyRequest(
                List.of(),
                List.of(PortalSecurityService.REGISTRATION_CHANNEL_SOCIAL_OAUTH),
                false,
                null,
                false
        ));
        service.verifyRegistrationPolicy(
                "security@example.com",
                null,
                PortalSecurityService.REGISTRATION_CHANNEL_SOCIAL_OAUTH
        );
    }

    private PortalSecurityService service(
            GatewayUserRepository userRepository,
            CredentialCryptoService credentialCryptoService) {
        return new PortalSecurityService(userRepository, credentialCryptoService);
    }

    private WebSession loggedInSession(GatewayUserEntity user) {
        WebSession session = MockServerWebExchange.from(MockServerHttpRequest.get("/portal").build())
                .getSession()
                .block();
        session.getAttributes().put("portalUserId", user.getId());
        return session;
    }

    private GatewayUserEntity user() {
        GatewayUserEntity user = new GatewayUserEntity();
        ReflectionTestUtils.setField(user, "id", 42L);
        user.setEmail("security@example.com");
        user.setDisplayName("Security");
        user.setActive(true);
        return user;
    }

    private String captchaAnswer(String question) {
        String[] parts = question.replace("= ?", "").split("\\+");
        int left = Integer.parseInt(parts[0].trim());
        int right = Integer.parseInt(parts[1].trim());
        return String.valueOf(left + right);
    }

    private KeyPair ecKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        return generator.generateKeyPair();
    }

    private String publicKeyPem(KeyPair keyPair) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }

    private String clientData(String type, String challenge, String origin) {
        return """
                {"type":"%s","challenge":"%s","origin":"%s"}
                """.formatted(type, challenge, origin).trim();
    }

    private String signAssertion(KeyPair keyPair, byte[] authenticatorData, String clientDataJson) throws Exception {
        byte[] clientDataHash = MessageDigest.getInstance("SHA-256").digest(clientDataJson.getBytes(StandardCharsets.UTF_8));
        ByteBuffer payload = ByteBuffer.allocate(authenticatorData.length + clientDataHash.length);
        payload.put(authenticatorData);
        payload.put(clientDataHash);
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(payload.array());
        return Base64.getEncoder().encodeToString(signature.sign());
    }
}
