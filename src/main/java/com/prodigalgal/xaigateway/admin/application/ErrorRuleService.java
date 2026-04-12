package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ErrorRulePreviewRequest;
import com.prodigalgal.xaigateway.admin.api.ErrorRulePreviewResponse;
import com.prodigalgal.xaigateway.admin.api.ErrorRuleRequest;
import com.prodigalgal.xaigateway.admin.api.ErrorRuleResponse;
import com.prodigalgal.xaigateway.gateway.core.error.ErrorRuleMatchContext;
import com.prodigalgal.xaigateway.gateway.core.error.GatewayRuleMatchedException;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayDegradationPolicy;
import com.prodigalgal.xaigateway.infra.persistence.entity.ErrorRuleEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.ErrorRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@Transactional
public class ErrorRuleService {

    private final ErrorRuleRepository errorRuleRepository;
    private final OpsAuditService opsAuditService;
    private final OpsAlertService opsAlertService;

    public ErrorRuleService(
            ErrorRuleRepository errorRuleRepository,
            OpsAuditService opsAuditService,
            OpsAlertService opsAlertService) {
        this.errorRuleRepository = errorRuleRepository;
        this.opsAuditService = opsAuditService;
        this.opsAlertService = opsAlertService;
    }

    @Transactional(readOnly = true)
    public List<ErrorRuleResponse> list() {
        return errorRuleRepository.findAllByOrderByPriorityAscCreatedAtAsc().stream().map(this::toResponse).toList();
    }

