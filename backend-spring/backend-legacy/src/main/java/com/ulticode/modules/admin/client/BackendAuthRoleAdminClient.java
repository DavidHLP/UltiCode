package com.ulticode.modules.admin.client;

import com.ulticode.common.util.TraceIdUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * P2-RBAC-001: thin HTTP client that the legacy module uses to forward
 * role / permission changes to the {@code backend-auth} service. The
 * legacy never writes to {@code users.role} or {@code user_permissions}
 * directly; the owner-only write path lives in
 * {@code com.ulticode.auth.permission.service.RoleAdministrationService}
 * (exposed via {@code RoleAdministrationController} at
 * {@code /auth/admin/users/{id}/role} and
 * {@code /auth/admin/users/{id}/permissions}). The ArchUnit
 * foreign-writer rule enforces the boundary at compile time.
 *
 * <p>Authentication: the client's caller is expected to be an
 * authenticated admin user (the management app's existing
 * {@code /admin/**} role gate). The JWT-bearing {@code access_token}
 * cookie is forwarded as {@code Authorization: Bearer <token>} so
 * backend-auth's own JwtAuthenticationFilter can re-validate and
 * rebuild the {@code SecurityContext} on its side; the
 * {@code @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")}
 * guard on the controller then authorises the call.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackendAuthRoleAdminClient {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final RestClient.Builder restClientBuilder;

    @Value("${ulticode.auth.service.base-url:http://localhost:9001/api/auth}")
    private String baseUrl;

    /**
     * Change a user's role via backend-auth.
     *
     * @param userId target user id
     * @param role   new role; the controller validates against the
     *               allow-list (USER / MODERATOR / ADMIN / SUPER_ADMIN)
     */
    public void changeRole(String userId, String role) {
        final String url = baseUrl + "/admin/users/" + userId + "/role";
        restClientBuilder.build()
                .post()
                .uri(url)
                .headers(h -> {
                    h.setContentType(MediaType.APPLICATION_JSON);
                    h.setBearerAuth(extractAccessToken());
                    h.set("X-Trace-Id", TraceIdUtil.current());
                })
                .body(Map.of("role", role))
                .retrieve()
                .toBodilessEntity();
        log.info("Role change forwarded to backend-auth: user={} role={}", userId, role);
    }

    /**
     * Grant a direct user permission via backend-auth.
     */
    public void grantPermission(String userId, String action, String resource, String expiresAtIso) {
        final String url = baseUrl + "/admin/users/" + userId + "/permissions";
        final Map<String, Object> body = expiresAtIso == null
                ? Map.of("action", action, "resource", resource)
                : Map.of("action", action, "resource", resource, "expiresAt", expiresAtIso);
        restClientBuilder.build()
                .post()
                .uri(url)
                .headers(h -> {
                    h.setContentType(MediaType.APPLICATION_JSON);
                    h.setBearerAuth(extractAccessToken());
                    h.set("X-Trace-Id", TraceIdUtil.current());
                })
                .body(body)
                .retrieve()
                .toBodilessEntity();
        log.info("Permission grant forwarded to backend-auth: user={} action={} resource={}",
                userId, action, resource);
    }

    /**
     * Revoke a direct user permission via backend-auth.
     */
    public void revokePermission(String userId, String action, String resource) {
        final String url = baseUrl + "/admin/users/" + userId + "/permissions";
        restClientBuilder.build()
                .method(org.springframework.http.HttpMethod.DELETE)
                .uri(url)
                .headers(h -> {
                    h.setContentType(MediaType.APPLICATION_JSON);
                    h.setBearerAuth(extractAccessToken());
                    h.set("X-Trace-Id", TraceIdUtil.current());
                })
                .body(Map.of("action", action, "resource", resource))
                .retrieve()
                .toBodilessEntity();
        log.info("Permission revoke forwarded to backend-auth: user={} action={} resource={}",
                userId, action, resource);
    }

    /**
     * Read the access_token cookie from the current servlet request so
     * the call to backend-auth reuses the admin's existing JWT. When
     * the call originates from a non-HTTP context (e.g. an async
     * worker, a startup task), the token is empty and backend-auth
     * will reject the call with 401 — the foreign-writer rule still
     * holds because the actual write only happens inside backend-auth.
     */
    private String extractAccessToken() {
        final ServletRequestAttributes attrs = (ServletRequestAttributes)
                RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "";
        }
        final HttpServletRequest req = attrs.getRequest();
        if (req == null || req.getCookies() == null) {
            return "";
        }
        for (jakarta.servlet.http.Cookie c : req.getCookies()) {
            if (ACCESS_TOKEN_COOKIE.equals(c.getName())) {
                return c.getValue();
            }
        }
        return "";
    }
}
