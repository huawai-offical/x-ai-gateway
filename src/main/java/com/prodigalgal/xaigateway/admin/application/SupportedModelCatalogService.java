package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountGroupRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SupportedModelCatalogService {

    private final UpstreamAccountGroupRepository upstreamAccountGroupRepository;
    private final UpstreamAccountRepository upstreamAccountRepository;
    private final UpstreamCredentialRepository upstreamCredentialRepository;

    public SupportedModelCatalogService(
            UpstreamAccountGroupRepository upstreamAccountGroupRepository,
            UpstreamAccountRepository upstreamAccountRepository,
            UpstreamCredentialRepository upstreamCredentialRepository) {
        this.upstreamAccountGroupRepository = upstreamAccountGroupRepository;
        this.upstreamAccountRepository = upstreamAccountRepository;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
    }

    public List<String> listByUpstreamProvider(UpstreamAccountProviderType providerType) {
        Set<String> models = new LinkedHashSet<>();

        upstreamAccountGroupRepository.findAllByOrderByCreatedAtDesc().forEach(group -> {
            if (providerType == null || providerType == group.getProviderType()) {
                models.addAll(normalize(group.getSupportedModels()));
            }
        });

        upstreamAccountRepository.findAllByOrderByCreatedAtDesc().forEach(account -> {
            if (providerType == null || providerType == account.getProviderType()) {
                models.addAll(normalize(account.getSupportedModels()));
            }
        });

        upstreamCredentialRepository.findAllByDeletedFalseOrderByCreatedAtDesc().forEach(credential -> {
            UpstreamAccountProviderType mappedProvider = mapToUpstreamProvider(credential.getProviderType());
            if (providerType == null || providerType == mappedProvider) {
                models.addAll(normalize(credential.getSupportedModels()));
            }
        });

        return models.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public List<String> listByProvider(ProviderType providerType) {
        Set<String> models = new LinkedHashSet<>();

        upstreamCredentialRepository.findAllByDeletedFalseOrderByCreatedAtDesc().forEach(credential -> {
            if (credential.getProviderType() == providerType) {
                models.addAll(normalize(credential.getSupportedModels()));
            }
        });

        UpstreamAccountProviderType mappedProvider = mapToUpstreamProvider(providerType);
        if (mappedProvider != null) {
            models.addAll(listByUpstreamProvider(mappedProvider));
        }

        return models.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public List<String> resolveForAccountImport(UpstreamAccountGroupEntity group, List<String> requestModels) {
        List<String> normalizedRequest = normalize(requestModels);
        if (!normalizedRequest.isEmpty()) {
            return normalizedRequest;
        }

        if (group != null) {
            List<String> normalizedGroup = normalize(group.getSupportedModels());
            if (!normalizedGroup.isEmpty()) {
                return normalizedGroup;
            }
            return listByUpstreamProvider(group.getProviderType());
        }

        return listByUpstreamProvider(UpstreamAccountProviderType.OPENAI_OAUTH);
    }

    public List<String> resolveForCredentialImport(
            ProviderType providerType,
            UpstreamAccountGroupEntity group,
            List<String> requestModels) {
        List<String> normalizedRequest = normalize(requestModels);
        if (!normalizedRequest.isEmpty()) {
            return normalizedRequest;
        }

        if (group != null) {
            List<String> normalizedGroup = normalize(group.getSupportedModels());
            if (!normalizedGroup.isEmpty()) {
                return normalizedGroup;
            }
        }

        return listByProvider(providerType);
    }

    public List<String> normalize(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }

        Map<String, String> deduplicated = new LinkedHashMap<>();
        for (String raw : source) {
            if (raw == null) {
                continue;
            }
            String model = raw.trim();
            if (model.isEmpty()) {
                continue;
            }
            deduplicated.putIfAbsent(model.toLowerCase(), model);
        }

        List<String> normalized = new ArrayList<>(deduplicated.values());
        normalized.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(normalized);
    }

    private UpstreamAccountProviderType mapToUpstreamProvider(ProviderType providerType) {
        if (providerType == null) {
            return null;
        }
        return switch (providerType) {
            case OPENAI_DIRECT, OPENAI_COMPATIBLE -> UpstreamAccountProviderType.OPENAI_OAUTH;
            case GEMINI_DIRECT -> UpstreamAccountProviderType.GEMINI_OAUTH;
            case ANTHROPIC_DIRECT -> UpstreamAccountProviderType.CLAUDE_ACCOUNT;
            case OLLAMA_DIRECT -> null;
        };
    }
}
