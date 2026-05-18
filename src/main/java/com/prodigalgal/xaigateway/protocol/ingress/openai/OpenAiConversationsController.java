package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceService;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/v1/conversations")
public class OpenAiConversationsController {

    private final DistributedKeyAuthenticationService distributedKeyAuthenticationService;
    private final GatewayAsyncResourceService gatewayAsyncResourceService;

    public OpenAiConversationsController(
            DistributedKeyAuthenticationService distributedKeyAuthenticationService,
            GatewayAsyncResourceService gatewayAsyncResourceService) {
        this.distributedKeyAuthenticationService = distributedKeyAuthenticationService;
        this.gatewayAsyncResourceService = gatewayAsyncResourceService;
    }

    @PostMapping
    public JsonNode createConversation(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody(required = false) JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.createConversation(distributedKey.id(), requestBody);
    }

    @GetMapping("/{conversationId}")
    public JsonNode getConversation(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String conversationId) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.getConversation(conversationId, distributedKey.id());
    }

    @PostMapping("/{conversationId}")
    public JsonNode updateConversation(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String conversationId,
            @RequestBody(required = false) JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.updateConversation(conversationId, distributedKey.id(), requestBody);
    }

    @DeleteMapping("/{conversationId}")
    public JsonNode deleteConversation(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String conversationId) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.deleteConversation(conversationId, distributedKey.id());
    }

    @PostMapping("/{conversationId}/items")
    public JsonNode createItems(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String conversationId,
            @RequestParam(required = false) List<String> include,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.createConversationItems(
                conversationId,
                distributedKey.id(),
                requestBody,
                include
        );
    }

    @GetMapping("/{conversationId}/items")
    public JsonNode listItems(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String conversationId,
            @RequestParam(required = false) String after,
            @RequestParam(required = false) List<String> include,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String order) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.listConversationItems(
                conversationId,
                distributedKey.id(),
                after,
                include,
                limit,
                order
        );
    }

    @GetMapping("/{conversationId}/items/{itemId}")
    public JsonNode getItem(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String conversationId,
            @PathVariable String itemId,
            @RequestParam(required = false) List<String> include) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.getConversationItem(
                conversationId,
                itemId,
                distributedKey.id(),
                include
        );
    }

    @DeleteMapping("/{conversationId}/items/{itemId}")
    public JsonNode deleteItem(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String conversationId,
            @PathVariable String itemId) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.deleteConversationItem(conversationId, itemId, distributedKey.id());
    }
}
