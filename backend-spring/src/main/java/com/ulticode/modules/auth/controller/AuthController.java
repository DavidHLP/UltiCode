package com.ulticode.modules.auth.controller;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.Result;
import com.ulticode.modules.auth.dto.ForgotPasswordDTO;
import com.ulticode.modules.auth.dto.LoginDTO;
import com.ulticode.modules.auth.dto.LoginResponse;
import com.ulticode.modules.auth.dto.RegisterDTO;
import com.ulticode.modules.auth.dto.ResetPasswordDTO;
import com.ulticode.modules.auth.dto.UserWithCsrfVO;
import com.ulticode.modules.auth.service.AuthService;
import com.ulticode.modules.auth.service.OAuthService;
import com.ulticode.modules.auth.service.PasswordResetService;
import com.ulticode.modules.permission.service.PermissionService;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.service.UserService;
import com.ulticode.security.csrf.CsrfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.Principal;
import java.util.List;

/**
 * Authentication controller for login, register, refresh, and logout.
 */
@Tag(name = "Auth", description = "Authentication endpoints")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CsrfService csrfService;
    private final UserService userService;
    private final PasswordResetService passwordResetService;
    private final OAuthService oauthService;
    private final PermissionService permissionService;

    @Value("${app.frontend-url:http://localhost:9002}")
    private String frontendUrl;
    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    @Operation(summary = "Login", description = "Authenticate user with username and password")
    @PostMapping("/login")
    @RateLimit(key = "login", limit = 10, period = 60)
    public Result<LoginResponse> login(
            @Valid @RequestBody LoginDTO loginDTO,
            HttpServletResponse response) {
        LoginResponse loginResponse = authService.login(loginDTO, response);
        return Result.success(loginResponse);
    }

    @Operation(summary = "Register", description = "Register a new user account")
    @PostMapping("/register")
    @RateLimit(key = "register", limit = 5, period = 60)
    public Result<LoginResponse> register(
            @Valid @RequestBody RegisterDTO registerDTO,
            HttpServletResponse response) {
        LoginResponse loginResponse = authService.register(registerDTO, response);
        return Result.success(loginResponse);
    }

    @Operation(summary = "Refresh token", description = "Refresh access token using refresh token from cookie")
    @RateLimit(key = "auth:refresh", limit = 20, period = 60)
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        String refreshToken = extractRefreshToken(request);
        LoginResponse loginResponse = authService.refresh(refreshToken, response);
        return Result.success(loginResponse);
    }

    @Operation(summary = "Logout", description = "Logout current user and clear auth cookie")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletResponse response) {
        authService.logout(response);
        return Result.success();
    }

    @Operation(summary = "Forgot password", description = "Send password reset email")
    @RateLimit(key = "auth:forgot-password", limit = 5, period = 60)
    @PostMapping("/forgot-password")
    public Result<Void> forgotPassword(@Valid @RequestBody ForgotPasswordDTO dto) {
        passwordResetService.forgotPassword(dto.getEmail());
        return Result.success();
    }

    @Operation(summary = "Reset password", description = "Reset password using token from email")
    @RateLimit(key = "auth:reset-password", limit = 5, period = 60)
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        passwordResetService.resetPassword(dto.getToken(), dto.getNewPassword());
        return Result.success();
    }

    @Operation(summary = "Get current user", description = "Get the authenticated user profile with CSRF token")
    @GetMapping("/me")
    public Result<UserWithCsrfVO> getCurrentUser(Principal principal) {
        String userId = principal.getName();
        User user = userService.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));

        String csrfToken = csrfService.generateToken(user.getId());
        UserVO userVO = userService.toVO(user);

        UserWithCsrfVO response = new UserWithCsrfVO();
        response.setUser(userVO);
        response.setCsrfToken(csrfToken);

        return Result.success(response);
    }

    @Operation(summary = "Get user permissions", description = "Get all permissions for the authenticated user")
    @GetMapping("/permissions")
    public Result<List<String>> getPermissions(Principal principal) {
        String userId = principal.getName();
        List<String> permissions = permissionService.getUserPermissionStrings(userId);
        return Result.success(permissions);
    }

    @Operation(summary = "GitHub login", description = "Redirect to GitHub OAuth")
    @GetMapping("/github")
    public void githubLogin(HttpServletResponse response) throws IOException {
        String authUrl = oauthService.getGithubAuthUrl();
        response.sendRedirect(authUrl);
    }

    @Operation(summary = "GitHub callback", description = "Handle GitHub OAuth callback")
    @GetMapping("/github/callback")
    public void githubCallback(@RequestParam String code, @RequestParam String state, HttpServletResponse response) throws IOException {
        oauthService.handleGithubCallback(code, state, response);
        response.sendRedirect(frontendUrl + "/?oauth=success");
    }

    @Operation(summary = "Google login", description = "Redirect to Google OAuth")
    @GetMapping("/google")
    public void googleLogin(HttpServletResponse response) throws IOException {
        String authUrl = oauthService.getGoogleAuthUrl();
        response.sendRedirect(authUrl);
    }

    @Operation(summary = "Google callback", description = "Handle Google OAuth callback")
    @GetMapping("/google/callback")
    public void googleCallback(@RequestParam String code, @RequestParam String state, HttpServletResponse response) throws IOException {
        oauthService.handleGoogleCallback(code, state, response);
        response.sendRedirect(frontendUrl + "/?oauth=success");
    }

    /**
     * Extract refresh token from cookies.
     *
     * @param request the HTTP request
     * @return the refresh token, or null if not found
     */
    private String extractRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (REFRESH_TOKEN_COOKIE.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
