package com.ulticode.admin;
import com.ulticode.BackendAdminApplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.ulticode.modules.admin.mapper.AdminDatasourceCanaryMapper;
import java.sql.Connection;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = BackendAdminApplication.class, properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
        + "org.apache.dubbo.spring.boot.autoconfigure.DubboAutoConfiguration,"
        + "com.alibaba.cloud.dubbo.bootstrap.DubboBootstrapAutoConfiguration")
@Testcontainers
class BackendAdminDatasourceContextIT {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_admin_context_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;
    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private AdminDatasourceCanaryMapper canaryMapper;

    @Test
    @DisplayName("full context registers MySQL datasource, MyBatis, and mapper scan")
    void fullContextRegistersDatasourceAndCanaryMapper() throws Exception {
        assertThat(applicationContext.getBean(DataSource.class)).isSameAs(dataSource);
        assertThat(applicationContext.getBean(SqlSessionFactory.class)).isSameAs(sqlSessionFactory);
        assertThat(applicationContext.getBean(AdminDatasourceCanaryMapper.class)).isSameAs(canaryMapper);

        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).containsIgnoringCase("MySQL");
        }
        assertThat(canaryMapper.selectOne()).isEqualTo(1);

        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            assertThat(connection.ping()).isEqualTo("PONG");
        }
    }
}
