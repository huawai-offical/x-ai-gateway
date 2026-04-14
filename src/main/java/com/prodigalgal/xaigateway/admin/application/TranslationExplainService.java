package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.TranslationExplainRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayDegradationPolicy;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompiler;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TranslationExplainService {

    private final TranslationExecutionPlanCompiler translationExecutionPlanCompiler;

    public TranslationExplainService(TranslationExecutionPlanCompiler translationExecutionPlanCompiler) {
        this.translationExecutionPlanCompiler = translationExecutionPlanCompiler;
    }

    public CanonicalExecutionPlan explain(TranslationExplainRequest request) {
        return translationExecutionPlanCompiler.compilePreview(
                request.distributedKeyPrefix(),
                request.protocol(),
                request.requestPath(),
                request.requestedModel(),
                request.degradationPolicy() == null || request.degradationPolicy().isBlank()
                        ? GatewayDegradationPolicy.ALLOW_LOSSY
                        : GatewayDegradationPolicy.from(request.degradationPolicy()),
                GatewayClientFamily.GENERIC_OPENAI,
                request.body()
        ).canonicalPlan();
    }
}
