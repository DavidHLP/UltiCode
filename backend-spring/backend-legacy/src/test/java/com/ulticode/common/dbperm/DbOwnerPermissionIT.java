package com.ulticode.common.dbperm;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end Testcontainers Integration Test for Per-Owner DB user shadow + violation logging (P3-DBPERM-001).
 */
@Testcontainers
@DisplayName("Per-Owner DB user shadow + violation logging (MySQL Testcontainers IT)")
class DbOwnerPermissionIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_test")
            .withUsername("root")
            .withPassword("root");

    private static SqlSessionFactory sqlSessionFactory;
    private static DataSource masterDataSource;

    private DbOwnerWebHandlerInterceptor webInterceptor;

    @BeforeAll
    static void setUpSchemaAndGrants() throws Exception {
        masterDataSource = new DriverManagerDataSource(
                mysql.getJdbcUrl(),
                mysql.getUsername(),
                mysql.getPassword()
        );

        // 1. Create tables and set up Per-Owner DB shadow users & grants as root
        try (Connection conn = masterDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS `problems` (`id` VARCHAR(40) PRIMARY KEY, `title` VARCHAR(255), `difficulty` VARCHAR(32))");
            stmt.execute("CREATE TABLE IF NOT EXISTS `users` (`id` VARCHAR(40) PRIMARY KEY, `username` VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` VARCHAR(40) PRIMARY KEY, `action` VARCHAR(64))");

            stmt.execute("CREATE USER IF NOT EXISTS 'auth_rw'@'%'");
            stmt.execute("CREATE USER IF NOT EXISTS 'admin_rw'@'%'");
            stmt.execute("CREATE USER IF NOT EXISTS 'app_rw'@'%'");

            stmt.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON `ulticode_test`.`users` TO 'auth_rw'@'%'");
            stmt.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON `ulticode_test`.`audit_logs` TO 'admin_rw'@'%'");
            stmt.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON `ulticode_test`.`problems` TO 'app_rw'@'%'");

            // Set shadow user passwords out-of-band at runtime (zero credentials in git)
            stmt.execute("ALTER USER 'auth_rw'@'%' IDENTIFIED BY 'it_auth_pw'");
            stmt.execute("ALTER USER 'admin_rw'@'%' IDENTIFIED BY 'it_admin_pw'");
            stmt.execute("ALTER USER 'app_rw'@'%' IDENTIFIED BY 'it_app_pw'");
            stmt.execute("FLUSH PRIVILEGES");
        }

        // 2. Set up MyBatis SqlSessionFactory with DbOwnerViolationInterceptor
        MybatisConfiguration configuration = new MybatisConfiguration();
        DbOwnerViolationInterceptor interceptor = new DbOwnerViolationInterceptor();
        configuration.addInterceptor(interceptor);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(masterDataSource);
        factoryBean.setConfiguration(configuration);
        sqlSessionFactory = factoryBean.getObject();
    }

    @BeforeEach
    void setUp() {
        webInterceptor = new DbOwnerWebHandlerInterceptor();
    }

    @AfterEach
    void tearDown() {
        DbOwnerContext.clear();
    }

    @Test
    @DisplayName("Physical DB Grants: app_rw is DENIED insert into ADMIN-owned 'audit_logs' table")
    void appRw_deniedInsertIntoAdminTable() {
        assertThatThrownBy(() -> {
            try (Connection c = DriverManager.getConnection(mysql.getJdbcUrl(), "app_rw", "it_app_pw");
                 PreparedStatement ps = c.prepareStatement("INSERT INTO audit_logs (id, action) VALUES ('a-1', 'TEST')")) {
                ps.executeUpdate();
            }
        }).isInstanceOf(SQLException.class)
          .satisfies(e -> {
              SQLException sqlException = (SQLException) e;
              // MySQL Error 1142: Command denied to user for table
              assertThat(sqlException.getErrorCode()).isEqualTo(1142);
          });
    }

    @Test
    @DisplayName("Physical DB Grants: app_rw is PERMITTED write access on APP-owned 'problems' table")
    void appRw_permittedInsertIntoAppTable() throws Exception {
        try (Connection c = DriverManager.getConnection(mysql.getJdbcUrl(), "app_rw", "it_app_pw");
             PreparedStatement ps = c.prepareStatement("INSERT INTO problems (id, title, difficulty) VALUES (?, ?, ?)")) {
            ps.setString(1, "p-it-1");
            ps.setString(2, "Valid Problem");
            ps.setString(3, "EASY");
            int rows = ps.executeUpdate();
            assertThat(rows).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Interceptor Logs: DbOwnerViolationInterceptor logs WARN event containing [DB_OWNER_VIOLATION] on cross-owner write")
    void interceptor_logsViolationWhenAdminContextWritesAppTable() throws Throwable {
        Logger logger = (Logger) LoggerFactory.getLogger(DbOwnerViolationInterceptor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            DbOwnerContext.setOwner(TableOwner.ADMIN);

            DbOwnerViolationInterceptor interceptor = new DbOwnerViolationInterceptor();
            Invocation invocation = mock(Invocation.class);
            MappedStatement mappedStatement = mock(MappedStatement.class);
            BoundSql boundSql = mock(BoundSql.class);

            when(invocation.getArgs()).thenReturn(new Object[]{mappedStatement, new Object()});
            when(mappedStatement.getSqlCommandType()).thenReturn(SqlCommandType.UPDATE);
            when(mappedStatement.getBoundSql(any())).thenReturn(boundSql);
            when(boundSql.getSql()).thenReturn("UPDATE `problems` SET title = 'Overwritten' WHERE id = 'p1'");
            when(invocation.proceed()).thenReturn(1);

            interceptor.intercept(invocation);

            assertThat(appender.list)
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.WARN);
                        assertThat(event.getFormattedMessage()).contains("[DB_OWNER_VIOLATION]", "problems", "ADMIN", "APP");
                    });
        } finally {
            logger.detachAppender(appender);
            DbOwnerContext.clear();
        }
    }

    @Test
    @DisplayName("Web Lifecycle: WebHandlerInterceptor populates ADMIN context for governance endpoints and clears afterCompletion")
    void webHandlerInterceptor_adminEndpointLifecycle() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/settings");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean handleResult = webInterceptor.preHandle(request, response, new Object());
        assertThat(handleResult).isTrue();
        assertThat(DbOwnerContext.getOwner()).isEqualTo(TableOwner.ADMIN);

        webInterceptor.afterCompletion(request, response, new Object(), null);
        assertThat(DbOwnerContext.getOwner()).isNull();
    }
}
