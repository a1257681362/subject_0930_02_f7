package com.evops.aqua.growth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.growth.dto.GrowthDeviceVersionPublishRequest;
import com.evops.aqua.growth.entity.GrowthChangeEvidence;
import com.evops.aqua.growth.entity.GrowthDeviceVersion;
import com.evops.aqua.growth.mapper.GrowthDeviceVersionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 测量设备校正系数版本：系数按设备版本保存。
 * 同设备发布新版本时旧版本自动停用；历史测量任务冻结当时系数快照，
 * 新版本只作用于之后录入的任务，绝不回算历史测量。
 */
@Service
public class GrowthDeviceVersionService {

    private final GrowthDeviceVersionMapper versionMapper;
    private final GrowthEvidenceService evidenceService;

    public GrowthDeviceVersionService(GrowthDeviceVersionMapper versionMapper,
                                      GrowthEvidenceService evidenceService) {
        this.versionMapper = versionMapper;
        this.evidenceService = evidenceService;
    }

    /**
     * 发布设备校正系数版本。幂等：同 requestId 重复发布返回首次记录。
     * 同设备已有 ACTIVE 版本时自动停用（历史任务快照不受影响）。
     */
    @Transactional(rollbackFor = Exception.class)
    public GrowthDeviceVersion publish(GrowthDeviceVersionPublishRequest request) {
        GrowthDeviceVersion duplicated = versionMapper.selectOne(new QueryWrapper<GrowthDeviceVersion>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        requirePositive(request.getWeightFactor(), "体重校正系数");
        requirePositive(request.getLengthFactor(), "长度校正系数");
        Long sameVersion = versionMapper.selectCount(new QueryWrapper<GrowthDeviceVersion>()
                .eq("device_code", request.getDeviceCode())
                .eq("version_no", request.getVersionNo()));
        if (sameVersion != null && sameVersion > 0) {
            throw new IllegalArgumentException("设备" + request.getDeviceCode()
                    + "已存在版本" + request.getVersionNo() + "，请更换版本号");
        }

        GrowthDeviceVersion current = versionMapper.selectOne(new QueryWrapper<GrowthDeviceVersion>()
                .eq("device_code", request.getDeviceCode())
                .eq("status", GrowthDeviceVersion.STATUS_ACTIVE).last("limit 1"));
        if (current != null) {
            GrowthDeviceVersion before = copyOf(current);
            current.setStatus(GrowthDeviceVersion.STATUS_RETIRED);
            versionMapper.updateById(current);
            evidenceService.record(GrowthChangeEvidence.ENTITY_DEVICE_VERSION, current.getId(),
                    GrowthChangeEvidence.ACTION_DEVICE_RETIRE, request.getRequestId(), request.getOperator(),
                    before, current, "设备" + current.getDeviceCode() + "发布新版本，旧版本"
                    + current.getVersionNo() + "停用；历史测量任务系数快照不变");
        }

        GrowthDeviceVersion version = new GrowthDeviceVersion();
        version.setDeviceCode(request.getDeviceCode());
        version.setVersionNo(request.getVersionNo());
        version.setWeightFactor(request.getWeightFactor());
        version.setLengthFactor(request.getLengthFactor());
        version.setEffectiveFrom(request.getEffectiveFrom());
        version.setStatus(GrowthDeviceVersion.STATUS_ACTIVE);
        version.setOperator(request.getOperator());
        version.setRequestId(request.getRequestId());
        version.setRemark(request.getRemark());
        versionMapper.insert(version);
        evidenceService.record(GrowthChangeEvidence.ENTITY_DEVICE_VERSION, version.getId(),
                GrowthChangeEvidence.ACTION_DEVICE_PUBLISH, request.getRequestId(), request.getOperator(),
                null, version, "发布设备" + version.getDeviceCode() + "校正系数版本" + version.getVersionNo()
                + "（体重系数" + version.getWeightFactor() + "，长度系数" + version.getLengthFactor() + "）");
        return version;
    }

    public GrowthDeviceVersion getOrThrow(Long id) {
        GrowthDeviceVersion version = versionMapper.selectById(id);
        if (version == null) {
            throw new IllegalArgumentException("设备校正系数版本不存在，id=" + id);
        }
        return version;
    }

    public void requireActive(GrowthDeviceVersion version) {
        if (!GrowthDeviceVersion.STATUS_ACTIVE.equals(version.getStatus())) {
            throw new IllegalArgumentException("设备" + version.getDeviceCode() + "版本"
                    + version.getVersionNo() + "已停用，不能用于新测量；历史测量保持原系数快照不变");
        }
    }

    public List<GrowthDeviceVersion> list(String deviceCode) {
        QueryWrapper<GrowthDeviceVersion> wrapper = new QueryWrapper<>();
        wrapper.eq(deviceCode != null && !deviceCode.trim().isEmpty(), "device_code", deviceCode);
        wrapper.orderByAsc("device_code", "id");
        return versionMapper.selectList(wrapper);
    }

    private void requirePositive(BigDecimal value, String name) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(name + "必须为正数");
        }
    }

    private GrowthDeviceVersion copyOf(GrowthDeviceVersion source) {
        GrowthDeviceVersion copy = new GrowthDeviceVersion();
        org.springframework.beans.BeanUtils.copyProperties(source, copy);
        return copy;
    }
}
