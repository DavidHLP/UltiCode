package com.ulticode.notification.dubbo.provider;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.ulticode.notification.api.dto.NotificationUserReferenceCountDTO;
import com.ulticode.notification.api.service.NotificationReconciliationReadPort;
import com.ulticode.modules.notification.mapper.NotificationReconciliationReadMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DisplayName("Notification reconciliation owner facts IT")
class NotificationReconciliationReadProviderIT {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_notification_reconciliation_test")
            .withUsername("test")
            .withPassword("test");

    private static HikariDataSource dataSource;
    private static SqlSession session;
    private static NotificationReconciliationReadProvider provider;

    @BeforeAll
    static void provision() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE notifications (
                  id VARCHAR(40) NOT NULL,
                  user_id VARCHAR(40) NOT NULL,
                  created_at DATETIME(3) NOT NULL,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
            statement.execute("INSERT INTO notifications (id, user_id, created_at) VALUES " +
                    "('n-1', 'user-1', '2026-08-01 10:00:00.000'), " +
                    "('n-2', 'user-1', '2026-08-02 10:00:00.000'), " +
                    "('n-3', 'user-2', '2026-08-03 10:00:00.000')");
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(MYSQL.getJdbcUrl());
        config.setUsername(MYSQL.getUsername());
        config.setPassword(MYSQL.getPassword());
        config.setMaximumPoolSize(2);
        dataSource = new HikariDataSource(config);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment(
                "test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(NotificationReconciliationReadMapper.class);
        SqlSessionFactory sessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);
        session = sessionFactory.openSession(true);
        NotificationReconciliationReadMapper mapper = session.getMapper(NotificationReconciliationReadMapper.class);
        provider = new NotificationReconciliationReadProvider(mapper);
    }

    @AfterAll
    static void cleanup() {
        if (session != null) {
            session.close();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    @DisplayName("full and incremental windows return grouped owner facts")
    void fullAndIncrementalWindowsReturnGroupedFacts() {
        List<NotificationUserReferenceCountDTO> full = provider.findUserReferenceCounts(
                "", null, NotificationReconciliationReadPort.MAX_PAGE_SIZE);
        assertThat(full).extracting(NotificationUserReferenceCountDTO::accountId)
                .containsExactly("user-1", "user-2");
        assertThat(full).extracting(NotificationUserReferenceCountDTO::rowCount)
                .containsExactly(2L, 1L);

        List<NotificationUserReferenceCountDTO> incremental = provider.findUserReferenceCounts(
                "", LocalDateTime.of(2026, 8, 2, 0, 0),
                NotificationReconciliationReadPort.MAX_PAGE_SIZE);
        assertThat(incremental).extracting(NotificationUserReferenceCountDTO::accountId)
                .containsExactly("user-1", "user-2");
        assertThat(incremental).extracting(NotificationUserReferenceCountDTO::rowCount)
                .containsExactly(1L, 1L);
    }

    @Test
    @DisplayName("cursor excludes earlier account groups")
    void cursorExcludesEarlierAccountGroups() {
        assertThat(provider.findUserReferenceCounts(
                "user-1", null, NotificationReconciliationReadPort.MAX_PAGE_SIZE))
                .extracting(NotificationUserReferenceCountDTO::accountId)
                .containsExactly("user-2");
    }
}
