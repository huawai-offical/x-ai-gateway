package com.prodigalgal.xaigateway.admin.application;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.admin.api.AdminAuthSettingsResponse;
import com.prodigalgal.xaigateway.admin.api.AdminAuthSettingsUpdateRequest;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayUnauthorizedException;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.SystemSettingEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.SystemSettingRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminConsoleCredentialService {

    static final String SETTING_KEY = "gateway.admin-console.credentials";
    private static final String VALUE_TYPE = "json";
    private static final String DESCRIPTION = "控制台管理员账号与密码哈希。";
    private static final Pattern ENCODED_PASSWORD_PATTERN = Pattern.compile("^\\{[^}]+}.*$");
    private static final char[] PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
    private static final int GENERATED_PASSWORD_LENGTH = 18;

    private final Logger log = LoggerFactory.getLogger(AdminConsoleCredentialService.class);
    private final SystemSettingRepository systemSettingRepository;
    private final GatewayProperties gatewayProperties;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminConsoleCredentialService(
            SystemSettingRepository systemSettingRepository,
            GatewayProperties gatewayProperties,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper) {
        this.systemSettingRepository = systemSettingRepository;
        this.gatewayProperties = gatewayProperties;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapAtStartup() {
        ensureInitialized();
    }

    public boolean matches(String username, String password) {
        PersistedAdminConsoleCredentials credentials = loadCredentials();
        return credentials.username().equals(username)
                && passwordEncoder.matches(password, credentials.passwordHash());
    }

    public AdminAuthSettingsResponse getSettings() {
        return toResponse(loadCredentials());
    }

    public AdminAuthSettingsResponse updateSettings(AdminAuthSettingsUpdateRequest request) {
        PersistedAdminConsoleCredentials current = loadCredentials();
        if (!passwordEncoder.matches(request.currentPassword(), current.passwordHash())) {
            throw new GatewayUnauthorizedException("当前密码不正确。");
        }

        String nextUsername = normalizeUsername(request.username());
        String nextPassword = request.newPassword();
        if (nextPassword != null && !nextPassword.isBlank() && nextPassword.length() < 12) {
            throw new IllegalArgumentException("新密码长度至少为 12 位。");
        }

        boolean usernameChanged = !current.username().equals(nextUsername);
        boolean passwordChanged = nextPassword != null
                && !nextPassword.isBlank()
                && !passwordEncoder.matches(nextPassword, current.passwordHash());
        if (!usernameChanged && !passwordChanged) {
            throw new IllegalArgumentException("未检测到账号或密码变化。");
        }

        Instant now = Instant.now();
        PersistedAdminConsoleCredentials updated = new PersistedAdminConsoleCredentials(
                nextUsername,
                passwordChanged ? passwordEncoder.encode(nextPassword) : current.passwordHash(),
                current.initializedAt(),
                now,
                "MANUAL_UPDATE"
        );
        write(updated);
        return toResponse(updated);
    }

    private PersistedAdminConsoleCredentials loadCredentials() {
        return ensureInitialized().credentials();
    }

    private synchronized InitializationResult ensureInitialized() {
        Optional<SystemSettingEntity> existing = systemSettingRepository.findBySettingKey(SETTING_KEY);
        if (existing.isPresent()) {
            return new InitializationResult(read(existing.get()), null, false);
        }

        BootstrapCredentials initialized = bootstrapCredentials();
        write(initialized.credentials());
        emitBootstrapMessage(initialized.credentials(), initialized.generatedPassword());
        return new InitializationResult(initialized.credentials(), initialized.generatedPassword(), true);
    }

    private BootstrapCredentials bootstrapCredentials() {
        Instant now = Instant.now();
        String username = normalizeUsername(gatewayProperties.getAdminConsole().getUsername());
        String configuredPassword = trimToNull(gatewayProperties.getAdminConsole().getPassword());
        if (configuredPassword == null) {
            String generatedPassword = generatePassword();
            return new BootstrapCredentials(
                    new PersistedAdminConsoleCredentials(
                            username,
                            passwordEncoder.encode(generatedPassword),
                            now,
                            now,
                            "RANDOM_BOOTSTRAP"
                    ),
                    generatedPassword
            );
        }

        String passwordHash = ENCODED_PASSWORD_PATTERN.matcher(configuredPassword).matches()
                ? configuredPassword
                : passwordEncoder.encode(configuredPassword);
        return new BootstrapCredentials(
                new PersistedAdminConsoleCredentials(
                        username,
                        passwordHash,
                        now,
                        now,
                        ENCODED_PASSWORD_PATTERN.matcher(configuredPassword).matches()
                                ? "CONFIG_PASSWORD_HASH"
                                : "CONFIG_PASSWORD"
                ),
                null
        );
    }

    private void emitBootstrapMessage(PersistedAdminConsoleCredentials credentials, String generatedPassword) {
        if (generatedPassword != null) {
            log.warn(
                    "\n================ Admin Console Bootstrap ================\n"
                            + "首次启动未检测到已持久化的控制台凭证，系统已生成随机初始密码并写入 system_setting。\n"
                            + "username: {}\n"
                            + "password: {}\n"
                            + "settingKey: {}\n"
                            + "请立即登录控制台并在“控制台认证”页面修改账号或密码。\n"
                            + "=========================================================",
                    credentials.username(),
                    generatedPassword,
                    SETTING_KEY
            );
            return;
        }

        log.info(
                "控制台凭证已初始化并持久化。username={} source={} settingKey={}",
                credentials.username(),
                credentials.source(),
                SETTING_KEY
        );
    }

    private PersistedAdminConsoleCredentials read(SystemSettingEntity entity) {
        try {
            PersistedAdminConsoleCredentials stored = objectMapper.readValue(
                    entity.getSettingValue(),
                    PersistedAdminConsoleCredentials.class
            );
            return normalize(stored);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法读取控制台持久化凭证。", exception);
        }
    }

    private PersistedAdminConsoleCredentials normalize(PersistedAdminConsoleCredentials stored) {
        String username = normalizeUsername(stored.username());
        if (stored.passwordHash() == null || stored.passwordHash().isBlank()) {
            throw new IllegalStateException("控制台持久化密码哈希缺失。");
        }
        Instant initializedAt = stored.initializedAt() == null ? Instant.now() : stored.initializedAt();
        Instant updatedAt = stored.updatedAt() == null ? initializedAt : stored.updatedAt();
        String source = trimToNull(stored.source());
        return new PersistedAdminConsoleCredentials(
                username,
                stored.passwordHash(),
                initializedAt,
                updatedAt,
                source == null ? "LEGACY_PERSISTED" : source
        );
    }

    private void write(PersistedAdminConsoleCredentials credentials) {
        SystemSettingEntity entity = systemSettingRepository.findBySettingKey(SETTING_KEY)
                .orElseGet(SystemSettingEntity::new);
        entity.setSettingKey(SETTING_KEY);
        entity.setValueType(VALUE_TYPE);
        entity.setDescription(DESCRIPTION);
        try {
            entity.setSettingValue(objectMapper.writeValueAsString(credentials));
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法保存控制台持久化凭证。", exception);
        }
        systemSettingRepository.save(entity);
    }

    private String normalizeUsername(String value) {
        String username = trimToNull(value);
        if (username == null) {
            throw new IllegalArgumentException("控制台用户名不能为空。");
        }
        return username;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String generatePassword() {
        StringBuilder builder = new StringBuilder(GENERATED_PASSWORD_LENGTH);
        for (int index = 0; index < GENERATED_PASSWORD_LENGTH; index += 1) {
            builder.append(PASSWORD_ALPHABET[secureRandom.nextInt(PASSWORD_ALPHABET.length)]);
        }
        return builder.toString();
    }

    private AdminAuthSettingsResponse toResponse(PersistedAdminConsoleCredentials credentials) {
        return new AdminAuthSettingsResponse(
                credentials.username(),
                true,
                credentials.source(),
                credentials.initializedAt(),
                credentials.updatedAt()
        );
    }

    private record InitializationResult(
            PersistedAdminConsoleCredentials credentials,
            String generatedPassword,
            boolean created
    ) {
    }

    private record BootstrapCredentials(
            PersistedAdminConsoleCredentials credentials,
            String generatedPassword
    ) {
    }

    private record PersistedAdminConsoleCredentials(
            String username,
            String passwordHash,
            Instant initializedAt,
            Instant updatedAt,
            String source
    ) {
    }
}
