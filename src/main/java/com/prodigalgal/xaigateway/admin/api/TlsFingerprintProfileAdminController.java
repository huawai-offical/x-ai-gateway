package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.NetworkGovernanceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/network/tls-profiles")
public class TlsFingerprintProfileAdminController {

    private final NetworkGovernanceService networkGovernanceService;

    public TlsFingerprintProfileAdminController(NetworkGovernanceService networkGovernanceService) {
        this.networkGovernanceService = networkGovernanceService;
    }

    @GetMapping
    public List<TlsFingerprintProfileResponse> list() { return networkGovernanceService.listTlsProfiles(); }

    @PostMapping
    public TlsFingerprintProfileResponse create(@Valid @RequestBody TlsFingerprintProfileRequest request) { return networkGovernanceService.saveTlsProfile(null, request); }

    @PutMapping("/{id}")
    public TlsFingerprintProfileResponse update(@PathVariable Long id, @Valid @RequestBody TlsFingerprintProfileRequest request) { return networkGovernanceService.saveTlsProfile(id, request); }
}
