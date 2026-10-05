package com.evops.aqua.growth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.growth.dto.GrowthPlanCreateRequest;
import com.evops.aqua.growth.dto.GrowthTaskGenerateRequest;
import com.evops.aqua.growth.entity.GrowthChangeEvidence;
import com.evops.aqua.growth.entity.GrowthMeasureTask;
import com.evops.aqua.growth.entity.GrowthSamplingPlan;
import com.evops.aqua.growth.mapper.GrowthMeasureTaskMapper;
import com.evops.aqua.growth.mapper.GrowthSamplingPlanMapper;
import com.evops.aqua.service.PondService;
import com.evops.aqua.service.SeedlingBatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生长抽样方案：按池塘和批次配置抽样数量、测量频次与允许偏差。
 * 同一批次同一时刻只允许一个 ACTIVE 方案；按频次生成测量任务，
 * 任务生成时冻结方案快照（抽样数量、允许偏差），同计划同池同日幂等。
 */
@Service
public class GrowthPlanService {

    private final GrowthSamplingPlanMapper planMapper;
    private final GrowthMeasureTaskMapper taskMapper;
    private final PondService pondService;
    private final SeedlingBatchService batchService;
    private final GrowthEvidenceService evidenceService;

    public GrowthPlanService(GrowthSamplingPlanMapper planMapper,
                             GrowthMeasureTaskMapper taskMapper,
                             PondService pondService,
                             SeedlingBatchService batchService,
                             GrowthEvidenceService evidenceService) {
        this.planMapper = planMapper;
        this.taskMapper = taskMapper;
        this.pondService = pondService;
        this.batchService = batchService;
        this.evidenceService = evidenceService;
    }

