package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileResponse;
import com.prodigalgal.xaigateway.gateway.core.file.UpstreamFileImportService;
import com.prodigalgal.xaigateway.gateway.core.file.UpstreamImportedFileResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/files")
public class GatewayFileImportController {

    private final UpstreamFileImportService upstreamFileImportService;

    public GatewayFileImportController(UpstreamFileImportService upstreamFileImportService) {
        this.upstreamFileImportService = upstreamFileImportService;
    }

    @PostMapping("/import")
    public UpstreamImportedFileResponse importFile(@Valid @RequestBody GatewayFileImportRequest request) {
        return upstreamFileImportService.importExternalReference(
                request.distributedKeyId(),
                request.providerType(),
                request.credentialId(),
                request.externalFileId(),
                request.externalFilename(),
                request.mimeType(),
                request.purpose()
        );
    }

    @PostMapping("/{fileKey}/sync")
    public GatewayFileResponse syncFile(@PathVariable String fileKey) {
        return upstreamFileImportService.syncImportedFile(fileKey);
    }
}
