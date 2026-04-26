package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.RedeemCodeBatchRequest;
import com.prodigalgal.xaigateway.admin.api.RedeemCodeUpdateRequest;
import com.prodigalgal.xaigateway.infra.persistence.entity.PromoCampaignEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RedeemCodeEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.PromoCampaignRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RedeemCodeRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PromoCodeAdminServiceTests {

    @Test
    void shouldImportRawTextCodesAndApplyDefaults() {
        PromoCampaignRepository campaignRepository = Mockito.mock(PromoCampaignRepository.class);
        RedeemCodeRepository codeRepository = Mockito.mock(RedeemCodeRepository.class);
        PromoCodeAdminService service = new PromoCodeAdminService(campaignRepository, codeRepository);
        PromoCampaignEntity campaign = campaign(3L);
        Mockito.when(campaignRepository.findById(3L)).thenReturn(Optional.of(campaign));
        Mockito.when(codeRepository.existsByCodeIgnoreCase(Mockito.anyString())).thenReturn(false);
        Mockito.when(codeRepository.save(Mockito.any())).thenAnswer(invocation -> {
            RedeemCodeEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 20L);
            return entity;
        });

        var response = service.createCodes(3L, new RedeemCodeBatchRequest(
                List.of(" alpha "),
                "beta\nalpha",
                0,
                null,
                2,
                false,
                Instant.parse("2026-05-01T00:00:00Z")
        ));

        assertEquals(2, response.size());
        assertEquals("ALPHA", response.getFirst().code());
        assertEquals(2, response.getFirst().maxUses());
        Mockito.verify(codeRepository, Mockito.times(2)).save(Mockito.any());
    }

    @Test
    void shouldRejectDeletingUsedRedeemCode() {
        PromoCampaignRepository campaignRepository = Mockito.mock(PromoCampaignRepository.class);
        RedeemCodeRepository codeRepository = Mockito.mock(RedeemCodeRepository.class);
        PromoCodeAdminService service = new PromoCodeAdminService(campaignRepository, codeRepository);
        PromoCampaignEntity campaign = campaign(3L);
        RedeemCodeEntity code = code(9L, campaign);
        code.setUsedCount(1);
        Mockito.when(campaignRepository.findById(3L)).thenReturn(Optional.of(campaign));
        Mockito.when(codeRepository.findByIdAndCampaign_Id(9L, 3L)).thenReturn(Optional.of(code));

        assertThrows(IllegalArgumentException.class, () -> service.deleteCode(3L, 9L));
        Mockito.verify(codeRepository, Mockito.never()).delete(Mockito.any());
    }

    @Test
    void shouldRejectMaxUsesLowerThanUsedCount() {
        PromoCampaignRepository campaignRepository = Mockito.mock(PromoCampaignRepository.class);
        RedeemCodeRepository codeRepository = Mockito.mock(RedeemCodeRepository.class);
        PromoCodeAdminService service = new PromoCodeAdminService(campaignRepository, codeRepository);
        PromoCampaignEntity campaign = campaign(3L);
        RedeemCodeEntity code = code(9L, campaign);
        code.setUsedCount(2);
        Mockito.when(campaignRepository.findById(3L)).thenReturn(Optional.of(campaign));
        Mockito.when(codeRepository.findByIdAndCampaign_Id(9L, 3L)).thenReturn(Optional.of(code));

        assertThrows(IllegalArgumentException.class, () -> service.updateCode(
                3L,
                9L,
                new RedeemCodeUpdateRequest(true, 1, null)
        ));
    }

    private PromoCampaignEntity campaign(Long id) {
        PromoCampaignEntity campaign = new PromoCampaignEntity();
        ReflectionTestUtils.setField(campaign, "id", id);
        campaign.setCampaignName("Welcome");
        campaign.setActive(true);
        return campaign;
    }

    private RedeemCodeEntity code(Long id, PromoCampaignEntity campaign) {
        RedeemCodeEntity code = new RedeemCodeEntity();
        ReflectionTestUtils.setField(code, "id", id);
        code.setCampaign(campaign);
        code.setCode("WELCOME");
        code.setActive(true);
        code.setMaxUses(3);
        return code;
    }
}
