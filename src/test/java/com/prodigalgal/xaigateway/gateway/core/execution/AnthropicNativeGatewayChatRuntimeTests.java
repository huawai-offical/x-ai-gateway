package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalContentPart;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileContent;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileResponse;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.provider.adapter.anthropic.AnthropicChatModelFactory;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AnthropicNativeGatewayChatRuntimeTests {

    @Test
    void shouldPreferAnthropicFileIdForGatewayDocumentBlocks() {
        GatewayFileService gatewayFileService = Mockito.mock(GatewayFileService.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        AnthropicNativeGatewayChatRuntime runtime = new AnthropicNativeGatewayChatRuntime(
                Mockito.mock(AnthropicChatModelFactory.class),
                gatewayFileService,
                distributedKeyQueryService
        );

        Mockito.when(distributedKeyQueryService.findActiveByKeyPrefix("sk-gw-test"))
                .thenReturn(Optional.of(distributedKeyView()));
        Mockito.when(gatewayFileService.resolveAnthropicExternalFileId("file-1", 1L))
                .thenReturn(Optional.of("file_anthropic_123"));

        Object block = ReflectionTestUtils.invokeMethod(
                runtime,
                "toDocumentBlock",
                "sk-gw-test",
                CanonicalContentPart.file("application/pdf", "gateway://file-1", "doc.pdf")
        );

        assertTrue(String.valueOf(block).contains("file_id"));
        assertTrue(String.valueOf(block).contains("file_anthropic_123"));
    }

    @Test
    void shouldFallbackToBase64DocumentWhenAnthropicFileBindingMissing() {
        GatewayFileService gatewayFileService = Mockito.mock(GatewayFileService.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        AnthropicNativeGatewayChatRuntime runtime = new AnthropicNativeGatewayChatRuntime(
                Mockito.mock(AnthropicChatModelFactory.class),
                gatewayFileService,
                distributedKeyQueryService
        );

        Mockito.when(distributedKeyQueryService.findActiveByKeyPrefix("sk-gw-test"))
                .thenReturn(Optional.of(distributedKeyView()));
        Mockito.when(gatewayFileService.resolveAnthropicExternalFileId("file-1", 1L))
                .thenReturn(Optional.empty());
        Mockito.when(gatewayFileService.getFileContent("file-1", 1L))
                .thenReturn(new GatewayFileContent(
                        GatewayFileResponse.from("file-1", "doc.pdf", "assistants", 4L, Instant.parse("2026-04-16T00:00:00Z"), "processed"),
                        "demo".getBytes(StandardCharsets.UTF_8),
                        "application/pdf"
                ));

        Object block = ReflectionTestUtils.invokeMethod(
                runtime,
                "toDocumentBlock",
                "sk-gw-test",
                CanonicalContentPart.file("application/pdf", "gateway://file-1", "doc.pdf")
        );

        assertTrue(String.valueOf(block).contains("ZGVtbw=="));
    }

    private DistributedKeyView distributedKeyView() {
        return new DistributedKeyView(
                1L,
                "test",
                "sk-gw-test",
                "masked",
                List.of(ProviderType.ANTHROPIC_DIRECT.name().toLowerCase()),
                List.of(),
                List.of()
        );
    }
}
