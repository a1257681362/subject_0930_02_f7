package com.evops.aqua.growth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.growth.dto.GrowthSampleInput;
import com.evops.aqua.growth.dto.GrowthSampleReviewRequest;
import com.evops.aqua.growth.dto.GrowthTaskConfirmRequest;
import com.evops.aqua.growth.dto.GrowthTaskEntryRequest;
import com.evops.aqua.growth.entity.GrowthChangeEvidence;
import com.evops.aqua.growth.entity.GrowthDeviceVersion;
import com.evops.aqua.growth.entity.GrowthHarvestForecast;
import com.evops.aqua.growth.entity.GrowthMeasureSample;
import com.evops.aqua.growth.entity.GrowthMeasureTask;
import com.evops.aqua.growth.entity.GrowthSamplingPlan;
import com.evops.aqua.growth.mapper.GrowthHarvestForecastMapper;
import com.evops.aqua.growth.mapper.GrowthMeasureSampleMapper;
import com.evops.aqua.growth.mapper.GrowthMeasureTaskMapper;
import com.evops.aqua.service.SeedlingBatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 生长测量任务：现场录入体重/长度计算均值；超允许偏差样本自动标记疑似异常。
 * 异常值不能静默删除——只能标记并填写复核理由，疑似/异常样本不计入均值。
 * 录入时冻结设备校正系数快照，历史测量不随新系数重算。
 * 预测出池量先算在任务上，主管确认后才写入批次预测。
 */
