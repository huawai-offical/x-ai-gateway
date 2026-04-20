package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.AdminAuthChallengeResponse;
import com.prodigalgal.xaigateway.admin.api.AdminLoginRequest;
import com.prodigalgal.xaigateway.admin.api.AdminSessionResponse;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayUnauthorizedException;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

@Service
public class AdminAuthService {

    private static final String POW_ALGORITHM = "SHA-256";
    private static final String CHALLENGE_SESSION_KEY = "adminConsoleLoginChallenge";
    private static final String AUTHENTICATED_AT_SESSION_KEY = "adminConsoleAuthenticatedAt";

    private final GatewayProperties gatewayProperties;
    private final PasswordEncoder passwordEncoder;
    private final ServerSecurityContextRepository securityContextRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminAuthService(
            GatewayProperties gatewayProperties,
            PasswordEncoder passwordEncoder) {
        this.gatewayProperties = gatewayProperties;
        this.passwordEncoder = passwordEncoder;
        this.securityContextRepository = new WebSessionServerSecurityContextRepository();
    }

    public Mono<AdminSessionResponse> currentSession(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .cast(Authentication.class)
                .filter(Authentication::isAuthenticated)
                .flatMap(authentication -> exchange.getSession()
                        .map(session -> authenticatedResponse(authentication.getName(), session)))
                .switchIfEmpty(exchange.getSession()
                        .map(this::readSessionResponse));
    }

    public Mono<AdminAuthChallengeResponse> issueChallenge(ServerWebExchange exchange) {
        if (!gatewayProperties.getAdminConsole().isEnabled()) {
            return Mono.error(new GatewayUnauthorizedException("控制台登录已禁用。"));
        }

        return exchange.getSession().map(session -> {
            LoginChallengeState challenge = nextChallengeState();
            session.getAttributes().put(CHALLENGE_SESSION_KEY, challenge);
            session.setMaxIdleTime(gatewayProperties.getAdminConsole().getChallengeTtl());
            return new AdminAuthChallengeResponse(
                    challenge.challengeId(),
                    challenge.mathPrompt(),
                    challenge.issuedAt(),
                    challenge.expiresAt(),
                    POW_ALGORITHM,
                    challenge.powSalt(),
                    challenge.powDifficulty()
            );
        });
    }