    public ErrorRuleResponse save(Long id, ErrorRuleRequest request) {
        ErrorRuleEntity entity = id == null
                ? new ErrorRuleEntity()
                : errorRuleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("未找到错误规则。"));
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setPriority(request.priority() == null ? 100 : request.priority());
        entity.setProviderType(blankToNull(request.providerType()));
        entity.setProtocol(blankToNull(request.protocol()));
        entity.setModelPattern(blankToNull(request.modelPattern()));
        entity.setRequestPath(blankToNull(request.requestPath()));
        entity.setHttpStatus(request.httpStatus());
        entity.setErrorCode(blankToNull(request.errorCode()));
        entity.setMatchScope(request.matchScope() == null ? "GATEWAY" : request.matchScope().trim().toUpperCase(Locale.ROOT));
        entity.setAction(request.action() == null ? "REWRITE" : request.action().trim().toUpperCase(Locale.ROOT));
        entity.setRewriteStatus(request.rewriteStatus());
        entity.setRewriteCode(blankToNull(request.rewriteCode()));
        entity.setRewriteMessage(blankToNull(request.rewriteMessage()));
        entity.setDowngradePolicy(blankToNull(request.downgradePolicy()));
        return toResponse(errorRuleRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public ErrorRulePreviewResponse preview(ErrorRulePreviewRequest request) {
        return new ErrorRulePreviewResponse(errorRuleRepository.findAllByEnabledTrueOrderByPriorityAscCreatedAtAsc().stream()
                .filter(rule -> matches(rule, toContext(request)))
                .map(this::toResponse)
                .toList());
    }

    public Optional<GatewayRuleMatchedException> evaluate(ErrorRuleMatchContext context) {
        for (ErrorRuleEntity rule : errorRuleRepository.findAllByEnabledTrueOrderByPriorityAscCreatedAtAsc()) {
            if (!matches(rule, context)) {
                continue;
            }
            opsAuditService.record("ERROR_RULE", "MATCHED", "error_rule", String.valueOf(rule.getId()), rule.getAction());
            opsAlertService.emitEvent(
                    "ERROR_RULE_MATCH",
                    "DOWNGRADE".equals(rule.getAction()) || "BLOCK".equals(rule.getAction()) ? "HIGH" : "MEDIUM",
                    "错误规则命中",
                    "rule#" + rule.getId() + " 命中 " + (context.requestPath() == null ? "-" : context.requestPath()),
                    "error_rule",
                    String.valueOf(rule.getId()),
                    null
            );
            return Optional.of(toException(rule, context));
        }
        return Optional.empty();
    }

    public List<ErrorRuleResponse> potentialMatches(String providerType, String protocol, String model, String requestPath) {
        ErrorRuleMatchContext context = new ErrorRuleMatchContext(providerType, protocol, model, requestPath, null, null, null, null);
        return errorRuleRepository.findAllByEnabledTrueOrderByPriorityAscCreatedAtAsc().stream()
                .filter(rule -> matches(rule, context))
                .map(this::toResponse)
                .toList();
    }

    private ErrorRuleMatchContext toContext(ErrorRulePreviewRequest request) {
        return new ErrorRuleMatchContext(
                blankToNull(request.providerType()),
                blankToNull(request.protocol()),
                blankToNull(request.model()),
                blankToNull(request.requestPath()),
                request.httpStatus(),
                blankToNull(request.errorCode()),
                blankToNull(request.matchScope()),
                blankToNull(request.message())
        );
    }

    private boolean matches(ErrorRuleEntity rule, ErrorRuleMatchContext context) {
        return matchesText(rule.getProviderType(), context.providerType())
                && matchesText(rule.getProtocol(), context.protocol())
                && matchesText(rule.getRequestPath(), context.requestPath())
                && matchesText(rule.getMatchScope(), context.matchScope())
                && matchesPattern(rule.getModelPattern(), context.model())
                && matchesInt(rule.getHttpStatus(), context.httpStatus())
                && matchesText(rule.getErrorCode(), context.errorCode());
    }

    private boolean matchesText(String expected, String actual) {
        return expected == null || expected.isBlank() || (actual != null && expected.equalsIgnoreCase(actual));
    }

    private boolean matchesPattern(String expected, String actual) {
        return expected == null || expected.isBlank() || (actual != null && actual.contains(expected));
    }

    private boolean matchesInt(Integer expected, Integer actual) {
        return expected == null || (actual != null && expected.equals(actual));
    }

    private GatewayRuleMatchedException toException(ErrorRuleEntity rule, ErrorRuleMatchContext context) {
        String action = rule.getAction();
        if ("PASSTHROUGH".equals(action)) {
            return new GatewayRuleMatchedException(context.httpStatus() == null ? 500 : context.httpStatus(),
                    context.errorCode() == null ? "UPSTREAM_ERROR" : context.errorCode(),
                    context.message() == null ? "上游错误透传。" : context.message());
        }
        if ("DOWNGRADE".equals(action)) {
            String policy = rule.getDowngradePolicy() == null ? GatewayDegradationPolicy.ALLOW_EMULATED.name() : rule.getDowngradePolicy();
            return new GatewayRuleMatchedException(rule.getRewriteStatus() == null ? 409 : rule.getRewriteStatus(),
                    rule.getRewriteCode() == null ? "DOWNGRADED_BY_RULE" : rule.getRewriteCode(),
                    rule.getRewriteMessage() == null ? "请求被错误规则降级到策略 " + policy : rule.getRewriteMessage());
        }
        if ("BLOCK".equals(action)) {
            return new GatewayRuleMatchedException(rule.getRewriteStatus() == null ? 400 : rule.getRewriteStatus(),
                    rule.getRewriteCode() == null ? "BLOCKED_BY_RULE" : rule.getRewriteCode(),
                    rule.getRewriteMessage() == null ? "请求被错误规则阻断。" : rule.getRewriteMessage());
        }
        return new GatewayRuleMatchedException(rule.getRewriteStatus() == null ? 502 : rule.getRewriteStatus(),
                rule.getRewriteCode() == null ? "REWRITTEN_ERROR" : rule.getRewriteCode(),
                rule.getRewriteMessage() == null ? "上游错误已被规则改写。" : rule.getRewriteMessage());
    }

    private ErrorRuleResponse toResponse(ErrorRuleEntity entity) {
        return new ErrorRuleResponse(
                entity.getId(),
                entity.isEnabled(),
                entity.getPriority(),
                entity.getProviderType(),
                entity.getProtocol(),
                entity.getModelPattern(),
                entity.getRequestPath(),
                entity.getHttpStatus(),
                entity.getErrorCode(),
                entity.getMatchScope(),
                entity.getAction(),
                entity.getRewriteStatus(),
                entity.getRewriteCode(),
                entity.getRewriteMessage(),
                entity.getDowngradePolicy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
