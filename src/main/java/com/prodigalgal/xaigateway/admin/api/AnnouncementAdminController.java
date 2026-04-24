package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.AnnouncementAdminService;
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
@RequestMapping("/admin/announcements")
public class AnnouncementAdminController {

    private final AnnouncementAdminService announcementAdminService;

    public AnnouncementAdminController(AnnouncementAdminService announcementAdminService) {
        this.announcementAdminService = announcementAdminService;
    }

    @GetMapping
    public List<AnnouncementResponse> list(@RequestParam(required = false) String status) {
        return announcementAdminService.list(status);
    }

    @GetMapping("/{id}")
    public AnnouncementResponse get(@PathVariable Long id) {
        return announcementAdminService.get(id);
    }

    @PostMapping
    public AnnouncementResponse create(@Valid @RequestBody AnnouncementRequest request) {
        return announcementAdminService.create(request);
    }

    @PutMapping("/{id}")
    public AnnouncementResponse update(@PathVariable Long id, @Valid @RequestBody AnnouncementRequest request) {
        return announcementAdminService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        announcementAdminService.delete(id);
    }
}
