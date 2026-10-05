package com.evops.aqua.growth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 生长测量模块变更证据：所有写操作留痕（含操作者与幂等请求号），只追加。
 */
@TableName("t_growth_change_evidence")
public class GrowthChangeEvidence extends BaseEntity {

    public static final String ENTITY_DEVICE_VERSION = "GROWTH_DEVICE_VERSION";
    public static final String ENTITY_PLAN = "GROWTH_PLAN";
    public static final String ENTITY_TASK = "GROWTH_TASK";
    public static final String ENTITY_SAMPLE = "GROWTH_SAMPLE";
    public static final String ENTITY_FORECAST = "GROWTH_FORECAST";

    public static final String ACTION_DEVICE_PUBLISH = "DEVICE_PUBLISH";
    public static final String ACTION_DEVICE_RETIRE = "DEVICE_RETIRE";
    public static final String ACTION_PLAN_CREATE = "PLAN_CREATE";
    public static final String ACTION_PLAN_CANCEL = "PLAN_CANCEL";
    public static final String ACTION_TASK_GENERATE = "TASK_GENERATE";
    public static final String ACTION_TASK_ENTRY = "TASK_ENTRY";
    public static final String ACTION_SAMPLE_REVIEW = "SAMPLE_REVIEW";
    public static final String ACTION_TASK_SUBMIT = "TASK_SUBMIT";
    public static final String ACTION_TASK_CONFIRM = "TASK_CONFIRM";
    public static final String ACTION_FORECAST_UPDATE = "FORECAST_UPDATE";

    private String entityType;
    private Long entityId;
    private String action;
    private String requestId;
    private String operator;
    private String beforeJson;
    private String afterJson;
    private String detail;

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getBeforeJson() { return beforeJson; }
    public void setBeforeJson(String beforeJson) { this.beforeJson = beforeJson; }
    public String getAfterJson() { return afterJson; }
    public void setAfterJson(String afterJson) { this.afterJson = afterJson; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
