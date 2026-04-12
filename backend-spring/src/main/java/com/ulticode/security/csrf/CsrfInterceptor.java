package com.ulticode.security.csrf;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * CSRF 拦截器
 * 验证状态变更请求中的 CSRF token
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CsrfInterceptor implements HandlerInterceptor {

    private final CsrfService csrfService;

    private static final Set<String> CSRF_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();

        // 只检查状态变更方法
        if (!CSRF_METHODS.contains(method)) {
            return true;
        }

        String path = request.getRequestURI();
        log.debug("CSRF check for {} {}", method, path);

        // 获取当前认证用户
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            // 未认证请求由 Spring Security 处理
            return true;
        }

        String userId = authentication.getName();
        if (userId == null || "anonymousUser".equals(userId)) {
            return true;
        }

        // 获取 CSRF token from header
        String csrfToken = request.getHeader("X-CSRF-Token");
        if (csrfToken == null || csrfToken.isEmpty()) {
            log.warn("CSRF token missing for user {} on {} {}", userId, method, path);
            throw new BusinessException(ErrorCode.FORBIDDEN, "CSRF token is required");
        }

        // 验证 token 并轮换
        String newToken = csrfService.validateAndRotateToken(userId, csrfToken);
        if (newToken == null) {
            log.warn("Invalid CSRF token for user {} on {} {}", userId, method, path);
            throw new BusinessException(ErrorCode.FORBIDDEN, "Invalid CSRF token");
        }

        // Return new token so the client can update its stored token
        response.setHeader("X-New-CSRF-Token", newToken);
        log.debug("CSRF validation and rotation passed for user {} on {} {}", userId, method, path);
        return true;
    }
}
