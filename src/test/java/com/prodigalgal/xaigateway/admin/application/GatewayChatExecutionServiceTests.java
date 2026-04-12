package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyGovernanceService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayChatPromptBuilder;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileResource;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayObservabilityService;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.provider.adapter.ProviderExecutionSupportService;
import com.prodigalgal.xaigateway.provider.adapter.anthropic.AnthropicChatModelFactory;
import com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiChatModelFactory;
import com.prodigalgal.xaigateway.provider.adapter.openai.OpenAiChatModelFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatewayChatExecutionServiceTests {

    @Test
    void shouldResolveGatewayFileReferenceToSpringAiMedia() {
        GatewayRouteSelectionService routeSelectionService = Mockito.mock(GatewayRouteSelectionService.class);
        ProviderExecutionSupportService providerExecutionSupportService = Mockito.mock(ProviderExecutionSupportService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        GatewayObservabilityService gatewayObservabilityService = Mockito.mock(GatewayObservabilityService.class);
        DistributedKeyGovernanceService distributedKeyGovernanceService = Mockito.mock(DistributedKeyGovernanceService.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        AccountSelectionService accountSelectionService = Mockito.mock(AccountSelectionService.class);
        GatewayFileService gatewayFileService = Mockito.mock(GatewayFileService.class);
        OpenAiChatModelFactory openAiChatModelFactory = Mockito.mock(OpenAiChatModelFactory.class);
        AnthropicChatModelFactory anthropicChatModelFactory = Mockito.mock(AnthropicChatModelFactory.class);
        GeminiChatModelFactory geminiChatModelFactory = Mockito.mock(GeminiChatModelFactory.class);
        GatewayChatPromptBuilder promptBuilder = new GatewayChatPromptBuilder(distributedKeyQueryService, gatewayFileService);

        Mockito.when(distributedKeyQueryService.findActiveByKeyPrefix("sk-gw-test"))
                .thenReturn(Optional.of(new DistributedKeyView(
                        1L,
                        "test",
                        "sk-gw-test",
                        "masked",
                        List.of(),
                        List.of(),
                        List.of()
                )));
        Mockito.when(gatewayFileService.resolveFileResource("file-123", 1L))
                .thenReturn(new GatewayFileResource(
                        "file-123",
                        "application/pdf",
                        "doc.pdf",
                        new ByteArrayResource("hello".getBytes(StandardCharsets.UTF_8))
                ));

        Object media = ReflectionTestUtils.invokeMethod(
                promptBuilder,
                "toMedia",
                "sk-gw-test",
                new ChatExecutionRequest.MediaInput(
                        "file",
                        "application/pdf",
                        "gateway://file-123",
                        "doc.pdf"
                )
        );

        assertEquals("application/pdf", ReflectionTestUtils.invokeMethod(media, "getMimeType").toString());
    }
}
