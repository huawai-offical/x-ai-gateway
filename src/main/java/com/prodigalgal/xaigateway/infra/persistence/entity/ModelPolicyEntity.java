package com.prodigalgal.xaigateway.infra.persistence.entity;

import com.prodigalgal.xaigateway.gateway.core.model.ModelPolicyScopeType;
import com.prodigalgal.xaigateway.infra.persistence.converter.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "model_policy",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_model_policy_scope_public_upstream",
                        columnNames = {"scope_type", "scope_id", "scope_ref", "public_model_key", "upstream_model_key", "policy_kind"}
                )
        },
        indexes = {
                @Index(name = "idx_model_policy_scope", columnList = "scope_type,scope_id,scope_ref"),
                @Index(name = "idx_model_policy_public_enabled", columnList = "public_model_key,enabled"),
                @Index(name = "idx_model_policy_upstream_enabled", columnList = "upstream_model_key,enabled"),
                @Index(name = "idx_model_policy_priority", columnList = "priority")
        }
)
@Comment("模型策略表，用于跨 DistributedKey、AccountGroup、Account、Credential、SiteProfile、Vendor 分层收缩与映射。")
public class ModelPolicyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    @Comment("主键。")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 32)
    @Comment("策略作用域类型。")
    private ModelPolicyScopeType scopeType;

    @Column(name = "scope_id")
    @Comment("策略作用域数字 ID。Vendor 这类非物理表作用域可为空。")
    private Long scopeId;

    @Column(name = "scope_ref", length = 128)
    @Comment("策略作用域字符串引用，例如 vendor_code。")
    private String scopeRef;

    @Column(name = "policy_kind", nullable = false, length = 32)
    @Comment("策略类型：ALLOW、DENY、MAP、DISCOVERED、RUNTIME。")
    private String policyKind = "ALLOW";

    @Column(name = "public_model", nullable = false, length = 256)
    @Comment("下游可见模型名或别名。")
    private String publicModel;

    @Column(name = "public_model_key", nullable = false, length = 256)
    @Comment("规范化后的下游模型 key。")
    private String publicModelKey;

    @Column(name = "upstream_model", length = 256)
    @Comment("真实上游模型名。为空时默认沿用 public_model。")
    private String upstreamModel;

    @Column(name = "upstream_model_key", length = 256)
    @Comment("规范化后的真实上游模型 key。")
    private String upstreamModelKey;

    @Column(name = "model_family", length = 64)
    @Comment("模型族，例如 chat、reasoning、coder、vision、embedding。")
    private String modelFamily;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "supported_protocols_json", columnDefinition = "text")
    @Comment("策略适用协议列表，空表示不限。")
    private List<String> supportedProtocols = List.of();

    @Column(name = "enabled", nullable = false)
    @Comment("是否启用。")
    private boolean enabled = true;

    @Column(name = "deny", nullable = false)
    @Comment("是否为显式拒绝策略。")
    private boolean deny = false;

    @Column(name = "priority", nullable = false)
    @Comment("同层策略优先级，值越小越优先。")
    private int priority = 100;

    @Column(name = "weight", nullable = false)
    @Comment("灰度或候选权重。")
    private int weight = 100;

    @Column(name = "capability_json", columnDefinition = "text")
    @Comment("模型能力声明。")
    private String capabilityJson;

    @Column(name = "request_overrides_json", columnDefinition = "text")
    @Comment("请求 override，例如 reasoning、extra_body、temperature 等。")
    private String requestOverridesJson;

    @Column(name = "response_overrides_json", columnDefinition = "text")
    @Comment("响应 override，例如 reasoning_content 回放字段等。")
    private String responseOverridesJson;

    @Column(name = "runtime_policy_json", columnDefinition = "text")
    @Comment("运行态策略，例如模型级限流、额度、fallback chain、canary。")
    private String runtimePolicyJson;

    @Column(name = "mapping_source", nullable = false, length = 32)
    @Comment("策略来源：manual、preset、discovered、imported。")
    private String mappingSource = "manual";

    @Column(name = "description", length = 512)
    @Comment("策略说明。")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    @Comment("创建时间（UTC）。")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    @Comment("最后更新时间（UTC）。")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public ModelPolicyScopeType getScopeType() {
        return scopeType;
    }

    public void setScopeType(ModelPolicyScopeType scopeType) {
        this.scopeType = scopeType;
    }

    public Long getScopeId() {
        return scopeId;
    }

    public void setScopeId(Long scopeId) {
        this.scopeId = scopeId;
    }

    public String getScopeRef() {
        return scopeRef;
    }

    public void setScopeRef(String scopeRef) {
        this.scopeRef = scopeRef;
    }

    public String getPolicyKind() {
        return policyKind;
    }

    public void setPolicyKind(String policyKind) {
        this.policyKind = policyKind;
    }

    public String getPublicModel() {
        return publicModel;
    }

    public void setPublicModel(String publicModel) {
        this.publicModel = publicModel;
    }

    public String getPublicModelKey() {
        return publicModelKey;
    }

    public void setPublicModelKey(String publicModelKey) {
        this.publicModelKey = publicModelKey;
    }

    public String getUpstreamModel() {
        return upstreamModel;
    }

    public void setUpstreamModel(String upstreamModel) {
        this.upstreamModel = upstreamModel;
    }

    public String getUpstreamModelKey() {
        return upstreamModelKey;
    }

    public void setUpstreamModelKey(String upstreamModelKey) {
        this.upstreamModelKey = upstreamModelKey;
    }

    public String getModelFamily() {
        return modelFamily;
    }

    public void setModelFamily(String modelFamily) {
        this.modelFamily = modelFamily;
    }

    public List<String> getSupportedProtocols() {
        return supportedProtocols;
    }

    public void setSupportedProtocols(List<String> supportedProtocols) {
        this.supportedProtocols = supportedProtocols;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDeny() {
        return deny;
    }

    public void setDeny(boolean deny) {
        this.deny = deny;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public String getCapabilityJson() {
        return capabilityJson;
    }

    public void setCapabilityJson(String capabilityJson) {
        this.capabilityJson = capabilityJson;
    }

    public String getRequestOverridesJson() {
        return requestOverridesJson;
    }

    public void setRequestOverridesJson(String requestOverridesJson) {
        this.requestOverridesJson = requestOverridesJson;
    }

    public String getResponseOverridesJson() {
        return responseOverridesJson;
    }

    public void setResponseOverridesJson(String responseOverridesJson) {
        this.responseOverridesJson = responseOverridesJson;
    }

    public String getRuntimePolicyJson() {
        return runtimePolicyJson;
    }

    public void setRuntimePolicyJson(String runtimePolicyJson) {
        this.runtimePolicyJson = runtimePolicyJson;
    }

    public String getMappingSource() {
        return mappingSource;
    }

    public void setMappingSource(String mappingSource) {
        this.mappingSource = mappingSource;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
