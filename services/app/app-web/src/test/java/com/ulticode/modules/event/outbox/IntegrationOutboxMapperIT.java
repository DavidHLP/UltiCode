package com.ulticode.modules.event.outbox;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DisplayName("Integration outbox mapper lease fencing IT")
class IntegrationOutboxMapperIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_integration_outbox_mapper_it")
            .withUsername("root")
            .withPassword("root");

    private static DataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUpSchema() throws Exception {
        dataSource = new DriverManagerDataSource(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE integration_outbox (
                      event_id          varchar(40)  NOT NULL,
                      owner             varchar(20)  NOT NULL,
                      aggregate_id      varchar(120) NOT NULL,
                      aggregate_version bigint       NOT NULL DEFAULT 0,
                      causation_id      varchar(40)  DEFAULT NULL,
                      trace_id          varchar(40)  DEFAULT NULL,
                      event_type        varchar(120) NOT NULL,
                      schema_version    int          NOT NULL DEFAULT 1,
                      payload           json         NOT NULL,
                      state             varchar(16)  NOT NULL DEFAULT 'PENDING',
                      attempts          int          NOT NULL DEFAULT 0,
                      last_error        text         DEFAULT NULL,
                      stream_id         varchar(80)  DEFAULT NULL,
                      created_at        datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                      claimed_at        datetime(3)  DEFAULT NULL,
                      claim_owner       varchar(80)  DEFAULT NULL,
                      delivered_at      datetime(3)  DEFAULT NULL,
                      next_retry_at     datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                      PRIMARY KEY (event_id),
                      KEY idx_outbox_state_retry (state, next_retry_at),
                      KEY idx_outbox_claim_owner (state, claim_owner, created_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(IntegrationOutboxMapper.class);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        sqlSessionFactory = factoryBean.getObject();
    }

    @BeforeEach
    void cleanOutbox() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM integration_outbox");
        }
    }

    @Test
    @DisplayName("a live row cannot be claimed or confirmed by another owner")
    void fencesSecondOwner() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession(false)) {
            insertRow(session, "evt-owner", "PENDING", null, null);
            IntegrationOutboxMapper mapper = session.getMapper(IntegrationOutboxMapper.class);

            assertThat(mapper.claimPending("owner-a", 50)).isEqualTo(1);
            assertThat(mapper.claimPending("owner-b", 50)).isZero();

            IntegrationOutboxRecord claimed = mapper.selectById("evt-owner");
            assertThat(claimed.getClaimOwner()).isEqualTo("owner-a");
            assertThat(mapper.markDelivered("evt-owner", "owner-b", "1-0")).isZero();
            assertThat(mapper.markDelivered("evt-owner", "owner-a", "1-0")).isEqualTo(1);
            session.commit();
        }
    }

    @Test
    @DisplayName("stale claims are requeued before a new owner claims them")
    void reclaimsStaleOwner() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession(false)) {
            insertRow(session, "evt-stale", "CLAIMED", "owner-a", "NOW(3) - INTERVAL 2 MINUTE");
            IntegrationOutboxMapper mapper = session.getMapper(IntegrationOutboxMapper.class);

            assertThat(mapper.reclaimStaleClaimed()).isEqualTo(1);
            assertThat(mapper.claimPending("owner-b", 50)).isEqualTo(1);
            assertThat(mapper.selectClaimed("owner-b")).extracting(IntegrationOutboxRecord::getEventId)
                    .containsExactly("evt-stale");
            session.commit();
        }
    }

    private static void insertRow(SqlSession session, String eventId, String state,
                                  String claimOwner, String claimedAtExpression) throws Exception {
        String claimOwnerValue = claimOwner == null ? "NULL" : "'" + claimOwner + "'";
        String claimedAtValue = claimedAtExpression == null ? "NULL" : claimedAtExpression;
        session.getConnection().createStatement().execute("""
                INSERT INTO integration_outbox
                    (event_id, owner, aggregate_id, aggregate_version, event_type,
                     schema_version, payload, state, attempts, claim_owner, claimed_at, next_retry_at)
                VALUES
                    ('%s', 'App', 'aggregate-1', 1, 'SubmissionJudged', 1, '{}', '%s', 0,
                     %s, %s, NOW(3))
                """.formatted(eventId, state, claimOwnerValue, claimedAtValue));
    }
}
