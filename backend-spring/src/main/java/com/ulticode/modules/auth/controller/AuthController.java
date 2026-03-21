package com.ulticode.modules.auth.controller;

import com.ulticode.common.response.Result;
import com.ulticode.modules.auth.dto.LoginDTO;
import com.ulticode.modules.auth.dto.LoginResponse;
import com.ulticode.modules.auth.dto.RegisterDTO;
import com.ulticode.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller for login, register, refresh, and logout.
 */
@Tag(name = "Auth", description = "Authentication endpoints")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    @Operation(summary = "Login", description = "Authenticate user with username and password")
    @PostMapping("/login")
    public Result<LoginResponse> login(
            @Valid @RequestBody LoginDTO loginDTO,
            HttpServletResponse response) {
        LoginResponse loginResponse = authService.login(loginDTO, response);
        return Result.success(loginResponse);
    }

    @Operation(summary = "Register", description = "Register a new user account")
    @PostMapping("/register")
    public Result<LoginResponse> register(
            @Valid @RequestBody RegisterDTO registerDTO,
            HttpServletResponse response) {
        LoginResponse loginResponse = authService.register(registerDTO, response);
        return Result.success(loginResponse);
    }

    @Operation(summary = "Refresh token", description = "Refresh access token using refresh token from cookie")
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
                if (ACCESS_TOKEN_COOKIE.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
