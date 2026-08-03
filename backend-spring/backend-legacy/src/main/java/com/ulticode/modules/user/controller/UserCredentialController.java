package com.ulticode.modules.user.controller;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.response.Result;
import com.ulticode.modules.user.dto.ChangePasswordDTO;
import com.ulticode.modules.user.port.UserWritePort;
import com.ulticode.websecurity.annotation.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Auth-owned user credential controller — retained in backend-legacy
 * (P7-RELOCATE-USER-REMAINDER-001).
 *
 * <p>The {@code /users/me/password} endpoint stays here because password
 * mutation is Auth-owned (credential management). The remaining 10 user
 * surface endpoints relocated to the app-side
 * {@code UserController} in backend-app.
 */
@Tag(name = "User", description = "User credential management")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserCredentialController {

    private final UserWritePort userWritePort;

    @Operation(summary = "Change password", description = "Change the current user's password")
    @ApiResponse(responseCode = "200", description = "Password changed")
    @ApiResponse(responseCode = "400", description = "Validation error or incorrect current password")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @RateLimit(key = "user:password", limit = 5, period = 60)
    @PatchMapping("/me/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO changePasswordDTO) {
        userWritePort.changePassword(changePasswordDTO);
        return Result.success();
    }
}
