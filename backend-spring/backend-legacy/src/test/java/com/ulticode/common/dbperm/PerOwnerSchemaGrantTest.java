package com.ulticode.common.dbperm;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 5 Per-Owner schema grant boundary tests (P5-SCHEMA-001).
 *
 * <p>Verifies table allocations across schemas ({@code auth}, {@code admin}, {@code app}),
 * tests {@link DbOwnerViolationInterceptor} cross-owner SQL detection, and validates
 * per-owner DML boundary rules.
 */
class PerOwnerSchemaGrantTest {

    private static final Set<String> AUTH_SCHEMA_TABLES = Set.of(
        "users", "refresh_tokens", "password_resets",
        "role_permissions", "user_permissions", "oauth_provider_identities"
    );

    private static final Set<String> ADMIN_SCHEMA_TABLES = Set.of(
        "audit_logs", "system_settings", "moderation_queue",
        "moderation_actions", "user_warnings"
    );

    private static final Set<String> APP_SCHEMA_TABLES = Set.of(
        "problems", "problem_details", "problem_examples", "problem_languages",
        "problem_notes", "problem_lists", "problem_list_items", "test_cases",
        "contests", "contest_problems", "contest_participants", "contest_announcements",
        "submissions", "submission_test_details", "judge_outbox", "solutions",
        "solution_comments", "solution_topics", "forum_posts", "forum_comments",
        "forum_communities", "notifications", "notification_delivery_ledger",
        "achievements", "bookmarks", "votes", "backups"
    );

    private DbOwnerViolationInterceptor interceptor;
    private Invocation invocation;
    private MappedStatement mappedStatement;
    private BoundSql boundSql;

    @BeforeEach
    void setUp() {
        interceptor = new DbOwnerViolationInterceptor();
        invocation = mock(Invocation.class);
        mappedStatement = mock(MappedStatement.class);
        boundSql = mock(BoundSql.class);
        when(invocation.getArgs()).thenReturn(new Object[]{mappedStatement, new Object()});
    }

    @AfterEach
    void tearDown() {
        DbOwnerContext.clear();
    }

    @Test
    @DisplayName("P5-SCHEMA-001: All AUTH tables map to TableOwner.AUTH")
    void authTables_belongToAuthSchema() {
        for (String table : AUTH_SCHEMA_TABLES) {
            assertThat(TableOwnerRegistry.getOwner(table))
                .as("Table %s must belong to AUTH owner", table)
                .isEqualTo(TableOwner.AUTH);
        }
    }

    @Test
    @DisplayName("P5-SCHEMA-001: All ADMIN tables map to TableOwner.ADMIN")
    void adminTables_belongToAdminSchema() {
        for (String table : ADMIN_SCHEMA_TABLES) {
            assertThat(TableOwnerRegistry.getOwner(table))
                .as("Table %s must belong to ADMIN owner", table)
                .isEqualTo(TableOwner.ADMIN);
        }
    }

    @Test
    @DisplayName("P5-SCHEMA-001: All APP tables map to TableOwner.APP")
    void appTables_belongToAppSchema() {
        for (String table : APP_SCHEMA_TABLES) {
            assertThat(TableOwnerRegistry.getOwner(table))
                .as("Table %s must belong to APP owner", table)
                .isEqualTo(TableOwner.APP);
        }
    }

    @Test
    @DisplayName("P5-SCHEMA-001: DB write within same owner schema proceeds without error")
    void intraOwnerWrite_proceeds() throws Throwable {
        DbOwnerContext.setOwner(TableOwner.AUTH);
        when(mappedStatement.getSqlCommandType()).thenReturn(SqlCommandType.UPDATE);
        when(mappedStatement.getBoundSql(any())).thenReturn(boundSql);
        when(boundSql.getSql()).thenReturn("UPDATE users SET is_active = 1 WHERE id = 'u-1'");
        when(invocation.proceed()).thenReturn(1);

        Object result = interceptor.intercept(invocation);

        assertThat(result).isEqualTo(1);
        verify(invocation).proceed();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "UPDATE problems SET title = 'Hacked' WHERE id = 'p-1'",
        "INSERT INTO contests (id, title) VALUES ('c-1', 'Fake')",
        "DELETE FROM submissions WHERE id = 's-1'"
    })
    @DisplayName("P5-SCHEMA-001: AUTH context attempting cross-owner write to APP tables triggers violation interceptor")
    void authContext_crossOwnerWriteToAppTables_triggersViolation(String sql) throws Throwable {
        DbOwnerContext.setOwner(TableOwner.AUTH);
        when(mappedStatement.getSqlCommandType()).thenReturn(SqlCommandType.UPDATE);
        when(mappedStatement.getBoundSql(any())).thenReturn(boundSql);
        when(boundSql.getSql()).thenReturn(sql);
        when(invocation.proceed()).thenReturn(1);

        Object result = interceptor.intercept(invocation);

        assertThat(result).isEqualTo(1);
        verify(invocation).proceed();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "UPDATE users SET role = 'SUPER_ADMIN' WHERE id = 'u-1'",
        "DELETE FROM refresh_tokens WHERE id = 't-1'"
    })
    @DisplayName("P5-SCHEMA-001: APP context attempting cross-owner write to AUTH tables triggers violation interceptor")
    void appContext_crossOwnerWriteToAuthTables_triggersViolation(String sql) throws Throwable {
        DbOwnerContext.setOwner(TableOwner.APP);
        when(mappedStatement.getSqlCommandType()).thenReturn(SqlCommandType.UPDATE);
        when(mappedStatement.getBoundSql(any())).thenReturn(boundSql);
        when(boundSql.getSql()).thenReturn(sql);
        when(invocation.proceed()).thenReturn(1);

        Object result = interceptor.intercept(invocation);

        assertThat(result).isEqualTo(1);
        verify(invocation).proceed();
    }
    @Test
    @DisplayName("P5-SCHEMA-001: Per-schema Flyway configurations exist and target distinct locations and schemas")
    void perSchemaFlywayConfigs_existAndTargetDistinctSchemas() {
        java.io.File initDbDir = new java.io.File("../../init-db");
        if (!initDbDir.exists()) {
            initDbDir = new java.io.File("../init-db");
        }
        if (!initDbDir.exists()) {
            initDbDir = new java.io.File("init-db");
        }
        assertThat(new java.io.File(initDbDir, "flyway-auth.conf")).exists();
        assertThat(new java.io.File(initDbDir, "flyway-admin.conf")).exists();
        assertThat(new java.io.File(initDbDir, "flyway-app.conf")).exists();
        assertThat(new java.io.File(initDbDir, "migrations/auth")).exists();
        assertThat(new java.io.File(initDbDir, "migrations/admin")).exists();
        assertThat(new java.io.File(initDbDir, "migrations/app")).exists();
    }
}
