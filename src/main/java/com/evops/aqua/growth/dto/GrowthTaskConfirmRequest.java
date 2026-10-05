package com.evops.aqua.growth.dto;

import javax.validation.constraints.NotBlank;

/**
 * 主管确认请求：确认人与确认意见缺一不可——
 * 只有主管确认后，任务上的预测出池量才更新到批次预测。
 */
public class GrowthTaskConfirmRequest {

    @NotBlank(message = "确认人不能为空")
    private String confirmedBy;

    @NotBlank(message = "确认意见不能为空")
    private String confirmRemark;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }
    public String getConfirmRemark() { return confirmRemark; }
    public void setConfirmRemark(String confirmRemark) { this.confirmRemark = confirmRemark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
