package com.ulticode.modules.lease;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.ulticode.common.lease.FencedLease;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Real MySQL proof for duplicate runners, expiry, crash recovery and stale tokens. */
@Testcontainers
@DisplayName("P3-LEASE-001: fenced lease MySQL IT")
class FencedJobLeaseIT {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_fenced_lease_test")
            .withUsername("test")
            .withPassword("test");

    private static HikariDataSource dataSource;
    private static SqlSessionFactory sessionFactory;

    @BeforeAll
    static void provision() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE fenced_job_leases (
                  lease_name varchar(120) NOT NULL,
                  fence_token bigint NOT NULL,
                  owner_token varchar(120) DEFAULT NULL,
                  leased_until datetime(3) DEFAULT NULL,
                  updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  PRIMARY KEY (lease_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        }
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(MYSQL.getJdbcUrl());
        hikari.setUsername(MYSQL.getUsername());
        hikari.setPassword(MYSQL.getPassword());
        hikari.setMaximumPoolSize(4);
        dataSource = new HikariDataSource(hikari);

        Configuration configuration = new Configuration();
        configuration.setEnvironment(new Environment(
                "test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(FencedJobLeaseMapper.class);
        sessionFactory = new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(configuration);
    }

    @AfterAll
    static void cleanup() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    @DisplayName("two runners leave exactly one live owner")
    void twoRunnersHaveOneWinner() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (SqlSession firstSession = sessionFactory.openSession(true);
             SqlSession secondSession = sessionFactory.openSession(true)) {
            FencedJobLeaseService first = new FencedJobLeaseService(
                    firstSession.getMapper(FencedJobLeaseMapper.class), Clock.systemUTC());
            FencedJobLeaseService second = new FencedJobLeaseService(
                    secondSession.getMapper(FencedJobLeaseMapper.class), Clock.systemUTC());
            List<Future<FencedLease>> futures = new ArrayList<>();
            futures.add(pool.submit(() -> first.tryAcquire("it:duplicate", Duration.ofSeconds(5))));
            futures.add(pool.submit(() -> second.tryAcquire("it:duplicate", Duration.ofSeconds(5))));

            List<FencedLease> winners = new ArrayList<>();
            for (Future<FencedLease> future : futures) {
                FencedLease lease = future.get();
                if (lease != null) {
                    winners.add(lease);
                }
            }
            assertThat(winners).hasSize(1);
            assertThat(winners.get(0).fenceToken()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("expiry gives a crashed replacement a higher token and blocks stale release")
    void expiryFencesCrashedRunner() throws Exception {
        try (SqlSession firstSession = sessionFactory.openSession(true);
             SqlSession secondSession = sessionFactory.openSession(true)) {
            FencedJobLeaseMapper firstMapper = firstSession.getMapper(FencedJobLeaseMapper.class);
            FencedJobLeaseService first = new FencedJobLeaseService(firstMapper, Clock.systemUTC());
            FencedJobLeaseService second = new FencedJobLeaseService(
                    secondSession.getMapper(FencedJobLeaseMapper.class), Clock.systemUTC());

            FencedLease oldLease = first.tryAcquire("it:expiry", Duration.ofSeconds(5));
            assertThat(oldLease).isNotNull();
            try (Connection connection = DriverManager.getConnection(
                    MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE fenced_job_leases "
                        + "SET leased_until = DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 SECOND) "
                        + "WHERE lease_name = 'it:expiry'");
            }

            FencedLease replacement = second.tryAcquire("it:expiry", Duration.ofSeconds(5));
            assertThat(replacement).isNotNull();
            assertThat(replacement.fenceToken()).isEqualTo(oldLease.fenceToken() + 1);
            assertThat(first.isHeld(oldLease)).isFalse();
            assertThat(first.release(oldLease)).isFalse();
        }
    }
}
