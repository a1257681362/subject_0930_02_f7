package com.evops.aqua.growth.controller;

import com.evops.aqua.growth.dto.GrowthSampleReviewRequest;
import com.evops.aqua.growth.dto.GrowthTaskConfirmRequest;
import com.evops.aqua.growth.dto.GrowthTaskEntryRequest;
import com.evops.aqua.growth.dto.GrowthTaskSubmitRequest;
import com.evops.aqua.growth.entity.GrowthChangeEvidence;
import com.evops.aqua.growth.entity.GrowthHarvestForecast;
import com.evops.aqua.growth.entity.GrowthMeasureSample;
import com.evops.aqua.growth.entity.GrowthMeasureTask;
import com.evops.aqua.growth.service.GrowthEvidenceService;
import com.evops.aqua.growth.service.GrowthMeasureTaskService;
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
 * 生长测量任务：现场录入计算均值、异常样本复核标记（不删除）、
 * 提交、主管确认后更新预测出池量、反查抽样方案、证据追溯。
 */
@RestController
@RequestMapping("/api/aqua/growth")
public class GrowthMeasureTaskController {

    private final GrowthMeasureTaskService taskService;
    private final GrowthEvidenceService evidenceService;

    public GrowthMeasureTaskController(GrowthMeasureTaskService taskService,
                                       GrowthEvidenceService evidenceService) {
        this.taskService = taskService;
        this.evidenceService = evidenceService;
    }

    @GetMapping("/tasks")
    public ApiResponse<List<GrowthMeasureTask>> listTasks(@RequestParam(required = false) Long pondId,
                                                          @RequestParam(required = false) Long batchId,
                                                          @RequestParam(required = false) Long planId,
                                                          @RequestParam(required = false) String status) {
        return ApiResponse.ok(taskService.listTasks(pondId, batchId, planId, status));
    }

    @GetMapping("/tasks/{id}")
    public ApiResponse<Map<String, Object>> taskDetail(@PathVariable Long id) {
        return ApiResponse.ok(taskService.taskDetail(id));
    }

    /** 现场录入：设备版本+样本列表，冻结系数快照并计算均值；超偏差样本自动标记疑似异常 */
    @PostMapping("/tasks/{id}/entry")
    public ApiResponse<GrowthMeasureTask> entry(@PathVariable Long id,
                                                @Validated @RequestBody GrowthTaskEntryRequest request) {
        return ApiResponse.ok(taskService.entry(id, request));
    }

    /** 样本复核：异常值只能标记并填写复核理由，不能删除；复核后重算均值 */
    @PostMapping("/tasks/{id}/samples/{sampleId}/review")
    public ApiResponse<GrowthMeasureSample> reviewSample(@PathVariable Long id,
                                                         @PathVariable Long sampleId,
                                                         @Validated @RequestBody GrowthSampleReviewRequest request) {
        return ApiResponse.ok(taskService.reviewSample(id, sampleId, request));
    }

    /** 提交：存在未复核疑似异常样本时拒绝 */
    @PostMapping("/tasks/{id}/submit")
    public ApiResponse<GrowthMeasureTask> submit(@PathVariable Long id,
                                                 @Validated @RequestBody GrowthTaskSubmitRequest request) {
        return ApiResponse.ok(taskService.submit(id, request.getSubmittedBy(), request.getRequestId()));
    }

    /** 主管确认：确认人与确认意见必填；确认后才更新批次预测出池量 */
    @PostMapping("/tasks/{id}/confirm")
    public ApiResponse<GrowthMeasureTask> confirm(@PathVariable Long id,
                                                  @Validated @RequestBody GrowthTaskConfirmRequest request) {
        return ApiResponse.ok(taskService.confirm(id, request));
    }

    /** 批次当前预测出池量：仅主管确认后才有值 */
    @GetMapping("/batches/{batchId}/forecast")
    public ApiResponse<GrowthHarvestForecast> forecast(@PathVariable Long batchId) {
        return ApiResponse.ok(taskService.forecastOf(batchId));
    }

    /** 从测量记录反查抽样方案：定位原单、处理人及异常原因 */
    @GetMapping("/tasks/{id}/trace")
    public ApiResponse<Map<String, Object>> trace(@PathVariable Long id) {
        return ApiResponse.ok(taskService.trace(id));
    }

    /** 修改证据查询：可按对象或幂等请求号追溯 */
    @GetMapping("/evidence")
    public ApiResponse<List<GrowthChangeEvidence>> evidence(@RequestParam(required = false) String entityType,
                                                            @RequestParam(required = false) Long entityId,
                                                            @RequestParam(required = false) String requestId) {
        return ApiResponse.ok(evidenceService.list(entityType, entityId, requestId));
    }
}
