package com.ulticode.auth.account.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AuthAccountQueryMapperCollationIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_auth_search_test")
            .withUsername("root")
            .withPassword("root");

    private static SqlSessionFactory sessionFactory;

    @BeforeAll
    static void setUp() throws Exception {
        var dataSource = new UnpooledDataSource(
                mysql.getDriverClassName(), mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        Configuration configuration = new Configuration(new Environment(
                "test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(AuthAccountQueryMapper.class);
        sessionFactory = new SqlSessionFactoryBuilder().build(configuration);

        try (SqlSession session = sessionFactory.openSession()) {
            try (var statement = session.getConnection().createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE users (
                          id varchar(36) NOT NULL,
                          username varchar(50) NOT NULL,
                          is_deleted tinyint(1) NOT NULL DEFAULT 0,
                          PRIMARY KEY (id)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                        """);
            }
            session.commit();
        }
    }

    @Test
    void countUsesProductionAccentAndCaseInsensitiveCollationAndExcludesDeletedAccounts() throws Exception {
        try (SqlSession session = sessionFactory.openSession()) {
            try (var statement = session.getConnection().createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO users (id, username, is_deleted) VALUES
                        ('jose', 'José', 0),
                        ('alice', 'ALICE', 0),
                        ('bob', 'bob', 0),
                        ('deleted', 'NoMatch', 1)
                        """);
            }
            AuthAccountQueryMapper mapper = session.getMapper(AuthAccountQueryMapper.class);

            long count = mapper.countByIdsExcludingUsernameMatch(
                    List.of("jose", "alice", "bob", "deleted"), "e");

            assertThat(count).isEqualTo(1);
        }
    }
}
