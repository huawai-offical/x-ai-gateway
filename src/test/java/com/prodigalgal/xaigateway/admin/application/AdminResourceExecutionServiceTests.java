package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.AdminResourceExecuteRequest;
import com.prodigalgal.xaigateway.admin.api.AdminResourceExecuteResponse;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlanCompilation;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceResponse;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionResult;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionService;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureService;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.SupportStatus;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompiler;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class AdminResourceExecutionServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldTreatFileContentAsBinaryAndExposeCanonicalSummary() {
        TranslationExecutionPlanCompiler translationExecutionPlanCompiler = Mockito.mock(TranslationExecutionPlanCompiler.class);
        GatewayRequestFeatureService gatewayRequestFeatureService = Mockito.mock(GatewayRequestFeatureService.class);
        GatewayResourceExecutionService gatewayResourceExecutionService = Mockito.mock(GatewayResourceExecutionService.class);
        AdminResourceExecutionService service = new AdminResourceExecutionService(
                translationExecutionPlanCompiler,
                gatewayRequestFeatureService,
                gatewayResourceExecutionService
        );

        GatewayRequestSemantics semantics = new GatewayRequestSemantics(
                TranslationResourceType.FILE,
                TranslationOperation.FILE_CONTENT_GET,
                "files",
                "/v1/files/{fileId}/content",
                List.of(),
                false
        );
        CanonicalExecutionPlan plan = new CanonicalExecutionPlan(
                true,
                CanonicalIngressProtocol.OPENAI,
                "/v1/files/file_123/content",
                "/v1/files/{fileId}/content",
                "files",
                "gpt-4o-mini",
                "gpt-4o-mini",
                "gpt-4o-mini",
                TranslationResourceType.FILE,
                TranslationOperation.FILE_CONTENT_GET,
                ExecutionKind.NATIVE,
                ExecutionBackend.NATIVE,
                SupportStatus.NATIVE,
                null,
                List.of(ExecutionBackend.NATIVE),
                "test",
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                List.of(),
                List.of(),
                Map.of(),
                List.of(),
                List.of()
        );
        CanonicalResourceResponse canonicalResponse = new CanonicalResourceResponse(
                TranslationResourceType.FILE,
                TranslationOperation.FILE_CONTENT_GET,
                "binary",
                "file.content",
                "file_123",
                "completed",
                List.of(),
                List.of(),
                null,
                3,
                Map.of("contentType", "application/pdf")
        );

        Mockito.when(gatewayRequestFeatureService.describe(eq("GET"), eq("/v1/files/file_123/content"), any()))
                .thenReturn(semantics);
        Mockito.when(gatewayRequestFeatureService.normalizePath("/v1/files/file_123/content"))
                .thenReturn("/v1/files/{fileId}/content");
        Mockito.when(translationExecutionPlanCompiler.compilePreview(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new CanonicalExecutionPlanCompilation(plan, null, semantics, null));
        Mockito.when(gatewayResourceExecutionService.executeDetailedBinaryJson(any(), eq("gpt-4o-mini")))
                .thenReturn(GatewayResourceExecutionResult.binary(
                        "req-file-content-1",
                        "file_123",
                        ResponseEntity.ok()
                                .contentType(MediaType.APPLICATION_PDF)
                                .body(new byte[] {1, 2, 3}),
                        canonicalResponse
                ));

        AdminResourceExecuteResponse response = service.execute(new AdminResourceExecuteRequest(
                "sk-gw-test",
                "openai",
                "GET",
                "/v1/files/file_123/content",
                "gpt-4o-mini",
                objectMapper.createObjectNode(),
                Map.of(),
                List.of()
        ));

        assertEquals(200, response.statusCode());
        assertEquals("req-file-content-1", response.requestId());
        assertEquals("file_123", response.gatewayResourceKey());
        assertEquals("application/pdf", response.contentType());
        assertEquals(3, response.binaryLength());
        assertEquals("binary", response.canonicalResponse().responseKind());
        assertEquals("file.content", response.canonicalResponse().objectType());
        assertEquals("file_123", response.canonicalResponse().objectId());
    }
}