    public Mono<AdminSessionResponse> login(AdminLoginRequest request, ServerWebExchange exchange) {
        GatewayProperties.AdminConsole adminConsole = gatewayProperties.getAdminConsole();
        if (!adminConsole.isEnabled()) {
            return Mono.error(new GatewayUnauthorizedException("控制台登录已禁用。"));
        }

        String username = request.username().trim();
        return exchange.getSession().flatMap(session -> {
            LoginChallengeState challenge = requireChallenge(session, request.challengeId());
            session.getAttributes().remove(CHALLENGE_SESSION_KEY);

            validateMathAnswer(request, challenge);
            validatePow(request, challenge);
            validateCredentials(username, request.password(), adminConsole);

            Instant authenticatedAt = Instant.now();
            session.setMaxIdleTime(adminConsole.getSessionTtl());
            session.getAttributes().put(AUTHENTICATED_AT_SESSION_KEY, authenticatedAt.toString());

            Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                    username,
                    "N/A",
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
            );

            return securityContextRepository.save(exchange, new SecurityContextImpl(authentication))
                    .thenReturn(authenticatedResponse(username, session));
        });
    }

    public Mono<Void> logout(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            session.getAttributes().remove(CHALLENGE_SESSION_KEY);
            session.getAttributes().remove(AUTHENTICATED_AT_SESSION_KEY);
            session.getAttributes().remove(WebSessionServerSecurityContextRepository.DEFAULT_SPRING_SECURITY_CONTEXT_ATTR_NAME);
            return session.invalidate();
        });
    }

    private LoginChallengeState requireChallenge(WebSession session, String challengeId) {
        Object value = session.getAttributes().get(CHALLENGE_SESSION_KEY);
        if (!(value instanceof LoginChallengeState challenge)) {
            throw new IllegalArgumentException("登录 challenge 已失效，请刷新验证码后重试。");
        }
        if (!challenge.challengeId().equals(challengeId)) {
            throw new IllegalArgumentException("登录 challenge 不匹配，请刷新验证码后重试。");
        }
        if (challenge.expiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("登录 challenge 已过期，请刷新验证码后重试。");
        }
        return challenge;
    }

    private void validateMathAnswer(AdminLoginRequest request, LoginChallengeState challenge) {
        if (!challenge.expectedAnswer().equals(request.mathAnswer())) {
            throw new IllegalArgumentException("数学验证码计算结果错误，请重试。");
        }
    }

    private void validatePow(AdminLoginRequest request, LoginChallengeState challenge) {
        if (!isPowValid(challenge, request.powNonce())) {
            throw new IllegalArgumentException("POW 校验失败，请刷新 challenge 后重试。");
        }
    }

    private void validateCredentials(
            String username,
            String password,
            GatewayProperties.AdminConsole adminConsole) {
        if (!adminConsole.getUsername().equals(username)
                || !passwordEncoder.matches(password, adminConsole.getPassword())) {
            throw new GatewayUnauthorizedException("账号或密码错误。");
        }
    }

    private boolean isPowValid(LoginChallengeState challenge, String nonce) {
        if (nonce == null || nonce.isBlank()) {
            return false;
        }
        byte[] hash;
        try {
            MessageDigest digest = MessageDigest.getInstance(POW_ALGORITHM);
            hash = digest.digest((challenge.challengeId() + ":" + challenge.powSalt() + ":" + nonce.trim())
                    .getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前环境不支持 SHA-256。", exception);
        }

        int leadingZeroHexChars = challenge.powDifficulty();
        for (int index = 0; index < leadingZeroHexChars; index += 1) {
            int currentByte = hash[index / 2] & 0xFF;
            int nibble = index % 2 == 0 ? (currentByte >>> 4) : (currentByte & 0x0F);
            if (nibble != 0) {
                return false;
            }
        }
        return true;
    }

    private LoginChallengeState nextChallengeState() {
        GatewayProperties.AdminConsole adminConsole = gatewayProperties.getAdminConsole();
        int min = Math.min(adminConsole.getMathMin(), adminConsole.getMathMax());
        int max = Math.max(adminConsole.getMathMin(), adminConsole.getMathMax());

        int first = randomBetween(min, max);
        int second = randomBetween(min, max);
        MathOperator operator = randomOperator();
        if (operator == MathOperator.SUBTRACT && first < second) {
            int swapped = first;
            first = second;
            second = swapped;
        }

        int expectedAnswer = switch (operator) {
            case ADD -> first + second;
            case SUBTRACT -> first - second;
            case MULTIPLY -> first * second;
        };

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(adminConsole.getChallengeTtl());
        return new LoginChallengeState(
                UUID.randomUUID().toString(),
                first + " " + operator.symbol + " " + second + " = ?",
                expectedAnswer,
                randomHex(12),
                adminConsole.getPowDifficulty(),
                issuedAt,
                expiresAt
        );
    }

    private int randomBetween(int min, int max) {
        if (min == max) {
            return min;
        }
        return secureRandom.nextInt(max - min + 1) + min;
    }

    private MathOperator randomOperator() {
        MathOperator[] values = MathOperator.values();
        return values[secureRandom.nextInt(values.length)];
    }

    private String randomHex(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            builder.append(Character.forDigit((current >> 4) & 0x0F, 16));
            builder.append(Character.forDigit(current & 0x0F, 16));
        }
        return builder.toString();
    }

    private AdminSessionResponse authenticatedResponse(String username, WebSession session) {
        Instant authenticatedAt = readAuthenticatedAt(session);
        Instant expiresAt = authenticatedAt == null
                ? null
                : authenticatedAt.plus(resolveSessionTtl(session.getMaxIdleTime()));
        return new AdminSessionResponse(true, username, authenticatedAt, expiresAt);
    }

    private AdminSessionResponse readSessionResponse(WebSession session) {
        Object rawSecurityContext = session.getAttributes()
                .get(WebSessionServerSecurityContextRepository.DEFAULT_SPRING_SECURITY_CONTEXT_ATTR_NAME);
        if (!(rawSecurityContext instanceof SecurityContext securityContext)) {
            return AdminSessionResponse.unauthenticated();
        }

        Authentication authentication = securityContext.getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return AdminSessionResponse.unauthenticated();
        }

        return authenticatedResponse(authentication.getName(), session);
    }

    private Instant readAuthenticatedAt(WebSession session) {
        Object raw = session.getAttributes().get(AUTHENTICATED_AT_SESSION_KEY);
        if (raw instanceof String text && !text.isBlank()) {
            return Instant.parse(text);
        }
        return null;
    }

    private Duration resolveSessionTtl(Duration sessionMaxIdle) {
        return sessionMaxIdle == null || sessionMaxIdle.isNegative()
                ? gatewayProperties.getAdminConsole().getSessionTtl()
                : sessionMaxIdle;
    }

    private enum MathOperator {
        ADD("+"),
        SUBTRACT("-"),
        MULTIPLY("*");

        private final String symbol;

        MathOperator(String symbol) {
            this.symbol = symbol;
        }
    }

    private record LoginChallengeState(
            String challengeId,
            String mathPrompt,
            Integer expectedAnswer,
            String powSalt,
            int powDifficulty,
            Instant issuedAt,
            Instant expiresAt
    ) {
    }
}
