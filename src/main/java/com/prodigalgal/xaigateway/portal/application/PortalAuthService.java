package com.prodigalgal.xaigateway.portal.application;

import com.prodigalgal.xaigateway.gateway.core.auth.GatewayUnauthorizedException;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.AnnouncementEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.AnnouncementReadStateEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserBalanceLedgerEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.PromoCampaignEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RedeemCodeEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RedeemCodeUsageEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UserSubscriptionEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.AnnouncementReadStateRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.AnnouncementRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserBalanceLedgerRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RedeemCodeRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RedeemCodeUsageRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UserSubscriptionRepository;
import com.prodigalgal.xaigateway.portal.api.PortalAnnouncementResponse;
import com.prodigalgal.xaigateway.portal.api.PortalBalanceLedgerResponse;
import com.prodigalgal.xaigateway.portal.api.PortalKeyResponse;
import com.prodigalgal.xaigateway.portal.api.PortalLoginRequest;
import com.prodigalgal.xaigateway.portal.api.PortalRedeemStatusResponse;
import com.prodigalgal.xaigateway.portal.api.PortalRedeemResponse;
import com.prodigalgal.xaigateway.portal.api.PortalRegisterRequest;
import com.prodigalgal.xaigateway.portal.api.PortalSessionResponse;
import com.prodigalgal.xaigateway.portal.api.PortalSubscriptionResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

@Service
@Transactional
public class PortalAuthService {

    private static final String PORTAL_USER_ID_SESSION_KEY = "portalUserId";
    private static final String PORTAL_AUTHENTICATED_AT_SESSION_KEY = "portalAuthenticatedAt";
    private static final Duration PORTAL_SESSION_TTL = Duration.ofHours(12);

    private final GatewayUserRepository gatewayUserRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final DistributedKeyRepository distributedKeyRepository;
    private final AnnouncementRepository announcementRepository;
    private final AnnouncementReadStateRepository announcementReadStateRepository;
    private final RedeemCodeRepository redeemCodeRepository;
    private final RedeemCodeUsageRepository redeemCodeUsageRepository;
    private final GatewayUserBalanceLedgerRepository balanceLedgerRepository;
    private final PasswordEncoder passwordEncoder;

