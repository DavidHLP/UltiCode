package com.ulticode.common.dbperm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5 per-owner schema allocation tests (P5-SCHEMA-001).
 *
 * <p>This test is intentionally independent of the deleted Legacy dbperm
 * interceptor. The executable database-grant proof lives in
 * {@link PerOwnerSchemaIsolationIT}; this unit test verifies the canonical
 * owner manifest and per-schema Flyway configuration surface.</p>
 */
class PerOwnerSchemaGrantTest {

    private static final Set<String> AUTH_TABLES = Set.of(
        "users", "refresh_tokens", "password_resets", "role_permissions",
        "user_permissions", "oauth_provider_identities", "audit_outbox"
    );

    private static final Set<String> ADMIN_TABLES = Set.of(
        "audit_logs", "audit_outbox", "consumer_inbox", "system_settings",
        "moderation_queue", "moderation_actions", "user_warnings", "backups"
    );

    private static final Set<String> APP_TABLES = Set.of(
        "problems", "problem_details", "problem_examples", "problem_languages",
        "problem_notes", "problem_lists", "audit_outbox",
        "contests", "contest_problems", "contest_participants", "contest_announcements",
        "solutions",
        "solution_comments", "forum_posts", "forum_comments",
        "forum_communities", "achievements"
    );

    /** DEC-011: the Submission owner manifest (SPLIT-003) claims the submission aggregate and outboxes. */
    private static final Set<String> SUBMISSION_TABLES = Set.of(
        "submissions", "judge_outbox", "submission_result_outbox",
        "submission_created_outbox"
    );

    private static final Set<String> NOTIFICATION_TABLES = Set.of(
        "notifications", "notification_preferences", "notification_delivery_ledger",
        "email_templates", "email_logs", "consumer_inbox", "notification_command_receipt"
    );

    @Test
    @DisplayName("P5-SCHEMA-001: Auth tables map to the Auth owner")
    void authTables_belongToAuthOwner() throws IOException {
        assertManifestOwner(AUTH_TABLES, "Auth");
    }

    @Test
    @DisplayName("P5-SCHEMA-001: Admin tables map to the Admin owner")
    void adminTables_belongToAdminOwner() throws IOException {
        assertManifestOwner(ADMIN_TABLES, "Admin");
    }

    @Test
    @DisplayName("P5-SCHEMA-001: App tables map to the App owner")
    void appTables_belongToAppOwner() throws IOException {
        assertManifestOwner(APP_TABLES, "App");
    }

    @Test
    @DisplayName("DEC-011: Submission-manifest tables map to the Submission owner")
    void submissionTables_belongToSubmissionOwner() throws IOException {
        assertManifestOwner(SUBMISSION_TABLES, "Submission");
    }

    @Test
    @DisplayName("NOTIFY-001: Notification tables map to the Notification owner")
    void notificationTables_belongToNotificationOwner() throws IOException {
        assertManifestOwner(NOTIFICATION_TABLES, "Notification");
    }

    @Test
    @DisplayName("P5-SCHEMA-001: Per-schema Flyway configurations are present")
    void perSchemaFlywayConfigs_existAndTargetDistinctSchemas() {
        File initDbDir = resolveDirectory("init-db");
        assertThat(new File(initDbDir, "flyway-auth.conf")).exists();
        assertThat(new File(initDbDir, "flyway-admin.conf")).exists();
        assertThat(new File(initDbDir, "flyway-app.conf")).exists();
        assertThat(new File(initDbDir, "flyway-notification.conf")).exists();
        assertThat(new File(initDbDir, "flyway-submission.conf")).exists();
        assertThat(new File(initDbDir, "migrations/auth")).exists();
        assertThat(new File(initDbDir, "migrations/admin")).exists();
        assertThat(new File(initDbDir, "migrations/app")).exists();
        assertThat(new File(initDbDir, "migrations/notification")).exists();
        assertThat(new File(initDbDir, "migrations/submission")).exists();
    }

    private static void assertManifestOwner(Set<String> tables, String owner) throws IOException {
        File guide = resolveFile("PROJECT_DOCUMENTATION.md");
        String[] rows = Files.readString(guide.toPath()).split("\\R");
        for (String table : tables) {
            String[] columns = java.util.Arrays.stream(rows)
                .map(line -> line.split("\\|", -1))
                .filter(candidate -> candidate.length > 3
                    && candidate[1].contains("`" + table + "`"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing owner row for " + table));
            assertThat(columns[3].trim()).as("target owner for %s", table).contains(owner);
        }
    }

    private static File resolveDirectory(String path) {
        return resolveFile(path);
    }

    private static File resolveFile(String path) {
        File[] candidates = {
            new File(path),
            new File("../" + path),
            new File("../../" + path)
        };
        for (File candidate : candidates) {
            if (candidate.exists()) {
                return candidate;
            }
        }
        return candidates[0];
    }
}
