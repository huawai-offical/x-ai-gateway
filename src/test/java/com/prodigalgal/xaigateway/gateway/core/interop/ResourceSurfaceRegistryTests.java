package com.prodigalgal.xaigateway.gateway.core.interop;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceSurfaceRegistryTests {

    @Test
    void shouldResolveStaticResourceSurfaceFromSingleRegistry() {
        ResourceSurfaceDefinition uploadPart = ResourceSurfaceRegistry
                .find("POST", "/v1/uploads/{uploadId}/parts")
                .orElseThrow();

        assertEquals("upload_part_add", uploadPart.key());
        assertEquals(TranslationResourceType.UPLOAD, uploadPart.resourceType());
        assertEquals(TranslationOperation.UPLOAD_PART_ADD, uploadPart.operation());
        assertEquals(List.of(InteropFeature.UPLOAD_CREATE, InteropFeature.FILE_OBJECT), uploadPart.requiredFeatures());
        assertEquals(RouteSelectionMode.STORED_LINEAGE, uploadPart.routeSelectionMode());
        assertTrue(uploadPart.providerSurface());
    }

    @Test
    void shouldProvideDefaultSurfacePathAndModelFromRegistry() {
        assertEquals("images", ResourceSurfaceRegistry.defaultSurface(
                TranslationResourceType.IMAGE,
                TranslationOperation.IMAGE_EDIT
        ));
        assertEquals("/v1/images/edits", ResourceSurfaceRegistry.defaultNormalizedPath(
                TranslationResourceType.IMAGE,
                TranslationOperation.IMAGE_EDIT
        ));
        assertEquals("gpt-image-1", ResourceSurfaceRegistry.defaultModel(TranslationOperation.IMAGE_EDIT).orElseThrow());
        assertEquals("resource-orchestration", ResourceSurfaceRegistry.defaultModel(TranslationOperation.FILE_GET).orElseThrow());
    }

    @Test
    void shouldExposeProviderSurfaceKeysInStableOrder() {
        List<String> keys = ResourceSurfaceRegistry.providerSurfaces().stream()
                .map(ResourceSurfaceDefinition::key)
                .toList();

        assertEquals(List.of(
                "chat_completion",
                "response_create",
                "embedding_create",
                "audio_transcription",
                "audio_translation",
                "image_generation",
                "image_edit",
                "image_variation",
                "moderation_create",
                "file_create",
                "file_list",
                "file_get",
                "file_content_get",
                "file_delete",
                "upload_create",
                "upload_get",
                "upload_part_add",
                "upload_complete",
                "upload_cancel",
                "rerank_create",
                "video_generation_create",
                "music_generation_create",
                "web_search_create"
        ), keys);
        assertTrue(ResourceSurfaceRegistry.capabilityOverviewFeatures().contains(InteropFeature.AUDIO_SPEECH));
        assertFalse(ResourceSurfaceRegistry.capabilityOverviewFeatures().contains(InteropFeature.ASYNC_TASK));
    }
}
