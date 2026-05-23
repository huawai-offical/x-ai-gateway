package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ProviderDomainCatalogResponse;
import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyAccountGroupBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.ProviderProtocolEndpointEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteCapabilitySnapshotEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccountGroupBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.ProviderProtocolEndpointRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteCapabilitySnapshotRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteModelCapabilityRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountGroupRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamSiteProfileRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProviderDomainCatalogService {

    private static final String DEFAULT_GROUP_NAME = "default";

    private final UpstreamSiteProfileRepository upstreamSiteProfileRepository;
    private final SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository;
    private final SiteModelCapabilityRepository siteModelCapabilityRepository;
    private final ProviderProtocolEndpointRepository providerProtocolEndpointRepository;
    private final UpstreamAccountGroupRepository upstreamAccountGroupRepository;
    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final DistributedKeyAccountGroupBindingRepository distributedKeyAccountGroupBindingRepository;

    public ProviderDomainCatalogService(
            UpstreamSiteProfileRepository upstreamSiteProfileRepository,
            SiteCapabilitySnapshotRepository siteCapabilitySnapshotRepository,
            SiteModelCapabilityRepository siteModelCapabilityRepository,
            ProviderProtocolEndpointRepository providerProtocolEndpointRepository,
            UpstreamAccountGroupRepository upstreamAccountGroupRepository,
            UpstreamCredentialRepository upstreamCredentialRepository,
            DistributedKeyAccountGroupBindingRepository distributedKeyAccountGroupBindingRepository) {
        this.upstreamSiteProfileRepository = upstreamSiteProfileRepository;
        this.siteCapabilitySnapshotRepository = siteCapabilitySnapshotRepository;
        this.siteModelCapabilityRepository = siteModelCapabilityRepository;
        this.providerProtocolEndpointRepository = providerProtocolEndpointRepository;
        this.upstreamAccountGroupRepository = upstreamAccountGroupRepository;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.distributedKeyAccountGroupBindingRepository = distributedKeyAccountGroupBindingRepository;
    }

    public ProviderDomainCatalogResponse catalog() {
        Instant generatedAt = Instant.now();
        List<UpstreamSiteProfileEntity> sites = upstreamSiteProfileRepository.findAll().stream()
                .sorted(Comparator.comparing(UpstreamSiteProfileEntity::getDisplayName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        List<ProviderProtocolEndpointEntity> endpoints = providerProtocolEndpointRepository.findAll().stream()
                .sorted(Comparator.comparing(ProviderProtocolEndpointEntity::getDisplayName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        List<UpstreamAccountGroupEntity> groups = upstreamAccountGroupRepository.findAllByOrderByCreatedAtDesc();
        List<UpstreamCredentialEntity> credentials = upstreamCredentialRepository.findAllByDeletedFalseOrderByCreatedAtDesc();
        List<DistributedKeyAccountGroupBindingEntity> bindings = distributedKeyAccountGroupBindingRepository.findAll();

        Map<Long, ProviderProtocolEndpointEntity> endpointById = endpoints.stream()
                .filter(endpoint -> endpoint.getId() != null)
                .collect(Collectors.toMap(
                        ProviderProtocolEndpointEntity::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<Long, UpstreamAccountGroupEntity> groupById = groups.stream()
                .filter(group -> group.getId() != null)
                .collect(Collectors.toMap(
                        UpstreamAccountGroupEntity::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<Long, List<ProviderProtocolEndpointEntity>> endpointsBySiteId = endpoints.stream()
                .collect(Collectors.groupingBy(
                        ProviderProtocolEndpointEntity::getSiteProfileId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<Long, List<UpstreamCredentialEntity>> credentialsBySiteId = credentials.stream()
                .filter(credential -> credential.getSiteProfileId() != null)
                .collect(Collectors.groupingBy(
                        UpstreamCredentialEntity::getSiteProfileId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<Long, List<UpstreamCredentialEntity>> credentialsByEndpointId = credentials.stream()
                .filter(credential -> credential.getProtocolEndpointId() != null)
                .collect(Collectors.groupingBy(
                        UpstreamCredentialEntity::getProtocolEndpointId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<Long, List<UpstreamCredentialEntity>> credentialsByGroupId = credentials.stream()
                .filter(credential -> credential.getGroupId() != null)
                .collect(Collectors.groupingBy(
                        UpstreamCredentialEntity::getGroupId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<Long, List<DistributedKeyAccountGroupBindingEntity>> bindingsByGroupId = bindings.stream()
                .filter(binding -> binding.getGroup() != null && binding.getGroup().getId() != null)
                .collect(Collectors.groupingBy(
                        binding -> binding.getGroup().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Set<Long> assignedGroupIds = credentials.stream()
                .map(UpstreamCredentialEntity::getGroupId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<ProviderDomainCatalogResponse.Vendor> vendors = sites.stream()
                .map(site -> toVendor(
                        site,
                        endpointsBySiteId.getOrDefault(site.getId(), List.of()),
                        credentialsBySiteId.getOrDefault(site.getId(), List.of()),
                        credentialsByEndpointId,
                        endpointById,
                        groupById,
                        bindingsByGroupId
                ))
                .toList();
        List<ProviderDomainCatalogResponse.AccountGroup> unassignedGroups = groups.stream()
                .filter(group -> group.getId() != null && !assignedGroupIds.contains(group.getId()))
                .map(group -> toAccountGroup(
                        group,
                        List.of(),
                        endpointById,
                        bindingsByGroupId.getOrDefault(group.getId(), List.of())
                ))
                .toList();
        ProviderDomainCatalogResponse.Summary summary = new ProviderDomainCatalogResponse.Summary(
                vendors.size(),
                endpoints.size(),
                groups.size(),
                credentials.size(),
                bindings.size()
        );
        return new ProviderDomainCatalogResponse(generatedAt, summary, vendors, unassignedGroups);
    }

    private ProviderDomainCatalogResponse.Vendor toVendor(
            UpstreamSiteProfileEntity site,
            List<ProviderProtocolEndpointEntity> endpoints,
            List<UpstreamCredentialEntity> siteCredentials,
            Map<Long, List<UpstreamCredentialEntity>> credentialsByEndpointId,
            Map<Long, ProviderProtocolEndpointEntity> endpointById,
            Map<Long, UpstreamAccountGroupEntity> groupById,
            Map<Long, List<DistributedKeyAccountGroupBindingEntity>> bindingsByGroupId) {
        SiteCapabilitySnapshotEntity snapshot = siteCapabilitySnapshotRepository.findBySiteProfile_Id(site.getId()).orElse(null);
        List<ProviderDomainCatalogResponse.ProtocolEndpoint> endpointResponses = endpoints.stream()
                .map(endpoint -> toProtocolEndpoint(endpoint, credentialsByEndpointId.getOrDefault(endpoint.getId(), List.of())))
                .toList();
        Map<Long, List<UpstreamCredentialEntity>> siteCredentialsByGroupId = siteCredentials.stream()
                .filter(credential -> credential.getGroupId() != null)
                .collect(Collectors.groupingBy(
                        UpstreamCredentialEntity::getGroupId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<ProviderDomainCatalogResponse.AccountGroup> accountGroups = siteCredentialsByGroupId.entrySet().stream()
                .sorted(Comparator.comparing(entry -> groupName(groupById.get(entry.getKey())), Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(entry -> {
                    UpstreamAccountGroupEntity group = groupById.get(entry.getKey());
                    if (group == null) {
                        return null;
                    }
                    return toAccountGroup(
                            group,
                            entry.getValue(),
                            endpointById,
                            bindingsByGroupId.getOrDefault(group.getId(), List.of())
                    );
                })
                .filter(Objects::nonNull)
                .toList();
        int modelCount = siteModelCapabilityRepository.findAllBySiteProfile_IdOrderByModelKeyAsc(site.getId()).size();
        return new ProviderDomainCatalogResponse.Vendor(
                site.getId(),
                site.getProfileCode(),
                site.getDisplayName(),
                site.getVendorCode(),
                site.getVendorName(),
                site.getProviderFamily(),
                site.getSiteKind(),
                site.isActive(),
                snapshot == null ? "UNKNOWN" : snapshot.getHealthState(),
                siteCredentials.size(),
                modelCount,
                endpointResponses,
                accountGroups
        );
    }

    private ProviderDomainCatalogResponse.ProtocolEndpoint toProtocolEndpoint(
            ProviderProtocolEndpointEntity endpoint,
            List<UpstreamCredentialEntity> endpointCredentials) {
        List<Long> accountGroupIds = endpointCredentials.stream()
                .map(UpstreamCredentialEntity::getGroupId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return new ProviderDomainCatalogResponse.ProtocolEndpoint(
                endpoint.getId(),
                endpoint.getEndpointCode(),
                endpoint.getDisplayName(),
                endpoint.getProtocolSuite(),
                endpoint.getProviderType(),
                endpoint.getSiteKind(),
                endpoint.getBaseUrl(),
                endpoint.isActive(),
                endpointCredentials.size(),
                accountGroupIds
        );
    }

    private ProviderDomainCatalogResponse.AccountGroup toAccountGroup(
            UpstreamAccountGroupEntity group,
            List<UpstreamCredentialEntity> scopedCredentials,
            Map<Long, ProviderProtocolEndpointEntity> endpointById,
            List<DistributedKeyAccountGroupBindingEntity> bindings) {
        List<ProviderDomainCatalogResponse.EndpointCoverage> endpointCoverage = endpointCoverage(scopedCredentials, endpointById);
        GroupKind groupKind = inferGroupKind(group, endpointCoverage, bindings);
        return new ProviderDomainCatalogResponse.AccountGroup(
                group.getId(),
                group.getGroupName(),
                group.getProviderType(),
                groupKind.kind(),
                groupKind.source(),
                isDefaultGroup(group),
                group.isActive(),
                nullToEmpty(group.getSupportedModels()),
                nullToEmpty(group.getSupportedProtocols()),
                nullToEmpty(group.getAllowedClientFamilies()),
                scopedCredentials.size(),
                endpointCoverage,
                scopedCredentials.stream().map(this::toCredential).toList(),
                bindings.stream()
                        .sorted(Comparator
                                .comparingInt(DistributedKeyAccountGroupBindingEntity::getPriority)
                                .thenComparing(binding -> binding.getCreatedAt(), Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(this::toDistributedKeyBinding)
                        .toList()
        );
    }

    private List<ProviderDomainCatalogResponse.EndpointCoverage> endpointCoverage(
            List<UpstreamCredentialEntity> scopedCredentials,
            Map<Long, ProviderProtocolEndpointEntity> endpointById) {
        return scopedCredentials.stream()
                .collect(Collectors.groupingBy(
                        credential -> credential.getProtocolEndpointId() == null ? -1L : credential.getProtocolEndpointId(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> {
                    ProviderProtocolEndpointEntity endpoint = endpointById.get(entry.getKey());
                    return endpoint == null ? "" : safe(endpoint.getDisplayName());
                }))
                .map(entry -> {
                    Long endpointId = entry.getKey() < 0 ? null : entry.getKey();
                    ProviderProtocolEndpointEntity endpoint = endpointId == null ? null : endpointById.get(endpointId);
                    return new ProviderDomainCatalogResponse.EndpointCoverage(
                            endpointId,
                            endpoint == null ? null : endpoint.getEndpointCode(),
                            endpoint == null ? "未绑定协议入口" : endpoint.getDisplayName(),
                            endpoint == null ? null : endpoint.getProtocolSuite(),
                            entry.getValue(),
                            endpoint == null ? "credential_protocol_endpoint_missing" : "credential_protocol_endpoint_id"
                    );
                })
                .toList();
    }

    private ProviderDomainCatalogResponse.Credential toCredential(UpstreamCredentialEntity credential) {
        Instant now = Instant.now();
        boolean cooldown = credential.getCooldownUntil() != null && credential.getCooldownUntil().isAfter(now);
        return new ProviderDomainCatalogResponse.Credential(
                credential.getId(),
                credential.getCredentialName(),
                credential.getProviderType(),
                credential.getSiteProfileId(),
                credential.getProtocolEndpointId(),
                credential.getGroupId(),
                credential.isActive(),
                cooldown,
                credentialStatus(credential, cooldown),
                nullToEmpty(credential.getSupportedModels()).size(),
                credential.getLastErrorCode(),
                credential.getLastErrorMessage(),
                credential.getCooldownUntil(),
                credential.getLastUsedAt()
        );
    }

    private ProviderDomainCatalogResponse.DistributedKeyBinding toDistributedKeyBinding(
            DistributedKeyAccountGroupBindingEntity binding) {
        DistributedKeyEntity key = binding.getDistributedKey();
        return new ProviderDomainCatalogResponse.DistributedKeyBinding(
                binding.getId(),
                key == null ? null : key.getId(),
                key == null ? null : key.getKeyName(),
                key == null ? null : key.getKeyPrefix(),
                binding.getProviderType(),
                binding.getPriority(),
                binding.isActive(),
                key != null && key.isActive()
        );
    }

    private GroupKind inferGroupKind(
            UpstreamAccountGroupEntity group,
            List<ProviderDomainCatalogResponse.EndpointCoverage> endpointCoverage,
            List<DistributedKeyAccountGroupBindingEntity> bindings) {
        if (isDefaultGroup(group)) {
            return new GroupKind("DEFAULT", "system_default");
        }
        String text = (safe(group.getGroupName()) + " " + safe(group.getDescription())).toLowerCase(Locale.ROOT);
        if (containsAny(text, "生产", "测试", "预发", "prod", "production", "test", "staging", "dev")) {
            return new GroupKind("ENVIRONMENT", "name_heuristic");
        }
        if (containsAny(text, "成本", "额度", "预算", "cost", "quota", "budget")) {
            return new GroupKind("COST_QUOTA", "name_heuristic");
        }
        if (containsAny(text, "备用", "主力", "冷却", "故障", "backup", "standby", "primary", "fallback")) {
            return new GroupKind("HEALTH_STANDBY", "name_heuristic");
        }
        if (endpointCoverage.size() == 1 && endpointCoverage.getFirst().endpointId() != null) {
            return new GroupKind("PROTOCOL_ENDPOINT", "credential_endpoint_coverage");
        }
        if (!bindings.isEmpty()) {
            return new GroupKind("CLIENT_AUTHORIZATION", "distributed_key_binding");
        }
        return new GroupKind("GENERAL_POOL", "generic_pool");
    }

    private String credentialStatus(UpstreamCredentialEntity credential, boolean cooldown) {
        if (!credential.isActive()) {
            return "INACTIVE";
        }
        if (cooldown) {
            return "COOLDOWN";
        }
        if (credential.getLastErrorCode() != null || credential.getLastErrorMessage() != null) {
            return "ERROR";
        }
        return "READY";
    }

    private boolean isDefaultGroup(UpstreamAccountGroupEntity group) {
        return group.getGroupName() != null && DEFAULT_GROUP_NAME.equalsIgnoreCase(group.getGroupName().trim());
    }

    private String groupName(UpstreamAccountGroupEntity group) {
        return group == null ? null : group.getGroupName();
    }

    private List<String> nullToEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record GroupKind(String kind, String source) {
    }
}
