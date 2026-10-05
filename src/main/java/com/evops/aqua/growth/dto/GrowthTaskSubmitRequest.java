package com.evops.aqua.growth.dto;

import javax.validation.constraints.NotBlank;

/**
 * 测量任务提交请求：存在未复核疑似异常样本时拒绝提交。
 */
public class GrowthTaskSubmitRequest {

    @NotBlank(message = "提交人不能为空")
    private String submittedBy;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
