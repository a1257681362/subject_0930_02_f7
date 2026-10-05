package com.evops.aqua.growth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生长测量任务：按方案频次生成，生成时冻结抽样数量与允许偏差快照。
 * 录入时冻结设备版本与校正系数快照（历史测量不随新系数重算）；
 * 均值只统计计入样本（疑似/异常不计入）；预测出池量先算在任务上，
 * 主管确认后才写入批次预测。
 */
@TableName("t_growth_measure_task")
public class GrowthMeasureTask extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_MEASURED = "MEASURED";
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_CONFIRMED = "CONFIRMED";

    private String taskNo;
    private Long planId;
    private Long pondId;
    private Long batchId;
    private LocalDate taskDate;
    private Integer sampleSize;
    private BigDecimal allowedDeviationPct;
    private String status;
    private Long deviceVersionId;
    private String deviceCode;
    private String deviceVersionNo;
    private BigDecimal weightFactor;
    private BigDecimal lengthFactor;
    private Integer sampleCount;
    private Integer suspectCount;
    private BigDecimal avgWeightRaw;
    private BigDecimal avgLengthRaw;
    private BigDecimal avgWeight;
    private BigDecimal avgLength;
    private BigDecimal predictedHarvestKg;
    private String enteredBy;
    private LocalDateTime enteredTime;
    private String submittedBy;
    private LocalDateTime submittedTime;
    private String confirmedBy;
    private LocalDateTime confirmedTime;
    private String confirmRemark;
    private String requestId;
    private String remark;

    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public LocalDate getTaskDate() { return taskDate; }
    public void setTaskDate(LocalDate taskDate) { this.taskDate = taskDate; }
    public Integer getSampleSize() { return sampleSize; }
    public void setSampleSize(Integer sampleSize) { this.sampleSize = sampleSize; }
    public BigDecimal getAllowedDeviationPct() { return allowedDeviationPct; }
    public void setAllowedDeviationPct(BigDecimal allowedDeviationPct) { this.allowedDeviationPct = allowedDeviationPct; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getDeviceVersionId() { return deviceVersionId; }
    public void setDeviceVersionId(Long deviceVersionId) { this.deviceVersionId = deviceVersionId; }
    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
    public String getDeviceVersionNo() { return deviceVersionNo; }
    public void setDeviceVersionNo(String deviceVersionNo) { this.deviceVersionNo = deviceVersionNo; }
    public BigDecimal getWeightFactor() { return weightFactor; }
    public void setWeightFactor(BigDecimal weightFactor) { this.weightFactor = weightFactor; }
    public BigDecimal getLengthFactor() { return lengthFactor; }
    public void setLengthFactor(BigDecimal lengthFactor) { this.lengthFactor = lengthFactor; }
    public Integer getSampleCount() { return sampleCount; }
    public void setSampleCount(Integer sampleCount) { this.sampleCount = sampleCount; }
    public Integer getSuspectCount() { return suspectCount; }
    public void setSuspectCount(Integer suspectCount) { this.suspectCount = suspectCount; }
    public BigDecimal getAvgWeightRaw() { return avgWeightRaw; }
    public void setAvgWeightRaw(BigDecimal avgWeightRaw) { this.avgWeightRaw = avgWeightRaw; }
    public BigDecimal getAvgLengthRaw() { return avgLengthRaw; }
    public void setAvgLengthRaw(BigDecimal avgLengthRaw) { this.avgLengthRaw = avgLengthRaw; }
    public BigDecimal getAvgWeight() { return avgWeight; }
    public void setAvgWeight(BigDecimal avgWeight) { this.avgWeight = avgWeight; }
    public BigDecimal getAvgLength() { return avgLength; }
    public void setAvgLength(BigDecimal avgLength) { this.avgLength = avgLength; }
    public BigDecimal getPredictedHarvestKg() { return predictedHarvestKg; }
    public void setPredictedHarvestKg(BigDecimal predictedHarvestKg) { this.predictedHarvestKg = predictedHarvestKg; }
    public String getEnteredBy() { return enteredBy; }
    public void setEnteredBy(String enteredBy) { this.enteredBy = enteredBy; }
    public LocalDateTime getEnteredTime() { return enteredTime; }
    public void setEnteredTime(LocalDateTime enteredTime) { this.enteredTime = enteredTime; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
    public LocalDateTime getSubmittedTime() { return submittedTime; }
    public void setSubmittedTime(LocalDateTime submittedTime) { this.submittedTime = submittedTime; }
    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }
    public LocalDateTime getConfirmedTime() { return confirmedTime; }
    public void setConfirmedTime(LocalDateTime confirmedTime) { this.confirmedTime = confirmedTime; }
    public String getConfirmRemark() { return confirmRemark; }
    public void setConfirmRemark(String confirmRemark) { this.confirmRemark = confirmRemark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
