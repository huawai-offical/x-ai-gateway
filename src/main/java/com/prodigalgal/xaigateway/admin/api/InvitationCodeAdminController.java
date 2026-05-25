package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.InvitationCodeAdminService;
import com.prodigalgal.xaigateway.admin.application.InvitationGrowthService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/invitation-codes")
public class InvitationCodeAdminController {

    private final InvitationCodeAdminService invitationCodeAdminService;
    private final InvitationGrowthService invitationGrowthService;

    public InvitationCodeAdminController(
            InvitationCodeAdminService invitationCodeAdminService,
            InvitationGrowthService invitationGrowthService) {
        this.invitationCodeAdminService = invitationCodeAdminService;
        this.invitationGrowthService = invitationGrowthService;
    }

    @GetMapping
    public List<InvitationCodeResponse> listCodes(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active) {
        return invitationCodeAdminService.listCodes(keyword, active);
    }

    @PostMapping
    public List<InvitationCodeResponse> createCodes(@RequestBody InvitationCodeBatchRequest request) {
        return invitationCodeAdminService.createCodes(request);
    }

    @PutMapping("/{id}")
    public InvitationCodeResponse updateCode(
            @PathVariable Long id,
            @RequestBody InvitationCodeUpdateRequest request) {
        return invitationCodeAdminService.updateCode(id, request);
    }

    @DeleteMapping("/{id}")
    public void deleteCode(@PathVariable Long id) {
        invitationCodeAdminService.deleteCode(id);
    }

    @GetMapping("/{id}/usages")
    public List<InvitationCodeUsageResponse> listUsages(@PathVariable Long id) {
        return invitationCodeAdminService.listUsages(id);
    }

    @GetMapping("/leaderboard")
    public List<InvitationLeaderboardEntryResponse> leaderboard(@RequestParam(required = false) Integer limit) {
        return invitationGrowthService.leaderboard(limit == null ? 20 : limit);
    }

    @GetMapping("/tree/{userId}")
    public InvitationTreeNodeResponse tree(
            @PathVariable Long userId,
            @RequestParam(required = false) Integer maxDepth) {
        return invitationGrowthService.tree(userId, maxDepth == null ? 5 : maxDepth);
    }
}
