package com.evops.aqua.growth.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 生长抽样方案创建请求：按池塘和批次配置抽样数量、测量频次与允许偏差。
 */
public class GrowthPlanCreateRequest {

    @NotNull(message = "池塘不能为空")
    private Long pondId;

    @NotNull(message = "批次不能为空")
    private Long batchId;

    @NotNull(message = "抽样数量不能为空")
    private Integer sampleSize;

    @NotNull(message = "测量频次（间隔天数）不能为空")
    private Integer frequencyDays;

    @NotNull(message = "允许偏差（百分比）不能为空")
    private BigDecimal allowedDeviationPct;

    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    @NotBlank(message = "操作者不能为空")
    private String operator;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    private String remark;

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
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
