package com.evops.aqua.growth.dto;

import javax.validation.constraints.NotBlank;
import java.time.LocalDate;

/**
 * 测量任务生成请求：默认按方案日期区间与频次生成；同方案同日期幂等。
 * 操作者必填——生成的每条任务都要留证到人。
 */
public class GrowthTaskGenerateRequest {

    private LocalDate startDate;
    private LocalDate endDate;

    @NotBlank(message = "操作者不能为空")
    private String operator;

    private String requestId;

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
