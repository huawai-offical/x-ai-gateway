package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.PromoCampaignRequest;
import com.prodigalgal.xaigateway.admin.api.PromoCampaignResponse;
import com.prodigalgal.xaigateway.admin.api.RedeemCodeBatchRequest;
import com.prodigalgal.xaigateway.admin.api.RedeemCodeResponse;
import com.prodigalgal.xaigateway.infra.persistence.entity.PromoCampaignEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RedeemCodeEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.PromoCampaignRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RedeemCodeRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PromoCodeAdminService {

    private final PromoCampaignRepository promoCampaignRepository;
    private final RedeemCodeRepository redeemCodeRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public PromoCodeAdminService(
            PromoCampaignRepository promoCampaignRepository,
            RedeemCodeRepository redeemCodeRepository) {
        this.promoCampaignRepository = promoCampaignRepository;
        this.redeemCodeRepository = redeemCodeRepository;
    }

    @Transactional(readOnly = true)
    public List<PromoCampaignResponse> listCampaigns() {
        return promoCampaignRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toCampaignResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PromoCampaignResponse getCampaign(Long id) {
        return toCampaignResponse(getCampaignRequired(id));
    }

    public PromoCampaignResponse createCampaign(PromoCampaignRequest request) {
        String name = requireText(request.campaignName(), "活动名称不能为空。");
        if (promoCampaignRepository.existsByCampaignNameIgnoreCase(name)) {
            throw new IllegalArgumentException("活动名称已存在。");
        }
        PromoCampaignEntity entity = new PromoCampaignEntity();
        applyCampaign(entity, request, true);
        return toCampaignResponse(promoCampaignRepository.save(entity));
    }

    public PromoCampaignResponse updateCampaign(Long id, PromoCampaignRequest request) {
        PromoCampaignEntity entity = getCampaignRequired(id);
        String name = requireText(request.campaignName(), "活动名称不能为空。");
        if (promoCampaignRepository.existsByCampaignNameIgnoreCaseAndIdNot(name, id)) {
            throw new IllegalArgumentException("活动名称已存在。");
        }
        applyCampaign(entity, request, false);
        return toCampaignResponse(promoCampaignRepository.save(entity));
    }

    public void deleteCampaign(Long id) {
        promoCampaignRepository.delete(getCampaignRequired(id));
    }

    @Transactional(readOnly = true)
    public List<RedeemCodeResponse> listCodes(Long campaignId) {
        getCampaignRequired(campaignId);
        return redeemCodeRepository.findAllByCampaign_IdOrderByCreatedAtDesc(campaignId).stream()
                .map(this::toCodeResponse)
                .toList();
    }

    public List<RedeemCodeResponse> createCodes(Long campaignId, RedeemCodeBatchRequest request) {
        PromoCampaignEntity campaign = getCampaignRequired(campaignId);
        List<String> codes = normalizeCodes(request);
        int maxUses = request.maxUses() == null ? 1 : Math.max(1, request.maxUses());
        boolean active = request.active() == null || request.active();
        List<RedeemCodeResponse> created = new ArrayList<>();
        for (String code : codes) {
            if (redeemCodeRepository.existsByCodeIgnoreCase(code)) {
                throw new IllegalArgumentException("兑换码已存在：" + code);
            }
            RedeemCodeEntity entity = new RedeemCodeEntity();
            entity.setCampaign(campaign);
            entity.setCode(code);
            entity.setActive(active);
            entity.setMaxUses(maxUses);
            entity.setExpiresAt(request.expiresAt());
            created.add(toCodeResponse(redeemCodeRepository.save(entity)));
        }
        return created;
    }

    private PromoCampaignEntity getCampaignRequired(Long id) {
        return promoCampaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定兑换活动。"));
    }

    private void applyCampaign(PromoCampaignEntity entity, PromoCampaignRequest request, boolean isCreate) {
        entity.setCampaignName(requireText(request.campaignName(), "活动名称不能为空。"));
        entity.setDescription(blankToNull(request.description()));
        entity.setActive(request.active() == null ? isCreate || entity.isActive() : request.active());
        entity.setRewardTokenCredits(Math.max(0L, request.rewardTokenCredits() == null ? entity.getRewardTokenCredits() : request.rewardTokenCredits()));
        entity.setMaxRedemptionsPerUser(Math.max(1, request.maxRedemptionsPerUser() == null ? entity.getMaxRedemptionsPerUser() : request.maxRedemptionsPerUser()));
        if (request.startsAt() != null && request.expiresAt() != null && request.expiresAt().isBefore(request.startsAt())) {
            throw new IllegalArgumentException("活动结束时间不能早于开始时间。");
        }
        entity.setStartsAt(request.startsAt());
        entity.setExpiresAt(request.expiresAt());
    }

    private List<String> normalizeCodes(RedeemCodeBatchRequest request) {
        List<String> codes = new ArrayList<>();
        if (request.codes() != null) {
            for (String raw : request.codes()) {
                String code = normalizeCode(raw);
                if (code != null && !codes.contains(code)) {
                    codes.add(code);
                }
            }
        }
        int generateCount = request.generateCount() == null ? 0 : Math.max(0, request.generateCount());
        String prefix = blankToNull(request.prefix());
        for (int index = 0; index < generateCount; index += 1) {
            String generated;
            do {
                generated = (prefix == null ? "XAG" : prefix.trim().toUpperCase(Locale.ROOT)) + "-" + randomCode();
            } while (codes.contains(generated) || redeemCodeRepository.existsByCodeIgnoreCase(generated));
            codes.add(generated);
        }
        if (codes.isEmpty()) {
            throw new IllegalArgumentException("请粘贴兑换码或指定生成数量。");
        }
        return codes;
    }

    private String randomCode() {
        byte[] bytes = new byte[6];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT);
    }

    private String normalizeCode(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String errorMessage) {
        String text = blankToNull(value);
        if (text == null) {
            throw new IllegalArgumentException(errorMessage);
        }
        return text;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private PromoCampaignResponse toCampaignResponse(PromoCampaignEntity entity) {
        return new PromoCampaignResponse(
                entity.getId(),
                entity.getCampaignName(),
                entity.getDescription(),
                entity.isActive(),
                entity.getRewardTokenCredits(),
                entity.getMaxRedemptionsPerUser(),
                entity.getStartsAt(),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private RedeemCodeResponse toCodeResponse(RedeemCodeEntity entity) {
        return new RedeemCodeResponse(
                entity.getId(),
                entity.getCampaign().getId(),
                entity.getCampaign().getCampaignName(),
                entity.getCode(),
                entity.isActive(),
                entity.getMaxUses(),
                entity.getUsedCount(),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