    /**
     * 配置抽样方案。幂等：同 requestId 重复配置返回首次方案。
     * 批次必须在该池且未出池；同批次已有 ACTIVE 方案时拒绝。
     */
    @Transactional(rollbackFor = Exception.class)
    public GrowthSamplingPlan create(GrowthPlanCreateRequest request) {
        GrowthSamplingPlan duplicated = planMapper.selectOne(new QueryWrapper<GrowthSamplingPlan>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        pondService.requireActive(pondService.getOrThrow(request.getPondId()));
        SeedlingBatch batch = batchService.getOrThrow(request.getBatchId());
        batchService.requireStocked(batch, "配置生长抽样方案");
        if (!batch.getPondId().equals(request.getPondId())) {
            throw new IllegalArgumentException("批次" + batch.getBatchNo() + "在池塘id=" + batch.getPondId()
                    + "，与方案池塘id=" + request.getPondId() + "不一致");
        }
        if (request.getSampleSize() == null || request.getSampleSize() <= 0) {
            throw new IllegalArgumentException("抽样数量必须为正整数");
        }
        if (request.getFrequencyDays() == null || request.getFrequencyDays() <= 0) {
            throw new IllegalArgumentException("测量频次（间隔天数）必须为正整数");
        }
        if (request.getAllowedDeviationPct() == null
                || request.getAllowedDeviationPct().signum() <= 0) {
            throw new IllegalArgumentException("允许偏差（百分比）必须为正数");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("方案结束日期不能早于开始日期");
        }
        Long active = planMapper.selectCount(new QueryWrapper<GrowthSamplingPlan>()
                .eq("batch_id", batch.getId()).eq("status", GrowthSamplingPlan.STATUS_ACTIVE));
        if (active != null && active > 0) {
            throw new IllegalArgumentException("批次" + batch.getBatchNo()
                    + "已存在生效中的抽样方案，同一批次同一时刻只允许一个生效方案，请先作废原方案");
        }

        GrowthSamplingPlan plan = new GrowthSamplingPlan();
        plan.setPlanNo(nextPlanNo(batch.getId()));
        plan.setPondId(request.getPondId());
        plan.setBatchId(batch.getId());
        plan.setSampleSize(request.getSampleSize());
        plan.setFrequencyDays(request.getFrequencyDays());
        plan.setAllowedDeviationPct(request.getAllowedDeviationPct());
        plan.setStartDate(request.getStartDate());
        plan.setEndDate(request.getEndDate());
        plan.setStatus(GrowthSamplingPlan.STATUS_ACTIVE);
        plan.setOperator(request.getOperator());
        plan.setRequestId(request.getRequestId());
        plan.setRemark(request.getRemark());
        planMapper.insert(plan);
        evidenceService.record(GrowthChangeEvidence.ENTITY_PLAN, plan.getId(),
                GrowthChangeEvidence.ACTION_PLAN_CREATE, request.getRequestId(), request.getOperator(),
                null, plan, "按池塘id=" + plan.getPondId() + "批次" + batch.getBatchNo()
                + "配置抽样方案：抽样" + plan.getSampleSize() + "尾，每" + plan.getFrequencyDays()
                + "天一次，允许偏差" + plan.getAllowedDeviationPct() + "%");
        return plan;
    }

    /**
     * 作废方案：不再生成新任务，已生成任务不受影响。
     */
    @Transactional(rollbackFor = Exception.class)
    public GrowthSamplingPlan cancel(Long planId, String operator, String requestId) {
        GrowthSamplingPlan plan = getOrThrow(planId);
        if (GrowthSamplingPlan.STATUS_CANCELLED.equals(plan.getStatus())) {
            return plan;
        }
        GrowthSamplingPlan before = copyOf(plan);
        plan.setStatus(GrowthSamplingPlan.STATUS_CANCELLED);
        planMapper.updateById(plan);
        evidenceService.record(GrowthChangeEvidence.ENTITY_PLAN, plan.getId(),
                GrowthChangeEvidence.ACTION_PLAN_CANCEL, requestId, operator, before, plan,
                "作废抽样方案" + plan.getPlanNo() + "，已生成测量任务不受影响");
        return plan;
    }

    /**
     * 按方案频次生成测量任务：区间内每隔 frequencyDays 天生成一条。
     * 幂等：同计划同池同日只生成一次，重复请求返回已有任务。
     * 任务生成时冻结方案快照（抽样数量、允许偏差）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generateTasks(Long planId, GrowthTaskGenerateRequest request) {
        GrowthSamplingPlan plan = getOrThrow(planId);
        if (!GrowthSamplingPlan.STATUS_ACTIVE.equals(plan.getStatus())) {
            throw new IllegalArgumentException("方案已作废，不能生成任务：" + plan.getPlanNo());
        }
        LocalDate start = request.getStartDate() == null ? plan.getStartDate() : request.getStartDate();
        LocalDate end = request.getEndDate() == null ? plan.getEndDate() : request.getEndDate();
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("生成区间结束日期不能早于开始日期");
        }

        List<GrowthMeasureTask> created = new ArrayList<>();
        List<GrowthMeasureTask> existed = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(plan.getFrequencyDays())) {
            List<GrowthMeasureTask> same = taskMapper.selectList(new QueryWrapper<GrowthMeasureTask>()
                    .eq("plan_id", plan.getId()).eq("task_date", date));
            if (!same.isEmpty()) {
                existed.add(same.get(0));
                continue;
            }
            GrowthMeasureTask task = new GrowthMeasureTask();
            task.setTaskNo("GT-" + plan.getId() + "-" + date);
            task.setPlanId(plan.getId());
            task.setPondId(plan.getPondId());
            task.setBatchId(plan.getBatchId());
            task.setTaskDate(date);
            task.setSampleSize(plan.getSampleSize());
            task.setAllowedDeviationPct(plan.getAllowedDeviationPct());
            task.setStatus(GrowthMeasureTask.STATUS_DRAFT);
            taskMapper.insert(task);
            created.add(task);
            evidenceService.record(GrowthChangeEvidence.ENTITY_TASK, task.getId(),
                    GrowthChangeEvidence.ACTION_TASK_GENERATE, request.getRequestId(), request.getOperator(),
                    null, task, "按方案" + plan.getPlanNo() + "生成" + date + "测量任务（冻结抽样"
                    + plan.getSampleSize() + "尾、允许偏差" + plan.getAllowedDeviationPct() + "%快照）");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("createdCount", created.size());
        result.put("existedCount", existed.size());
        result.put("created", created);
        result.put("existed", existed);
        return result;
    }

    public GrowthSamplingPlan getOrThrow(Long id) {
        GrowthSamplingPlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new IllegalArgumentException("生长抽样方案不存在，id=" + id);
        }
        return plan;
    }

    public List<GrowthSamplingPlan> list(Long pondId, Long batchId, String status) {
        QueryWrapper<GrowthSamplingPlan> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(batchId != null, "batch_id", batchId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return planMapper.selectList(wrapper);
    }

    private String nextPlanNo(Long batchId) {
        Long count = planMapper.selectCount(new QueryWrapper<GrowthSamplingPlan>()
                .eq("batch_id", batchId));
        return "GP-" + batchId + "-" + (count + 1);
    }

    private GrowthSamplingPlan copyOf(GrowthSamplingPlan source) {
        GrowthSamplingPlan copy = new GrowthSamplingPlan();
        org.springframework.beans.BeanUtils.copyProperties(source, copy);
        return copy;
    }
}
