package com.ulticode.auth.adapter.in.web;

import static com.ulticode.websecurity.csrf.CookieCsrfFilter.CSRF_TOKEN_COOKIE;
import static com.ulticode.websecurity.csrf.CookieCsrfFilter.REFRESH_TOKEN_COOKIE;

import com.ulticode.auth.dto.AuthUserVO;
import com.ulticode.auth.dto.LoginDTO;
import com.ulticode.auth.dto.LoginResponse;
import com.ulticode.auth.dto.RegisterDTO;
import com.ulticode.auth.dto.UserWithCsrfVO;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import com.ulticode.auth.service.AuthenticationWorkflow;
import com.ulticode.auth.service.CurrentSessionQuery;
import com.ulticode.auth.session.AuthSession;
import com.ulticode.auth.session.SessionCookieAdapter;
import com.ulticode.common.response.Result;
import com.ulticode.websecurity.annotation.RateLimit;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CookieValue;
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

    private final AuthenticationWorkflow authenticationWorkflow;
    private final SessionCookieAdapter sessionCookieAdapter;
    private final CurrentSessionQuery currentSessionQuery;

    @PostMapping("/login")
    @RateLimit(limit = 10, period = 60, key = "auth:login:ip:{ip}")
    public Result<LoginResponse> login(@Valid @RequestBody LoginDTO loginDTO, HttpServletResponse response) {
        AuthSession session = authenticationWorkflow.login(loginDTO.getUsername(), loginDTO.getPassword());
        sessionCookieAdapter.apply(session, response);
        return Result.success(session.response());
    }

    @PostMapping("/register")
    @RateLimit(limit = 5, period = 60, key = "auth:register:ip:{ip}")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterDTO registerDTO, HttpServletResponse response) {
        AuthSession session = authenticationWorkflow.register(
                registerDTO.getUsername(), registerDTO.getEmail(), registerDTO.getPassword());
        sessionCookieAdapter.apply(session, response);
        return Result.success(session.response());
    }

    @PostMapping("/refresh")
    @RateLimit(limit = 20, period = 60, key = "auth:refresh:ip:{ip}")
    public Result<LoginResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        AuthSession session = authenticationWorkflow.refresh(extractRefreshToken(request));
        sessionCookieAdapter.apply(session, response);
        return Result.success(session.response());
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        AuthSession session = authenticationWorkflow.logout(extractRefreshToken(request));
        sessionCookieAdapter.apply(session, response);
        return Result.success();
    }

    @GetMapping("/me")
    public Result<UserWithCsrfVO> getCurrentUser(
            Principal principal,
            @CookieValue(name = CSRF_TOKEN_COOKIE, required = false) String csrfToken) {
        return Result.success(toUserWithCsrf(currentUser(principal), csrfToken));
    }

    @GetMapping("/permissions")
    public Result<List<String>> getPermissions(Principal principal) {
        return Result.success(currentSessionQuery.permissions(requireAccountId(principal)));
    }

    private CurrentSessionQuery.CurrentUser currentUser(Principal principal) {
        return currentSessionQuery.currentUser(requireAccountId(principal));
    }

    private String requireAccountId(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_TOKEN_EXPIRED);
        }
        return principal.getName();
    }

    private UserWithCsrfVO toUserWithCsrf(CurrentSessionQuery.CurrentUser currentUser, String csrfToken) {
        AuthUserVO userVO = new AuthUserVO(
                currentUser.accountId(),
                currentUser.username(),
                currentUser.username(),
                currentUser.email() != null ? currentUser.email() : "",
                currentUser.role(),
                currentUser.active(),
                currentUser.banned(),
                currentUser.joinedAt() != null ? currentUser.joinedAt().toString() : ""
        );

        UserWithCsrfVO response = new UserWithCsrfVO();
        response.setUser(userVO);
        response.setCsrfToken(csrfToken);
        return response;
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
