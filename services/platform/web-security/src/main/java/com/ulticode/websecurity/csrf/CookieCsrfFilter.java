package com.ulticode.websecurity.csrf;

import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.util.TraceIdUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Double-submit CSRF enforcement for browser requests authenticated by UltiCode cookies.
 * Bearer-only traffic is not a browser credential flow and is left unchanged.
 */
public final class CookieCsrfFilter extends OncePerRequestFilter {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    public static final String CSRF_TOKEN_COOKIE = "csrf_token";
    public static final String CSRF_HEADER = "X-CSRF-Token";
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9-]{1,128}");

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (isSafeMethod(request.getMethod()) || !hasCredentialCookie(request.getCookies())) {
            filterChain.doFilter(request, response);
            return;
        }

        String submittedToken = request.getHeader(CSRF_HEADER);
        if (submittedToken == null || submittedToken.isBlank()) {
            writeError(response, "CSRF token is required");
            return;
        }

        String cookieToken = cookieValue(request.getCookies(), CSRF_TOKEN_COOKIE);
        if (cookieToken == null || !constantTimeEquals(cookieToken, submittedToken)) {
            writeError(response, "Invalid CSRF token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isSafeMethod(String method) {
        return "GET".equals(method)
                || "HEAD".equals(method)
                || "OPTIONS".equals(method)
                || "TRACE".equals(method);
    }

    private static boolean hasCredentialCookie(Cookie[] cookies) {
        return hasCookieValue(cookies, ACCESS_TOKEN_COOKIE) || hasCookieValue(cookies, REFRESH_TOKEN_COOKIE);
    }

    private static boolean hasCookieValue(Cookie[] cookies, String name) {
        String value = cookieValue(cookies, name);
        return value != null && !value.isBlank();
    }

    private static String cookieValue(Cookie[] cookies, String name) {
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookie != null && name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static boolean constantTimeEquals(String expected, String submitted) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                submitted.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        String traceId = TraceIdUtil.current();
        if (traceId == null || !SAFE_TRACE_ID.matcher(traceId).matches()) {
            traceId = "";
        }
        response.getWriter().write("{\"code\":" + BaseErrorCode.FORBIDDEN.code()
                + ",\"message\":\"" + message + "\",\"data\":null,\"traceId\":\""
                + traceId + "\"}");
    }
}
