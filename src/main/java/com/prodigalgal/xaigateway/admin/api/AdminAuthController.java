package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.AdminAuthService;
import com.prodigalgal.xaigateway.admin.application.AdminConsoleCredentialService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Validated
@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final AdminConsoleCredentialService adminConsoleCredentialService;

    public AdminAuthController(
            AdminAuthService adminAuthService,
            AdminConsoleCredentialService adminConsoleCredentialService) {
        this.adminAuthService = adminAuthService;
        this.adminConsoleCredentialService = adminConsoleCredentialService;
    }

    @GetMapping("/session")
    public Mono<AdminSessionResponse> session(ServerWebExchange exchange) {
        return adminAuthService.currentSession(exchange);
    }

    @PostMapping("/challenge")
    public Mono<AdminAuthChallengeResponse> challenge(ServerWebExchange exchange) {
        return adminAuthService.issueChallenge(exchange);
    }

    @PostMapping("/login")
    public Mono<AdminSessionResponse> login(
            @Valid @RequestBody AdminLoginRequest request,
            ServerWebExchange exchange) {
        return adminAuthService.login(request, exchange);
    }

    @PostMapping("/logout")
    public Mono<Void> logout(ServerWebExchange exchange) {
        return adminAuthService.logout(exchange);
    }

    @GetMapping("/settings")
    public Mono<AdminAuthSettingsResponse> settings() {
        return Mono.fromCallable(adminConsoleCredentialService::getSettings);
    }

    @PutMapping("/settings")
    public Mono<AdminAuthSettingsResponse> updateSettings(
            @Valid @RequestBody AdminAuthSettingsUpdateRequest request) {
        return Mono.fromCallable(() -> adminConsoleCredentialService.updateSettings(request));
    }
}
