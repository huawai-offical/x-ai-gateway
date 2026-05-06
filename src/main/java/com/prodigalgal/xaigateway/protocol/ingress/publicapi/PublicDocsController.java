package com.prodigalgal.xaigateway.protocol.ingress.publicapi;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/public/docs")
public class PublicDocsController {

    private final PublicDocsBundleService publicDocsBundleService;

    public PublicDocsController(PublicDocsBundleService publicDocsBundleService) {
        this.publicDocsBundleService = publicDocsBundleService;
    }

    @GetMapping("/compatibility")
    public PublicDocsBundleResponse compatibility(@RequestParam(defaultValue = "zh-CN") String locale) {
        return publicDocsBundleService.bundle(locale);
    }

    @GetMapping("/openapi.json")
    public JsonNode openApi() {
        return publicDocsBundleService.openApi();
    }
}
