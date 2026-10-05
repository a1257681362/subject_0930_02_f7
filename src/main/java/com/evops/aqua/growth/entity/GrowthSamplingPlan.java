package com.evops.aqua.growth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 生长抽样方案：按池塘和批次配置抽样数量、测量频次（间隔天数）与允许偏差（百分比）。
 * 同一批次同一时刻只允许一个 ACTIVE 方案。
 */
@TableName("t_growth_sampling_plan")
public class GrowthSamplingPlan extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private String planNo;
    private Long pondId;
    private Long batchId;
    private Integer sampleSize;
    private Integer frequencyDays;
    private BigDecimal allowedDeviationPct;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private String operator;
    private String requestId;
    private String remark;

    public String getPlanNo() { return planNo; }
    public void setPlanNo(String planNo) { this.planNo = planNo; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Integer getSampleSize() { return sampleSize; }
    public void setSampleSize(Integer sampleSize) { this.sampleSize = sampleSize; }
    public Integer getFrequencyDays() { return frequencyDays; }
    public void setFrequencyDays(Integer frequencyDays) { this.frequencyDays = frequencyDays; }
    public BigDecimal getAllowedDeviationPct() { return allowedDeviationPct; }
    public void setAllowedDeviationPct(BigDecimal allowedDeviationPct) { this.allowedDeviationPct = allowedDeviationPct; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
