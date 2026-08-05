package com.ulticode.auth.adapter.in.web;

import com.ulticode.auth.dto.ForgotPasswordDTO;
import com.ulticode.auth.dto.ResetPasswordDTO;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import com.ulticode.auth.service.PasswordResetService;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.response.Result;
import com.ulticode.websecurity.annotation.RateLimit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Password reset HTTP controller exposing forgot/reset endpoints.
 *
 * <p>AUTH-COMP-003: wires {@link PasswordResetService} to the HTTP surface.
 * The service logic (token hashing, expiry, email dispatch, revocation of
 * refresh tokens on reset) is owned by {@link PasswordResetService}; this
 * controller is a thin adapter.
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    /**
     * Request a password reset email.
     *
     * <p>Returns success regardless of whether the email exists, to avoid
     * user enumeration.
     *
     * @param request the forgot-password request containing the email
     */
    @PostMapping("/forgot-password")
    @RateLimit(limit = 5, period = 60, key = "auth:forgot:ip:{ip}")
    public Result<Void> forgotPassword(@Valid @RequestBody ForgotPasswordDTO request) {
        passwordResetService.forgotPassword(request.getEmail());
        return Result.success();
    }

    /**
     * Reset password using the token from the email link.
     *
     * @param request the reset-password request containing the token and new password
     */
    @PostMapping("/reset-password")
    @RateLimit(limit = 5, period = 60, key = "auth:reset:ip:{ip}")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO request) {
        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            throw new AuthBusinessException(BaseErrorCode.VALIDATION_FAILED,
                    "Password must be at least 6 characters");
        }
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return Result.success();
    }
}
