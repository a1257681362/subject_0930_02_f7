package com.evops.aqua.growth.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 现场录入请求：设备版本 + 样本列表。
 * 录入时冻结设备校正系数快照；样本数必须等于方案抽样数量；
 * 录入后计算均值，超允许偏差的样本自动标记疑似异常待复核。
 */
public class GrowthTaskEntryRequest {

    @NotNull(message = "设备版本不能为空")
    private Long deviceVersionId;

    @NotBlank(message = "录入人不能为空")
    private String enteredBy;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    @NotEmpty(message = "样本列表不能为空")
    @Valid
    private List<GrowthSampleInput> samples;

    public Long getDeviceVersionId() { return deviceVersionId; }
    public void setDeviceVersionId(Long deviceVersionId) { this.deviceVersionId = deviceVersionId; }
    public String getEnteredBy() { return enteredBy; }
    public void setEnteredBy(String enteredBy) { this.enteredBy = enteredBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public List<GrowthSampleInput> getSamples() { return samples; }
    public void setSamples(List<GrowthSampleInput> samples) { this.samples = samples; }
}
