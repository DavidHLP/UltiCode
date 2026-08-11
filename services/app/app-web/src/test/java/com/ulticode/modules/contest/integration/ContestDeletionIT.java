package com.ulticode.modules.contest.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CONTEST-006: executable MySQL evidence for the owner delete transaction.
 *
 * <p>The SQL order mirrors {@code ContestCascadeMapper} and the parent lock in
 * {@code ContestLifecycleServiceImpl}. This deliberately tests InnoDB/FK
 * behavior directly; Spring/MyBatis wiring remains part of CONTEST-008.</p>
 */
@Testcontainers
class ContestDeletionIT {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_contest_deletion_it")
            .withUsername("root")
            .withPassword("root");

    @BeforeAll
    static void createSchema() throws Exception {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE contests (
                  id varchar(40) NOT NULL,
                  status varchar(20) NOT NULL,
                  is_deleted tinyint(1) NOT NULL DEFAULT 0,
                  deleted_at datetime(3) DEFAULT NULL,
                  deleted_by varchar(40) DEFAULT NULL,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB
                """);
            statement.execute("""
                CREATE TABLE virtual_contest_sessions (
                  id varchar(40) NOT NULL,
                  contest_id varchar(40) NOT NULL,
                  user_id varchar(40) NOT NULL,
                  status varchar(20) NOT NULL,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB
                """);
            statement.execute("""
                CREATE TABLE contest_analytics (
                  id varchar(40) NOT NULL,
                  contest_id varchar(40) NOT NULL,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB
                """);
            statement.execute("""
                CREATE TABLE contest_announcements (
                  id varchar(40) NOT NULL,
                  contest_id varchar(40) NOT NULL,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB
                """);
            statement.execute("""
                CREATE TABLE contest_participants (
                  id varchar(40) NOT NULL,
                  contest_id varchar(40) NOT NULL,
                  user_id varchar(40) NOT NULL,
                  status varchar(20) NOT NULL,
                  is_virtual tinyint(1) NOT NULL DEFAULT 0,
                  virtual_session_id varchar(40) DEFAULT NULL,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB
                """);
            statement.execute("""
                CREATE TABLE contest_problems (
                  id varchar(40) NOT NULL,
                  contest_id varchar(40) NOT NULL,
                  problem_id bigint NOT NULL,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB
                """);
            statement.execute("""
                CREATE TABLE contest_rankings (
                  id varchar(40) NOT NULL,
                  contest_id varchar(40) NOT NULL,
                  user_id varchar(40) NOT NULL,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB
                """);
            statement.execute("""
                CREATE TABLE contest_submissions (
                  id varchar(40) NOT NULL,
                  submission_id varchar(40) NOT NULL,
                  contest_id varchar(40) NOT NULL,
                  contest_problem_id varchar(40) NOT NULL,
                  participant_id varchar(40) NOT NULL,
                  virtual_session_id varchar(40) DEFAULT NULL,
                  submitted_at datetime(3) NOT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_contest_submission_submission_id (submission_id)
                ) ENGINE=InnoDB
                """);
            statement.execute("""
                CREATE TABLE contest_problem_results (
                  id varchar(40) NOT NULL,
                  contest_id varchar(40) NOT NULL,
                  contest_problem_id varchar(40) NOT NULL,
                  participant_id varchar(40) NOT NULL,
                  ranking_id varchar(40) DEFAULT NULL,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB
                """);
            statement.execute("""
                CREATE TABLE first_solve_records (
                  id varchar(40) NOT NULL,
                  contest_id varchar(40) NOT NULL,
                  problem_id bigint NOT NULL,
                  user_id varchar(40) NOT NULL,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB
                """);
            statement.execute("""
                CREATE TABLE contest_rating_calculations (
                  id varchar(40) NOT NULL,
                  contest_id varchar(40) NOT NULL,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB
                """);
            statement.execute("""
                CREATE TABLE contest_adjudication_receipts (
                  id varchar(40) NOT NULL,
                  submission_id varchar(40) NOT NULL,
                  generation bigint NOT NULL,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB
                """);
        }
        applyRelationalGuardMigration();
    }

    @AfterEach
    void clearRows() throws SQLException {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM contest_adjudication_receipts");
            statement.executeUpdate("DELETE FROM contest_problem_results");
            statement.executeUpdate("DELETE FROM contest_submissions");
            statement.executeUpdate("DELETE FROM first_solve_records");
            statement.executeUpdate("DELETE FROM contest_rating_calculations");
            statement.executeUpdate("DELETE FROM contest_rankings");
            statement.executeUpdate("DELETE FROM contest_analytics");
            statement.executeUpdate("DELETE FROM contest_announcements");
            statement.executeUpdate("DELETE FROM contest_participants");
            statement.executeUpdate("DELETE FROM virtual_contest_sessions");
            statement.executeUpdate("DELETE FROM contest_problems");
            statement.executeUpdate("DELETE FROM contests");
        }
    }

    @Test
    void relationalGuardMigrationCanBeRetriedWithoutDuplicateConstraintFailure() throws Exception {
        applyRelationalGuardMigration();
    }

    @Test
    void deleteContestRemovesEveryOwnedRelationAndSoftDeletesParent() throws Exception {
        insertFixture();

        deleteContest("contest-1", "admin-1");

        assertThat(parentState()).containsExactly(true, true);
        assertThat(List.of("contest_analytics", "contest_announcements", "contest_participants",
                "contest_problem_results", "contest_problems", "contest_rankings",
                "contest_submissions", "first_solve_records", "contest_rating_calculations",
                "contest_adjudication_receipts", "virtual_contest_sessions"))
                .allSatisfy(table -> assertThat(count(table)).as(table).isZero());
    }

    @Test
    void repeatedDeleteCleansLeftoverRowsAfterTheParentWasSoftDeleted() throws Exception {
        insertFixture();
        deleteContest("contest-1", "admin-1");

        // Simulate a legacy parent-only delete that left one relation behind.
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO contest_announcements (id, contest_id) VALUES (?, ?)")) {
            statement.setString(1, "announcement-retry");
            statement.setString(2, "contest-1");
            statement.executeUpdate();
        }

        deleteContest("contest-1", "admin-retry");

        assertThat(count("contest_announcements")).isZero();
        assertThat(parentState()).containsExactly(true, true);
    }

    @Test
    void compositeForeignKeysRejectCrossContestChildReferences() throws Exception {
        insertFixture();
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO contests (id, status) VALUES ('contest-2', 'UPCOMING')");
            statement.executeUpdate("INSERT INTO contest_problems (id, contest_id, problem_id) "
                    + "VALUES ('problem-2', 'contest-2', 200)");
            statement.executeUpdate("INSERT INTO contest_participants (id, contest_id, user_id, status) "
                    + "VALUES ('participant-2', 'contest-2', 'user-2', 'REGISTERED')");

            assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO contest_submissions "
                    + "(id, submission_id, contest_id, contest_problem_id, participant_id, virtual_session_id, submitted_at) "
                    + "VALUES ('cross-contest-submission', 'submission-cross', 'contest-1', 'problem-2', "
                    + "'participant-2', NULL, NOW(3))"))
                    .isInstanceOf(SQLIntegrityConstraintViolationException.class);

            statement.executeUpdate("INSERT INTO contest_rankings (id, contest_id, user_id) "
                    + "VALUES ('ranking-2', 'contest-2', 'user-2')");
            assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO contest_problem_results "
                    + "(id, contest_id, contest_problem_id, participant_id, ranking_id) "
                    + "VALUES ('cross-contest-result', 'contest-1', 'problem-1', 'participant-1', 'ranking-2')"))
                    .isInstanceOf(SQLIntegrityConstraintViolationException.class);
        }
    }

    @Test
    void foreignKeysRejectNewContestOrphans() {
        assertThatThrownBy(() -> {
            try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO contest_participants (id, contest_id, user_id, status) VALUES (?, ?, ?, ?)")) {
                statement.setString(1, "orphan-participant");
                statement.setString(2, "missing-contest");
                statement.setString(3, "user-1");
                statement.setString(4, "REGISTERED");
                statement.executeUpdate();
            }
        }).isInstanceOf(SQLIntegrityConstraintViolationException.class);
    }

    private static void insertFixture() throws SQLException {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO contests (id, status) VALUES ('contest-1', 'UPCOMING')");
            statement.executeUpdate("INSERT INTO virtual_contest_sessions (id, contest_id, user_id, status) "
                    + "VALUES ('virtual-session-1', 'contest-1', 'user-2', 'IN_PROGRESS')");
            statement.executeUpdate("INSERT INTO contest_participants (id, contest_id, user_id, status) "
                    + "VALUES ('participant-1', 'contest-1', 'user-1', 'REGISTERED')");
            statement.executeUpdate("INSERT INTO contest_participants "
                    + "(id, contest_id, user_id, status, is_virtual, virtual_session_id) "
                    + "VALUES ('participant-virtual-1', 'contest-1', 'user-2', 'STARTED', 1, 'virtual-session-1')");
            statement.executeUpdate("INSERT INTO contest_problems (id, contest_id, problem_id) "
                    + "VALUES ('problem-1', 'contest-1', 100)");
            statement.executeUpdate("INSERT INTO contest_rankings (id, contest_id, user_id) "
                    + "VALUES ('ranking-1', 'contest-1', 'user-1')");
            statement.executeUpdate("INSERT INTO contest_analytics (id, contest_id) "
                    + "VALUES ('analytics-1', 'contest-1')");
            statement.executeUpdate("INSERT INTO contest_announcements (id, contest_id) "
                    + "VALUES ('announcement-1', 'contest-1')");
            statement.executeUpdate("INSERT INTO contest_submissions "
                    + "(id, submission_id, contest_id, contest_problem_id, participant_id, virtual_session_id, submitted_at) "
                    + "VALUES ('contest-submission-1', 'submission-1', 'contest-1', 'problem-1', 'participant-1', NULL, NOW(3))");
            statement.executeUpdate("INSERT INTO contest_problem_results "
                    + "(id, contest_id, contest_problem_id, participant_id, ranking_id) "
                    + "VALUES ('result-1', 'contest-1', 'problem-1', 'participant-1', 'ranking-1')");
            statement.executeUpdate("INSERT INTO first_solve_records (id, contest_id, problem_id, user_id) "
                    + "VALUES ('first-solve-1', 'contest-1', 100, 'user-1')");
            statement.executeUpdate("INSERT INTO contest_rating_calculations (id, contest_id) "
                    + "VALUES ('rating-receipt-1', 'contest-1')");
            statement.executeUpdate("INSERT INTO contest_adjudication_receipts (id, submission_id, generation) "
                    + "VALUES ('adjudication-receipt-1', 'submission-1', 1)");
        }
    }

    private static void deleteContest(String contestId, String deletedBy) throws SQLException {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                boolean deleted;
                String status;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT is_deleted, status FROM contests WHERE id = ? FOR UPDATE")) {
                    statement.setString(1, contestId);
                    try (ResultSet result = statement.executeQuery()) {
                        assertThat(result.next()).isTrue();
                        deleted = result.getBoolean("is_deleted");
                        status = result.getString("status");
                    }
                }
                if (!deleted && !"UPCOMING".equals(status) && !"FINISHED".equals(status)) {
                    throw new AssertionError("unexpected deletion status: " + status);
                }

                execute(connection, "DELETE FROM contest_adjudication_receipts "
                        + "WHERE submission_id IN (SELECT submission_id FROM contest_submissions WHERE contest_id = ?)", contestId);
                execute(connection, "DELETE FROM contest_problem_results WHERE contest_id = ?", contestId);
                execute(connection, "DELETE FROM contest_submissions WHERE contest_id = ?", contestId);
                execute(connection, "DELETE FROM first_solve_records WHERE contest_id = ?", contestId);
                execute(connection, "DELETE FROM contest_rankings WHERE contest_id = ?", contestId);
                execute(connection, "DELETE FROM contest_analytics WHERE contest_id = ?", contestId);
                execute(connection, "DELETE FROM contest_announcements WHERE contest_id = ?", contestId);
                execute(connection, "DELETE FROM contest_rating_calculations WHERE contest_id = ?", contestId);
                execute(connection, "DELETE FROM contest_participants WHERE contest_id = ?", contestId);
                execute(connection, "DELETE FROM virtual_contest_sessions WHERE contest_id = ?", contestId);
                execute(connection, "DELETE FROM contest_problems WHERE contest_id = ?", contestId);
                if (!deleted) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE contests SET is_deleted = 1, deleted_by = ? WHERE id = ? AND is_deleted = 0")) {
                        statement.setString(1, deletedBy);
                        statement.setString(2, contestId);
                        assertThat(statement.executeUpdate()).isEqualTo(1);
                    }
                }
                connection.commit();
            } catch (Throwable failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    private static void execute(Connection connection, String sql, String contestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, contestId);
            statement.executeUpdate();
        }
    }

    private static List<Boolean> parentState() throws SQLException {
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
                "SELECT is_deleted, deleted_by IS NOT NULL FROM contests WHERE id = 'contest-1'")) {
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return List.of(result.getBoolean(1), result.getBoolean(2));
            }
        }
    }

    private static int count(String table) throws SQLException {
        try (Connection connection = open(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        }
    }

    private static void applyRelationalGuardMigration() throws Exception {
        Path current = Path.of("").toAbsolutePath();
        Path migration = null;
        while (current != null && migration == null) {
            Path candidate = current.resolve(
                    "init-db/migrations/V20260810150000__Add_Contest_Relational_Guards.sql");
            if (Files.exists(candidate)) {
                migration = candidate;
            }
            current = current.getParent();
        }
        assertThat(migration).as("CONTEST-006 migration path").isNotNull();
        String script = Files.readString(migration).replaceAll("(?m)--.*$", "");
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            for (String sql : splitSqlStatements(script)) {
                statement.execute(sql);
            }
        }
    }

    private static List<String> splitSqlStatements(String script) {
        List<String> statements = new ArrayList<>();
        String delimiter = ";";
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("DELIMITER ")) {
                delimiter = trimmed.substring("DELIMITER ".length()).trim();
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(delimiter)) {
                String statement = current.toString();
                statement = statement.substring(0, statement.lastIndexOf(delimiter)).trim();
                if (!statement.isBlank()) {
                    statements.add(statement);
                }
                current.setLength(0);
            }
        }
        if (!current.toString().isBlank()) {
            statements.add(current.toString().trim());
        }
        return statements;
    }

    private static Connection open() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", "root");
    }
}
