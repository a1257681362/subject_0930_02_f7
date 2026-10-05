package com.evops.aqua.growth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 测量设备校正系数版本：系数按设备版本保存。
 * 同设备发布新版本时旧版本自动停用（RETIRED）；
 * 历史测量任务冻结当时系数快照，不随新系数重算。
 */
@TableName("t_growth_device_version")
public class GrowthDeviceVersion extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_RETIRED = "RETIRED";

    private String deviceCode;
    private String versionNo;
    private BigDecimal weightFactor;
    private BigDecimal lengthFactor;
    private LocalDate effectiveFrom;
    private String status;
    private String operator;
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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
