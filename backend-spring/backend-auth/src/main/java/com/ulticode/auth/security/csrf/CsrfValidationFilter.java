package com.ulticode.auth.security.csrf;

import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.util.TraceIdUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CSRF validation filter that runs within the Spring Security filter chain,
 * after JWT authentication has been established.
 *
 * <p>This is a private copy inside backend-auth of backend-legacy's
 * {@code com.ulticode.security.csrf.CsrfValidationFilter}. The Strangler
 * Fig contract keeps backend-legacy's copy unchanged until Phase 4
 * cutover; the only intentional difference is that this copy references
 * {@link BaseErrorCode} from backend-common instead of the legacy
 * {@code com.ulticode.common.exception.ErrorCode}, because backend-auth
 * must not depend on backend-legacy. The {@code FORBIDDEN} code and
 * message byte values are kept identical to the legacy HTTP envelope
 * (see {@code ErrorCodeDelegationTest} in backend-legacy).
 */
@Slf4j
@RequiredArgsConstructor
public class CsrfValidationFilter extends OncePerRequestFilter {

    private final CsrfService csrfService;

    private static final Set<String> CSRF_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");
    private static final String JSON_RESPONSE_TEMPLATE =
            "{\"code\":%d,\"message\":\"%s\",\"data\":null,\"traceId\":\"%s\"}";

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
            writeErrorResponse(response, BaseErrorCode.FORBIDDEN.code(), "CSRF token is required");
            return;
        }

        String newToken = csrfService.validateAndRotateToken(userId, csrfToken);
        if (newToken == null) {
            log.warn("Invalid CSRF token for user {} on {} {}", userId, method, request.getRequestURI());
            writeErrorResponse(response, BaseErrorCode.FORBIDDEN.code(), "Invalid CSRF token");
            return;
        }

        response.setHeader("X-New-CSRF-Token", newToken);
        log.debug("CSRF validation and rotation passed for user {} on {} {}", userId, method, request.getRequestURI());
        filterChain.doFilter(request, response);
    }

    private void writeErrorResponse(HttpServletResponse response, int code, String message) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        String traceId = TraceIdUtil.current();
        String body = String.format(JSON_RESPONSE_TEMPLATE, code, escapeJson(message), traceId);
        response.getWriter().write(body);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
