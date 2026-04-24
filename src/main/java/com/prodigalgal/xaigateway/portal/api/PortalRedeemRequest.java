package com.prodigalgal.xaigateway.portal.api;

import jakarta.validation.constraints.NotBlank;

public record PortalRedeemRequest(
        @NotBlank(message = "兑换码不能为空。")
        String code
) {
}
