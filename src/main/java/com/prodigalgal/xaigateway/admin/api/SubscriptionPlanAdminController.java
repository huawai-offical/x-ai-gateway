package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.SubscriptionPlanAdminService;
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
@RequestMapping("/admin/plans")
public class SubscriptionPlanAdminController {

    private final SubscriptionPlanAdminService subscriptionPlanAdminService;

    public SubscriptionPlanAdminController(SubscriptionPlanAdminService subscriptionPlanAdminService) {
        this.subscriptionPlanAdminService = subscriptionPlanAdminService;
    }

    @GetMapping
    public List<SubscriptionPlanResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active) {
        return subscriptionPlanAdminService.list(keyword, active);
    }

    @GetMapping("/{id}")
    public SubscriptionPlanResponse get(@PathVariable Long id) {
        return subscriptionPlanAdminService.get(id);
    }

    @PostMapping
    public SubscriptionPlanResponse create(@Valid @RequestBody SubscriptionPlanRequest request) {
        return subscriptionPlanAdminService.create(request);
    }

    @PutMapping("/{id}")
    public SubscriptionPlanResponse update(@PathVariable Long id, @Valid @RequestBody SubscriptionPlanRequest request) {
        return subscriptionPlanAdminService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        subscriptionPlanAdminService.delete(id);
    }
}
