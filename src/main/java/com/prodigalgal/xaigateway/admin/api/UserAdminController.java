package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.UserAdminService;
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
@RequestMapping("/admin/users")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    public List<GatewayUserResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active) {
        return userAdminService.list(keyword, active);
    }

    @GetMapping("/{id}")
    public GatewayUserResponse get(@PathVariable Long id) {
        return userAdminService.get(id);
    }

    @PostMapping
    public GatewayUserResponse create(@Valid @RequestBody GatewayUserRequest request) {
        return userAdminService.create(request);
    }

    @PutMapping("/{id}")
    public GatewayUserResponse update(@PathVariable Long id, @Valid @RequestBody GatewayUserRequest request) {
        return userAdminService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        userAdminService.delete(id);
    }
}
