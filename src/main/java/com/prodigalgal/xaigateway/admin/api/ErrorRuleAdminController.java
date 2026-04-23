package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.ErrorRuleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/error-rules")
public class ErrorRuleAdminController {

    private final ErrorRuleService errorRuleService;

    public ErrorRuleAdminController(ErrorRuleService errorRuleService) {
        this.errorRuleService = errorRuleService;
    }

    @GetMapping
    public List<ErrorRuleResponse> list() {
        return errorRuleService.list();
    }

    @PostMapping
    public ErrorRuleResponse create(@Valid @RequestBody ErrorRuleRequest request) {
        return errorRuleService.save(null, request);
    }

    @PutMapping("/{id}")
    public ErrorRuleResponse update(@PathVariable Long id, @Valid @RequestBody ErrorRuleRequest request) {
        return errorRuleService.save(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        errorRuleService.delete(id);
    }

    @PostMapping("/preview")
    public ErrorRulePreviewResponse preview(@RequestBody ErrorRulePreviewRequest request) {
        return errorRuleService.preview(request);
    }
}
