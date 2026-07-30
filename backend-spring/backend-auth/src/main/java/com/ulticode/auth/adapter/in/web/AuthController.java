package com.ulticode.auth.adapter.in.web;

import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.dto.LoginDTO;
import com.ulticode.auth.dto.LoginResponse;
import com.ulticode.auth.dto.RegisterDTO;
import com.ulticode.auth.dto.UserWithCsrfVO;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import com.ulticode.auth.permission.service.PermissionService;
import com.ulticode.auth.security.csrf.CsrfService;
import com.ulticode.auth.service.AuthService;
import com.ulticode.common.response.Result;
import com.ulticode.websecurity.annotation.RateLimit;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * Authentication controller exposing /auth/** endpoints inside backend-auth.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthAccountPort accountPort;
    private final CsrfService csrfService;
    private final PermissionService permissionService;

    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    @PostMapping("/login")
    @RateLimit(limit = 10, period = 60, key = "auth:login:ip:{ip}")
    public Result<LoginResponse> login(@Valid @RequestBody LoginDTO loginDTO, HttpServletResponse response) {
        LoginResponse loginResponse = authService.login(loginDTO, response);
        return Result.success(loginResponse);
    }

    @PostMapping("/register")
    @RateLimit(limit = 5, period = 60, key = "auth:register:ip:{ip}")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterDTO registerDTO, HttpServletResponse response) {
        LoginResponse loginResponse = authService.register(registerDTO, response);
        return Result.success(loginResponse);
    }

    @PostMapping("/refresh")
    @RateLimit(limit = 20, period = 60, key = "auth:refresh:ip:{ip}")
    public Result<LoginResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshToken(request);
        LoginResponse loginResponse = authService.refresh(refreshToken, response);
        return Result.success(loginResponse);
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(extractRefreshToken(request), response);
        return Result.success();
    }

    @GetMapping("/me")
    public Result<UserWithCsrfVO> getCurrentUser(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_TOKEN_EXPIRED);
        }
        String userId = principal.getName();
        AuthAccountRecord account = accountPort.findById(userId)
                .orElseThrow(() -> new AuthBusinessException(AuthErrorCode.AUTH_USER_NOT_FOUND));

        String csrfToken = csrfService.generateToken(account.id());
        UserIdentityDTO identity = new UserIdentityDTO(
                account.id(), account.username(), account.role(),
                Boolean.TRUE.equals(account.isActive()), Boolean.TRUE.equals(account.isBanned())
        );

        UserWithCsrfVO response = new UserWithCsrfVO();
        response.setUser(identity);
        response.setCsrfToken(csrfToken);

        return Result.success(response);
    }

    @GetMapping("/permissions")
    public Result<List<String>> getPermissions(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_TOKEN_EXPIRED);
        }
        List<String> permissions = permissionService.getUserPermissionStrings(principal.getName());
        return Result.success(permissions);
    }

    private String extractRefreshToken(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (REFRESH_TOKEN_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
