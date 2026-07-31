package com.ulticode.common.dbperm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * URI-prefix to {@link TableOwner} routing matrix for {@link DbOwnerWebHandlerInterceptor}.
 *
 * <p>The routing reflects table ownership, not endpoint audience: admin business endpoints
 * delegate writes through App owner ports (P3-OWNER-001) and therefore run under APP.
 */
class DbOwnerWebHandlerInterceptorTest {

    private DbOwnerWebHandlerInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new DbOwnerWebHandlerInterceptor();
    }

    @AfterEach
    void tearDown() {
        DbOwnerContext.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/auth/login", "/auth/refresh", "/api/auth/oauth/github",
        "/users/me", "/users/u-1",
        "/admin/users", "/admin/users/u-1/ban", "/admin/users/u-1/permissions",
        "/admin/account", "/admin/account/roles"
    })
    @DisplayName("AUTH context: auth self-service, user profile, and admin account management routes")
    void resolveOwner_authRoutes(String uri) {
        assertThat(DbOwnerWebHandlerInterceptor.resolveOwner(uri)).isEqualTo(TableOwner.AUTH);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/admin/settings", "/admin/settings/rate-limit",
        "/admin/audit", "/admin/audit/logs",
        "/admin/dashboard", "/admin/analytics",
        "/admin/backups", "/moderation", "/moderation/queue", "/moderation/queue/q-1/apply"
    })
    @DisplayName("ADMIN context: governance and Backup routes over admin-owned tables")
    void resolveOwner_adminRoutes(String uri) {
        assertThat(DbOwnerWebHandlerInterceptor.resolveOwner(uri)).isEqualTo(TableOwner.ADMIN);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/problems", "/contests", "/submissions", "/forum/posts",
        "/admin/problems", "/admin/problems/p-1/publish",
        "/admin/contest", "/admin/scoring-rules",
        "/admin/submissions", "/admin/submissions/rejudge",
        "/admin/solutions", "/admin/forum", "/admin/comments",
        "/admin/notifications", "/admin/tags", "/admin/problem-lists",
        "/admin/subscriptions"
    })
    @DisplayName("APP context: public routes and app-owned admin business endpoints")
    void resolveOwner_appRoutes(String uri) {
        assertThat(DbOwnerWebHandlerInterceptor.resolveOwner(uri)).isEqualTo(TableOwner.APP);
    }

    @Test
    @DisplayName("preHandle sets context and afterCompletion clears it (governance route)")
    void preHandleAndAfterCompletion_lifecycle() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/settings");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(DbOwnerContext.getOwner()).isEqualTo(TableOwner.ADMIN);

        interceptor.afterCompletion(request, response, new Object(), null);
        assertThat(DbOwnerContext.getOwner()).isNull();
    }

    @Test
    @DisplayName("preHandle sets APP context for admin business endpoint (table ownership wins over audience)")
    void preHandle_adminBusinessEndpoint_appContext() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/problems");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(DbOwnerContext.getOwner()).isEqualTo(TableOwner.APP);
    }
}
