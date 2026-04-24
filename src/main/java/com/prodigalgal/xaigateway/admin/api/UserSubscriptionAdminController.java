package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.UserSubscriptionAdminService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/subscriptions")
public class UserSubscriptionAdminController {

    private final UserSubscriptionAdminService userSubscriptionAdminService;

    public UserSubscriptionAdminController(UserSubscriptionAdminService userSubscriptionAdminService) {
        this.userSubscriptionAdminService = userSubscriptionAdminService;
    }

    @GetMapping
    public List<UserSubscriptionResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long planId) {
        return userSubscriptionAdminService.list(status, userId, planId);
    }

    @GetMapping("/{id}")
    public UserSubscriptionResponse get(@PathVariable Long id) {
        return userSubscriptionAdminService.get(id);
    }

    @PostMapping
    public UserSubscriptionResponse create(@Valid @RequestBody UserSubscriptionRequest request) {
        return userSubscriptionAdminService.create(request);
    }

    @PutMapping("/{id}")
    public UserSubscriptionResponse update(@PathVariable Long id, @Valid @RequestBody UserSubscriptionRequest request) {
        return userSubscriptionAdminService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        userSubscriptionAdminService.delete(id);
    }
}
