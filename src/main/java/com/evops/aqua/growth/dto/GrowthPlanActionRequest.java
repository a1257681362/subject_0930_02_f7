package com.evops.aqua.growth.dto;

import javax.validation.constraints.NotBlank;

/**
 * 方案作废请求：作废后不再生成新任务，已生成任务不受影响。
 */
public class GrowthPlanActionRequest {

    @NotBlank(message = "操作者不能为空")
    private String operator;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
