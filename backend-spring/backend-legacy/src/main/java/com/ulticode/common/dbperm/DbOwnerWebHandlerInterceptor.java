package com.ulticode.common.dbperm;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Web HandlerInterceptor that populates {@link DbOwnerContext} based on HTTP URI prefix (P3-DBPERM-001).
 *
 * <p>The owner context reflects the <em>domain that owns the tables the endpoint writes</em>,
 * not the audience of the endpoint. Admin business endpoints (problems, contests, submissions,
 * solutions, forum, comments, notifications, tags, ...) delegate mutations through the
 * App owner ports established in P3-OWNER-001, so they execute under {@link TableOwner#APP};
 * governance endpoints and Backup execute under {@link TableOwner#ADMIN}.
 *
 * <ul>
 *   <li>{@code /auth/**}, {@code /users/**}, {@code /admin/users/**}, {@code /admin/account/**}
 *       &rarr; {@link TableOwner#AUTH} (users aggregate incl. credentials/roles/permissions)</li>
 *   <li>{@code /admin/settings/**}, {@code /admin/audit/**}, {@code /admin/dashboard/**},
 *       {@code /admin/analytics/**}, {@code /moderation/**}, {@code /admin/backups/**}
 *       &rarr; {@link TableOwner#ADMIN}
 *       (system_settings, audit_logs, moderation_queue/moderation_actions/user_warnings/backups)</li>
 *   <li>All other routes (incl. admin business endpoints such as {@code /admin/problems/**})
 *       &rarr; {@link TableOwner#APP}</li>
 * </ul>
 */
@Slf4j
@Component
public class DbOwnerWebHandlerInterceptor implements HandlerInterceptor {

    private static final String[] AUTH_PREFIXES = {
        "/auth", "/users", "/admin/users", "/admin/account"
    };
    private static final String[] ADMIN_PREFIXES = {
        "/admin/settings", "/admin/audit", "/admin/dashboard", "/admin/analytics",
        "/admin/backups", "/moderation"
    };

    @Value("${app.db-owner.guard.enabled:true}")
    private boolean guardEnabled = true;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!guardEnabled) {
            return true;
        }

        String uri = request.getRequestURI();
        TableOwner owner = resolveOwner(uri);

        DbOwnerContext.setOwner(owner);
        log.trace("Set DbOwnerContext to {} for URI: {}", owner, uri);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        DbOwnerContext.clear();
    }

    /**
     * Resolve the owning domain for a request URI. An optional leading {@code /api} prefix
     * is stripped first so both {@code /auth/**} and {@code /api/auth/**} shapes match.
     */
    static TableOwner resolveOwner(String uri) {
        if (uri == null || uri.isEmpty()) {
            return TableOwner.APP;
        }
        String path = uri.startsWith("/api/") ? uri.substring(4) : uri;
        for (String prefix : AUTH_PREFIXES) {
            if (matchesPrefix(path, prefix)) {
                return TableOwner.AUTH;
            }
        }
        for (String prefix : ADMIN_PREFIXES) {
            if (matchesPrefix(path, prefix)) {
                return TableOwner.ADMIN;
            }
        }
        return TableOwner.APP;
    }

    private static boolean matchesPrefix(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }
}
