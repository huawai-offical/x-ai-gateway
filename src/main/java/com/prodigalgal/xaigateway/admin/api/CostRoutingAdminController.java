package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.CostRoutingService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/cost-routing")
public class CostRoutingAdminController {

    private final CostRoutingService costRoutingService;

    public CostRoutingAdminController(CostRoutingService costRoutingService) {
        this.costRoutingService = costRoutingService;
    }

    @GetMapping("/models")
    public List<CostModelResponse> models() {
        return costRoutingService.listModels();
    }

    @PostMapping("/models")
    public CostModelResponse createModel(@RequestBody CostModelRequest request) {
        return costRoutingService.saveModel(null, request);
    }

    @PutMapping("/models/{id}")
    public CostModelResponse updateModel(@PathVariable Long id, @RequestBody CostModelRequest request) {
        return costRoutingService.saveModel(id, request);
    }

    @DeleteMapping("/models/{id}")
    public void deleteModel(@PathVariable Long id) {
        costRoutingService.deleteModel(id);
    }

    @PostMapping("/estimate")
    public CostEstimateResponse estimate(@RequestBody CostEstimateRequest request) {
        return costRoutingService.estimate(request);
    }

    @GetMapping("/summary")
    public CostSummaryResponse summary() {
        return costRoutingService.summary();
    }
}
