package com.ulticode.modules.notification.controller;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.notification.dto.*;
import com.ulticode.modules.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for notification operations.
 */
@Tag(name = "Notification", description = "Notification management API")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "Get notification list")
    @GetMapping
    public Result<PageResult<NotificationVO>> list(NotificationQueryDTO query) {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(notificationService.list(userId, query));
    }

    @Operation(summary = "Get unread notification count")
    @GetMapping("/unread-count")
    public Result<UnreadCountVO> getUnreadCount() {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(notificationService.getUnreadCount(userId));
    }

    @Operation(summary = "Get notification preferences")
    @GetMapping("/preferences")
    public Result<NotificationPreferenceVO> getPreferences() {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(notificationService.getPreferences(userId));
    }

    @Operation(summary = "Update notification preferences")
    @RateLimit(key = "notification:update-preferences", limit = 20, period = 60)
    @PatchMapping("/preferences")
    public Result<NotificationPreferenceVO> updatePreferences(
            @Valid @RequestBody UpdateNotificationPreferenceDTO dto) {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(notificationService.updatePreferences(userId, dto));
    }

    @Operation(summary = "Mark all notifications as read")
    @RateLimit(key = "notification:mark-all-read", limit = 20, period = 60)
    @PostMapping("/mark-all-read")
    public Result<Void> markAllRead() {
        String userId = SecurityUtil.getCurrentUserId();
        notificationService.markAllRead(userId);
        return Result.success();
    }

    @Operation(summary = "Delete all notifications")
    @RateLimit(key = "notification:clear-all", limit = 20, period = 60)
    @DeleteMapping("/clear")
    public Result<Void> clearAll() {
        String userId = SecurityUtil.getCurrentUserId();
        notificationService.clearAll(userId);
        return Result.success();
    }

    @Operation(summary = "Update a single notification")
    @RateLimit(key = "notification:update", limit = 20, period = 60)
    @PatchMapping("/{id}")
    public Result<NotificationVO> updateNotification(
            @PathVariable String id,
            @Valid @RequestBody UpdateNotificationDTO dto) {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(notificationService.updateNotification(userId, id, dto));
    }

    @Operation(summary = "Delete a single notification")
    @RateLimit(key = "notification:delete", limit = 20, period = 60)
    @DeleteMapping("/{id}")
    public Result<Void> deleteNotification(@PathVariable String id) {
        String userId = SecurityUtil.getCurrentUserId();
        notificationService.deleteNotification(userId, id);
        return Result.success();
    }
}
