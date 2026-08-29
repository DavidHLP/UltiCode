package com.ulticode.modules.admin.controller;

import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.auth.api.command.ChangePasswordCommand;
import com.ulticode.auth.api.dto.AccountMutationDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AccountManagementService;
import com.ulticode.auth.api.dto.ChangePasswordDTO;
import com.ulticode.common.auth.AdminActors;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.Result;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.common.util.TraceIdUtil;
import com.ulticode.modules.admin.dto.AdminUpdateUserDTO;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.projection.AdminUserProjection;
import com.ulticode.modules.admin.service.UserManagementService;
import com.ulticode.websecurity.annotation.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin account controller for managing the current admin user's profile and credentials.
 * Rebound from Legacy imports to AdminUserProjection and AccountManagementService.
 */
@Tag(name = "Admin - Account", description = "Admin account management endpoints")
@RestController
@RequestMapping("/admin/account")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminAccountController {

    private final AdminUserProjection adminUserProjection;
    private final UserManagementService userManagementService;
    @Autowired(required = false)
    private final AccountManagementService accountManagementService;
    private final CurrentUserProvider currentUserProvider;

    @Operation(summary = "Get current admin profile", description = "Get the profile of the currently authenticated admin user")
    @GetMapping("/profile")
    public Result<AdminUserVO> getProfile() {
        String userId = getCurrentUserIdOrThrow();
        AdminUserVO user = adminUserProjection.getUserById(userId);
        return Result.success(user);
    }

    @Operation(summary = "Update current admin profile", description = "Update the profile of the currently authenticated admin user")
    @PatchMapping("/profile")
    public Result<AdminUserVO> updateProfile(@Valid @RequestBody AdminUpdateUserDTO updateDTO) {
        String userId = getCurrentUserIdOrThrow();
        AdminUserVO user = userManagementService.updateUser(userId, updateDTO);
        return Result.success(user);
    }

    @Operation(summary = "Change password", description = "Change the current admin user's password")
    @RateLimit(key = "admin:password", limit = 5, period = 60)
    @PostMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO changePasswordDTO) {
        String userId = getCurrentUserIdOrThrow();
        if (!changePasswordDTO.getNewPassword().equals(changePasswordDTO.getConfirmPassword())) {
            throw new BusinessException(BaseErrorCode.VALIDATION_FAILED, "Password confirmation does not match");
        }

        if (accountManagementService == null) {
            throw new BusinessException(BaseErrorCode.UNKNOWN_ERROR,
                    "AccountManagementService unavailable");
        }
        ChangePasswordCommand command = new ChangePasswordCommand(
                UUID.randomUUID().toString(),
                IdMetadata.mint(),
                new ActorDelegation(
                        AdminActors.typeOf(currentUserProvider),
                        userId, userId, "admin self password change"),
                currentTrace(),
                userId,
                changePasswordDTO.getCurrentPassword(),
                changePasswordDTO.getNewPassword());

        RpcResult<AccountMutationDTO> res = accountManagementService.changePassword(command);
        if (res == null || !res.success()) {
            if (res != null && res.error() != null
                    && res.error().code() == AuthErrorCode.PASSWORD_MISMATCH.code()) {
                throw new BusinessException(BaseErrorCode.BAD_REQUEST, "Current password is incorrect");
            }
            throw new BusinessException(BaseErrorCode.UNKNOWN_ERROR);
        }
        return Result.success();
    }

    @Operation(summary = "Get subscription", description = "Get the current admin user's subscription info")
    @GetMapping("/subscription")
    public Result<SubscriptionVO> getSubscription() {
        SubscriptionVO subscription = new SubscriptionVO();
        subscription.setPlan("FREE");
        subscription.setStatus("ACTIVE");
        return Result.success(subscription);
    }

    private String getCurrentUserIdOrThrow() {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(BaseErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private TraceMetadata currentTrace() {
        String reqId = TraceIdUtil.current();
        if (reqId == null || reqId.isBlank()) {
            reqId = "t-" + UUID.randomUUID();
        }
        return new TraceMetadata(reqId, null, null, null);
    }

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
