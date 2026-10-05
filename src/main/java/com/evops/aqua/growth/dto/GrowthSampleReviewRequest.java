package com.evops.aqua.growth.dto;

import javax.validation.constraints.NotBlank;

/**
 * 样本复核请求：异常值不能静默删除，只能标记并填写复核理由——
 * 复核理由必填；复核为异常的样本不计入均值，但记录永久保留。
 */
public class GrowthSampleReviewRequest {

    /** 复核结论：ABNORMAL（标记异常，不计入均值）或 NORMAL（复核正常，计入均值） */
    @NotBlank(message = "复核结论不能为空")
    private String decision;

    @NotBlank(message = "复核理由不能为空")
    private String reviewReason;

    @NotBlank(message = "复核人不能为空")
    private String reviewedBy;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String reviewReason) { this.reviewReason = reviewReason; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
