package com.ulticode.modules.admin.controller;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.response.Result;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.user.dto.ChangePasswordDTO;
import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.port.UserWritePort;
import com.ulticode.modules.user.projection.UserReadProjection;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin account controller for managing the current admin user's profile.
 * Endpoints: /admin/account/profile
 */
@Tag(name = "Admin - Account", description = "Admin account management endpoints")
@RestController
@RequestMapping("/admin/account")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminAccountController {

    private final UserReadProjection userReadProjection;
    private final UserWritePort userWritePort;
    private final CurrentUserProvider currentUserProvider;

    @Operation(summary = "Get current admin profile", description = "Get the profile of the currently authenticated admin user")
    @GetMapping("/profile")
    public Result<UserVO> getProfile() {
        UserVO user = userReadProjection.getCurrentUser();
        return Result.success(user);
    }

    @Operation(summary = "Update current admin profile", description = "Update the profile of the currently authenticated admin user")
    @PatchMapping("/profile")
    public Result<UserVO> updateProfile(@Valid @RequestBody UpdateUserDTO updateDTO) {
        UserVO user = userWritePort.updateCurrentUser(updateDTO);
        return Result.success(user);
    }

    @Operation(summary = "Change password", description = "Change the current admin user's password")
    @RateLimit(key = "admin:password", limit = 5, period = 60)
    @PostMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO changePasswordDTO) {
        userWritePort.changePassword(changePasswordDTO);
        return Result.success();
    }

    @Operation(summary = "Get subscription", description = "Get the current admin user's subscription info")
    @GetMapping("/subscription")
    public Result<SubscriptionVO> getSubscription() {
        // TODO: Implement subscription retrieval
        SubscriptionVO subscription = new SubscriptionVO();
        subscription.setPlan("FREE");
        subscription.setStatus("ACTIVE");
        return Result.success(subscription);
    }

    /**
     * VO for subscription info.
     */
    public static class SubscriptionVO {
        private String id;
        private String plan;
        private String status;
        private String startedAt;
        private String expiresAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getPlan() { return plan; }
        public void setPlan(String plan) { this.plan = plan; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getStartedAt() { return startedAt; }
        public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
        public String getExpiresAt() { return expiresAt; }
        public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
    }
}
