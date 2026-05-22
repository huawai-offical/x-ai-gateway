package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.ModelPolicyAdminService;
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
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/model-policies")
public class ModelPolicyAdminController {

    private final ModelPolicyAdminService modelPolicyAdminService;

    public ModelPolicyAdminController(ModelPolicyAdminService modelPolicyAdminService) {
        this.modelPolicyAdminService = modelPolicyAdminService;
    }

    @GetMapping
    public List<ModelPolicyResponse> list() {
        return modelPolicyAdminService.list();
    }

    @PostMapping
    public ModelPolicyResponse create(@Valid @RequestBody ModelPolicyRequest request) {
        return modelPolicyAdminService.create(request);
    }

    @PutMapping("/{id}")
    public ModelPolicyResponse update(@PathVariable Long id, @Valid @RequestBody ModelPolicyRequest request) {
        return modelPolicyAdminService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        modelPolicyAdminService.delete(id);
    }

    @PostMapping("/preview")
    public ModelPolicyPreviewResponse preview(@Valid @RequestBody ModelPolicyPreviewRequest request) {
        return modelPolicyAdminService.preview(request);
    }

    @GetMapping("/conflicts")
    public List<ModelPolicyConflictResponse> conflicts() {
        return modelPolicyAdminService.conflicts();
    }
}
