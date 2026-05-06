package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.SecurityPolicyService;
import com.prodigalgal.xaigateway.portal.api.PortalRegistrationPolicyRequest;
import com.prodigalgal.xaigateway.portal.api.PortalRegistrationPolicyResponse;
import com.prodigalgal.xaigateway.portal.application.PortalSecurityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/security")
public class SecurityAdminController {

    private final SecurityPolicyService securityPolicyService;
    private final PortalSecurityService portalSecurityService;

    public SecurityAdminController(
            SecurityPolicyService securityPolicyService,
            PortalSecurityService portalSecurityService) {
        this.securityPolicyService = securityPolicyService;
        this.portalSecurityService = portalSecurityService;
    }

    @PostMapping("/scan")
    public SecurityScanResponse scan(@RequestBody SecurityScanRequest request) {
        SecurityScanResponse urlResponse = securityPolicyService.scanUrl(request == null ? null : request.url());
        if (!urlResponse.allowed()) {
            return urlResponse;
        }
        return securityPolicyService.scanText(request == null ? null : request.text());
    }

    @GetMapping("/registration-policy")
    public PortalRegistrationPolicyResponse registrationPolicy() {
        return portalSecurityService.registrationPolicy();
    }

    @PutMapping("/registration-policy")
    public PortalRegistrationPolicyResponse updateRegistrationPolicy(@RequestBody PortalRegistrationPolicyRequest request) {
        return portalSecurityService.updateRegistrationPolicy(request);
    }
}
