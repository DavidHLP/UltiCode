package com.ulticode.modules.admin.controller;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.dto.AdminNotificationVO;
import com.ulticode.modules.admin.dto.CreateSystemNotificationRequest;
import com.ulticode.modules.admin.dto.UpdateSystemNotificationRequest;
import com.ulticode.modules.admin.service.AdminNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin controller for notification management.
 * Handles system announcements and notification operations for admin panel.
 */
@Tag(name = "Admin - Notifications", description = "Notification management endpoints for admin panel")
@RestController
@RequestMapping("/admin/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminNotificationController {

    private final AdminNotificationService adminNotificationService;

    @Operation(summary = "Get all system notifications", description = "Get list of all system announcements with creator information")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<AdminNotificationVO>> getAllNotifications() {
        return Result.success(adminNotificationService.getAllSystemNotifications());
    }

    @Operation(summary = "Create system notification", description = "Create a new system announcement and send to target users")
    @RateLimit(key = "admin:notification-create", limit = 30, period = 60)
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminNotificationVO> createNotification(@Valid @RequestBody CreateSystemNotificationRequest request) {
        return Result.success(adminNotificationService.createSystemNotification(request));
    }

    @Operation(summary = "Delete notification", description = "Delete a system notification and all related user notifications")
    @RateLimit(key = "admin:notification-delete", limit = 30, period = 60)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> deleteNotification(
            @io.swagger.v3.oas.annotations.Parameter(description = "Notification ID")
            @PathVariable String id) {
        adminNotificationService.deleteNotification(id);
        return Result.success();
    }

    @Operation(summary = "Update notification", description = "Update a system notification and all its user copies")
    @RateLimit(key = "admin:notification-update", limit = 30, period = 60)
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminNotificationVO> updateNotification(
            @io.swagger.v3.oas.annotations.Parameter(description = "Notification ID")
            @PathVariable String id,
            @Valid @RequestBody UpdateSystemNotificationRequest request) {
        return Result.success(adminNotificationService.updateSystemNotification(id, request));
    }
}