    public PortalAuthService(
            GatewayUserRepository gatewayUserRepository,
            UserSubscriptionRepository userSubscriptionRepository,
            DistributedKeyRepository distributedKeyRepository,
            AnnouncementRepository announcementRepository,
            AnnouncementReadStateRepository announcementReadStateRepository,
            RedeemCodeRepository redeemCodeRepository,
            RedeemCodeUsageRepository redeemCodeUsageRepository,
            GatewayUserBalanceLedgerRepository balanceLedgerRepository,
            PasswordEncoder passwordEncoder) {
        this.gatewayUserRepository = gatewayUserRepository;
        this.userSubscriptionRepository = userSubscriptionRepository;
        this.distributedKeyRepository = distributedKeyRepository;
        this.announcementRepository = announcementRepository;
        this.announcementReadStateRepository = announcementReadStateRepository;
        this.redeemCodeRepository = redeemCodeRepository;
        this.redeemCodeUsageRepository = redeemCodeUsageRepository;
        this.balanceLedgerRepository = balanceLedgerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Mono<PortalSessionResponse> currentSession(ServerWebExchange exchange) {
        return exchange.getSession().map(session -> {
            Long userId = readUserId(session);
            if (userId == null) {
                return PortalSessionResponse.unauthenticated();
            }
            return gatewayUserRepository.findById(userId)
                    .filter(GatewayUserEntity::isActive)
                    .map(user -> toSessionResponse(user, session))
                    .orElseGet(PortalSessionResponse::unauthenticated);
        });
    }

    public Mono<PortalSessionResponse> register(PortalRegisterRequest request, ServerWebExchange exchange) {
        String email = normalizeEmail(request.email());
        if (gatewayUserRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("该邮箱已经注册。");
        }

        GatewayUserEntity user = new GatewayUserEntity();
        user.setEmail(email);
        user.setDisplayName(blankToNull(request.displayName()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setActive(true);
        user.setLastLoginAt(Instant.now());
        GatewayUserEntity saved = gatewayUserRepository.save(user);
        return exchange.getSession().map(session -> authenticateSession(saved, session));
    }

    public Mono<PortalSessionResponse> login(PortalLoginRequest request, ServerWebExchange exchange) {
        String email = normalizeEmail(request.email());
        GatewayUserEntity user = gatewayUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new GatewayUnauthorizedException("邮箱或密码错误。"));
        if (!user.isActive()) {
            throw new GatewayUnauthorizedException("该用户已停用。");
        }
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new GatewayUnauthorizedException("邮箱或密码错误。");
        }

        user.setLastLoginAt(Instant.now());
        gatewayUserRepository.save(user);
        return exchange.getSession().map(session -> authenticateSession(user, session));
    }

    public Mono<Void> logout(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            session.getAttributes().remove(PORTAL_USER_ID_SESSION_KEY);
            session.getAttributes().remove(PORTAL_AUTHENTICATED_AT_SESSION_KEY);
            return session.invalidate();
        });
    }

    @Transactional(readOnly = true)
    public List<PortalSubscriptionResponse> listSubscriptions(WebSession session) {
        GatewayUserEntity user = requireCurrentUser(session);
        return userSubscriptionRepository.findAllByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toSubscriptionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PortalKeyResponse> listKeys(WebSession session) {
        GatewayUserEntity user = requireCurrentUser(session);
        return distributedKeyRepository.findAllByOwnerUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toKeyResponse)
                .toList();
    }

    public List<PortalAnnouncementResponse> listAnnouncements(WebSession session) {
        GatewayUserEntity user = requireCurrentUser(session);
        Set<Long> activePlanIds = activePlanIds(user.getId());
        Set<Long> readIds = announcementReadStateRepository.findAllByUser_Id(user.getId()).stream()
                .map(state -> state.getAnnouncement().getId())
                .collect(Collectors.toSet());
        Instant now = Instant.now();
        return announcementRepository.findAllByStatusOrderByPublishedAtDescCreatedAtDesc("PUBLISHED").stream()
                .filter(entity -> isAnnouncementVisible(entity, user.getId(), activePlanIds, now))
                .map(entity -> toAnnouncementResponse(entity, readIds.contains(entity.getId())))
                .toList();
    }

    public PortalRedeemStatusResponse redeemStatus(WebSession session) {
        GatewayUserEntity user = requireCurrentUser(session);
        long balance = currentBalance(user.getId());
        return new PortalRedeemStatusResponse(true, "兑换码入口已启用。", balance);
    }

    public PortalAnnouncementResponse markAnnouncementRead(WebSession session, Long announcementId) {
        GatewayUserEntity user = requireCurrentUser(session);
        AnnouncementEntity announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定公告。"));
        if (!isAnnouncementVisible(announcement, user.getId(), activePlanIds(user.getId()), Instant.now())) {
            throw new GatewayUnauthorizedException("无权读取该公告。");
        }
        announcementReadStateRepository.findByAnnouncement_IdAndUser_Id(announcementId, user.getId())
                .orElseGet(() -> {
                    AnnouncementReadStateEntity state = new AnnouncementReadStateEntity();
                    state.setAnnouncement(announcement);
                    state.setUser(user);
                    state.setReadAt(Instant.now());
                    return announcementReadStateRepository.save(state);
                });
        return toAnnouncementResponse(announcement, true);
    }

    public PortalRedeemResponse redeem(WebSession session, String rawCode) {
        GatewayUserEntity user = requireCurrentUser(session);
        String code = normalizeRedeemCode(rawCode);
        RedeemCodeEntity redeemCode = redeemCodeRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new IllegalArgumentException("兑换码不存在。"));
        validateRedeemCode(redeemCode, user);

        RedeemCodeUsageEntity usage = new RedeemCodeUsageEntity();
        usage.setRedeemCode(redeemCode);
        usage.setCampaign(redeemCode.getCampaign());
        usage.setUser(user);
        usage.setUsedAt(Instant.now());
        redeemCodeUsageRepository.save(usage);

        redeemCode.setUsedCount(redeemCode.getUsedCount() + 1);
        redeemCodeRepository.save(redeemCode);

        long delta = redeemCode.getCampaign().getRewardTokenCredits();
        long balanceAfter = currentBalance(user.getId()) + delta;
        GatewayUserBalanceLedgerEntity ledger = new GatewayUserBalanceLedgerEntity();
        ledger.setUser(user);
        ledger.setDeltaTokenCredits(delta);
        ledger.setBalanceAfterTokenCredits(balanceAfter);
        ledger.setReason("REDEEM_CODE");
        ledger.setReferenceType("REDEEM_CODE");
        ledger.setReferenceId(redeemCode.getCode());
        balanceLedgerRepository.save(ledger);

        return new PortalRedeemResponse(
                true,
                "兑换成功，Token 额度已入账。",
                redeemCode.getCampaign().getCampaignName(),
                delta,
                balanceAfter,
                usage.getUsedAt()
        );
    }

    public List<PortalBalanceLedgerResponse> listBalanceLedger(WebSession session) {
        GatewayUserEntity user = requireCurrentUser(session);
        return balanceLedgerRepository.findAllByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toBalanceLedgerResponse)
                .toList();
    }

    private PortalSessionResponse authenticateSession(GatewayUserEntity user, WebSession session) {
        Instant authenticatedAt = Instant.now();
        session.setMaxIdleTime(PORTAL_SESSION_TTL);
        session.getAttributes().put(PORTAL_USER_ID_SESSION_KEY, user.getId());
        session.getAttributes().put(PORTAL_AUTHENTICATED_AT_SESSION_KEY, authenticatedAt.toString());
        return toSessionResponse(user, session);
    }

    private GatewayUserEntity requireCurrentUser(WebSession session) {
        Long userId = readUserId(session);
        if (userId == null) {
            throw new GatewayUnauthorizedException("请先登录用户门户。");
        }
        return gatewayUserRepository.findById(userId)
                .filter(GatewayUserEntity::isActive)
                .orElseThrow(() -> new GatewayUnauthorizedException("门户会话已失效，请重新登录。"));
    }

    private Long readUserId(WebSession session) {
        Object raw = session.getAttributes().get(PORTAL_USER_ID_SESSION_KEY);
        if (raw instanceof Long value) {
            return value;
        }
        if (raw instanceof Number value) {
            return value.longValue();
        }
        if (raw instanceof String value && !value.isBlank()) {
            return Long.parseLong(value);
        }
        return null;
    }

    private PortalSessionResponse toSessionResponse(GatewayUserEntity user, WebSession session) {
        Instant authenticatedAt = readAuthenticatedAt(session);
        Instant expiresAt = authenticatedAt == null ? null : authenticatedAt.plus(PORTAL_SESSION_TTL);
        return new PortalSessionResponse(
                true,
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                authenticatedAt,
                expiresAt
        );
    }

    private Instant readAuthenticatedAt(WebSession session) {
        Object raw = session.getAttributes().get(PORTAL_AUTHENTICATED_AT_SESSION_KEY);
        if (raw instanceof String value && !value.isBlank()) {
            return Instant.parse(value);
        }
        return null;
    }

    private PortalSubscriptionResponse toSubscriptionResponse(UserSubscriptionEntity entity) {
        return new PortalSubscriptionResponse(
                entity.getId(),
                entity.getPlan().getId(),
                entity.getPlan().getPlanName(),
                entity.getStatus(),
                entity.getStartsAt(),
                entity.getExpiresAt(),
                entity.isAutoRenew(),
                entity.getPlan().getRpmLimit(),
                entity.getPlan().getTpmLimit(),
                entity.getPlan().getConcurrencyLimit(),
                entity.getPlan().getDailyTokenLimit(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private PortalKeyResponse toKeyResponse(DistributedKeyEntity entity) {
        return new PortalKeyResponse(
                entity.getId(),
                entity.getKeyName(),
                entity.getMaskedKey(),
                entity.isActive(),
                entity.getAllowedProtocols(),
                entity.getAllowedModels(),
                entity.getExpiresAt(),
                entity.getRpmLimit(),
                entity.getTpmLimit(),
                entity.getConcurrencyLimit(),
                entity.getLastUsedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private boolean isAnnouncementVisible(AnnouncementEntity entity, Long userId, Set<Long> activePlanIds, Instant now) {
        if (!"PUBLISHED".equals(entity.getStatus())) {
            return false;
        }
        if (entity.getPublishedAt() != null && entity.getPublishedAt().isAfter(now)) {
            return false;
        }
        if (entity.getExpiresAt() != null && !entity.getExpiresAt().isAfter(now)) {
            return false;
        }
        return switch (entity.getAudienceType()) {
            case "GLOBAL" -> true;
            case "USER" -> entity.getAudienceUser() != null && userId.equals(entity.getAudienceUser().getId());
            case "PLAN" -> entity.getAudiencePlan() != null && activePlanIds.contains(entity.getAudiencePlan().getId());
            default -> false;
        };
    }

    private Set<Long> activePlanIds(Long userId) {
        Instant now = Instant.now();
        return userSubscriptionRepository.findAllByUser_IdOrderByCreatedAtDesc(userId).stream()
                .filter(entity -> "ACTIVE".equals(entity.getStatus()))
                .filter(entity -> entity.getStartsAt() == null || !entity.getStartsAt().isAfter(now))
                .filter(entity -> entity.getExpiresAt() == null || entity.getExpiresAt().isAfter(now))
                .map(entity -> entity.getPlan().getId())
                .collect(Collectors.toSet());
    }

    private PortalAnnouncementResponse toAnnouncementResponse(AnnouncementEntity entity, boolean read) {
        return new PortalAnnouncementResponse(
                String.valueOf(entity.getId()),
                entity.getTitle(),
                entity.getSummary(),
                entity.getBody(),
                read,
                entity.getPublishedAt()
        );
    }

    private void validateRedeemCode(RedeemCodeEntity redeemCode, GatewayUserEntity user) {
        Instant now = Instant.now();
        PromoCampaignEntity campaign = redeemCode.getCampaign();
        if (!redeemCode.isActive()) {
            throw new IllegalArgumentException("兑换码已停用。");
        }
        if (redeemCode.getExpiresAt() != null && !redeemCode.getExpiresAt().isAfter(now)) {
            throw new IllegalArgumentException("兑换码已过期。");
        }
        if (redeemCode.getUsedCount() >= redeemCode.getMaxUses()) {
            throw new IllegalArgumentException("兑换码可用次数已用尽。");
        }
        if (!campaign.isActive()) {
            throw new IllegalArgumentException("兑换活动已停用。");
        }
        if (campaign.getStartsAt() != null && campaign.getStartsAt().isAfter(now)) {
            throw new IllegalArgumentException("兑换活动尚未开始。");
        }
        if (campaign.getExpiresAt() != null && !campaign.getExpiresAt().isAfter(now)) {
            throw new IllegalArgumentException("兑换活动已结束。");
        }
        if (redeemCodeUsageRepository.existsByRedeemCode_IdAndUser_Id(redeemCode.getId(), user.getId())) {
            throw new IllegalArgumentException("该兑换码已被当前用户使用。");
        }
        long campaignUsageCount = redeemCodeUsageRepository.countByCampaign_IdAndUser_Id(campaign.getId(), user.getId());
        if (campaignUsageCount >= campaign.getMaxRedemptionsPerUser()) {
            throw new IllegalArgumentException("当前用户已达到该活动兑换次数上限。");
        }
    }

    private long currentBalance(Long userId) {
        return balanceLedgerRepository.findTopByUser_IdOrderByCreatedAtDescIdDesc(userId)
                .map(GatewayUserBalanceLedgerEntity::getBalanceAfterTokenCredits)
                .orElse(0L);
    }

    private PortalBalanceLedgerResponse toBalanceLedgerResponse(GatewayUserBalanceLedgerEntity entity) {
        return new PortalBalanceLedgerResponse(
                entity.getId(),
                entity.getDeltaTokenCredits(),
                entity.getBalanceAfterTokenCredits(),
                entity.getReason(),
                entity.getReferenceType(),
                entity.getReferenceId(),
                entity.getCreatedAt()
        );
    }

    private String normalizeRedeemCode(String rawCode) {
        String value = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        if (value.isBlank()) {
            throw new IllegalArgumentException("兑换码不能为空。");
        }
        return value;
    }

    private String normalizeEmail(String email) {
        String value = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            throw new IllegalArgumentException("邮箱不能为空。");
        }
        return value;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
