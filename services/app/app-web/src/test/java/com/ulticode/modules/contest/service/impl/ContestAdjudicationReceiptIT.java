package com.ulticode.modules.contest.service.impl;

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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CONTEST-002: MySQL evidence for the adjudication fence and race-sensitive
 * contest writes. The unit seam covers policy; this class covers InnoDB.
 */
@Testcontainers
class ContestAdjudicationReceiptIT {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_contest_adjudication_it")
            .withUsername("root")
            .withPassword("root");

    @BeforeAll
    static void createSchema() throws SQLException {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE contest_submissions (
                  id varchar(40) NOT NULL,
                  submission_id varchar(40) NOT NULL,
                  submitted_at datetime(3) NOT NULL,
                  is_accepted tinyint(1) NOT NULL DEFAULT 0,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_contest_submission (submission_id)
                ) ENGINE=InnoDB
                """);
            statement.execute("""
                CREATE TABLE contest_participants (
                  id varchar(40) NOT NULL,
                  attempt_count int NOT NULL DEFAULT 0,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB
                """);
            statement.execute("""
                CREATE TABLE contest_adjudication_receipts (
                  id varchar(40) NOT NULL,
                  submission_id varchar(40) NOT NULL,
                  generation bigint NOT NULL,
                  verdict varchar(30) NOT NULL,
                  is_accepted tinyint(1) NOT NULL,
                  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  PRIMARY KEY (id),
                  UNIQUE KEY uniq_contest_adjudication_receipt (submission_id, generation)
                ) ENGINE=InnoDB
                """);
            statement.execute("""
                CREATE TABLE first_solve_records (
                  id varchar(40) NOT NULL,
                  contest_id varchar(40) NOT NULL,
                  problem_id bigint NOT NULL,
                  user_id varchar(40) NOT NULL,
                  solved_at datetime(3) NOT NULL,
                  time_spent int NOT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_first_solve (contest_id, problem_id)
                ) ENGINE=InnoDB
                """);
        }
    }

    @AfterEach
    void clearRows() throws SQLException {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM first_solve_records");
            statement.executeUpdate("DELETE FROM contest_adjudication_receipts");
            statement.executeUpdate("DELETE FROM contest_participants");
            statement.executeUpdate("DELETE FROM contest_submissions");
        }
    }

    @Test
    void duplicateReceiptForSameGenerationIsOneCommittedRow() throws SQLException {
        try (Connection connection = open()) {
            insertSubmissionAndParticipant(connection);
            assertThat(insertReceipt(connection, "receipt-1", 7)).isEqualTo(1);
            assertThat(insertReceipt(connection, "receipt-2", 7)).isEqualTo(0);
        }

        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM contest_adjudication_receipts WHERE submission_id = ?")) {
            statement.setString(1, "submission-1");
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void concurrentSameGenerationAppliesParticipantAttemptOnce() throws Exception {
        try (Connection connection = open()) {
            insertSubmissionAndParticipant(connection);
        }

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return applyReceiptAndAttempt(7);
                }));
            }
            ready.await();
            start.countDown();

            assertThat(results.get(0).get()).isNotEqualTo(results.get(1).get());
            assertThat(results.stream().filter(future -> get(future)).count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT attempt_count FROM contest_participants WHERE id = 'participant-1'")) {
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void concurrentFirstSolveInsertHasOneWinner() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = List.of(
                    pool.submit(() -> insertFirstSolve(ready, start, "first-solve-1", "user-1")),
                    pool.submit(() -> insertFirstSolve(ready, start, "first-solve-2", "user-2")));
            ready.await();
            start.countDown();

            assertThat(get(results.get(0))).isNotEqualTo(get(results.get(1)));
            assertThat(results.stream().filter(ContestAdjudicationReceiptIT::get).count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean applyReceiptAndAttempt(long generation) throws SQLException {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement lock = connection.prepareStatement(
                        "SELECT id FROM contest_submissions WHERE submission_id = ? FOR UPDATE")) {
                    lock.setString(1, "submission-1");
                    try (ResultSet ignored = lock.executeQuery()) {
                        if (!ignored.next()) {
                            connection.rollback();
                            return false;
                        }
                    }
                }

                long latest = 0;
                try (PreparedStatement latestStatement = connection.prepareStatement(
                        "SELECT generation FROM contest_adjudication_receipts "
                                + "WHERE submission_id = ? ORDER BY generation DESC LIMIT 1 FOR UPDATE")) {
                    latestStatement.setString(1, "submission-1");
                    try (ResultSet result = latestStatement.executeQuery()) {
                        if (result.next()) {
                            latest = result.getLong(1);
                        }
                    }
                }
                if (latest >= generation) {
                    connection.commit();
                    return false;
                }

                int inserted = insertReceipt(connection, "receipt-" + generation, generation);
                if (inserted == 0) {
                    connection.commit();
                    return false;
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE contest_participants SET attempt_count = attempt_count + 1 "
                                + "WHERE id = 'participant-1'")) {
                    update.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    private boolean insertFirstSolve(CountDownLatch ready, CountDownLatch start,
                                      String id, String userId) throws Exception {
        ready.countDown();
        start.await();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO first_solve_records "
                             + "(id, contest_id, problem_id, user_id, solved_at, time_spent) "
                             + "VALUES (?, 'contest-1', 100, ?, NOW(3), 0)")) {
            statement.setString(1, id);
            statement.setString(2, userId);
            statement.executeUpdate();
            return true;
        } catch (SQLException failure) {
            if (failure.getErrorCode() == 1062) {
                return false;
            }
            throw failure;
        }
    }

    private static int insertReceipt(Connection connection, String id, long generation)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT IGNORE INTO contest_adjudication_receipts "
                        + "(id, submission_id, generation, verdict, is_accepted) "
                        + "VALUES (?, 'submission-1', ?, 'Accepted', 1)")) {
            statement.setString(1, id);
            statement.setLong(2, generation);
            return statement.executeUpdate();
        }
    }

    private static void insertSubmissionAndParticipant(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO contest_submissions "
                    + "(id, submission_id, submitted_at) VALUES "
                    + "('contest-submission-1', 'submission-1', NOW(3))");
            statement.executeUpdate("INSERT INTO contest_participants (id) VALUES ('participant-1')");
        }
    }

    private static Connection open() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", "root");
    }

    private static boolean get(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }
}
