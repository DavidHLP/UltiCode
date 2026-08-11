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
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CONTEST-004: executable InnoDB evidence for durable contest admission keys,
 * capacity claiming and submission identity uniqueness.
 */
@Testcontainers
class ContestAdmissionIT {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_contest_admission_it")
            .withUsername("root")
            .withPassword("root");

    @BeforeAll
    static void createSchema() throws Exception {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE contests (
                  id varchar(40) NOT NULL,
                  status varchar(20) NOT NULL,
                  registered_count int NOT NULL DEFAULT 0,
                  max_participants int DEFAULT NULL,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB
                """);
            // Baseline shape from V20260602_120000. The test applies the real
            // CONTEST-004 migration instead of duplicating its generated columns.
            statement.execute("""
                CREATE TABLE contest_participants (
                  id varchar(40) NOT NULL,
                  contest_id varchar(40) NOT NULL,
                  user_id varchar(40) NOT NULL,
                  status varchar(20) NOT NULL,
                  is_virtual tinyint(1) NOT NULL DEFAULT 0,
                  virtual_session_id varchar(64) DEFAULT NULL,
                  registered_at datetime(3) NOT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY contest_participants_contest_id_user_id_virtual_session_id_key
                    (contest_id, user_id, virtual_session_id)
                ) ENGINE=InnoDB
                """);
            statement.execute("""
                CREATE TABLE contest_submissions (
                  id varchar(40) NOT NULL,
                  submission_id varchar(40) NOT NULL,
                  contest_id varchar(40) NOT NULL,
                  submitted_at datetime(3) NOT NULL,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB
                """);
            applyAdmissionMigration(connection);
        }
    }

    private static void applyAdmissionMigration(Connection connection) throws Exception {
        Path current = Path.of("").toAbsolutePath();
        Path migration = null;
        while (current != null && migration == null) {
            Path candidate = current.resolve(
                    "init-db/migrations/V20260810130000__Harden_Contest_Admission_And_Registration.sql");
            if (Files.exists(candidate)) {
                migration = candidate;
            }
            current = current.getParent();
        }
        assertThat(migration).as("CONTEST-004 migration path").isNotNull();
        String script = Files.readString(migration).replaceAll("(?m)--.*$", "");
        try (Statement statement = connection.createStatement()) {
            for (String sql : script.split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        }
    }

    @AfterEach
    void clearRows() throws SQLException {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM contest_submissions");
            statement.executeUpdate("DELETE FROM contest_participants");
            statement.executeUpdate("DELETE FROM contests");
        }
    }

    @Test
    void concurrentRealRegistrationClaimsOneCapacitySlot() throws Exception {
        insertContest(1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = List.of(
                    pool.submit(() -> register(ready, start, "user-1", "participant-1")),
                    pool.submit(() -> register(ready, start, "user-2", "participant-2")));
            ready.await();
            start.countDown();

            assertThat(get(results.get(0))).isNotEqualTo(get(results.get(1)));
            assertThat(results.stream().map(ContestAdmissionIT::get).toList())
                    .containsExactlyInAnyOrder("REGISTERED", "FULL");
        } finally {
            pool.shutdownNow();
        }

        assertThat(count("contest_participants")).isEqualTo(1);
        assertThat(contestRegisteredCount()).isEqualTo(1);
    }

    @Test
    void duplicateRealRegistrationOnFullContestIsIdentityConflict() throws Exception {
        insertContest(1);
        assertThat(register(new CountDownLatch(0), new CountDownLatch(0),
                "user-1", "participant-1")).isEqualTo("REGISTERED");

        assertThat(register(new CountDownLatch(0), new CountDownLatch(0),
                "user-1", "participant-duplicate")).isEqualTo("DUPLICATE");
        assertThat(count("contest_participants")).isEqualTo(1);
        assertThat(contestRegisteredCount()).isEqualTo(1);
    }

    @Test
    void virtualActiveKeyAllowsReplayAfterFinishButOnlyOneActiveSession() throws SQLException {
        insertContest(null);
        insertParticipant("virtual-1", "user-1", true, "FINISHED", "session-1");
        insertParticipant("virtual-2", "user-1", true, "STARTED", "session-2");

        assertThat(insertVirtual("virtual-3", "user-1", "session-3"))
                .isEqualTo("DUPLICATE");
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
                "UPDATE contest_participants SET status = 'FINISHED' WHERE id = 'virtual-2'")) {
            statement.executeUpdate();
        }
        assertThat(insertVirtual("virtual-3", "user-1", "session-3"))
                .isEqualTo("INSERTED");
        assertThat(count("contest_participants")).isEqualTo(3);
    }

    @Test
    void submissionMappingIdentityIsUnique() throws SQLException {
        insertContest(null);
        insertSubmission("contest-submission-1", "submission-1");
        assertThat(insertDuplicateSubmission("contest-submission-2", "submission-1"))
                .isEqualTo("DUPLICATE");
        assertThat(count("contest_submissions")).isEqualTo(1);
    }

    private static String register(CountDownLatch ready, CountDownLatch start,
                                   String userId, String participantId) throws Exception {
        ready.countDown();
        start.await();
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                // Match ContestMapper.selectByIdForUpdate used by registerForContest:
                // lifecycle transitions cannot pass the UPCOMING guard concurrently.
                try (PreparedStatement lock = connection.prepareStatement(
                        "SELECT status FROM contests WHERE id = 'contest-1' "
                                + "AND status = 'UPCOMING' FOR UPDATE")) {
                    try (ResultSet result = lock.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            return "CLOSED";
                        }
                    }
                }
                insertParticipant(connection, participantId, userId, false, "REGISTERED", null);
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE contests SET registered_count = registered_count + 1 "
                                + "WHERE id = 'contest-1' AND "
                                + "(max_participants IS NULL OR registered_count < max_participants)")) {
                    if (update.executeUpdate() == 0) {
                        connection.rollback();
                        return "FULL";
                    }
                }
                connection.commit();
                return "REGISTERED";
            } catch (SQLIntegrityConstraintViolationException duplicate) {
                connection.rollback();
                return "DUPLICATE";
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    private static String insertVirtual(String participantId, String userId, String sessionId)
            throws SQLException {
        try (Connection connection = open()) {
            try {
                insertParticipant(connection, participantId, userId, true, "STARTED", sessionId);
                return "INSERTED";
            } catch (SQLIntegrityConstraintViolationException duplicate) {
                return "DUPLICATE";
            }
        }
    }

    private static void insertParticipant(String id, String userId, boolean virtual,
                                          String status, String sessionId) throws SQLException {
        try (Connection connection = open()) {
            insertParticipant(connection, id, userId, virtual, status, sessionId);
        }
    }

    private static void insertParticipant(Connection connection, String id, String userId,
                                          boolean virtual, String status, String sessionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO contest_participants "
                        + "(id, contest_id, user_id, status, is_virtual, virtual_session_id, registered_at) "
                        + "VALUES (?, 'contest-1', ?, ?, ?, ?, ?)")) {
            statement.setString(1, id);
            statement.setString(2, userId);
            statement.setString(3, status);
            statement.setBoolean(4, virtual);
            statement.setString(5, sessionId);
            statement.setObject(6, LocalDateTime.now());
            statement.executeUpdate();
        }
    }

    private static void insertContest(Integer capacity) throws SQLException {
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO contests (id, status, max_participants) VALUES ('contest-1', 'UPCOMING', ?)")) {
            if (capacity == null) {
                statement.setNull(1, java.sql.Types.INTEGER);
            } else {
                statement.setInt(1, capacity);
            }
            statement.executeUpdate();
        }
    }

    private static void insertSubmission(String id, String submissionId) throws SQLException {
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO contest_submissions "
                        + "(id, submission_id, contest_id, submitted_at) "
                        + "VALUES (?, ?, 'contest-1', ?)")) {
            statement.setString(1, id);
            statement.setString(2, submissionId);
            statement.setObject(3, LocalDateTime.now());
            statement.executeUpdate();
        }
    }

    private static String insertDuplicateSubmission(String id, String submissionId) throws SQLException {
        try {
            insertSubmission(id, submissionId);
            return "INSERTED";
        } catch (SQLIntegrityConstraintViolationException duplicate) {
            return "DUPLICATE";
        }
    }

    private static int count(String table) throws SQLException {
        try (Connection connection = open(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        }
    }

    private static int contestRegisteredCount() throws SQLException {
        try (Connection connection = open(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT registered_count FROM contests WHERE id = 'contest-1'")) {
            result.next();
            return result.getInt(1);
        }
    }

    private static String get(Future<String> future) {
        try {
            return future.get();
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static Connection open() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", "root");
    }
}
