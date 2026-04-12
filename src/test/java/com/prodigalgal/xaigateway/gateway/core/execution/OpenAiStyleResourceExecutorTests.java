package com.prodigalgal.xaigateway.gateway.core.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class OpenAiStyleResourceExecutorTests {

    @Test
    void shouldAllowOpenAiCompatibleAudioWhenPathAndAuthAreCompatible() {
        GatewayOpenAiPassthroughService passthroughService = Mockito.mock(GatewayOpenAiPassthroughService.class);
        OpenAiAudioGatewayResourceExecutor executor = new OpenAiAudioGatewayResourceExecutor(passthroughService);
        GatewayResourceExecutionContext context = context(ProviderType.OPENAI_COMPATIBLE, UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC, AuthStrategy.BEARER, PathStrategy.OPENAI_V1, "/v1/audio/speech");
        GatewayOpenAiPassthroughService.CatalogSiteRequest siteRequest =
                new GatewayOpenAiPassthroughService.CatalogSiteRequest(Mockito.mock(WebClient.class), "/v1/audio/speech");
        Mockito.when(passthroughService.buildPreparedSiteRequest(any(), any(), any(), eq("/v1/audio/speech"))).thenReturn(siteRequest);
        Mockito.when(passthroughService.executePreparedBinary(any(), any(), any(), eq("/v1/audio/speech"), eq("/v1/audio/speech"), any()))
                .thenReturn(ResponseEntity.ok(new byte[] {1, 2, 3}));

        ObjectNode request = new ObjectMapper().createObjectNode().put("model", "tts-1");
        executor.executeBinary(context, request, "tts-1");

        Mockito.verify(passthroughService).executePreparedBinary(any(), any(), any(), eq("/v1/audio/speech"), eq("/v1/audio/speech"), any());
    }

    @Test
    void shouldRejectImagesWhenPathStrategyIsNotOpenAiV1() {
        OpenAiImagesGatewayResourceExecutor executor = new OpenAiImagesGatewayResourceExecutor(Mockito.mock(GatewayOpenAiPassthroughService.class));
        GatewayResourceExecutionContext context = context(
                ProviderType.OPENAI_DIRECT,
                UpstreamSiteKind.AZURE_OPENAI,
                AuthStrategy.AZURE_API_KEY,
                PathStrategy.AZURE_OPENAI_DEPLOYMENT,
                "/v1/images/generations"
        );

        assertThrows(IllegalArgumentException.class, () -> executor.executeJson(context, new ObjectMapper().createObjectNode(), null));
    }

    @Test
    void shouldRejectModerationsWhenAuthStrategyIsUnsupported() {
        OpenAiModerationsGatewayResourceExecutor executor = new OpenAiModerationsGatewayResourceExecutor(Mockito.mock(GatewayOpenAiPassthroughService.class));
        GatewayResourceExecutionContext context = context(
                ProviderType.OPENAI_COMPATIBLE,
                UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC,
                AuthStrategy.UNSUPPORTED,
                PathStrategy.OPENAI_V1,
                "/v1/moderations"
        );

        assertThrows(IllegalArgumentException.class, () -> executor.executeJson(context, new ObjectMapper().createObjectNode(), null));
    }

    private GatewayResourceExecutionContext context(
            ProviderType providerType,
            UpstreamSiteKind siteKind,
            AuthStrategy authStrategy,
            PathStrategy pathStrategy,
            String requestPath) {
        CatalogCandidateView candidate = new CatalogCandidateView(
                101L,
                "candidate",
                providerType,
                1L,
                ProviderFamily.OPENAI,
                siteKind,
                authStrategy,
                pathStrategy,
                ErrorSchemaStrategy.OPENAI_ERROR,
                "https://example.com",
                "model-a",
                "model-a",
                List.of("openai"),
                true,
                false,
                false,
                false,
                false,
                false,
                ReasoningTransport.NONE,
                com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel.NATIVE
        );
        RouteCandidateView routeCandidateView = new RouteCandidateView(candidate, 11L, 10, 100);
        RouteSelectionResult selectionResult = new RouteSelectionResult(
                1L,
                "sk-gw-test",
                "model-a",
                "model-a",
                "model-a",
                "openai",
                "prefix",
                "fingerprint",
                "model-a",
                RouteSelectionSource.WEIGHTED_HASH,
                routeCandidateView,
                List.of(routeCandidateView)
        );
        UpstreamCredentialEntity credential = new UpstreamCredentialEntity();
        credential.setBaseUrl("https://example.com");
        credential.setProviderType(providerType);
        return new GatewayResourceExecutionContext(selectionResult, credential, "api-key", requestPath);
    }
}
