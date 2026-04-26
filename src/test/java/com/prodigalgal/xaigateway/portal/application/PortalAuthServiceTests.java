package com.prodigalgal.xaigateway.portal.application;

import com.prodigalgal.xaigateway.gateway.core.auth.GatewayUnauthorizedException;
import com.prodigalgal.xaigateway.gateway.core.auth.AccessGroupEntitlementService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeySecretService;
import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.AccessGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.AnnouncementEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyAccountPoolBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserBalanceLedgerEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.PromoCampaignEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RedeemCodeEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SubscriptionPlanEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountPoolEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UserSubscriptionEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.AnnouncementReadStateRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.AnnouncementRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccountPoolBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserBalanceLedgerRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RedeemCodeRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RedeemCodeUsageRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountPoolRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UserSubscriptionRepository;
import com.prodigalgal.xaigateway.portal.api.PortalKeyCreateRequest;
import com.prodigalgal.xaigateway.portal.api.PortalLoginRequest;
import com.prodigalgal.xaigateway.portal.api.PortalRegisterRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalAuthServiceTests {

    private final GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
    private final UserSubscriptionRepository subscriptionRepository = Mockito.mock(UserSubscriptionRepository.class);
    private final DistributedKeyRepository keyRepository = Mockito.mock(DistributedKeyRepository.class);
    private final AnnouncementRepository announcementRepository = Mockito.mock(AnnouncementRepository.class);
    private final AnnouncementReadStateRepository announcementReadStateRepository = Mockito.mock(AnnouncementReadStateRepository.class);
    private final RedeemCodeRepository redeemCodeRepository = Mockito.mock(RedeemCodeRepository.class);
    private final RedeemCodeUsageRepository redeemCodeUsageRepository = Mockito.mock(RedeemCodeUsageRepository.class);
    private final GatewayUserBalanceLedgerRepository balanceLedgerRepository = Mockito.mock(GatewayUserBalanceLedgerRepository.class);
    private final UpstreamAccountPoolRepository accountPoolRepository = Mockito.mock(UpstreamAccountPoolRepository.class);
    private final DistributedKeyAccountPoolBindingRepository keyPoolBindingRepository = Mockito.mock(DistributedKeyAccountPoolBindingRepository.class);
    private final DistributedKeySecretService distributedKeySecretService = new DistributedKeySecretService();
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final AccessGroupEntitlementService accessGroupEntitlementService = Mockito.mock(AccessGroupEntitlementService.class);
    private final PortalAuthService service = new PortalAuthService(
            userRepository,
            subscriptionRepository,
            keyRepository,
            announcementRepository,
            announcementReadStateRepository,
            redeemCodeRepository,
            redeemCodeUsageRepository,
            balanceLedgerRepository,
            accountPoolRepository,
            keyPoolBindingRepository,
            distributedKeySecretService,
            passwordEncoder,
            accessGroupEntitlementService
    );

    @Test
    void shouldRegisterUserAndCreatePortalSession() {
        Mockito.when(userRepository.existsByEmailIgnoreCase("alpha@example.com")).thenReturn(false);
        Mockito.when(userRepository.save(Mockito.any())).thenAnswer(invocation -> {
            GatewayUserEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 7L);
            return entity;
        });
        MockServerWebExchange exchange = exchange();

        var session = service.register(new PortalRegisterRequest(
                " Alpha@Example.com ",
                "Alpha",
                "password-123"
        ), exchange).block();

        assertTrue(session.authenticated());
        assertEquals(7L, session.userId());
        assertEquals("alpha@example.com", session.email());
        Mockito.verify(userRepository).save(Mockito.argThat(user ->
                "alpha@example.com".equals(user.getEmail())
                        && passwordEncoder.matches("password-123", user.getPasswordHash())));
    }

    @Test
    void shouldLoginAndListCurrentUserSubscriptionsAndKeys() {
        GatewayUserEntity user = user(8L, "beta@example.com", "password-123");
        Mockito.when(userRepository.findByEmailIgnoreCase("beta@example.com")).thenReturn(Optional.of(user));
        Mockito.when(userRepository.findById(8L)).thenReturn(Optional.of(user));
        Mockito.when(userRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(subscriptionRepository.findAllByUser_IdOrderByCreatedAtDesc(8L))
                .thenReturn(List.of(subscription(31L)));
        Mockito.when(keyRepository.findAllByOwnerUser_IdOrderByCreatedAtDesc(8L))
                .thenReturn(List.of(distributedKey(41L)));
        MockServerWebExchange exchange = exchange();

        var session = service.login(new PortalLoginRequest("beta@example.com", "password-123"), exchange).block();
        var subscriptions = service.listSubscriptions(exchange.getSession().block());
        var keys = service.listKeys(exchange.getSession().block());

        assertTrue(session.authenticated());
        assertEquals("Pro", subscriptions.getFirst().planName());
        assertEquals("Portal Key", keys.getFirst().keyName());
    }

    @Test
    void shouldFilterAnnouncementsAndMarkRead() {
        GatewayUserEntity user = user(8L, "beta@example.com", "password-123");
        AnnouncementEntity global = announcement(51L, "GLOBAL", null);
        AnnouncementEntity planOnly = announcement(52L, "PLAN", plan(3L));
        AnnouncementEntity accessGroupOnly = announcement(53L, "ACCESS_GROUP", null);
        accessGroupOnly.setAudienceAccessGroup(accessGroup(6L));
        Mockito.when(userRepository.findByEmailIgnoreCase("beta@example.com")).thenReturn(Optional.of(user));
        Mockito.when(userRepository.findById(8L)).thenReturn(Optional.of(user));
        Mockito.when(userRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(subscriptionRepository.findAllByUser_IdOrderByCreatedAtDesc(8L))
                .thenReturn(List.of(subscription(31L)));
        Mockito.when(announcementRepository.findAllByStatusOrderByPublishedAtDescCreatedAtDesc("PUBLISHED"))
                .thenReturn(List.of(global, planOnly, accessGroupOnly));
        Mockito.when(announcementRepository.findById(51L)).thenReturn(Optional.of(global));
        Mockito.when(accessGroupEntitlementService.activeAccessGroupIdsForUser(8L)).thenReturn(Set.of(6L));
        Mockito.when(announcementReadStateRepository.findAllByUser_Id(8L)).thenReturn(List.of());
        Mockito.when(announcementReadStateRepository.findByAnnouncement_IdAndUser_Id(51L, 8L)).thenReturn(Optional.empty());
        Mockito.when(announcementReadStateRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        MockServerWebExchange exchange = exchange();
        service.login(new PortalLoginRequest("beta@example.com", "password-123"), exchange).block();

        var announcements = service.listAnnouncements(exchange.getSession().block());
        var read = service.markAnnouncementRead(exchange.getSession().block(), 51L);

        assertEquals(3, announcements.size());
        assertEquals("公告 51", announcements.getFirst().title());
        assertTrue(read.read());
    }

    @Test
    void shouldRedeemCodeOnceAndWriteBalanceLedger() {
        GatewayUserEntity user = user(8L, "beta@example.com", "password-123");
        RedeemCodeEntity code = redeemCode(61L, "WELCOME-1", 5000L);
        Mockito.when(userRepository.findByEmailIgnoreCase("beta@example.com")).thenReturn(Optional.of(user));
        Mockito.when(userRepository.findById(8L)).thenReturn(Optional.of(user));
        Mockito.when(userRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(redeemCodeRepository.findByCodeIgnoreCase("WELCOME-1")).thenReturn(Optional.of(code));
        Mockito.when(redeemCodeUsageRepository.existsByRedeemCode_IdAndUser_Id(61L, 8L)).thenReturn(false);
        Mockito.when(redeemCodeUsageRepository.countByCampaign_IdAndUser_Id(6L, 8L)).thenReturn(0L);
        Mockito.when(balanceLedgerRepository.findTopByUser_IdOrderByCreatedAtDescIdDesc(8L)).thenReturn(Optional.empty());
        Mockito.when(balanceLedgerRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(redeemCodeUsageRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(redeemCodeRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        MockServerWebExchange exchange = exchange();
        service.login(new PortalLoginRequest("beta@example.com", "password-123"), exchange).block();

        var response = service.redeem(exchange.getSession().block(), " welcome-1 ");

        assertTrue(response.success());
        assertEquals(5000L, response.deltaTokenCredits());
        assertEquals(5000L, response.balanceAfterTokenCredits());
        assertEquals(1, code.getUsedCount());
        Mockito.verify(balanceLedgerRepository).save(Mockito.any(GatewayUserBalanceLedgerEntity.class));
    }

    @Test
    void shouldCreateRotateAndDisableOwnedPortalKey() {
        GatewayUserEntity user = user(8L, "beta@example.com", "password-123");
        UpstreamAccountPoolEntity pool = defaultPool();
        Mockito.when(userRepository.findByEmailIgnoreCase("beta@example.com")).thenReturn(Optional.of(user));
        Mockito.when(userRepository.findById(8L)).thenReturn(Optional.of(user));
        Mockito.when(userRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(accountPoolRepository.findByPoolNameIgnoreCase("default")).thenReturn(Optional.of(pool));
        AtomicReference<DistributedKeyEntity> savedKeyRef = new AtomicReference<>();
        Mockito.when(keyRepository.save(Mockito.any())).thenAnswer(invocation -> {
            DistributedKeyEntity key = invocation.getArgument(0);
            if (key.getId() == null) {
                ReflectionTestUtils.setField(key, "id", 81L);
            }
            savedKeyRef.set(key);
            return key;
        });
        Mockito.when(keyPoolBindingRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        MockServerWebExchange exchange = exchange();
        service.login(new PortalLoginRequest("beta@example.com", "password-123"), exchange).block();

        var created = service.createKey(exchange.getSession().block(), new PortalKeyCreateRequest(
                "Portal Self Key",
                List.of("openai"),
                List.of("gpt-5-mini"),
                null,
                null,
                null
        ));
        DistributedKeyEntity savedKey = savedKeyRef.get();
        savedKey.setOwnerUser(user);
        Mockito.when(keyRepository.findById(81L)).thenReturn(Optional.of(savedKey));
        var rotated = service.rotateKey(exchange.getSession().block(), 81L);
        var disabled = service.disableKey(exchange.getSession().block(), 81L);

        assertTrue(created.fullKey().startsWith("sk-gw-"));
        assertEquals("Portal Self Key", created.key().keyName());
        assertTrue(created.key().active());
        assertTrue(rotated.fullKey().startsWith("sk-gw-"));
        assertTrue(!disabled.active());
        Mockito.verify(keyPoolBindingRepository).save(Mockito.any(DistributedKeyAccountPoolBindingEntity.class));
    }

    @Test
    void shouldRejectPortalKeyOperationForOtherUser() {
        GatewayUserEntity user = user(8L, "beta@example.com", "password-123");
        GatewayUserEntity otherUser = user(9L, "other@example.com", "password-123");
        DistributedKeyEntity otherKey = distributedKey(91L);
        otherKey.setOwnerUser(otherUser);
        Mockito.when(userRepository.findByEmailIgnoreCase("beta@example.com")).thenReturn(Optional.of(user));
        Mockito.when(userRepository.findById(8L)).thenReturn(Optional.of(user));
        Mockito.when(userRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(keyRepository.findById(91L)).thenReturn(Optional.of(otherKey));
        MockServerWebExchange exchange = exchange();
        service.login(new PortalLoginRequest("beta@example.com", "password-123"), exchange).block();

        GatewayUnauthorizedException exception = assertThrows(
                GatewayUnauthorizedException.class,
                () -> service.disableKey(exchange.getSession().block(), 91L)
        );

        assertEquals("无权操作该 Key。", exception.getMessage());
    }

    @Test
    void shouldRejectPortalResourceWithoutSession() {
        GatewayUnauthorizedException exception = assertThrows(
                GatewayUnauthorizedException.class,
                () -> service.listKeys(exchange().getSession().block())
        );

        assertEquals("请先登录用户门户。", exception.getMessage());
    }

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/portal").build());
    }

    private GatewayUserEntity user(Long id, String email, String password) {
        GatewayUserEntity user = new GatewayUserEntity();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail(email);
        user.setDisplayName("Beta");
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode(password));
        return user;
    }

    private UserSubscriptionEntity subscription(Long id) {
        SubscriptionPlanEntity plan = plan(3L);

        UserSubscriptionEntity subscription = new UserSubscriptionEntity();
        ReflectionTestUtils.setField(subscription, "id", id);
        subscription.setPlan(plan);
        subscription.setStatus("ACTIVE");
        subscription.setStartsAt(Instant.parse("2026-04-24T00:00:00Z"));
        subscription.setExpiresAt(Instant.parse("2026-05-24T00:00:00Z"));
        return subscription;
    }

    private SubscriptionPlanEntity plan(Long id) {
        SubscriptionPlanEntity plan = new SubscriptionPlanEntity();
        ReflectionTestUtils.setField(plan, "id", id);
        plan.setPlanName("Pro");
        plan.setRpmLimit(120);
        plan.setTpmLimit(240000);
        plan.setConcurrencyLimit(4);
        plan.setDailyTokenLimit(2_000_000L);
        return plan;
    }

    private DistributedKeyEntity distributedKey(Long id) {
        DistributedKeyEntity key = new DistributedKeyEntity();
        ReflectionTestUtils.setField(key, "id", id);
        key.setKeyName("Portal Key");
        key.setMaskedKey("xag_****_tail");
        key.setAllowedProtocols(List.of("openai"));
        key.setAllowedModels(List.of("gpt-5-mini"));
        key.setActive(true);
        return key;
    }

    private AnnouncementEntity announcement(Long id, String audienceType, SubscriptionPlanEntity plan) {
        AnnouncementEntity announcement = new AnnouncementEntity();
        ReflectionTestUtils.setField(announcement, "id", id);
        announcement.setTitle("公告 " + id);
        announcement.setSummary("摘要");
        announcement.setBody("正文");
        announcement.setStatus("PUBLISHED");
        announcement.setAudienceType(audienceType);
        announcement.setAudiencePlan(plan);
        announcement.setPublishedAt(Instant.parse("2026-04-24T00:00:00Z"));
        return announcement;
    }

    private AccessGroupEntity accessGroup(Long id) {
        AccessGroupEntity accessGroup = new AccessGroupEntity();
        ReflectionTestUtils.setField(accessGroup, "id", id);
        accessGroup.setGroupName("default");
        accessGroup.setActive(true);
        return accessGroup;
    }

    private RedeemCodeEntity redeemCode(Long id, String code, long reward) {
        PromoCampaignEntity campaign = new PromoCampaignEntity();
        ReflectionTestUtils.setField(campaign, "id", 6L);
        campaign.setCampaignName("Welcome");
        campaign.setActive(true);
        campaign.setRewardTokenCredits(reward);
        campaign.setMaxRedemptionsPerUser(1);

        RedeemCodeEntity redeemCode = new RedeemCodeEntity();
        ReflectionTestUtils.setField(redeemCode, "id", id);
        redeemCode.setCampaign(campaign);
        redeemCode.setCode(code);
        redeemCode.setActive(true);
        redeemCode.setMaxUses(1);
        redeemCode.setUsedCount(0);
        return redeemCode;
    }

    private UpstreamAccountPoolEntity defaultPool() {
        UpstreamAccountPoolEntity pool = new UpstreamAccountPoolEntity();
        ReflectionTestUtils.setField(pool, "id", 16L);
        pool.setPoolName("default");
        pool.setProviderType(UpstreamAccountProviderType.OPENAI_OAUTH);
        pool.setSupportedProtocols(List.of("openai", "responses"));
        pool.setSupportedModels(List.of("gpt-5-mini"));
        pool.setAllowedClientFamilies(List.of("GENERIC_OPENAI", "CODEX"));
        pool.setActive(true);
        return pool;
    }
}
