package com.ulticode.app.dubbo.provider;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.ulticode.modules.dashboard.mapper.DashboardAdminMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the App-owned Dashboard SQL against a real owner schema. */
@Testcontainers
class DashboardAdminReadProviderIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("app")
            .withUsername("app_rw")
            .withPassword("app-pw");

    private static HikariDataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession session;
    private DashboardAdminReadProvider provider;

    @BeforeAll
    static void createSchema() throws Exception {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(mysql.getJdbcUrl());
        dataSource.setUsername(mysql.getUsername());
        dataSource.setPassword(mysql.getPassword());
        dataSource.setMaximumPoolSize(2);
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE problems (id BIGINT PRIMARY KEY, difficulty VARCHAR(32), "
                    + "is_active TINYINT NOT NULL, is_deleted TINYINT NOT NULL, created_at DATETIME NOT NULL, "
                    + "updated_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE contests (id VARCHAR(40) PRIMARY KEY, start_time DATETIME NOT NULL, "
                    + "end_time DATETIME NOT NULL, created_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE solutions (id VARCHAR(40) PRIMARY KEY, problem_id BIGINT NOT NULL, "
                    + "user_id VARCHAR(40) NOT NULL, title VARCHAR(200) NOT NULL, content TEXT NOT NULL, "
                    + "created_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE forum_posts (id VARCHAR(40) PRIMARY KEY, community_id VARCHAR(40) NOT NULL, "
                    + "user_id VARCHAR(40) NOT NULL, title VARCHAR(200) NOT NULL, content TEXT NOT NULL, "
                    + "created_at DATETIME NOT NULL)");
        }

        var configuration = new MybatisConfiguration();
        configuration.addMapper(DashboardAdminMapper.class);
        var factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        sqlSessionFactory = factoryBean.getObject();
    }

    @BeforeEach
    void setUp() throws Exception {
        session = sqlSessionFactory.openSession(false);
        try (var statement = session.getConnection().createStatement()) {
            statement.execute("DELETE FROM forum_posts");
            statement.execute("DELETE FROM solutions");
            statement.execute("DELETE FROM contests");
            statement.execute("DELETE FROM problems");
        }
        session.commit();
        provider = new DashboardAdminReadProvider(session.getMapper(DashboardAdminMapper.class));
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.rollback();
            session.close();
        }
    }

    @AfterAll
    static void closeDataSource() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void readsOnlyAppOwnedDashboardTables() throws Exception {
        try (var statement = session.getConnection().createStatement()) {
            statement.execute("INSERT INTO problems VALUES (1, 'EASY', 1, 0, '2026-08-20 09:00:00', '2026-08-20 09:00:00')");
            statement.execute("INSERT INTO problems VALUES (2, 'HARD', 0, 0, '2026-08-20 09:00:00', '2026-08-20 09:00:00')");
            statement.execute("INSERT INTO contests VALUES ('c-1', '2026-08-21 09:00:00', '2026-08-22 09:00:00', '2026-08-20 09:00:00')");
            statement.execute("INSERT INTO solutions VALUES ('s-1', 1, 'u-1', 'Solution', 'content', '2026-08-20 09:00:00')");
            statement.execute("INSERT INTO forum_posts VALUES ('p-1', 'c-1', 'u-1', 'Post', 'content', '2026-08-20 09:00:00')");
        }
        session.commit();

        var stats = provider.loadDashboardStats(LocalDateTime.of(2026, 8, 20, 10, 0));

        assertThat(stats.totalProblems()).isEqualTo(2);
        assertThat(stats.publishedProblems()).isEqualTo(1);
        assertThat(stats.totalContests()).isEqualTo(1);
        assertThat(stats.totalSolutions()).isEqualTo(1);
        assertThat(stats.publishedSolutions()).isEqualTo(1);
        assertThat(stats.flaggedSolutions()).isZero();
        assertThat(stats.forumPosts()).isEqualTo(1);
        assertThat(stats.forumComments()).isZero();
        assertThat(stats.forumCommunities()).isZero();
    }

    @Test
    void returnsOwnerChartBucketsWithWhitelistedPeriodFormat() throws Exception {
        try (var statement = session.getConnection().createStatement()) {
            statement.execute("INSERT INTO problems VALUES (1, 'EASY', 1, 0, '2026-08-20 09:00:00', '2026-08-20 09:00:00')");
        }
        session.commit();

        var chart = provider.loadDashboardChartData(
                "problems",
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 20, 23, 59),
                "day");

        assertThat(chart).hasSize(1);
        assertThat(chart.get(0).date()).isEqualTo("2026-08-20");
        assertThat(chart.get(0).count()).isEqualTo(1);
    }
}
