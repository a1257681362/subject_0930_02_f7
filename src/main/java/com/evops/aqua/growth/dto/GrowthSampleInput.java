package com.evops.aqua.growth.dto;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 单尾样本录入：体重（克）与长度（厘米）。
 */
public class GrowthSampleInput {

    @NotNull(message = "样本体重不能为空")
    private BigDecimal weightG;

    @NotNull(message = "样本长度不能为空")
    private BigDecimal lengthCm;

    public BigDecimal getWeightG() { return weightG; }
    public void setWeightG(BigDecimal weightG) { this.weightG = weightG; }
    public BigDecimal getLengthCm() { return lengthCm; }
    public void setLengthCm(BigDecimal lengthCm) { this.lengthCm = lengthCm; }
}
