package com.ulticode.websecurity.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resolves the client IP address from an HTTP request using a single,
 * unified header-precedence rule.
 *
 * <p>The unified precedence (matches the most thorough prior impl):
 * <ol>
 *   <li>{@code X-Forwarded-For} &mdash; leftmost address in the comma chain
 *       (the original client per RFC 7239). Trimmed; blank and the literal
 *       {@code "unknown"} sentinel are skipped.</li>
 *   <li>{@code X-Real-IP} &mdash; single-value variant set by some proxies.</li>
 *   <li>{@code request.getRemoteAddr()} &mdash; direct TCP peer.</li>
 *   <li>{@code "unknown"} &mdash; when all sources are absent/null.</li>
 * </ol>
 *
 * <p>Stateless; safe as a singleton Spring bean.
 */
public class ClientIpResolver {

    private static final String UNKNOWN = "unknown";

    /**
     * Header precedence &mdash; X-Forwarded-For first (the standard chain
     * header), then X-Real-IP (the nginx single-value variant).
     */
    private static final String[] FORWARDED_HEADERS = {"X-Forwarded-For", "X-Real-IP"};

    /**
     * Resolves the client IP from an explicit request.
     *
     * @param request the servlet request (may be {@code null})
     * @return the resolved IP, never {@code null}; {@code "unknown"} as last resort
     */
    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }

        for (String header : FORWARDED_HEADERS) {
            String value = request.getHeader(header);
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty() && !UNKNOWN.equalsIgnoreCase(trimmed)) {
                    return trimmed.contains(",") ? trimmed.split(",")[0].trim() : trimmed;
                }
            }
        }

        String ip = request.getRemoteAddr();
        return ip != null ? ip : UNKNOWN;
    }

    /**
     * Resolves the client IP from the current thread's request context.
     *
     * <p>Convenience for call sites that don't already hold the
     * {@link HttpServletRequest} (AOP aspects, scheduled tasks behind a
     * web request). Returns {@code "unknown"} when no request is bound to
     * the thread (e.g. async / scheduled execution without a web context).
     *
     * @return the resolved IP, never {@code null}; {@code "unknown"} as last resort
     */
    public String resolveCurrent() {
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return UNKNOWN;
        }
        return resolve(attributes.getRequest());
    }
}
