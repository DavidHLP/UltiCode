package com.ulticode.security.csrf;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * CSRF validation filter that runs within the Spring Security filter chain,
 * after JWT authentication has been established.
 *
 * <p>Replaces the previous CsrfInterceptor (WebMvc interceptor) so that CSRF
 * validation is part of the security filter chain rather than the MVC layer.
 *
 * <p>Validates CSRF tokens on state-changing methods (POST, PUT, DELETE, PATCH)
 * for authenticated users by delegating to the Redis-backed CsrfService.
 */
@Slf4j
@RequiredArgsConstructor
public class CsrfValidationFilter extends OncePerRequestFilter {

    private final CsrfService csrfService;

    private static final Set<String> CSRF_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String method = request.getMethod();

        if (!CSRF_METHODS.contains(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = authentication.getName();
        if (userId == null || "anonymousUser".equals(userId)) {
            filterChain.doFilter(request, response);
            return;
        }

        String csrfToken = request.getHeader("X-CSRF-Token");
        if (csrfToken == null || csrfToken.isEmpty()) {
            log.warn("CSRF token missing for user {} on {} {}", userId, method, request.getRequestURI());
            throw new BusinessException(ErrorCode.FORBIDDEN, "CSRF token is required");
        }

        String newToken = csrfService.validateAndRotateToken(userId, csrfToken);
        if (newToken == null) {
            log.warn("Invalid CSRF token for user {} on {} {}", userId, method, request.getRequestURI());
            throw new BusinessException(ErrorCode.FORBIDDEN, "Invalid CSRF token");
        }

        response.setHeader("X-New-CSRF-Token", newToken);
        log.debug("CSRF validation and rotation passed for user {} on {} {}", userId, method, request.getRequestURI());
        filterChain.doFilter(request, response);
    }
}
