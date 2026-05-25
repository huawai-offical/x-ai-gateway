package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.InvitationCodeBatchRequest;
import com.prodigalgal.xaigateway.admin.api.InvitationCodeUpdateRequest;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.InvitationCodeEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.InvitationCodeUsageEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.InvitationCodeRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.InvitationCodeUsageRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvitationCodeAdminServiceTests {

    @Test
    void shouldImportRawTextCodesAndApplyDefaults() {
        InvitationCodeRepository codeRepository = Mockito.mock(InvitationCodeRepository.class);
        InvitationCodeUsageRepository usageRepository = Mockito.mock(InvitationCodeUsageRepository.class);
        InvitationCodeAdminService service = new InvitationCodeAdminService(codeRepository, usageRepository);
        Mockito.when(codeRepository.existsByCodeIgnoreCase(Mockito.anyString())).thenReturn(false);
        Mockito.when(codeRepository.save(Mockito.any())).thenAnswer(invocation -> {
            InvitationCodeEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 20L);
            return entity;
        });

        var response = service.createCodes(new InvitationCodeBatchRequest(
                List.of(" alpha "),
                "beta\nalpha",
                0,
                null,
                2,
                false,
                Instant.parse("2026-05-01T00:00:00Z"),
                null,
                0L,
                0L,
                null,
                null,
                null,
                null,
                "内测邀请"
        ));

        assertEquals(2, response.size());
        assertEquals("ALPHA", response.getFirst().code());
        assertEquals(2, response.getFirst().maxUses());
        assertEquals("内测邀请", response.getFirst().notes());
        Mockito.verify(codeRepository, Mockito.times(2)).save(Mockito.any());
    }

    @Test
    void shouldRejectDeletingUsedInvitationCode() {
        InvitationCodeRepository codeRepository = Mockito.mock(InvitationCodeRepository.class);
        InvitationCodeUsageRepository usageRepository = Mockito.mock(InvitationCodeUsageRepository.class);
        InvitationCodeAdminService service = new InvitationCodeAdminService(codeRepository, usageRepository);
        InvitationCodeEntity code = code(9L);
        code.setUsedCount(1);
        Mockito.when(codeRepository.findById(9L)).thenReturn(Optional.of(code));

        assertThrows(IllegalArgumentException.class, () -> service.deleteCode(9L));
        Mockito.verify(codeRepository, Mockito.never()).delete(Mockito.any());
    }

    @Test
    void shouldRejectMaxUsesLowerThanUsedCount() {
        InvitationCodeRepository codeRepository = Mockito.mock(InvitationCodeRepository.class);
        InvitationCodeUsageRepository usageRepository = Mockito.mock(InvitationCodeUsageRepository.class);
        InvitationCodeAdminService service = new InvitationCodeAdminService(codeRepository, usageRepository);
        InvitationCodeEntity code = code(9L);
        code.setUsedCount(2);
        Mockito.when(codeRepository.findById(9L)).thenReturn(Optional.of(code));

        assertThrows(IllegalArgumentException.class, () -> service.updateCode(
                9L,
                new InvitationCodeUpdateRequest(true, 1, null, null, null, null, null, null, null, null, null)
        ));
    }

    @Test
    void shouldApplyOwnerAndRewardWhenCreatingInvitationCode() {
        InvitationCodeRepository codeRepository = Mockito.mock(InvitationCodeRepository.class);
        InvitationCodeUsageRepository usageRepository = Mockito.mock(InvitationCodeUsageRepository.class);
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        InvitationCodeAdminService service = new InvitationCodeAdminService(codeRepository, usageRepository, userRepository, null);
        GatewayUserEntity owner = new GatewayUserEntity();
        ReflectionTestUtils.setField(owner, "id", 42L);
        owner.setEmail("owner@example.com");
        owner.setDisplayName("Owner");
        Mockito.when(userRepository.findById(42L)).thenReturn(Optional.of(owner));
        Mockito.when(codeRepository.existsByCodeIgnoreCase(Mockito.anyString())).thenReturn(false);
        Mockito.when(codeRepository.save(Mockito.any())).thenAnswer(invocation -> {
            InvitationCodeEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 20L);
            return entity;
        });

        var response = service.createCodes(new InvitationCodeBatchRequest(
                List.of("owner-reward"),
                null,
                0,
                null,
                1,
                true,
                null,
                42L,
                3000L,
                0L,
                null,
                null,
                null,
                null,
                null
        ));

        assertEquals(42L, response.getFirst().ownerUserId());
        assertEquals("owner@example.com", response.getFirst().ownerEmail());
        assertEquals(3000L, response.getFirst().rewardTokenCredits());
    }

    @Test
    void shouldRejectNegativeInvitationReward() {
        InvitationCodeRepository codeRepository = Mockito.mock(InvitationCodeRepository.class);
        InvitationCodeUsageRepository usageRepository = Mockito.mock(InvitationCodeUsageRepository.class);
        InvitationCodeAdminService service = new InvitationCodeAdminService(codeRepository, usageRepository);

        assertThrows(IllegalArgumentException.class, () -> service.createCodes(new InvitationCodeBatchRequest(
                List.of("bad-reward"),
                null,
                0,
                null,
                1,
                true,
                null,
                null,
                -1L,
                0L,
                null,
                null,
                null,
                null,
                null
        )));
    }

    @Test
    void shouldListInvitationCodeUsages() {
        InvitationCodeRepository codeRepository = Mockito.mock(InvitationCodeRepository.class);
        InvitationCodeUsageRepository usageRepository = Mockito.mock(InvitationCodeUsageRepository.class);
        InvitationCodeAdminService service = new InvitationCodeAdminService(codeRepository, usageRepository);
        InvitationCodeEntity code = code(9L);
        GatewayUserEntity user = new GatewayUserEntity();
        ReflectionTestUtils.setField(user, "id", 42L);
        InvitationCodeUsageEntity usage = new InvitationCodeUsageEntity();
        ReflectionTestUtils.setField(usage, "id", 81L);
        usage.setInvitationCode(code);
        usage.setUser(user);
        usage.setRegistrationEmail("alpha@example.com");
        usage.setRegistrationChannel("INVITE_CODE");
        usage.setRequestSource("PORTAL_REGISTER");
        usage.setRewardTokenCredits(1200L);
        usage.setUsedAt(Instant.parse("2026-05-24T00:00:00Z"));
        Mockito.when(codeRepository.findById(9L)).thenReturn(Optional.of(code));
        Mockito.when(usageRepository.findAllByInvitationCode_IdOrderByUsedAtDesc(9L)).thenReturn(List.of(usage));

        var usages = service.listUsages(9L);

        assertEquals(1, usages.size());
        assertEquals("INVITE-1", usages.getFirst().code());
        assertEquals("alpha@example.com", usages.getFirst().registrationEmail());
        assertEquals(1200L, usages.getFirst().rewardTokenCredits());
    }

    private InvitationCodeEntity code(Long id) {
        InvitationCodeEntity code = new InvitationCodeEntity();
        ReflectionTestUtils.setField(code, "id", id);
        code.setCode("INVITE-1");
        code.setActive(true);
        code.setMaxUses(3);
        return code;
    }
}
