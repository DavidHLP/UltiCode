package com.ulticode.modules.admin.controller;

import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.service.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/** Public availability only; changes remain protected by AdminSettingsController. */
@RestController
@RequiredArgsConstructor
public class PublicAgentFeatureController {
    private final SystemSettingsService service;

    @GetMapping("/api/v1/public/features/agent")
    public Result<Map<String, Boolean>> agentFeature() {
        return Result.success(Map.of("enabled", service.isAgentEnabled()));
    }
}
