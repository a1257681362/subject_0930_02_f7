package com.evops.aqua.growth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 批次预测出池量：每批一行当前值，仅主管确认测量任务后更新；
 * 历次变化经 t_growth_change_evidence 追溯。
 */
@TableName("t_growth_harvest_forecast")
public class GrowthHarvestForecast extends BaseEntity {

    private Long batchId;
    private Long pondId;
    private BigDecimal avgWeight;
    private BigDecimal avgLength;
    private BigDecimal predictedHarvestKg;
    private Long sourceTaskId;
    private String confirmedBy;
    private LocalDateTime confirmedTime;

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public BigDecimal getAvgWeight() { return avgWeight; }
    public void setAvgWeight(BigDecimal avgWeight) { this.avgWeight = avgWeight; }
    public BigDecimal getAvgLength() { return avgLength; }
    public void setAvgLength(BigDecimal avgLength) { this.avgLength = avgLength; }
    public BigDecimal getPredictedHarvestKg() { return predictedHarvestKg; }
    public void setPredictedHarvestKg(BigDecimal predictedHarvestKg) { this.predictedHarvestKg = predictedHarvestKg; }
    public Long getSourceTaskId() { return sourceTaskId; }
    public void setSourceTaskId(Long sourceTaskId) { this.sourceTaskId = sourceTaskId; }
    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }
    public LocalDateTime getConfirmedTime() { return confirmedTime; }
    public void setConfirmedTime(LocalDateTime confirmedTime) { this.confirmedTime = confirmedTime; }
}
