package com.evops.aqua.growth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.growth.entity.GrowthChangeEvidence;
import com.evops.aqua.growth.entity.GrowthDeviceVersion;
import com.evops.aqua.growth.entity.GrowthMeasureSample;
import com.evops.aqua.growth.entity.GrowthMeasureTask;
import com.evops.aqua.growth.mapper.GrowthChangeEvidenceMapper;
import com.evops.aqua.growth.mapper.GrowthDeviceVersionMapper;
import com.evops.aqua.growth.mapper.GrowthMeasureSampleMapper;
import com.evops.aqua.growth.mapper.GrowthMeasureTaskMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 生长测量与出池预测验收（aqua-7）。
 * 主线：设备校正系数版本 → 按池塘和批次配置抽样方案（抽样数量/频次/允许偏差）
 * → 按频次生成测量任务 → 现场录入体重长度计算均值（超偏差自动疑似）
 * → 异常值只能标记并填写复核理由（不可删除）→ 提交 → 主管确认后才更新预测出池量
 * → 新系数版本不回算历史测量 → 从测量记录反查原单/处理人/异常原因，全程留证。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GrowthMeasurementAcceptanceTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private GrowthDeviceVersionMapper deviceVersionMapper;
    @Autowired
    private GrowthMeasureTaskMapper taskMapper;
    @Autowired
    private GrowthMeasureSampleMapper sampleMapper;
    @Autowired
    private GrowthChangeEvidenceMapper evidenceMapper;

    private final List<String> report = new ArrayList<>();
    private String sessionCookie;

    private Long pond1;
    private Long pond2;
    private Long batch1;       // 50000尾
    private Long plan1;        // 抽样4尾、每7天、允许偏差20%
    private Long deviceV1;     // 体重×1.10 长度×1.00
    private Long deviceV2;     // 体重×1.20 长度×1.05
    private Long task1;        // 2026-10-08
    private Long task2;        // 2026-10-15
    private Long task1Sample4; // 疑似异常样本

    @Test
    void acceptanceGrowthMeasurement() {
        checkpoint("CP0", "登录认证", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("username", "bootstrap");
            body.put("password", "bootstrap");
            ResponseEntity<String> resp = call(HttpMethod.POST, "/api/auth/login", body);
            assertSuccess(json(resp), "登录");
            List<String> cookies = resp.getHeaders().get(HttpHeaders.SET_COOKIE);
            check(cookies != null && !cookies.isEmpty(), "登录成功但未下发会话Cookie");
            sessionCookie = cookies.get(0).split(";")[0];
        });

        checkpoint("CP1", "建池与投苗", () -> {
            pond1 = createPond("GRO-P1", "生长一号池");
            pond2 = createPond("GRO-P2", "生长二号池");
            batch1 = stockBatch("GRO-C01", pond1);
        });

        checkpoint("CP2", "登记设备校正系数版本V1：缺操作者被拒，requestId幂等", () -> {
            JsonNode denied = json(call(HttpMethod.POST, "/api/aqua/growth/device-versions",
                    deviceBody("SCALE-G1", "V1", "1.10", "1.00", null, "gro-dev-v1")));
            check(!denied.path("success").asBoolean(true), "缺操作者发布设备版本应被参数校验拒绝");

            JsonNode v1 = json(call(HttpMethod.POST, "/api/aqua/growth/device-versions",
                    deviceBody("SCALE-G1", "V1", "1.10", "1.00", "设备员刘校", "gro-dev-v1")));
            assertSuccess(v1, "发布设备版本V1");
            deviceV1 = v1.path("data").path("id").asLong();
            check("ACTIVE".equals(v1.path("data").path("status").asText()), "新版本应为ACTIVE");

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/growth/device-versions",
                    deviceBody("SCALE-G1", "V1", "1.10", "1.00", "设备员刘校", "gro-dev-v1")));
            assertSuccess(again, "重复发布V1");
            check(again.path("data").path("id").asLong() == deviceV1, "同requestId重复发布应返回同一版本");
            check(deviceVersionMapper.selectCount(new QueryWrapper<GrowthDeviceVersion>()
                    .eq("device_code", "SCALE-G1")) == 1L, "幂等发布不得产生第二条版本");
        });

        checkpoint("CP3", "按池塘和批次配置抽样方案：批次不在该池被拒、同批次唯一生效、幂等", () -> {
            JsonNode wrongPond = json(call(HttpMethod.POST, "/api/aqua/growth/plans",
                    planBody(pond2, batch1, 4, 7, "20", "2026-10-08", "2026-10-22", "技术员赵测", "gro-plan-x")));
            check(!wrongPond.path("success").asBoolean(true)
                            && wrongPond.path("message").asText().contains("不一致"),
                    "批次不在该池应被拒绝：" + wrongPond.path("message").asText());

            JsonNode plan = json(call(HttpMethod.POST, "/api/aqua/growth/plans",
                    planBody(pond1, batch1, 4, 7, "20", "2026-10-08", "2026-10-22", "技术员赵测", "gro-plan-1")));
            assertSuccess(plan, "配置抽样方案");
            plan1 = plan.path("data").path("id").asLong();
            check(plan.path("data").path("sampleSize").asInt() == 4
                            && plan.path("data").path("frequencyDays").asInt() == 7
                            && new BigDecimal("20").compareTo(
                            new BigDecimal(plan.path("data").path("allowedDeviationPct").asText())) == 0,
                    "方案应保存抽样数量4、频次7天、允许偏差20%");

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/growth/plans",
                    planBody(pond1, batch1, 4, 7, "20", "2026-10-08", "2026-10-22", "技术员赵测", "gro-plan-1")));
            check(again.path("data").path("id").asLong() == plan1, "同requestId重复配置应返回同一方案");

            JsonNode second = json(call(HttpMethod.POST, "/api/aqua/growth/plans",
                    planBody(pond1, batch1, 6, 3, "15", "2026-10-08", "2026-10-22", "技术员赵测", "gro-plan-2")));
            check(!second.path("success").asBoolean(true)
                            && second.path("message").asText().contains("生效"),
                    "同批次第二个生效方案应被拒绝：" + second.path("message").asText());
        });

        checkpoint("CP4", "按频次生成测量任务：同计划同池同日幂等，任务冻结方案快照", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/growth/plans/" + plan1 + "/tasks/generate",
                    generateBody("gro-gen-1")));
            assertSuccess(resp, "生成测量任务");
            check(resp.path("data").path("createdCount").asInt() == 3,
                    "10-08至10-22每7天应生成3个任务，实际=" + resp.path("data").path("createdCount").asInt());
            JsonNode created = resp.path("data").path("created");
            task1 = created.get(0).path("id").asLong();
            task2 = created.get(1).path("id").asLong();
            check("2026-10-08".equals(created.get(0).path("taskDate").asText())
                            && "2026-10-15".equals(created.get(1).path("taskDate").asText())
                            && "2026-10-22".equals(created.get(2).path("taskDate").asText()),
                    "任务日期应为10-08/10-15/10-22");
            check(created.get(0).path("sampleSize").asInt() == 4
                            && new BigDecimal("20").compareTo(
                            new BigDecimal(created.get(0).path("allowedDeviationPct").asText())) == 0,
                    "任务应冻结方案抽样数量与允许偏差快照");

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/growth/plans/" + plan1 + "/tasks/generate",
                    generateBody("gro-gen-2")));
            check(again.path("data").path("createdCount").asInt() == 0
                            && again.path("data").path("existedCount").asInt() == 3,
                    "重复生成不应产生新任务");
            check(taskMapper.selectCount(new QueryWrapper<GrowthMeasureTask>()
                    .eq("plan_id", plan1)) == 3L, "幂等生成不得产生第四条任务");
        });

        checkpoint("CP5", "现场录入体重长度：样本数须等于抽样数量，冻结系数快照，超偏差自动疑似", () -> {
            JsonNode wrongCount = json(call(HttpMethod.POST, "/api/aqua/growth/tasks/" + task1 + "/entry",
                    entryBody(deviceV1, "录入人孙测", "gro-entry-x",
                            sample("100", "20"), sample("100", "20"), sample("100", "20"))));
            check(!wrongCount.path("success").asBoolean(true)
                            && wrongCount.path("message").asText().contains("抽样数量"),
                    "样本数与方案抽样数量不一致应被拒绝：" + wrongCount.path("message").asText());

            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/growth/tasks/" + task1 + "/entry",
                    entryBody(deviceV1, "录入人孙测", "gro-entry-1",
                            sample("100", "20"), sample("100", "20"), sample("100", "20"), sample("200", "30"))));
            assertSuccess(resp, "现场录入任务1");
            check("MEASURED".equals(resp.path("data").path("status").asText()), "录入后应为已测量");
            check(resp.path("data").path("sampleCount").asInt() == 4
                            && resp.path("data").path("suspectCount").asInt() == 1,
                    "应录入4尾并自动标记1尾疑似异常");
            check(new BigDecimal("1.10").compareTo(
                    new BigDecimal(resp.path("data").path("weightFactor").asText())) == 0
                            && "V1".equals(resp.path("data").path("deviceVersionNo").asText()),
                    "任务应冻结设备V1系数快照");
            check(new BigDecimal("100.0000").compareTo(
                    new BigDecimal(resp.path("data").path("avgWeightRaw").asText())) == 0
                            && new BigDecimal("110.0000").compareTo(
                            new BigDecimal(resp.path("data").path("avgWeight").asText())) == 0,
                    "均值应只统计计入样本：原始100g、校正110g，实际="
                            + resp.path("data").path("avgWeightRaw").asText() + "/"
                            + resp.path("data").path("avgWeight").asText());
            check(new BigDecimal("5500.00").compareTo(
                    new BigDecimal(resp.path("data").path("predictedHarvestKg").asText())) == 0,
                    "预测出池量应为110g×50000尾=5500kg，实际="
                            + resp.path("data").path("predictedHarvestKg").asText());

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/growth/tasks/" + task1 + "/entry",
                    entryBody(deviceV1, "录入人孙测", "gro-entry-1",
                            sample("100", "20"), sample("100", "20"), sample("100", "20"), sample("200", "30"))));
            assertSuccess(again, "同requestId重复录入");
            check(sampleMapper.selectCount(new QueryWrapper<GrowthMeasureSample>()
                    .eq("task_id", task1)) == 4L, "幂等录入不得重复写样本");

            List<GrowthMeasureSample> samples = sampleMapper.selectList(
                    new QueryWrapper<GrowthMeasureSample>().eq("task_id", task1).orderByAsc("seq_no"));
            check(GrowthMeasureSample.STATUS_SUSPECT.equals(samples.get(3).getStatus()),
                    "第4尾（200g/30cm）超允许偏差应被标记疑似异常");
            task1Sample4 = samples.get(3).getId();
        });

        checkpoint("CP6", "存在未复核疑似异常样本时禁止提交", () -> {
            JsonNode denied = json(call(HttpMethod.POST, "/api/aqua/growth/tasks/" + task1 + "/submit",
                    submitBody("录入人孙测", "gro-submit-x")));
            check(!denied.path("success").asBoolean(true)
                            && denied.path("message").asText().contains("复核"),
                    "有疑似异常未复核应拒绝提交：" + denied.path("message").asText());
            check(GrowthMeasureTask.STATUS_MEASURED.equals(taskMapper.selectById(task1).getStatus()),
                    "任务应保持已测量状态");
        });

        checkpoint("CP7", "异常值只能标记并填写复核理由：缺理由被拒，标记后样本保留不删除", () -> {
            JsonNode noReason = json(call(HttpMethod.POST,
                    "/api/aqua/growth/tasks/" + task1 + "/samples/" + task1Sample4 + "/review",
                    reviewBody("ABNORMAL", null, "复核人陈检", "gro-review-x")));
            check(!noReason.path("success").asBoolean(true), "缺复核理由应被参数校验拒绝");

            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/growth/tasks/" + task1 + "/samples/" + task1Sample4 + "/review",
                    reviewBody("ABNORMAL", "该尾疑似混入杂种，个体明显偏大，不计入均值", "复核人陈检", "gro-review-1")));
            assertSuccess(resp, "复核标记异常");
            check(GrowthMeasureSample.STATUS_ABNORMAL.equals(resp.path("data").path("status").asText()),
                    "样本应被标记为异常");
            check("复核人陈检".equals(resp.path("data").path("reviewedBy").asText())
                            && resp.path("data").path("reviewReason").asText().contains("杂种"),
                    "复核人与复核理由必须落库");

            GrowthMeasureSample kept = sampleMapper.selectById(task1Sample4);
            check(kept != null && new BigDecimal("200.00").compareTo(kept.getWeightG()) == 0,
                    "异常样本必须保留原值不得删除");
            check(sampleMapper.selectCount(new QueryWrapper<GrowthMeasureSample>()
                    .eq("task_id", task1)) == 4L, "复核标记不得减少样本行数");

            GrowthMeasureTask t1 = taskMapper.selectById(task1);
            check(new BigDecimal("100.0000").compareTo(t1.getAvgWeightRaw()) == 0
                            && new BigDecimal("5500.00").compareTo(t1.getPredictedHarvestKg()) == 0,
                    "标记异常后均值与预测应重算且不计入异常样本");
        });

        checkpoint("CP8", "提交测量任务：预测出池量算在任务上，批次预测尚未更新", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/growth/tasks/" + task1 + "/submit",
                    submitBody("录入人孙测", "gro-submit-1")));
            assertSuccess(resp, "提交任务1");
            check("SUBMITTED".equals(resp.path("data").path("status").asText()), "提交后应为已提交");

            JsonNode forecast = json(call(HttpMethod.GET, "/api/aqua/growth/batches/" + batch1 + "/forecast", null));
            check(forecast.path("data").isNull(), "主管确认前批次预测出池量应为空");
        });

        checkpoint("CP9", "主管确认后才更新预测出池量：确认人/意见缺一不可，重复确认幂等", () -> {
            JsonNode noBy = json(call(HttpMethod.POST, "/api/aqua/growth/tasks/" + task1 + "/confirm",
                    confirmBody(null, "同意纳入预测", "gro-confirm-x1")));
            check(!noBy.path("success").asBoolean(true), "缺确认人应被参数校验拒绝");
            JsonNode noRemark = json(call(HttpMethod.POST, "/api/aqua/growth/tasks/" + task1 + "/confirm",
                    confirmBody("主管王场长", null, "gro-confirm-x2")));
            check(!noRemark.path("success").asBoolean(true), "缺确认意见应被参数校验拒绝");
            check(GrowthMeasureTask.STATUS_SUBMITTED.equals(taskMapper.selectById(task1).getStatus()),
                    "确认不全时任务应停留已提交");

            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/growth/tasks/" + task1 + "/confirm",
                    confirmBody("主管王场长", "数据复核无误，同意纳入出池预测", "gro-confirm-1")));
            assertSuccess(resp, "主管确认任务1");
            check("CONFIRMED".equals(resp.path("data").path("status").asText()), "确认后应为已确认");

            JsonNode forecast = json(call(HttpMethod.GET, "/api/aqua/growth/batches/" + batch1 + "/forecast", null));
            check(new BigDecimal("5500.00").compareTo(
                    new BigDecimal(forecast.path("data").path("predictedHarvestKg").asText())) == 0
                            && forecast.path("data").path("sourceTaskId").asLong() == task1
                            && "主管王场长".equals(forecast.path("data").path("confirmedBy").asText()),
                    "确认后批次预测出池量应为5500kg并溯源任务1");

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/growth/tasks/" + task1 + "/confirm",
                    confirmBody("主管王场长", "数据复核无误，同意纳入出池预测", "gro-confirm-1")));
            assertSuccess(again, "重复确认");
            check(evidenceCount(GrowthChangeEvidence.ACTION_FORECAST_UPDATE, null) == 1,
                    "重复确认不得重复更新批次预测");
        });

        checkpoint("CP10", "新系数版本发布：旧版本自动停用，历史测量不随新系数重算", () -> {
            JsonNode v2 = json(call(HttpMethod.POST, "/api/aqua/growth/device-versions",
                    deviceBody("SCALE-G1", "V2", "1.20", "1.05", "设备员刘校", "gro-dev-v2")));
            assertSuccess(v2, "发布设备版本V2");
            deviceV2 = v2.path("data").path("id").asLong();
            check(GrowthDeviceVersion.STATUS_RETIRED.equals(deviceVersionMapper.selectById(deviceV1).getStatus()),
                    "旧版本V1应自动停用");

            GrowthMeasureTask t1 = taskMapper.selectById(task1);
            check(new BigDecimal("1.10").compareTo(t1.getWeightFactor()) == 0
                            && new BigDecimal("110.0000").compareTo(t1.getAvgWeight()) == 0
                            && "V1".equals(t1.getDeviceVersionNo()),
                    "历史任务应保持V1系数快照与均值，不随新系数重算");
        });

        checkpoint("CP11", "停用版本不能用于新测量；任务2用V2录入，疑似异常复核为正常计入均值", () -> {
            JsonNode retired = json(call(HttpMethod.POST, "/api/aqua/growth/tasks/" + task2 + "/entry",
                    entryBody(deviceV1, "录入人孙测", "gro-entry-2x",
                            sample("110", "22"), sample("110", "22"), sample("110", "22"), sample("150", "22"))));
            check(!retired.path("success").asBoolean(true)
                            && retired.path("message").asText().contains("已停用"),
                    "停用设备版本应被拒绝：" + retired.path("message").asText());

            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/growth/tasks/" + task2 + "/entry",
                    entryBody(deviceV2, "录入人孙测", "gro-entry-2",
                            sample("110", "22"), sample("110", "22"), sample("110", "22"), sample("150", "22"))));
            assertSuccess(resp, "任务2用V2录入");
            check(resp.path("data").path("suspectCount").asInt() == 1, "第4尾150g超偏差应疑似");
            check(new BigDecimal("132.0000").compareTo(
                    new BigDecimal(resp.path("data").path("avgWeight").asText())) == 0,
                    "疑似未复核前均值应为110×1.20=132g，实际="
                            + resp.path("data").path("avgWeight").asText());

            List<GrowthMeasureSample> samples = sampleMapper.selectList(
                    new QueryWrapper<GrowthMeasureSample>().eq("task_id", task2).orderByAsc("seq_no"));
            JsonNode review = json(call(HttpMethod.POST,
                    "/api/aqua/growth/tasks/" + task2 + "/samples/" + samples.get(3).getId() + "/review",
                    reviewBody("NORMAL", "复称确认该尾个体正常偏大，予以保留计入", "复核人陈检", "gro-review-2")));
            assertSuccess(review, "复核为正常");
            check(GrowthMeasureSample.STATUS_REVIEWED_NORMAL.equals(review.path("data").path("status").asText()),
                    "样本应标记为复核正常");

            GrowthMeasureTask t2 = taskMapper.selectById(task2);
            check(new BigDecimal("120.0000").compareTo(t2.getAvgWeightRaw()) == 0
                            && new BigDecimal("144.0000").compareTo(t2.getAvgWeight()) == 0
                            && new BigDecimal("7200.00").compareTo(t2.getPredictedHarvestKg()) == 0,
                    "复核正常后应计入均值：原始120g、校正144g、预测7200kg，实际="
                            + t2.getAvgWeightRaw() + "/" + t2.getAvgWeight() + "/" + t2.getPredictedHarvestKg());

            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/growth/tasks/" + task2 + "/submit",
                    submitBody("录入人孙测", "gro-submit-2"))), "提交任务2");
            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/growth/tasks/" + task2 + "/confirm",
                    confirmBody("主管王场长", "生长达标，更新出池预测", "gro-confirm-2"))), "确认任务2");

            JsonNode forecast = json(call(HttpMethod.GET, "/api/aqua/growth/batches/" + batch1 + "/forecast", null));
            check(new BigDecimal("7200.00").compareTo(
                    new BigDecimal(forecast.path("data").path("predictedHarvestKg").asText())) == 0
                            && forecast.path("data").path("sourceTaskId").asLong() == task2,
                    "批次预测出池量应更新为7200kg并溯源任务2");
        });

        checkpoint("CP12", "从测量记录反查抽样方案：定位原单、处理人及异常原因", () -> {
            JsonNode trace = json(call(HttpMethod.GET, "/api/aqua/growth/tasks/" + task1 + "/trace", null));
            assertSuccess(trace, "反查任务1");
            JsonNode data = trace.path("data");
            check(data.path("samplingPlan").path("planNo").asText().startsWith("GP-")
                            && data.path("samplingPlan").path("sampleSize").asInt() == 4
                            && data.path("samplingPlan").path("frequencyDays").asInt() == 7
                            && "技术员赵测".equals(data.path("samplingPlan").path("planOperator").asText()),
                    "反查应定位抽样方案原单及登记人");
            check("录入人孙测".equals(data.path("handlers").path("enteredBy").asText())
                            && "主管王场长".equals(data.path("handlers").path("confirmedBy").asText())
                            && data.path("handlers").path("reviewers").toString().contains("复核人陈检"),
                    "反查应定位录入/复核/确认处理人");
            JsonNode abnormalities = data.path("abnormalities");
            check(abnormalities.size() == 1
                            && GrowthMeasureSample.STATUS_ABNORMAL.equals(
                            abnormalities.get(0).path("status").asText())
                            && abnormalities.get(0).path("reviewReason").asText().contains("杂种")
                            && "复核人陈检".equals(abnormalities.get(0).path("reviewedBy").asText()),
                    "反查应给出异常样本、异常原因与复核人");
            check(new BigDecimal("1.10").compareTo(
                    new BigDecimal(data.path("deviceSnapshot").path("weightFactor").asText())) == 0,
                    "反查应给出测量时冻结的设备系数快照");
        });

        checkpoint("CP13", "全程留证：每条证据带操作者，可按对象或requestId追溯", () -> {
            check(evidenceCount(GrowthChangeEvidence.ACTION_DEVICE_PUBLISH, null) == 2, "设备版本发布证据应为2条");
            check(evidenceCount(GrowthChangeEvidence.ACTION_DEVICE_RETIRE, deviceV1) == 1, "旧版本停用证据缺失");
            check(evidenceCount(GrowthChangeEvidence.ACTION_PLAN_CREATE, plan1) == 1, "方案配置证据缺失");
            check(evidenceCount(GrowthChangeEvidence.ACTION_TASK_GENERATE, null) == 3, "任务生成证据应为3条");
            check(evidenceCount(GrowthChangeEvidence.ACTION_TASK_ENTRY, task1) == 1
                            && evidenceCount(GrowthChangeEvidence.ACTION_TASK_ENTRY, task2) == 1,
                    "录入证据应逐任务各一条");
            check(evidenceCount(GrowthChangeEvidence.ACTION_TASK_SUBMIT, task1) == 1
                            && evidenceCount(GrowthChangeEvidence.ACTION_TASK_CONFIRM, task1) == 1,
                    "提交/确认证据缺失");
            check(evidenceCount(GrowthChangeEvidence.ACTION_FORECAST_UPDATE, null) == 2,
                    "批次预测更新证据应为2条（任务1写入、任务2更新）");
            List<GrowthChangeEvidence> all = evidenceMapper.selectList(new QueryWrapper<>());
            check(!all.isEmpty() && all.stream().allMatch(e -> e.getOperator() != null
                    && !e.getOperator().trim().isEmpty()), "每条证据都必须带操作者");

            JsonNode resp = json(call(HttpMethod.GET,
                    "/api/aqua/growth/evidence?entityType=GROWTH_TASK&entityId=" + task1, null));
            assertSuccess(resp, "按任务追溯证据");
            List<String> actions = new ArrayList<>();
            resp.path("data").forEach(e -> actions.add(e.path("action").asText()));
            check(actions.containsAll(Arrays.asList("TASK_GENERATE", "TASK_ENTRY", "SAMPLE_REVIEW",
                            "TASK_SUBMIT", "TASK_CONFIRM")),
                    "任务1应可追溯到生成/录入/复核/提交/确认全链路，实际=" + actions);

            JsonNode byRequest = json(call(HttpMethod.GET,
                    "/api/aqua/growth/evidence?requestId=gro-review-1", null));
            check(byRequest.path("data").size() >= 1, "应可按requestId追溯复核证据");
        });

        report.add("验收结论：全部检查点通过——按池塘和批次配置抽样数量/频次/允许偏差，现场录入计算均值，"
                + "异常值只标记不删除且复核理由必填，校正系数按设备版本保存且历史测量不重算，"
                + "主管确认后才更新预测出池量，测量记录可反查原单/处理人/异常原因，全程留证");
        printReport();
    }

    // ------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------

    private Long createPond(String code, String name) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("pondCode", code);
        body.put("pondName", name);
        body.put("seaArea", "东山湾海区");
        body.put("waterDepthM", "1.50");
        body.put("salinityPpt", "28.5");
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/ponds", body));
        assertSuccess(resp, "建池[" + code + "]");
        return resp.path("data").path("id").asLong();
    }

    private Long stockBatch(String batchNo, Long pondId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("batchNo", batchNo);
        body.put("pondId", pondId);
        body.put("species", "LARGE_YELLOW_CROAKER");
        body.put("seedSource", "东山湾苗种场");
        body.put("quantity", 50000);
        body.put("stockDate", "2026-10-01");
        body.put("plannedHarvestDate", "2027-03-01");
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/batches", body));
        assertSuccess(resp, "投苗[" + batchNo + "]");
        return resp.path("data").path("id").asLong();
    }

    private Map<String, Object> deviceBody(String deviceCode, String versionNo, String weightFactor,
                                           String lengthFactor, String operator, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("deviceCode", deviceCode);
        body.put("versionNo", versionNo);
        body.put("weightFactor", weightFactor);
        body.put("lengthFactor", lengthFactor);
        body.put("effectiveFrom", "2026-10-01");
        if (operator != null) {
            body.put("operator", operator);
        }
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> planBody(Long pondId, Long batchId, int sampleSize, int frequencyDays,
                                         String deviationPct, String startDate, String endDate,
                                         String operator, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("pondId", pondId);
        body.put("batchId", batchId);
        body.put("sampleSize", sampleSize);
        body.put("frequencyDays", frequencyDays);
        body.put("allowedDeviationPct", deviationPct);
        body.put("startDate", startDate);
        body.put("endDate", endDate);
        body.put("operator", operator);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> generateBody(String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("operator", "技术员赵测");
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> sample(String weightG, String lengthCm) {
        Map<String, Object> sample = new HashMap<>();
        sample.put("weightG", weightG);
        sample.put("lengthCm", lengthCm);
        return sample;
    }

    private Map<String, Object> entryBody(Long deviceVersionId, String enteredBy, String requestId,
                                          Map<String, Object>... samples) {
        Map<String, Object> body = new HashMap<>();
        body.put("deviceVersionId", deviceVersionId);
        body.put("enteredBy", enteredBy);
        body.put("requestId", requestId);
        body.put("samples", Arrays.asList(samples));
        return body;
    }

    private Map<String, Object> reviewBody(String decision, String reviewReason,
                                           String reviewedBy, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("decision", decision);
        if (reviewReason != null) {
            body.put("reviewReason", reviewReason);
        }
        body.put("reviewedBy", reviewedBy);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> submitBody(String submittedBy, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("submittedBy", submittedBy);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> confirmBody(String confirmedBy, String confirmRemark, String requestId) {
        Map<String, Object> body = new HashMap<>();
        if (confirmedBy != null) {
            body.put("confirmedBy", confirmedBy);
        }
        if (confirmRemark != null) {
            body.put("confirmRemark", confirmRemark);
        }
        body.put("requestId", requestId);
        return body;
    }

    private long evidenceCount(String action, Long entityId) {
        QueryWrapper<GrowthChangeEvidence> wrapper = new QueryWrapper<GrowthChangeEvidence>()
                .eq("action", action);
        if (entityId != null) {
            wrapper.eq("entity_id", entityId);
        }
        Long c = evidenceMapper.selectCount(wrapper);
        return c == null ? 0L : c;
    }

    private void checkpoint(String id, String name, CheckpointBody body) {
        try {
            body.run();
            String line = "通过 " + id + " " + name;
            report.add(line);
            System.out.println("[验收] " + line);
        } catch (Throwable t) {
            String stop = "停点 " + id + "「" + name + "」失败，原因：" + t.getMessage();
            report.add(stop);
            report.add("验收结论：未通过，后续检查点未执行");
            printReport();
            fail("验收" + stop);
        }
    }

    private void printReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== 生长测量与出池预测验收报告 ==========\n");
        for (String line : report) {
            sb.append(line).append('\n');
        }
        sb.append("==========================================\n");
        System.out.println(sb);
    }

    private ResponseEntity<String> call(HttpMethod method, String url, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (sessionCookie != null) {
            headers.add(HttpHeaders.COOKIE, sessionCookie);
        }
        return rest.exchange(url, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private void assertSuccess(JsonNode node, String what) {
        if (!node.path("success").asBoolean(false)) {
            throw new IllegalStateException(what + "接口返回失败：" + node.path("message").asText());
        }
    }

    private void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private interface CheckpointBody {
        void run() throws Exception;
    }
}
