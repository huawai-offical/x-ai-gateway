package com.prodigalgal.xaigateway.gateway.core.credential;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService;
import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
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
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

class CredentialMaterialResolverTests {

    @Test
    void shouldResolveStoredCredentialAsUnifiedMaterial() {
        AccountSelectionService accountSelectionService = Mockito.mock(AccountSelectionService.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        Mockito.when(credentialCryptoService.decrypt("cipher")).thenReturn("secret-value");

        UpstreamCredentialEntity credential = new UpstreamCredentialEntity();
        credential.setProviderType(ProviderType.OPENAI_COMPATIBLE);
        credential.setBaseUrl("https://api.cohere.ai/compatibility/v1");
        credential.setApiKeyCiphertext("cipher");
        credential.setApiKeyFingerprint("fp-secret");
        credential.setCredentialMetadataJson("{\"projectId\":\"ignored\"}");

        CredentialMaterialResolver resolver = new CredentialMaterialResolver(
                accountSelectionService,
                credentialCryptoService,
                new ObjectMapper()
        );

        ResolvedCredentialMaterial material = resolver.resolveStored(credential);

        assertEquals(CredentialAuthKind.API_KEY, material.authKind());
        assertEquals("secret-value", material.secret());
        assertEquals("fp-secret", material.secretFingerprint());
        assertEquals("ignored", material.metadataString("projectId"));
    }

    @Test
    void shouldResolveAccountBackedTokenAsCredentialMaterial() {
        AccountSelectionService accountSelectionService = Mockito.mock(AccountSelectionService.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        Mockito.when(credentialCryptoService.decrypt("token-cipher")).thenReturn("ya29.token");
        Mockito.when(credentialCryptoService.fingerprint("ya29.token")).thenReturn("fp-token");

        UpstreamAccountEntity account = new UpstreamAccountEntity();
        account.setProviderType(UpstreamAccountProviderType.GEMINI_OAUTH);
        account.setAccessTokenCiphertext("token-cipher");
        account.setMetadataJson("{\"projectId\":\"proj-1\",\"location\":\"us-central1\"}");

        Mockito.when(accountSelectionService.resolveActiveAccount(
                        eq(1L),
                        eq(ProviderType.GEMINI_DIRECT),
                        eq(GatewayClientFamily.GENERIC_OPENAI),
                        anyInt()))
                .thenReturn(Optional.of(account));

        UpstreamCredentialEntity credential = new UpstreamCredentialEntity();
        credential.setProviderType(ProviderType.GEMINI_DIRECT);
        credential.setBaseUrl("https://aiplatform.googleapis.com/v1/projects/demo/locations/us-central1/endpoints/openapi");
        credential.setApiKeyCiphertext("fallback");
        credential.setApiKeyFingerprint("fp-fallback");
        credential.setAuthKind(CredentialAuthKind.API_KEY);
        credential.setCredentialMetadataJson("{\"location\":\"europe-west4\"}");

        CredentialMaterialResolver resolver = new CredentialMaterialResolver(
                accountSelectionService,
                credentialCryptoService,
                new ObjectMapper()
        );

        ResolvedCredentialMaterial material = resolver.resolve(selectionResult(), credential);

        assertEquals(CredentialAuthKind.GOOGLE_ACCESS_TOKEN, material.authKind());
        assertEquals("ya29.token", material.secret());
        assertEquals("fp-token", material.secretFingerprint());
        assertEquals("proj-1", material.projectId());
        assertEquals("us-central1", material.location());
        assertEquals("account", material.source());
    }

    private RouteSelectionResult selectionResult() {
        CatalogCandidateView candidate = new CatalogCandidateView(
                101L,
                "credential",
                ProviderType.GEMINI_DIRECT,
                1L,
                ProviderFamily.GEMINI,
                UpstreamSiteKind.VERTEX_AI,
                AuthStrategy.BEARER,
                PathStrategy.GEMINI_V1BETA_MODELS,
                ErrorSchemaStrategy.GEMINI_ERROR,
                "https://aiplatform.googleapis.com/v1/projects/demo/locations/us-central1/endpoints/openapi",
                "gemini-2.5-pro",
                "gemini-2.5-pro",
                List.of("google_native"),
                true,
                true,
                true,
                false,
                false,
                true,
                true,
                false,
                ReasoningTransport.GEMINI_THOUGHTS,
                InteropCapabilityLevel.NATIVE
        );
        RouteCandidateView routeCandidateView = new RouteCandidateView(candidate, 11L, 10, 100);
        return new RouteSelectionResult(
                1L,
                "sk-gw-test",
                "gemini-2.5-pro",
                "gemini-2.5-pro",
                "gemini-2.5-pro",
                "google_native",
                "prefix",
                "fingerprint",
                "gemini-2.5-pro",
                RouteSelectionSource.WEIGHTED_HASH,
                routeCandidateView,
                List.of(routeCandidateView)
        );
    }
}
