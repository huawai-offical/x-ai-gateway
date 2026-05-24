package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LosslessTranslationMatrixServiceTests {

    private final LosslessTranslationMatrixService service = new LosslessTranslationMatrixService();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldAllowOnlyDeclaredLosslessConversationAttributes() {
        LosslessTranslationMatrixEntry role = service.classify(
                CanonicalIngressProtocol.OPENAI,
                CanonicalIngressProtocol.ANTHROPIC_NATIVE,
                TranslationResourceType.CHAT,
                TranslationOperation.CHAT_COMPLETION,
                "message.role"
        );
        LosslessTranslationMatrixEntry text = service.classify(
                CanonicalIngressProtocol.GOOGLE_NATIVE,
                CanonicalIngressProtocol.OPENAI,
                TranslationResourceType.CHAT,
                TranslationOperation.CHAT_COMPLETION,
                "content.text"
        );

        assertEquals(LosslessTranslationSupport.LOSSLESS, role.support());
        assertEquals(LosslessTranslationSupport.LOSSLESS, text.support());
        assertTrue(role.canTranslateLosslessly());
        assertFalse(text.mustFailWhenRequestedAsTranslation());
    }

    @Test
    void shouldRequireNativeRouteForOpaqueProviderState() {
        LosslessTranslationMatrixEntry compact = service.classify(
                CanonicalIngressProtocol.RESPONSES,
                CanonicalIngressProtocol.ANTHROPIC_NATIVE,
                TranslationResourceType.RESPONSE,
                TranslationOperation.RESPONSE_CREATE,
                "response.compaction"
        );
        LosslessTranslationMatrixEntry encryptedReasoning = service.classify(
                CanonicalIngressProtocol.ANTHROPIC_NATIVE,
                CanonicalIngressProtocol.OPENAI,
                TranslationResourceType.CHAT,
                TranslationOperation.CHAT_COMPLETION,
                "reasoning.encrypted_content"
        );
        LosslessTranslationMatrixEntry providerFileId = service.classify(
                CanonicalIngressProtocol.OPENAI,
                CanonicalIngressProtocol.GOOGLE_NATIVE,
                TranslationResourceType.CHAT,
                TranslationOperation.CHAT_COMPLETION,
                "content.file.provider_file_id"
        );

        assertEquals(LosslessTranslationSupport.NATIVE_REQUIRED, compact.support());
        assertEquals("native_compaction_required", compact.failureCode());
        assertEquals(LosslessTranslationSupport.NATIVE_REQUIRED, encryptedReasoning.support());
        assertEquals(LosslessTranslationSupport.NATIVE_REQUIRED, providerFileId.support());
        assertTrue(compact.mustFailWhenRequestedAsTranslation());
        assertTrue(encryptedReasoning.mustFailWhenRequestedAsTranslation());
        assertTrue(providerFileId.mustFailWhenRequestedAsTranslation());
    }

    @Test
    void shouldBlockUndeclaredAttributesByDefault() {
        LosslessTranslationMatrixEntry unknown = service.classify(
                CanonicalIngressProtocol.OPENAI,
                CanonicalIngressProtocol.GOOGLE_NATIVE,
                TranslationResourceType.CHAT,
                TranslationOperation.CHAT_COMPLETION,
                "metadata.partial_marker"
        );

        assertEquals(LosslessTranslationSupport.UNSUPPORTED, unknown.support());
        assertEquals("unsupported_translation_attribute", unknown.failureCode());
        assertTrue(unknown.mustFailWhenRequestedAsTranslation());
    }

    @Test
    void shouldTreatSameProtocolAsNativeRouteInsteadOfTranslation() {
        LosslessTranslationMatrixEntry sameProtocol = service.classify(
                CanonicalIngressProtocol.OPENAI,
                CanonicalIngressProtocol.OPENAI,
                TranslationResourceType.CHAT,
                TranslationOperation.CHAT_COMPLETION,
                "content.text"
        );

        assertEquals(LosslessTranslationSupport.NATIVE_REQUIRED, sameProtocol.support());
        assertEquals("native_route_required", sameProtocol.failureCode());
    }

    @Test
    void shouldExpandRequestSemanticsIntoAttributeMatrixEntries() {
        GatewayRequestSemantics semantics = new GatewayRequestSemantics(
                TranslationResourceType.CHAT,
                TranslationOperation.CHAT_COMPLETION,
                List.of(InteropFeature.CHAT_TEXT, InteropFeature.REASONING, InteropFeature.FILE_INPUT),
                true
        );

        List<LosslessTranslationMatrixEntry> entries = service.entriesForSemantics(
                CanonicalIngressProtocol.OPENAI,
                CanonicalIngressProtocol.ANTHROPIC_NATIVE,
                semantics
        );

        assertTrue(entries.stream().anyMatch(entry -> entry.attributePath().equals("message.role")
                && entry.support() == LosslessTranslationSupport.LOSSLESS));
        assertTrue(entries.stream().anyMatch(entry -> entry.attributePath().equals("reasoning.encrypted_content")
                && entry.support() == LosslessTranslationSupport.NATIVE_REQUIRED));
        assertTrue(entries.stream().anyMatch(entry -> entry.attributePath().equals("content.file.provider_file_id")
                && entry.support() == LosslessTranslationSupport.NATIVE_REQUIRED));
    }

    @Test
    void shouldExtractRequestBodyAttributesInsteadOfOnlyFeatureGroups() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", "gpt-4o");
        body.putArray("messages")
                .addObject()
                .put("role", "user")
                .putArray("content")
                .addObject()
                .put("type", "input_file")
                .putObject("input_file")
                .put("file_id", "file_123");
        GatewayRequestSemantics semantics = new GatewayRequestSemantics(
                TranslationResourceType.CHAT,
                TranslationOperation.CHAT_COMPLETION,
                List.of(InteropFeature.CHAT_TEXT, InteropFeature.FILE_INPUT),
                true
        );

        List<LosslessTranslationMatrixEntry> blockers = service.blockingEntriesForRequest(
                CanonicalIngressProtocol.OPENAI,
                CanonicalIngressProtocol.ANTHROPIC_NATIVE,
                semantics,
                body
        );

        assertTrue(blockers.stream().anyMatch(entry -> entry.attributePath().equals("content.file.provider_file_id")
                && entry.support() == LosslessTranslationSupport.NATIVE_REQUIRED));
        assertFalse(blockers.stream().anyMatch(entry -> entry.attributePath().equals("content.file.inline_data")));
    }

    @Test
    void shouldNotValidateSameProtocolNativeRouteAsTranslation() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", "gpt-4o");
        body.putArray("messages")
                .addObject()
                .put("role", "user")
                .put("content", "hello");
        GatewayRequestSemantics semantics = new GatewayRequestSemantics(
                TranslationResourceType.CHAT,
                TranslationOperation.CHAT_COMPLETION,
                List.of(InteropFeature.CHAT_TEXT),
                true
        );

        List<LosslessTranslationMatrixEntry> blockers = service.blockingEntriesForRequest(
                CanonicalIngressProtocol.OPENAI,
                CanonicalIngressProtocol.OPENAI,
                semantics,
                body
        );

        assertTrue(blockers.isEmpty());
    }

    @Test
    void shouldNotTreatNonConversationPromptAsChatTextAttribute() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", "imagen-3.0-capability-001");
        body.put("prompt", "edit this image");
        GatewayRequestSemantics semantics = new GatewayRequestSemantics(
                TranslationResourceType.IMAGE,
                TranslationOperation.IMAGE_EDIT,
                List.of(InteropFeature.IMAGE_EDIT),
                true
        );

        List<LosslessTranslationMatrixEntry> blockers = service.blockingEntriesForRequest(
                CanonicalIngressProtocol.OPENAI,
                CanonicalIngressProtocol.GOOGLE_NATIVE,
                semantics,
                body
        );

        assertEquals(List.of("image.edit.request"), blockers.stream()
                .map(LosslessTranslationMatrixEntry::attributePath)
                .toList());
        assertEquals("native_image_edit_required", blockers.get(0).failureCode());
    }

    @Test
    void shouldNeverUseLossyOrEmulatedTermsInMatrixSupport() {
        assertTrue(service.entries().stream()
                .map(entry -> entry.support().name())
                .noneMatch(value -> value.equals("LOSSY") || value.equals("EMULATED")));
    }
}
