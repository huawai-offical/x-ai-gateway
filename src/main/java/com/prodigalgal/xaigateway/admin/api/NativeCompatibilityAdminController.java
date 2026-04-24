package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.NativeCompatibilityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/native-compatibility")
public class NativeCompatibilityAdminController {

    private final NativeCompatibilityService nativeCompatibilityService;

    public NativeCompatibilityAdminController(NativeCompatibilityService nativeCompatibilityService) {
        this.nativeCompatibilityService = nativeCompatibilityService;
    }

    @GetMapping("/matrix")
    public NativeCompatibilityResponse matrix() {
        return nativeCompatibilityService.matrix();
    }
}
