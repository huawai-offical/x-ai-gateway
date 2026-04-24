package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.PromoCodeAdminService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/promo-codes")
public class PromoCodeAdminController {

    private final PromoCodeAdminService promoCodeAdminService;

    public PromoCodeAdminController(PromoCodeAdminService promoCodeAdminService) {
        this.promoCodeAdminService = promoCodeAdminService;
    }

    @GetMapping
    public List<PromoCampaignResponse> listCampaigns() {
        return promoCodeAdminService.listCampaigns();
    }

    @GetMapping("/{id}")
    public PromoCampaignResponse getCampaign(@PathVariable Long id) {
        return promoCodeAdminService.getCampaign(id);
    }

    @PostMapping
    public PromoCampaignResponse createCampaign(@Valid @RequestBody PromoCampaignRequest request) {
        return promoCodeAdminService.createCampaign(request);
    }

    @PutMapping("/{id}")
    public PromoCampaignResponse updateCampaign(
            @PathVariable Long id,
            @Valid @RequestBody PromoCampaignRequest request) {
        return promoCodeAdminService.updateCampaign(id, request);
    }

    @DeleteMapping("/{id}")
    public void deleteCampaign(@PathVariable Long id) {
        promoCodeAdminService.deleteCampaign(id);
    }

    @GetMapping("/{id}/codes")
    public List<RedeemCodeResponse> listCodes(@PathVariable Long id) {
        return promoCodeAdminService.listCodes(id);
    }

    @PostMapping("/{id}/codes")
    public List<RedeemCodeResponse> createCodes(
            @PathVariable Long id,
            @RequestBody RedeemCodeBatchRequest request) {
        return promoCodeAdminService.createCodes(id, request);
    }
}
