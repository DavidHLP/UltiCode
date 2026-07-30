package com.ulticode.modules.admin.controller;

import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.dto.ClearCacheResponseVO;
import com.ulticode.modules.admin.dto.settings.AllSettingsVO;
import com.ulticode.modules.admin.dto.settings.EmailSettingsVO;
import com.ulticode.modules.admin.dto.settings.FeatureTogglesVO;
import com.ulticode.modules.admin.dto.settings.GeneralSettingsVO;
import com.ulticode.modules.admin.dto.settings.MaintenanceModeRequest;
import com.ulticode.modules.admin.dto.settings.MaintenanceModeVO;
import com.ulticode.modules.admin.dto.settings.RateLimitSettingsVO;
import com.ulticode.modules.admin.dto.settings.UploadSettingsVO;
import com.ulticode.modules.admin.service.SystemSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin settings controller for managing system settings.
 * Endpoints: /admin/settings/*
 *
 * <p>Persistence is delegated to {@link SystemSettingsService}, which stores
 * each settings category as a JSON row in the {@code system_settings} table.
 */
@Tag(name = "Admin - Settings", description = "Admin system settings endpoints")
@RestController
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminSettingsController {

    private final SystemSettingsService service;

    // ===== read paths =====

    @Operation(summary = "Get all settings", description = "Get all system settings")
    @GetMapping("/all")
    public Result<AllSettingsVO> getAllSettings() {
        return Result.success(service.getAllSettings());
    }

    @Operation(summary = "Get general settings", description = "Get general system settings")
    @GetMapping
    public Result<GeneralSettingsVO> getSettings() {
        return Result.success(service.getGeneralSettings());
    }

    @Operation(summary = "Get email settings", description = "Get email configuration settings")
    @GetMapping("/email")
    public Result<EmailSettingsVO> getEmailSettings() {
        return Result.success(service.getEmailSettings());
    }

    @Operation(summary = "Get rate limit settings", description = "Get rate limit configuration")
    @GetMapping("/rate-limits")
    public Result<RateLimitSettingsVO> getRateLimitSettings() {
        return Result.success(service.getRateLimitSettings());
    }

    @Operation(summary = "Get upload settings", description = "Get upload configuration")
    @GetMapping("/uploads")
    public Result<UploadSettingsVO> getUploadSettings() {
        return Result.success(service.getUploadSettings());
    }

    @Operation(summary = "Get feature toggles", description = "Get feature toggle settings")
    @GetMapping("/features")
    public Result<FeatureTogglesVO> getFeatureToggles() {
        return Result.success(service.getFeatureToggles());
    }

    // ===== write paths =====

    @Operation(summary = "Update general settings", description = "Update general system settings")
    @PatchMapping
    public Result<GeneralSettingsVO> updateSettings(@Valid @RequestBody GeneralSettingsVO vo) {
        return Result.success(service.updateGeneralSettings(vo));
    }

    @Operation(summary = "Update email settings", description = "Update email configuration settings")
    @PatchMapping("/email")
    public Result<EmailSettingsVO> updateEmailSettings(@Valid @RequestBody EmailSettingsVO vo) {
        return Result.success(service.updateEmailSettings(vo));
    }

    @Operation(summary = "Update rate limit settings", description = "Update rate limit configuration")
    @PatchMapping("/rate-limits")
    public Result<RateLimitSettingsVO> updateRateLimitSettings(@Valid @RequestBody RateLimitSettingsVO vo) {
        return Result.success(service.updateRateLimitSettings(vo));
    }

    @Operation(summary = "Update upload settings", description = "Update upload configuration")
    @PatchMapping("/uploads")
    public Result<UploadSettingsVO> updateUploadSettings(@Valid @RequestBody UploadSettingsVO vo) {
        return Result.success(service.updateUploadSettings(vo));
    }

    @Operation(summary = "Update feature toggles", description = "Update feature toggle settings")
    @PatchMapping("/features")
    public Result<FeatureTogglesVO> updateFeatureToggles(@Valid @RequestBody FeatureTogglesVO vo) {
        return Result.success(service.updateFeatureToggles(vo));
    }

    // ===== commands =====

    @Operation(summary = "Toggle maintenance mode", description = "Enable or disable maintenance mode")
    @PostMapping("/maintenance")
    public Result<MaintenanceModeVO> toggleMaintenance(@Valid @RequestBody MaintenanceModeRequest request) {
        return Result.success(service.toggleMaintenance(request));
    }

    @Operation(summary = "Clear cache", description = "Clear system cache (placeholder)")
    @PostMapping("/cache/clear")
    public Result<ClearCacheResponseVO> clearCache() {
        return Result.success(service.clearCache());
    }

    @Operation(summary = "Reset to defaults", description = "Reset all settings to default values")
    @PostMapping("/reset")
    public Result<AllSettingsVO> resetToDefaults() {
        return Result.success(service.resetToDefaults());
    }
}
