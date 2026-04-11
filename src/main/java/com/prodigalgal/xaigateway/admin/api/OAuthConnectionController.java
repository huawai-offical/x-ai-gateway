package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.OAuthConnectionService;
import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/oauth")
public class OAuthConnectionController {

    private final OAuthConnectionService oauthConnectionService;

    public OAuthConnectionController(OAuthConnectionService oauthConnectionService) {
        this.oauthConnectionService = oauthConnectionService;
    }

    @PostMapping("/{provider}/start")
    public OauthStartResponse start(@PathVariable String provider, @RequestBody(required = false) OauthStartRequest request) {
        OauthStartRequest safeRequest = request == null ? new OauthStartRequest(null, null) : request;
        return oauthConnectionService.start(UpstreamAccountProviderType.valueOf(provider.toUpperCase()), safeRequest.poolId(), safeRequest.redirectPath());
    }

    @GetMapping("/{provider}/callback")
    public ResponseEntity<Void> callback(
            @PathVariable String provider,
            @RequestParam String code,
            @RequestParam String state) {
        String redirect = oauthConnectionService.complete(UpstreamAccountProviderType.valueOf(provider.toUpperCase()), code, state);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, redirect).build();
    }
}
