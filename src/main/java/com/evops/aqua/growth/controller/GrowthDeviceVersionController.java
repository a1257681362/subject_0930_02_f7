package com.evops.aqua.growth.controller;

import com.evops.aqua.growth.dto.GrowthDeviceVersionPublishRequest;
import com.evops.aqua.growth.entity.GrowthDeviceVersion;
import com.evops.aqua.growth.service.GrowthDeviceVersionService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 测量设备校正系数版本：系数按设备版本保存；
 * 新版本发布后旧版本自动停用，历史测量不随新系数重算。
 */
@RestController
@RequestMapping("/api/aqua/growth/device-versions")
public class GrowthDeviceVersionController {

    private final GrowthDeviceVersionService deviceVersionService;

    public GrowthDeviceVersionController(GrowthDeviceVersionService deviceVersionService) {
        this.deviceVersionService = deviceVersionService;
    }

    /** 发布设备校正系数版本：同设备旧版本自动停用；requestId 幂等 */
    @PostMapping
    public ApiResponse<GrowthDeviceVersion> publish(@Validated @RequestBody GrowthDeviceVersionPublishRequest request) {
        return ApiResponse.ok(deviceVersionService.publish(request));
    }

    @GetMapping
    public ApiResponse<List<GrowthDeviceVersion>> list(@RequestParam(required = false) String deviceCode) {
        return ApiResponse.ok(deviceVersionService.list(deviceCode));
    }
}
