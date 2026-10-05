package com.evops.aqua.growth.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 设备校正系数版本发布请求：系数按设备版本保存；
 * 同设备发布新版本时旧版本自动停用，历史测量不随新系数重算。
 */
public class GrowthDeviceVersionPublishRequest {

    @NotBlank(message = "设备编号不能为空")
    private String deviceCode;

    @NotBlank(message = "版本号不能为空")
    private String versionNo;

    @NotNull(message = "体重校正系数不能为空")
    private BigDecimal weightFactor;

    @NotNull(message = "长度校正系数不能为空")
    private BigDecimal lengthFactor;

    @NotNull(message = "生效日期不能为空")
    private LocalDate effectiveFrom;

    @NotBlank(message = "操作者不能为空")
    private String operator;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    private String remark;

    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
    public String getVersionNo() { return versionNo; }
    public void setVersionNo(String versionNo) { this.versionNo = versionNo; }
    public BigDecimal getWeightFactor() { return weightFactor; }
    public void setWeightFactor(BigDecimal weightFactor) { this.weightFactor = weightFactor; }
    public BigDecimal getLengthFactor() { return lengthFactor; }
    public void setLengthFactor(BigDecimal lengthFactor) { this.lengthFactor = lengthFactor; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
