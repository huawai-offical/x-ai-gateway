package com.prodigalgal.xaigateway.provider.adapter.gemini;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import io.micrometer.observation.ObservationRegistry;
import java.time.Instant;
import java.util.Date;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Service;

@Service
public class GeminiChatModelFactory {

    private final ObservationRegistry observationRegistry;

    public GeminiChatModelFactory(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    public GoogleGenAiChatModel create(String baseUrl, String apiKey, GoogleGenAiChatOptions options) {
        Client client = createClient(baseUrl, apiKey);
        return GoogleGenAiChatModel.builder()
                .genAiClient(client)
                .defaultOptions(options)
                .observationRegistry(observationRegistry)
                .build();
    }

    public Client createClient(String baseUrl, String apiKey) {
        HttpOptions.Builder httpOptionsBuilder = HttpOptions.builder();
        if (baseUrl != null && !baseUrl.isBlank()) {
            httpOptionsBuilder.baseUrl(baseUrl);
        }
        return Client.builder()
                .apiKey(apiKey)
                .vertexAI(false)
                .httpOptions(httpOptionsBuilder.build())
                .build();
    }

    public GoogleGenAiChatModel create(
            UpstreamSiteKind siteKind,
            String baseUrl,
            ResolvedCredentialMaterial credentialMaterial,
            GoogleGenAiChatOptions options) {
        Client client = createClient(siteKind, baseUrl, credentialMaterial);
        return GoogleGenAiChatModel.builder()
                .genAiClient(client)
                .defaultOptions(options)
                .observationRegistry(observationRegistry)
                .build();
    }

    public Client createClient(
            UpstreamSiteKind siteKind,
            String baseUrl,
            ResolvedCredentialMaterial credentialMaterial) {
        if (siteKind == UpstreamSiteKind.VERTEX_AI) {
            String projectId = requireMetadata(credentialMaterial.projectId(), "projectId");
            String location = requireMetadata(credentialMaterial.location(), "location");
            GoogleCredentials credentials = GoogleCredentials.create(new AccessToken(
                    credentialMaterial.secret(),
                    Date.from(Instant.now().plusSeconds(3000))
            ));
            return Client.builder()
                    .credentials(credentials)
                    .project(projectId)
                    .location(location)
                    .vertexAI(true)
                    .build();
        }
        return createClient(baseUrl, credentialMaterial.secret());
    }

    private String requireMetadata(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Vertex 凭证缺少必需 metadata：" + key);
        }
        return value;
    }
}
