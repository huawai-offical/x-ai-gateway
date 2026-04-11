package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.ModelAliasAdminService;
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
@RequestMapping("/admin/model-aliases")
public class ModelAliasAdminController {

    private final ModelAliasAdminService modelAliasAdminService;

    public ModelAliasAdminController(ModelAliasAdminService modelAliasAdminService) {
        this.modelAliasAdminService = modelAliasAdminService;
    }

    @GetMapping
    public List<ModelAliasResponse> list() {
        return modelAliasAdminService.list();
    }

    @PostMapping
    public ModelAliasResponse create(@Valid @RequestBody ModelAliasRequest request) {
        return modelAliasAdminService.create(request);
    }

    @PostMapping("/preview")
    public ModelAliasPreviewResponse preview(@Valid @RequestBody ModelAliasPreviewRequest request) {
        return modelAliasAdminService.preview(request);
    }

    @PutMapping("/{id}")
    public ModelAliasResponse update(@PathVariable Long id, @Valid @RequestBody ModelAliasRequest request) {
        return modelAliasAdminService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        modelAliasAdminService.delete(id);
    }
}
