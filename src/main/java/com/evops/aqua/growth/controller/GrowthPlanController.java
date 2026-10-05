package com.evops.aqua.growth.controller;

import com.evops.aqua.growth.dto.GrowthPlanActionRequest;
import com.evops.aqua.growth.dto.GrowthPlanCreateRequest;
import com.evops.aqua.growth.dto.GrowthTaskGenerateRequest;
import com.evops.aqua.growth.entity.GrowthSamplingPlan;
import com.evops.aqua.growth.service.GrowthPlanService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 生长抽样方案：按池塘和批次配置抽样数量、测量频次及允许偏差；按频次生成测量任务。
 */
@RestController
@RequestMapping("/api/aqua/growth/plans")
public class GrowthPlanController {

    private final GrowthPlanService planService;

    public GrowthPlanController(GrowthPlanService planService) {
        this.planService = planService;
    }

    /** 配置抽样方案：requestId 幂等；同批次只允许一个生效方案 */
    @PostMapping
    public ApiResponse<GrowthSamplingPlan> create(@Validated @RequestBody GrowthPlanCreateRequest request) {
        return ApiResponse.ok(planService.create(request));
    }

    @GetMapping
    public ApiResponse<List<GrowthSamplingPlan>> list(@RequestParam(required = false) Long pondId,
                                                      @RequestParam(required = false) Long batchId,
                                                      @RequestParam(required = false) String status) {
        return ApiResponse.ok(planService.list(pondId, batchId, status));
    }

    /** 作废方案：不再生成新任务，已生成任务不受影响 */
    @PostMapping("/{id}/cancel")
    public ApiResponse<GrowthSamplingPlan> cancel(@PathVariable Long id,
                                                  @Validated @RequestBody GrowthPlanActionRequest request) {
        return ApiResponse.ok(planService.cancel(id, request.getOperator(), request.getRequestId()));
    }

    /** 按频次生成测量任务：同计划同池同日幂等，任务冻结方案快照 */
    @PostMapping("/{id}/tasks/generate")
    public ApiResponse<Map<String, Object>> generateTasks(@PathVariable Long id,
                                                          @Validated @RequestBody GrowthTaskGenerateRequest request) {
        return ApiResponse.ok(planService.generateTasks(id, request));
    }
}
