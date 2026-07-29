package com.ulticode.common.dbperm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5 Per-Owner schema grant boundary tests (P5-SCHEMA-001).
 *
 * <p>Verifies table allocations across schemas ({@code auth}, {@code admin}, {@code app})
 * and enforces strict cross-schema access rejection rules for per-owner DB users.
 */
class PerOwnerSchemaGrantTest {

    private static final Set<String> AUTH_SCHEMAS_TABLES = Set.of(
        "users", "refresh_tokens", "password_resets",
        "role_permissions", "user_permissions", "oauth_provider_identities"
    );

    private static final Set<String> ADMIN_SCHEMAS_TABLES = Set.of(
        "audit_logs", "system_settings", "moderation_queue",
        "moderation_actions", "user_warnings"
    );

    private static final Set<String> APP_SCHEMAS_TABLES = Set.of(
        "problems", "problem_details", "problem_examples", "problem_languages",
        "problem_notes", "problem_lists", "problem_list_items", "test_cases",
        "contests", "contest_problems", "contest_participants", "contest_announcements",
        "submissions", "submission_test_details", "judge_outbox", "solutions",
        "solution_comments", "solution_topics", "forum_posts", "forum_comments",
        "forum_communities", "notifications", "notification_delivery_ledger",
        "achievements", "bookmarks", "votes", "backups"
    );

    @Test
    @DisplayName("P5-SCHEMA-001: All AUTH tables belong to 'auth' schema")
    void authTables_belongToAuthSchema() {
        for (String table : AUTH_SCHEMAS_TABLES) {
            assertThat(TableOwnerRegistry.getOwner(table))
                .as("Table %s must belong to AUTH owner", table)
                .isEqualTo(TableOwner.AUTH);
        }
    }

    @Test
    @DisplayName("P5-SCHEMA-001: All ADMIN tables belong to 'admin' schema")
    void adminTables_belongToAdminSchema() {
        for (String table : ADMIN_SCHEMAS_TABLES) {
            assertThat(TableOwnerRegistry.getOwner(table))
                .as("Table %s must belong to ADMIN owner", table)
                .isEqualTo(TableOwner.ADMIN);
        }
    }

    @Test
    @DisplayName("P5-SCHEMA-001: All APP tables belong to 'app' schema")
    void appTables_belongToAppSchema() {
        for (String table : APP_SCHEMAS_TABLES) {
            assertThat(TableOwnerRegistry.getOwner(table))
                .as("Table %s must belong to APP owner", table)
                .isEqualTo(TableOwner.APP);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"problems", "contests", "submissions", "forum_posts", "solutions"})
    @DisplayName("P5-SCHEMA-001: auth_rw is forbidden from accessing APP schema tables")
    void authRw_forbiddenFromAppTables(String appTable) {
        TableOwner owner = TableOwnerRegistry.getOwner(appTable);
        assertThat(owner).isNotEqualTo(TableOwner.AUTH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"users", "refresh_tokens", "password_resets"})
    @DisplayName("P5-SCHEMA-001: app_rw is forbidden from accessing AUTH schema tables")
    void appRw_forbiddenFromAuthTables(String authTable) {
        TableOwner owner = TableOwnerRegistry.getOwner(authTable);
        assertThat(owner).isNotEqualTo(TableOwner.APP);
    }

    @ParameterizedTest
    @ValueSource(strings = {"users", "refresh_tokens", "problems", "submissions"})
    @DisplayName("P5-SCHEMA-001: admin_rw is forbidden from accessing AUTH and APP schema tables")
    void adminRw_forbiddenFromAuthAndAppTables(String table) {
        TableOwner owner = TableOwnerRegistry.getOwner(table);
        assertThat(owner).isNotEqualTo(TableOwner.ADMIN);
    }
}
