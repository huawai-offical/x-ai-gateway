package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/chat")
public class AdminChatExecuteController {

    private final GatewayChatExecutionService gatewayChatExecutionService;

    public AdminChatExecuteController(GatewayChatExecutionService gatewayChatExecutionService) {
        this.gatewayChatExecutionService = gatewayChatExecutionService;
    }

    @PostMapping("/execute")
    public AdminChatExecuteResponse execute(@Valid @RequestBody AdminChatExecuteRequest request) {
        return gatewayChatExecutionService.execute(request);
    }
}
