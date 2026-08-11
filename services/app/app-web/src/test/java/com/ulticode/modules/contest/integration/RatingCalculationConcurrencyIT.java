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
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CONTEST-005: MySQL evidence for cross-contest rating serialization.
 *
 * <p>The production calculation claims one receipt per contest and locks the
 * shared global-ranking rows in deterministic user-id order. This test uses
 * the same SQL shape and verifies that two contest calculations both survive
 * when they update the same user concurrently.</p>
 */
@Testcontainers
class RatingCalculationConcurrencyIT {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_rating_concurrency_it")
            .withUsername("root")
            .withPassword("root");

    @BeforeAll
    static void createSchema() throws SQLException {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE global_rankings (
                  id varchar(40) NOT NULL,
                  user_id varchar(40) NOT NULL,
                  username varchar(120) NOT NULL,
                  global_rank int NOT NULL DEFAULT 1,
                  rating int NOT NULL DEFAULT 1500,
                  max_rating int NOT NULL DEFAULT 1500,
                  contests_attended int NOT NULL DEFAULT 0,
                  contests_rated int NOT NULL DEFAULT 0,
                  last_contest_id varchar(40) DEFAULT NULL,
                  updated_at datetime(3) NOT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY global_rankings_user_id_key (user_id)
                ) ENGINE=InnoDB
                """);
            statement.execute("""
                CREATE TABLE contest_rating_calculations (
                  id varchar(40) NOT NULL,
                  contest_id varchar(40) NOT NULL,
                  calculated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_contest_rating_calculations_contest_id (contest_id)
                ) ENGINE=InnoDB
                """);
            statement.executeUpdate("""
                INSERT INTO global_rankings
                    (id, user_id, username, updated_at)
                VALUES ('ranking-1', 'user-1', 'user-1', NOW(3))
                """);
        }
    }

    @AfterEach
    void clearReceiptsAndResetRating() throws SQLException {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM contest_rating_calculations");
            statement.executeUpdate("UPDATE global_rankings SET rating = 1500, contests_attended = 0, "
                    + "contests_rated = 0, last_contest_id = NULL");
        }
    }

    @Test
    void concurrentDifferentContestCalculationsDoNotLoseSharedUserUpdate() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger lockedOrder = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = List.of(
                    pool.submit(() -> applyRating("contest-a", "receipt-a", ready, start, lockedOrder)),
                    pool.submit(() -> applyRating("contest-b", "receipt-b", ready, start, lockedOrder)));
            ready.await();
            start.countDown();

            assertThat(results).allSatisfy(result -> assertThat(result.get()).isTrue());
        } finally {
            pool.shutdownNow();
        }

        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT rating, contests_attended, contests_rated FROM global_rankings "
                             + "WHERE user_id = 'user-1'")) {
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt("rating")).isEqualTo(1502);
                assertThat(result.getInt("contests_attended")).isEqualTo(2);
                assertThat(result.getInt("contests_rated")).isEqualTo(2);
            }
        }

        assertThat(count("contest_rating_calculations")).isEqualTo(2);
    }

    @Test
    void duplicateContestReceiptIsIdempotent() throws SQLException {
        assertThat(insertReceipt("receipt-a", "contest-a")).isEqualTo(1);
        assertThat(insertReceipt("receipt-retry", "contest-a")).isEqualTo(0);
        assertThat(count("contest_rating_calculations")).isEqualTo(1);
    }

    private boolean applyRating(String contestId, String receiptId,
                                CountDownLatch ready, CountDownLatch start,
                                AtomicInteger lockedOrder) throws Exception {
        ready.countDown();
        start.await();

        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                if (insertReceipt(connection, receiptId, contestId) == 0) {
                    connection.commit();
                    return false;
                }

                int order = lockedOrder.getAndIncrement();
                int currentRating;
                try (PreparedStatement lock = connection.prepareStatement(
                        "SELECT user_id, rating FROM global_rankings "
                                + "WHERE user_id IN (?) ORDER BY user_id FOR UPDATE")) {
                    lock.setString(1, "user-1");
                    try (ResultSet result = lock.executeQuery()) {
                        assertThat(result.next()).isTrue();
                        currentRating = result.getInt("rating");
                    }
                }

                if (order == 0) {
                    Thread.sleep(100);
                }

                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE global_rankings SET rating = ?, max_rating = GREATEST(max_rating, ?), "
                                + "contests_attended = contests_attended + 1, "
                                + "contests_rated = contests_rated + 1, last_contest_id = ?, updated_at = NOW(3) "
                                + "WHERE user_id = ?")) {
                    update.setInt(1, currentRating + 1);
                    update.setInt(2, currentRating + 1);
                    update.setString(3, contestId);
                    update.setString(4, "user-1");
                    assertThat(update.executeUpdate()).isEqualTo(1);
                }
                connection.commit();
                return true;
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    private static int insertReceipt(String id, String contestId) throws SQLException {
        try (Connection connection = open()) {
            return insertReceipt(connection, id, contestId);
        }
    }

    private static int insertReceipt(Connection connection, String id, String contestId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT IGNORE INTO contest_rating_calculations (id, contest_id) VALUES (?, ?)")) {
            statement.setString(1, id);
            statement.setString(2, contestId);
            return statement.executeUpdate();
        }
    }

    private static int count(String table) throws SQLException {
        try (Connection connection = open();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        }
    }

    private static Connection open() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", "root");
    }
}