@Service
public class GrowthMeasureTaskService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal THOUSAND = new BigDecimal("1000");

    private final GrowthMeasureTaskMapper taskMapper;
    private final GrowthMeasureSampleMapper sampleMapper;
    private final GrowthHarvestForecastMapper forecastMapper;
    private final GrowthPlanService planService;
    private final GrowthDeviceVersionService deviceVersionService;
    private final SeedlingBatchService batchService;
    private final GrowthEvidenceService evidenceService;

    public GrowthMeasureTaskService(GrowthMeasureTaskMapper taskMapper,
                                    GrowthMeasureSampleMapper sampleMapper,
                                    GrowthHarvestForecastMapper forecastMapper,
                                    GrowthPlanService planService,
                                    GrowthDeviceVersionService deviceVersionService,
                                    SeedlingBatchService batchService,
                                    GrowthEvidenceService evidenceService) {
        this.taskMapper = taskMapper;
        this.sampleMapper = sampleMapper;
        this.forecastMapper = forecastMapper;
        this.planService = planService;
        this.deviceVersionService = deviceVersionService;
        this.batchService = batchService;
        this.evidenceService = evidenceService;
    }

    /**
     * 现场录入：设备版本 + 样本列表（体重/长度），样本数必须等于方案抽样数量。
     * 录入时冻结设备校正系数快照；按允许偏差自动标记疑似异常样本；
     * 均值只统计计入样本并套用冻结系数。幂等：同 requestId 重复录入返回原任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public GrowthMeasureTask entry(Long taskId, GrowthTaskEntryRequest request) {
        GrowthMeasureTask task = getOrThrow(taskId);
        if (!GrowthMeasureTask.STATUS_DRAFT.equals(task.getStatus())) {
            if (request.getRequestId() != null && request.getRequestId().equals(task.getRequestId())) {
                return task;
            }
            throw new IllegalArgumentException("任务已录入，不能重复录入，状态=" + task.getStatus()
                    + "；异常样本请走复核标记，不得删除重录");
        }
        GrowthDeviceVersion version = deviceVersionService.getOrThrow(request.getDeviceVersionId());
        deviceVersionService.requireActive(version);
        List<GrowthSampleInput> inputs = request.getSamples();
        if (inputs.size() != task.getSampleSize()) {
            throw new IllegalArgumentException("样本数量" + inputs.size() + "与方案抽样数量"
                    + task.getSampleSize() + "不一致，须按方案抽样数量录入");
        }

        GrowthMeasureTask before = copyOf(task);
        BigDecimal meanWeight = meanOf(inputs, true);
        BigDecimal meanLength = meanOf(inputs, false);
        int seq = 1;
        int suspects = 0;
        for (GrowthSampleInput input : inputs) {
            GrowthMeasureSample sample = new GrowthMeasureSample();
            sample.setTaskId(task.getId());
            sample.setSeqNo(seq++);
            sample.setWeightG(input.getWeightG());
            sample.setLengthCm(input.getLengthCm());
            boolean suspect = beyondDeviation(input.getWeightG(), meanWeight, task.getAllowedDeviationPct())
                    || beyondDeviation(input.getLengthCm(), meanLength, task.getAllowedDeviationPct());
            sample.setStatus(suspect ? GrowthMeasureSample.STATUS_SUSPECT : GrowthMeasureSample.STATUS_NORMAL);
            if (suspect) {
                suspects++;
            }
            sampleMapper.insert(sample);
        }

        task.setDeviceVersionId(version.getId());
        task.setDeviceCode(version.getDeviceCode());
        task.setDeviceVersionNo(version.getVersionNo());
        task.setWeightFactor(version.getWeightFactor());
        task.setLengthFactor(version.getLengthFactor());
        task.setEnteredBy(request.getEnteredBy());
        task.setEnteredTime(LocalDateTime.now());
        task.setRequestId(request.getRequestId());
        task.setStatus(GrowthMeasureTask.STATUS_MEASURED);
        recomputeAverages(task);
        taskMapper.updateById(task);
        evidenceService.record(GrowthChangeEvidence.ENTITY_TASK, task.getId(),
                GrowthChangeEvidence.ACTION_TASK_ENTRY, request.getRequestId(), request.getEnteredBy(),
                before, task, "现场录入" + inputs.size() + "尾样本，冻结设备" + version.getDeviceCode()
                + "版本" + version.getVersionNo() + "系数快照（体重×" + version.getWeightFactor()
                + "，长度×" + version.getLengthFactor() + "），自动标记疑似异常" + suspects + "尾");
        return task;
    }

    /**
     * 样本复核：异常值不能静默删除，只能标记并填写复核理由（理由必填，由参数校验保证）。
     * 复核为异常（ABNORMAL）不计入均值；复核为正常（REVIEWED_NORMAL）计入均值。
     * 提交前可反复复核，每次复核留证；提交后样本冻结。
     */
    @Transactional(rollbackFor = Exception.class)
    public GrowthMeasureSample reviewSample(Long taskId, Long sampleId, GrowthSampleReviewRequest request) {
        GrowthMeasureTask task = getOrThrow(taskId);
        if (!GrowthMeasureTask.STATUS_MEASURED.equals(task.getStatus())) {
            throw new IllegalArgumentException("仅已录入未提交的任务可复核样本，当前状态：" + task.getStatus());
        }
        GrowthMeasureSample sample = getSampleOrThrow(taskId, sampleId);
        String decision = request.getDecision() == null ? "" : request.getDecision().trim().toUpperCase();
        String newStatus;
        if ("ABNORMAL".equals(decision)) {
            newStatus = GrowthMeasureSample.STATUS_ABNORMAL;
        } else if ("NORMAL".equals(decision)) {
            newStatus = GrowthMeasureSample.STATUS_REVIEWED_NORMAL;
        } else {
            throw new IllegalArgumentException("复核结论只能是 ABNORMAL（标记异常）或 NORMAL（复核正常）：" + decision);
        }
        GrowthMeasureSample before = copyOf(sample);
        sample.setStatus(newStatus);
        sample.setReviewReason(request.getReviewReason());
        sample.setReviewedBy(request.getReviewedBy());
        sample.setReviewedTime(LocalDateTime.now());
        sampleMapper.updateById(sample);

        GrowthMeasureTask taskBefore = copyOf(task);
        recomputeAverages(task);
        taskMapper.updateById(task);
        evidenceService.record(GrowthChangeEvidence.ENTITY_SAMPLE, sample.getId(),
                GrowthChangeEvidence.ACTION_SAMPLE_REVIEW, request.getRequestId(), request.getReviewedBy(),
                before, sample, "样本第" + sample.getSeqNo() + "尾复核为"
                + ("NORMAL".equals(decision) ? "正常（计入均值）" : "异常（不计入均值，样本保留不删除）")
                + "，复核理由：" + request.getReviewReason());
        evidenceService.record(GrowthChangeEvidence.ENTITY_TASK, task.getId(),
                GrowthChangeEvidence.ACTION_SAMPLE_REVIEW, request.getRequestId(), request.getReviewedBy(),
                taskBefore, task, "样本复核后重算均值与预测出池量");
        return sample;
    }

    /**
     * 提交：存在未复核疑似异常样本时拒绝。幂等：已提交/已确认重复提交返回原任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public GrowthMeasureTask submit(Long taskId, String submittedBy, String requestId) {
        GrowthMeasureTask task = getOrThrow(taskId);
        if (GrowthMeasureTask.STATUS_SUBMITTED.equals(task.getStatus())
                || GrowthMeasureTask.STATUS_CONFIRMED.equals(task.getStatus())) {
            return task;
        }
        if (!GrowthMeasureTask.STATUS_MEASURED.equals(task.getStatus())) {
            throw new IllegalArgumentException("仅已录入任务可提交，当前状态：" + task.getStatus());
        }
        Long suspects = sampleMapper.selectCount(new QueryWrapper<GrowthMeasureSample>()
                .eq("task_id", taskId).eq("status", GrowthMeasureSample.STATUS_SUSPECT));
        if (suspects != null && suspects > 0) {
            throw new IllegalArgumentException("存在" + suspects + "尾疑似异常样本未复核，"
                    + "异常值不能静默删除，请先逐尾复核标记并填写复核理由");
        }
        GrowthMeasureTask before = copyOf(task);
        task.setStatus(GrowthMeasureTask.STATUS_SUBMITTED);
        task.setSubmittedBy(submittedBy);
        task.setSubmittedTime(LocalDateTime.now());
        taskMapper.updateById(task);
        evidenceService.record(GrowthChangeEvidence.ENTITY_TASK, task.getId(),
                GrowthChangeEvidence.ACTION_TASK_SUBMIT, requestId, submittedBy, before, task,
                "提交测量任务，均值体重" + task.getAvgWeight() + "g、长度" + task.getAvgLength()
                + "cm，预测出池量" + task.getPredictedHarvestKg() + "kg（待主管确认）");
        return task;
    }

    /**
     * 主管确认：确认人与确认意见缺一不可（参数校验保证）。
     * 只有确认后，任务上的预测出池量才更新到批次预测；重复确认幂等不重复更新。
     */
    @Transactional(rollbackFor = Exception.class)
    public GrowthMeasureTask confirm(Long taskId, GrowthTaskConfirmRequest request) {
        GrowthMeasureTask task = getOrThrow(taskId);
        if (GrowthMeasureTask.STATUS_CONFIRMED.equals(task.getStatus())) {
            return task;
        }
        if (!GrowthMeasureTask.STATUS_SUBMITTED.equals(task.getStatus())) {
            throw new IllegalArgumentException("仅已提交任务可主管确认，当前状态：" + task.getStatus());
        }
        GrowthMeasureTask before = copyOf(task);
        task.setStatus(GrowthMeasureTask.STATUS_CONFIRMED);
        task.setConfirmedBy(request.getConfirmedBy());
        task.setConfirmedTime(LocalDateTime.now());
        task.setConfirmRemark(request.getConfirmRemark());
        taskMapper.updateById(task);
        evidenceService.record(GrowthChangeEvidence.ENTITY_TASK, task.getId(),
                GrowthChangeEvidence.ACTION_TASK_CONFIRM, request.getRequestId(), request.getConfirmedBy(),
                before, task, "主管确认测量结果：" + request.getConfirmRemark());
        applyForecast(task, request);
        return task;
    }

    public GrowthMeasureTask getOrThrow(Long id) {
        GrowthMeasureTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new IllegalArgumentException("生长测量任务不存在，id=" + id);
        }
        return task;
    }

    public List<GrowthMeasureTask> listTasks(Long pondId, Long batchId, Long planId, String status) {
        QueryWrapper<GrowthMeasureTask> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(batchId != null, "batch_id", batchId);
        wrapper.eq(planId != null, "plan_id", planId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("task_date", "id");
        return taskMapper.selectList(wrapper);
    }

    public Map<String, Object> taskDetail(Long taskId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("task", getOrThrow(taskId));
        detail.put("samples", samplesOf(taskId));
        return detail;
    }

    public List<GrowthMeasureSample> samplesOf(Long taskId) {
        return sampleMapper.selectList(new QueryWrapper<GrowthMeasureSample>()
                .eq("task_id", taskId).orderByAsc("seq_no"));
    }

    /**
     * 批次当前预测出池量：仅主管确认后才有值。
     */
    public GrowthHarvestForecast forecastOf(Long batchId) {
        return forecastMapper.selectOne(new QueryWrapper<GrowthHarvestForecast>()
                .eq("batch_id", batchId).last("limit 1"));
    }

    /**
     * 从测量记录反查抽样方案：定位原单（方案与任务）、处理人（方案登记/录入/提交/确认/复核）、
     * 异常原因（逐尾复核结论与复核理由）。
     */
    public Map<String, Object> trace(Long taskId) {
        GrowthMeasureTask task = getOrThrow(taskId);
        GrowthSamplingPlan plan = planService.getOrThrow(task.getPlanId());
        List<GrowthMeasureSample> samples = samplesOf(taskId);

        Map<String, Object> original = new LinkedHashMap<>();
        original.put("planId", plan.getId());
        original.put("planNo", plan.getPlanNo());
        original.put("planStatus", plan.getStatus());
        original.put("pondId", plan.getPondId());
        original.put("batchId", plan.getBatchId());
        original.put("sampleSize", plan.getSampleSize());
        original.put("frequencyDays", plan.getFrequencyDays());
        original.put("allowedDeviationPct", plan.getAllowedDeviationPct());
        original.put("planOperator", plan.getOperator());
        original.put("planRequestId", plan.getRequestId());
        original.put("taskNo", task.getTaskNo());
        original.put("taskDate", task.getTaskDate());

        Set<String> reviewers = new LinkedHashSet<>();
        List<Map<String, Object>> abnormalities = new ArrayList<>();
        for (GrowthMeasureSample sample : samples) {
            if (sample.getReviewedBy() != null) {
                reviewers.add(sample.getReviewedBy());
            }
            if (!GrowthMeasureSample.STATUS_NORMAL.equals(sample.getStatus())) {
                Map<String, Object> abnormal = new LinkedHashMap<>();
                abnormal.put("sampleId", sample.getId());
                abnormal.put("seqNo", sample.getSeqNo());
                abnormal.put("weightG", sample.getWeightG());
                abnormal.put("lengthCm", sample.getLengthCm());
                abnormal.put("status", sample.getStatus());
                abnormal.put("reviewReason", sample.getReviewReason());
                abnormal.put("reviewedBy", sample.getReviewedBy());
                abnormal.put("reviewedTime", sample.getReviewedTime());
                abnormalities.add(abnormal);
            }
        }

        Map<String, Object> handlers = new LinkedHashMap<>();
        handlers.put("enteredBy", task.getEnteredBy());
        handlers.put("enteredTime", task.getEnteredTime());
        handlers.put("submittedBy", task.getSubmittedBy());
        handlers.put("submittedTime", task.getSubmittedTime());
        handlers.put("confirmedBy", task.getConfirmedBy());
        handlers.put("confirmedTime", task.getConfirmedTime());
        handlers.put("reviewers", new ArrayList<>(reviewers));

        Map<String, Object> device = new LinkedHashMap<>();
        device.put("deviceVersionId", task.getDeviceVersionId());
        device.put("deviceCode", task.getDeviceCode());
        device.put("versionNo", task.getDeviceVersionNo());
        device.put("weightFactor", task.getWeightFactor());
        device.put("lengthFactor", task.getLengthFactor());

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("task", task);
        trace.put("samplingPlan", original);
        trace.put("handlers", handlers);
        trace.put("deviceSnapshot", device);
        trace.put("abnormalities", abnormalities);
        trace.put("batchForecast", forecastOf(task.getBatchId()));
        return trace;
    }

    // ------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------

    /**
     * 主管确认后更新批次预测出池量（每批一行当前值），前后快照留证。
     */
    private void applyForecast(GrowthMeasureTask task, GrowthTaskConfirmRequest request) {
        GrowthHarvestForecast forecast = forecastMapper.selectOne(new QueryWrapper<GrowthHarvestForecast>()
                .eq("batch_id", task.getBatchId()).last("limit 1"));
        if (forecast == null) {
            forecast = new GrowthHarvestForecast();
            forecast.setBatchId(task.getBatchId());
            forecast.setPondId(task.getPondId());
            forecast.setAvgWeight(task.getAvgWeight());
            forecast.setAvgLength(task.getAvgLength());
            forecast.setPredictedHarvestKg(task.getPredictedHarvestKg());
            forecast.setSourceTaskId(task.getId());
            forecast.setConfirmedBy(request.getConfirmedBy());
            forecast.setConfirmedTime(task.getConfirmedTime());
            forecastMapper.insert(forecast);
            evidenceService.record(GrowthChangeEvidence.ENTITY_FORECAST, forecast.getId(),
                    GrowthChangeEvidence.ACTION_FORECAST_UPDATE, request.getRequestId(), request.getConfirmedBy(),
                    null, forecast, "主管确认后写入批次预测出池量" + forecast.getPredictedHarvestKg()
                    + "kg（来源任务" + task.getTaskNo() + "）");
        } else {
            GrowthHarvestForecast before = copyOf(forecast);
            forecast.setPondId(task.getPondId());
            forecast.setAvgWeight(task.getAvgWeight());
            forecast.setAvgLength(task.getAvgLength());
            forecast.setPredictedHarvestKg(task.getPredictedHarvestKg());
            forecast.setSourceTaskId(task.getId());
            forecast.setConfirmedBy(request.getConfirmedBy());
            forecast.setConfirmedTime(task.getConfirmedTime());
            forecastMapper.updateById(forecast);
            evidenceService.record(GrowthChangeEvidence.ENTITY_FORECAST, forecast.getId(),
                    GrowthChangeEvidence.ACTION_FORECAST_UPDATE, request.getRequestId(), request.getConfirmedBy(),
                    before, forecast, "主管确认后更新批次预测出池量为" + forecast.getPredictedHarvestKg()
                    + "kg（来源任务" + task.getTaskNo() + "）");
        }
    }

    /**
     * 重算任务均值与预测出池量：只统计计入样本（NORMAL/REVIEWED_NORMAL），
     * 套用录入时冻结的校正系数；预测出池量 = 校正均重 × 批次现存数量。
     */
    private void recomputeAverages(GrowthMeasureTask task) {
        List<GrowthMeasureSample> samples = samplesOf(task.getId());
        BigDecimal sumWeight = BigDecimal.ZERO;
        BigDecimal sumLength = BigDecimal.ZERO;
        int counted = 0;
        int suspects = 0;
        for (GrowthMeasureSample sample : samples) {
            if (GrowthMeasureSample.STATUS_SUSPECT.equals(sample.getStatus())) {
                suspects++;
                continue;
            }
            if (GrowthMeasureSample.STATUS_ABNORMAL.equals(sample.getStatus())) {
                continue;
            }
            sumWeight = sumWeight.add(sample.getWeightG());
            sumLength = sumLength.add(sample.getLengthCm());
            counted++;
        }
        task.setSampleCount(samples.size());
        task.setSuspectCount(suspects);
        if (counted == 0) {
            task.setAvgWeightRaw(null);
            task.setAvgLengthRaw(null);
            task.setAvgWeight(null);
            task.setAvgLength(null);
            task.setPredictedHarvestKg(null);
            return;
        }
        BigDecimal count = new BigDecimal(counted);
        BigDecimal avgWeightRaw = sumWeight.divide(count, 4, RoundingMode.HALF_UP);
        BigDecimal avgLengthRaw = sumLength.divide(count, 4, RoundingMode.HALF_UP);
        task.setAvgWeightRaw(avgWeightRaw);
        task.setAvgLengthRaw(avgLengthRaw);
        task.setAvgWeight(avgWeightRaw.multiply(task.getWeightFactor()).setScale(4, RoundingMode.HALF_UP));
        task.setAvgLength(avgLengthRaw.multiply(task.getLengthFactor()).setScale(4, RoundingMode.HALF_UP));
        SeedlingBatch batch = batchService.getOrThrow(task.getBatchId());
        task.setPredictedHarvestKg(task.getAvgWeight()
                .multiply(new BigDecimal(batch.getQuantity()))
                .divide(THOUSAND, 2, RoundingMode.HALF_UP));
    }

    /**
     * 允许偏差判定：|样本值 - 均值| / 均值 > 允许偏差% 即疑似异常。
     * 用乘法比较避免除法舍入：|v - mean| × 100 > mean × pct。
     */
    private boolean beyondDeviation(BigDecimal value, BigDecimal mean, BigDecimal allowedDeviationPct) {
        if (mean.signum() == 0) {
            return false;
        }
        return value.subtract(mean).abs().multiply(HUNDRED)
                .compareTo(mean.multiply(allowedDeviationPct)) > 0;
    }

    private BigDecimal meanOf(List<GrowthSampleInput> inputs, boolean weight) {
        BigDecimal sum = BigDecimal.ZERO;
        for (GrowthSampleInput input : inputs) {
            sum = sum.add(weight ? input.getWeightG() : input.getLengthCm());
        }
        return sum.divide(new BigDecimal(inputs.size()), 10, RoundingMode.HALF_UP);
    }

    private GrowthMeasureSample getSampleOrThrow(Long taskId, Long sampleId) {
        GrowthMeasureSample sample = sampleMapper.selectById(sampleId);
        if (sample == null || !sample.getTaskId().equals(taskId)) {
            throw new IllegalArgumentException("任务id=" + taskId + "下不存在样本id=" + sampleId);
        }
        return sample;
    }

    private GrowthMeasureTask copyOf(GrowthMeasureTask source) {
        GrowthMeasureTask copy = new GrowthMeasureTask();
        org.springframework.beans.BeanUtils.copyProperties(source, copy);
        return copy;
    }

    private GrowthMeasureSample copyOf(GrowthMeasureSample source) {
        GrowthMeasureSample copy = new GrowthMeasureSample();
        org.springframework.beans.BeanUtils.copyProperties(source, copy);
        return copy;
    }

    private GrowthHarvestForecast copyOf(GrowthHarvestForecast source) {
        GrowthHarvestForecast copy = new GrowthHarvestForecast();
        org.springframework.beans.BeanUtils.copyProperties(source, copy);
        return copy;
    }
}
