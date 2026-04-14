package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.AdminResourceExecutionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/resource")
public class AdminResourceExecuteController {

    private final AdminResourceExecutionService adminResourceExecutionService;

    public AdminResourceExecuteController(AdminResourceExecutionService adminResourceExecutionService) {
        this.adminResourceExecutionService = adminResourceExecutionService;
    }

    @PostMapping("/execute")
    public AdminResourceExecuteResponse execute(@Valid @RequestBody AdminResourceExecuteRequest request) {
        return adminResourceExecutionService.execute(request);
    }
}
