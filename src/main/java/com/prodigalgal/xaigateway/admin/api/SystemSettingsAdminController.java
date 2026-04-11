package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.SystemSettingsAdminService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/settings")
public class SystemSettingsAdminController {

    private final SystemSettingsAdminService systemSettingsAdminService;

    public SystemSettingsAdminController(SystemSettingsAdminService systemSettingsAdminService) {
        this.systemSettingsAdminService = systemSettingsAdminService;
    }

    @GetMapping
    public SystemSettingsResponse get() {
        return systemSettingsAdminService.get();
    }

    @PutMapping
    public SystemSettingsResponse save(@Valid @RequestBody SystemSettingsRequest request) {
        return systemSettingsAdminService.save(request);
    }

    @PostMapping("/reset")
    public SystemSettingsResponse reset() {
        return systemSettingsAdminService.reset();
    }
}
