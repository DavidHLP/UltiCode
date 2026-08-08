package com.ulticode.auth.adapter.in.web;

import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.auth.api.command.ChangePasswordCommand;
import com.ulticode.auth.api.dto.AccountMutationDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AccountManagementService;
import com.ulticode.auth.api.dto.ChangePasswordDTO;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.response.Result;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.websecurity.annotation.RateLimit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Auth-owned user credential controller located inside backend-auth.
 *
 * <p>Handles self-password changes at the {@code /auth/me/password} boundary.
 * Delegates mutations to {@link AccountManagementService} with full actor delegation
 * and trace propagation. AUTH-COMP-001: moved from /users to /auth so Nginx
 * /api/auth/ catch-all routes password mutations to backend-auth instead of
 * the App catch-all at /api/users/.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserCredentialController {

    private final AccountManagementService accountManagementService;
    private final CurrentUserProvider currentUserProvider;

    @RateLimit(key = "user:password", limit = 5, period = 60)
    @PatchMapping("/me/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO changePasswordDTO) {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null || userId.isBlank()) {
            throw new AuthBusinessException(BaseErrorCode.UNAUTHORIZED);
        }

        if (!changePasswordDTO.getNewPassword().equals(changePasswordDTO.getConfirmPassword())) {
            throw new AuthBusinessException(BaseErrorCode.VALIDATION_FAILED, "Password confirmation does not match");
        }

        ChangePasswordCommand command = new ChangePasswordCommand(
                UUID.randomUUID().toString(),
                IdMetadata.mint(),
                new ActorDelegation("USER", userId, userId, "self password change"),
                TraceMetadata.EMPTY,
                userId,
                changePasswordDTO.getCurrentPassword(),
                changePasswordDTO.getNewPassword());

        RpcResult<AccountMutationDTO> rpcResult = accountManagementService.changePassword(command);
        if (!rpcResult.success()) {
            if (rpcResult.error() != null) {
                int code = rpcResult.error().code();
                if (code == AuthErrorCode.PASSWORD_MISMATCH.code()) {
                    throw new AuthBusinessException(BaseErrorCode.BAD_REQUEST, "Current password is incorrect");
                }
                if (code == AuthErrorCode.ACCOUNT_NOT_FOUND.code()) {
                    throw new AuthBusinessException(BaseErrorCode.NOT_FOUND, "Account not found");
                }
            }
            throw new AuthBusinessException(BaseErrorCode.UNKNOWN_ERROR);
        }

        return Result.success();
    }
}
