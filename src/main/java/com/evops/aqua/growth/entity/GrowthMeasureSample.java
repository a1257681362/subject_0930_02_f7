package com.evops.aqua.growth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 测量样本（单尾）：异常值不能静默删除——应用层不提供删除入口，
 * 只能标记为异常并填写复核理由；疑似（SUSPECT）与异常（ABNORMAL）样本不计入均值。
 */
@TableName("t_growth_measure_sample")
public class GrowthMeasureSample extends BaseEntity {

    /** 正常：计入均值 */
    public static final String STATUS_NORMAL = "NORMAL";
    /** 疑似异常：超允许偏差由系统自动标记，待复核，暂不计入均值 */
    public static final String STATUS_SUSPECT = "SUSPECT";
    /** 复核为正常：计入均值 */
    public static final String STATUS_REVIEWED_NORMAL = "REVIEWED_NORMAL";
    /** 复核为异常：不计入均值；只标记不删除 */
    public static final String STATUS_ABNORMAL = "ABNORMAL";

    private Long taskId;
    private Integer seqNo;
    private BigDecimal weightG;
    private BigDecimal lengthCm;
    private String status;
    private String reviewReason;
    private String reviewedBy;
    private LocalDateTime reviewedTime;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Integer getSeqNo() { return seqNo; }
    public void setSeqNo(Integer seqNo) { this.seqNo = seqNo; }
    public BigDecimal getWeightG() { return weightG; }
    public void setWeightG(BigDecimal weightG) { this.weightG = weightG; }
    public BigDecimal getLengthCm() { return lengthCm; }
    public void setLengthCm(BigDecimal lengthCm) { this.lengthCm = lengthCm; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String reviewReason) { this.reviewReason = reviewReason; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedTime() { return reviewedTime; }
    public void setReviewedTime(LocalDateTime reviewedTime) { this.reviewedTime = reviewedTime; }
}
