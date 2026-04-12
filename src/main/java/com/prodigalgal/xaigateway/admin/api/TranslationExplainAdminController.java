package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.TranslationExplainService;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlan;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/translation")
public class TranslationExplainAdminController {

    private final TranslationExplainService translationExplainService;

    public TranslationExplainAdminController(TranslationExplainService translationExplainService) {
        this.translationExplainService = translationExplainService;
    }

    @PostMapping("/explain")
    public TranslationExecutionPlan explain(@Valid @RequestBody TranslationExplainRequest request) {
        return translationExplainService.explain(request);
    }
}
