package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileBindingResponse;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileBindingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/files/{fileKey}/bindings")
public class GatewayFileBindingController {

    private final GatewayFileBindingService gatewayFileBindingService;

    public GatewayFileBindingController(GatewayFileBindingService gatewayFileBindingService) {
        this.gatewayFileBindingService = gatewayFileBindingService;
    }

    @GetMapping
    public List<GatewayFileBindingResponse> list(@PathVariable String fileKey) {
        return gatewayFileBindingService.listBindings(fileKey);
    }

    @PostMapping
    public GatewayFileBindingResponse create(
            @PathVariable String fileKey,
            @Valid @RequestBody GatewayFileBindingRequest request) {
        return gatewayFileBindingService.createBinding(
                fileKey,
                request.providerType(),
                request.credentialId(),
                request.externalFileId(),
                request.externalFilename()
        );
    }

    @DeleteMapping("/{bindingId}")
    public void delete(
            @PathVariable String fileKey,
            @PathVariable Long bindingId) {
        gatewayFileBindingService.deleteBinding(fileKey, bindingId);
    }
}
