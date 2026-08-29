package com.ulticode.modules.queue.outbox.dispatcher;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.submission.api.queue.JudgeJobEnvelope;
import com.ulticode.modules.submission.created.SubmissionCreatedDispatcher;
import com.ulticode.modules.submission.created.SubmissionCreatedOutboxMapper;
import com.ulticode.modules.submission.created.SubmissionCreatedOutboxRecord;
import com.ulticode.modules.submission.outbox.entity.JudgeOutboxRecord;
import com.ulticode.modules.submission.outbox.mapper.JudgeOutboxMapper;
import com.ulticode.modules.submission.result.ResultEventPublisher;
import com.ulticode.modules.submission.result.SubmissionResultDispatcher;
import com.ulticode.modules.submission.result.SubmissionResultOutboxMapper;
import com.ulticode.modules.submission.result.SubmissionResultOutboxRecord;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SPLIT-003 slice-3: outbox consumers against real MySQL + Redis containers.
 *
 * <p>Verifies the two migrated consumers against the {@code submission}
 * schema: {@link JudgeOutboxDispatcher} claims real (non-shadow) rows and
 * enqueues a {@link JudgeJobEnvelope} to the judge Redis Stream; and
 * {@link SubmissionResultDispatcher} claims result rows and XADDs a
 * SubmissionJudged event to {@code stream:integration} with the App-compatible
 * field layout (DEC-014).
 */
@Testcontainers
@DisplayName("SPLIT-003 slice-3: backend-submission outbox consumers")
class SubmissionOutboxDispatcherIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("submission")
            .withUsername("submission_rw")
            .withPassword("submission-pw");

    @Container
    private static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private static SqlSessionFactory sqlSessionFactory;
    private static RedissonClient redissonClient;
    private static StringRedisTemplate stringRedisTemplate;

    private SqlSession session;
    private JudgeOutboxMapper judgeOutboxMapper;
    private SubmissionResultOutboxMapper resultOutboxMapper;
    private SubmissionCreatedOutboxMapper createdOutboxMapper;

    @BeforeAll
    static void createSchema() throws Exception {
        DataSource dataSource = new HikariDataSource() {{
            setJdbcUrl(mysql.getJdbcUrl());
            setUsername(mysql.getUsername());
            setPassword(mysql.getPassword());
            setMaximumPoolSize(2);
        }};

        try (var c = dataSource.getConnection();
             var st = c.createStatement()) {
            st.execute("""
                CREATE TABLE judge_outbox (
                    id VARCHAR(64) PRIMARY KEY,
                    submission_id VARCHAR(64) NOT NULL,
                    generation BIGINT NOT NULL,
                    payload JSON NULL,
                    is_shadow TINYINT NOT NULL DEFAULT 0,
                    state VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    attempts INT NOT NULL DEFAULT 0,
                    next_retry_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    sent_at DATETIME NULL,
                    last_error VARCHAR(1000) NULL,
                    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
            st.execute("""
                CREATE TABLE submission_result_outbox (
                    id VARCHAR(64) PRIMARY KEY,
                    submission_id VARCHAR(64) NOT NULL,
                    generation BIGINT NOT NULL,
                    user_id VARCHAR(64) NOT NULL,
                    problem_id VARCHAR(64) NOT NULL,
                    verdict VARCHAR(64) NOT NULL,
                    runtime_ms INT NOT NULL,
                    memory_mb DOUBLE NOT NULL,
                    contest_id VARCHAR(64) NULL,
                    state VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    attempts INT NOT NULL DEFAULT 0,
                    last_error VARCHAR(1000) NULL,
                    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    claimed_at DATETIME NULL,
                    claim_owner VARCHAR(128) NULL,
                    delivered_at DATETIME NULL,
                    next_retry_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
            st.execute("""
                CREATE TABLE submission_created_outbox (
                    id VARCHAR(64) PRIMARY KEY,
                    submission_id VARCHAR(64) NOT NULL,
                    generation BIGINT NOT NULL,
                    user_id VARCHAR(64) NOT NULL,
                    problem_id VARCHAR(64) NOT NULL,
                    contest_id VARCHAR(64) NOT NULL,
                    virtual_session_id VARCHAR(64) NULL,
                    language VARCHAR(50) NOT NULL,
                    occurred_at DATETIME(3) NOT NULL,
                    state VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    attempts INT NOT NULL DEFAULT 0,
                    last_error VARCHAR(1000) NULL,
                    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    claimed_at DATETIME NULL,
                    claim_owner VARCHAR(128) NULL,
                    delivered_at DATETIME NULL,
                    next_retry_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setCacheEnabled(false);
        configuration.addMapper(JudgeOutboxMapper.class);
        configuration.addMapper(SubmissionResultOutboxMapper.class);
        configuration.addMapper(SubmissionCreatedOutboxMapper.class);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        sqlSessionFactory = factoryBean.getObject();

        // Real Redis client for the judge queue adapter + the result publisher.
        Config redissonConfig = new Config();
        redissonConfig.useSingleServer()
                .setAddress("redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        redissonClient = Redisson.create(redissonConfig);

        RedisStandaloneConfiguration redisStandalone = new RedisStandaloneConfiguration(
                redis.getHost(), redis.getMappedPort(6379));
        LettuceConnectionFactory lettuceFactory = new LettuceConnectionFactory(redisStandalone);
        lettuceFactory.afterPropertiesSet();
        stringRedisTemplate = new StringRedisTemplate(lettuceFactory);
        stringRedisTemplate.afterPropertiesSet();
    }

    @BeforeEach
    void setUp() throws Exception {
        session = sqlSessionFactory.openSession(false);
        judgeOutboxMapper = session.getMapper(JudgeOutboxMapper.class);
        resultOutboxMapper = session.getMapper(SubmissionResultOutboxMapper.class);
        createdOutboxMapper = session.getMapper(SubmissionCreatedOutboxMapper.class);

        session.getConnection().createStatement().execute(
                "DELETE FROM submission_result_outbox");
        session.getConnection().createStatement().execute("DELETE FROM judge_outbox");
        session.getConnection().createStatement().execute("DELETE FROM submission_created_outbox");
        session.commit();

        redissonClient.getKeys().flushall();
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.rollback();
            session.close();
        }
    }

    private static JudgeOutboxDispatcher newJudgeDispatcher(
            JudgeOutboxMapper mapper,
            ObjectProvider<com.ulticode.submission.api.queue.JudgeQueue> provider) {
        JudgeOutboxDispatcher dispatcher = new JudgeOutboxDispatcher(
                mapper,
                provider,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
                Clock.systemUTC(),
                () -> UUID.randomUUID().toString());
        // @Value default 1970-01-01 is not injected when constructed directly.
        try {
            var field = JudgeOutboxDispatcher.class.getDeclaredField("cutoverAt");
            field.setAccessible(true);
            field.set(dispatcher, LocalDateTime.of(1970, 1, 1, 0, 0));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return dispatcher;
    }

    private UuidGenerator uuid() {
        return () -> UUID.randomUUID().toString();
    }

    private static JudgeOutboxRecord newJudgeOutboxRow(String submissionId, long generation) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("problemId", "101");
        payload.put("userId", "u-1");
        payload.put("language", "java");
        payload.put("code", "class X {}");
        payload.put("timeLimitMs", 2000);
        payload.put("memoryLimitKb", 262144);
        JudgeOutboxRecord row = new JudgeOutboxRecord();
        row.setId(UUID.randomUUID().toString());
        row.setSubmissionId(submissionId);
        row.setGeneration(generation);
        row.setPayload(payload);
        row.setIsShadow(false);
        row.setState("PENDING");
        row.setAttempts(0);
        row.setCreatedAt(LocalDateTime.now(Clock.systemUTC()));
        // claimRealDispatch uses next_retry_at <= NOW() (second precision);
        // a fresh CURRENT_TIMESTAMP(3) default is not claimable in the same
        // second, so backdate it like the App tests do.
        row.setNextRetryAt(LocalDateTime.now(Clock.systemUTC()).minusSeconds(1));
        return row;
    }

    @Test
    @DisplayName("judge outbox dispatcher enqueues real rows to the judge stream and marks SENT")
    void judgeDispatcherEnqueuesAndMarksSent() {
        JudgeOutboxRecord row = newJudgeOutboxRow("sub-1", 1L);
        judgeOutboxMapper.insert(row);
        session.commit();

        com.ulticode.submission.api.queue.JudgeQueue judgeQueue = mock(
                com.ulticode.submission.api.queue.JudgeQueue.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<com.ulticode.submission.api.queue.JudgeQueue> provider =
                mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(judgeQueue);

        JudgeOutboxDispatcher dispatcher = newJudgeDispatcher(judgeOutboxMapper, provider);

        dispatcher.dispatch();
        session.commit();

        // Row marked SENT.
        JudgeOutboxRecord after = judgeOutboxMapper.selectById(row.getId());
        assertThat(after).isNotNull();
        assertThat(after.getState()).isEqualTo("SENT");
    }

    @Test
    @DisplayName("judge outbox dispatcher keeps real rows retryable when the queue provider is absent")
    void judgeDispatcherRetriesWhenProviderAbsent() {
        JudgeOutboxRecord row = newJudgeOutboxRow("sub-2", 1L);
        judgeOutboxMapper.insert(row);
        session.commit();

        @SuppressWarnings("unchecked")
        ObjectProvider<com.ulticode.submission.api.queue.JudgeQueue> provider =
                mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        JudgeOutboxDispatcher dispatcher = newJudgeDispatcher(judgeOutboxMapper, provider);

        dispatcher.dispatch();
        session.commit();

        JudgeOutboxRecord after = judgeOutboxMapper.selectById(row.getId());
        assertThat(after).isNotNull();
        assertThat(after.getState()).isEqualTo("PENDING");
        assertThat(after.getAttempts()).isGreaterThan(0);
        assertThat(after.getLastError()).contains("unavailable");
    }

    @Test
    @DisplayName("result outbox dispatcher XADDs SubmissionJudged to stream:integration and marks DELIVERED")
    void resultDispatcherPublishesAndMarksDelivered() {
        SubmissionResultOutboxRecord row = new SubmissionResultOutboxRecord();
        row.setId(UUID.randomUUID().toString());
        row.setSubmissionId("sub-3");
        row.setGeneration(2L);
        row.setUserId("u-1");
        row.setProblemId("101");
        row.setVerdict("ACCEPTED");
        row.setRuntimeMs(12);
        row.setMemoryMb(8.5);
        row.setState("PENDING");
        row.setAttempts(0);
        row.setCreatedAt(LocalDateTime.now(Clock.systemUTC()));
        resultOutboxMapper.insert(row);
        session.commit();

        ResultEventPublisher publisher =
                new ResultEventPublisher(stringRedisTemplate, new ObjectMapper());
        SubmissionResultDispatcher dispatcher =
                new SubmissionResultDispatcher(resultOutboxMapper, publisher);

        int published = dispatcher.dispatch();
        session.commit();
        assertThat(published).isEqualTo(1);

        // Row marked DELIVERED.
        SubmissionResultOutboxRecord after = resultOutboxMapper.selectById(row.getId());
        assertThat(after).isNotNull();
        assertThat(after.getState()).isEqualTo("DELIVERED");

        // Event present on stream:integration with App-compatible fields.
        List<MapRecord<String, Object, Object>> entries = stringRedisTemplate
                .opsForStream()
                .read(StreamReadOptions.empty().count(10),
                        StreamOffset.create("stream:integration", ReadOffset.from("0-0")));
        assertThat(entries).isNotEmpty();
        MapRecord<String, Object, Object> event = entries.get(0);
        assertThat(event.getValue()).containsKeys(
                "eventId", "owner", "aggregateId", "aggregateVersion",
                "eventType", "schemaVersion", "payload");
        assertThat(event.getValue().get("eventType")).isEqualTo("SubmissionJudged");
        assertThat(event.getValue().get("owner")).isEqualTo("Submission");
        assertThat(event.getValue().get("aggregateId")).isEqualTo("sub-3");
        assertThat(event.getValue().get("payload").toString()).contains("ACCEPTED");
    }

    @Test
    @DisplayName("created outbox dispatcher publishes SubmissionCreated and marks DELIVERED")
    void createdDispatcherPublishesAndMarksDelivered() {
        SubmissionCreatedOutboxRecord row = new SubmissionCreatedOutboxRecord();
        row.setId(UUID.randomUUID().toString());
        row.setSubmissionId("sub-created-1");
        row.setGeneration(1L);
        row.setUserId("u-1");
        row.setProblemId("101");
        row.setContestId("contest-1");
        row.setVirtualSessionId("session-1");
        row.setLanguage("java");
        row.setOccurredAt(LocalDateTime.now(Clock.systemUTC()).minusSeconds(1));
        row.setState("PENDING");
        row.setAttempts(0);
        row.setCreatedAt(LocalDateTime.now(Clock.systemUTC()).minusSeconds(1));
        row.setNextRetryAt(LocalDateTime.now(Clock.systemUTC()).minusSeconds(1));
        createdOutboxMapper.insert(row);
        session.commit();

        ResultEventPublisher publisher =
                new ResultEventPublisher(stringRedisTemplate,
                        new ObjectMapper().registerModule(new JavaTimeModule()));
        SubmissionCreatedDispatcher dispatcher =
                new SubmissionCreatedDispatcher(createdOutboxMapper, publisher);

        assertThat(dispatcher.dispatch()).isEqualTo(1);
        session.commit();

        SubmissionCreatedOutboxRecord after = createdOutboxMapper.selectById(row.getId());
        assertThat(after).isNotNull();
        assertThat(after.getState()).isEqualTo("DELIVERED");

        List<MapRecord<String, Object, Object>> entries = stringRedisTemplate
                .opsForStream()
                .read(StreamReadOptions.empty().count(10),
                        StreamOffset.create("stream:integration", ReadOffset.from("0-0")));
        assertThat(entries).isNotEmpty();
        MapRecord<String, Object, Object> event = entries.get(0);
        assertThat(event.getValue().get("eventType")).isEqualTo("SubmissionCreated");
        assertThat(event.getValue().get("owner")).isEqualTo("Submission");
        assertThat(event.getValue().get("payload").toString())
                .contains("contest-1")
                .doesNotContain("code");
    }

    @Test
    @DisplayName("result outbox dispatcher retries rows on XADD failure")
    void resultDispatcherRetriesOnPublishFailure() {
        SubmissionResultOutboxRecord row = new SubmissionResultOutboxRecord();
        row.setId(UUID.randomUUID().toString());
        row.setSubmissionId("sub-4");
        row.setGeneration(1L);
        row.setUserId("u-1");
        row.setProblemId("101");
        row.setVerdict("WRONG_ANSWER");
        row.setRuntimeMs(12);
        row.setMemoryMb(8.5);
        row.setState("PENDING");
        row.setAttempts(0);
        row.setCreatedAt(LocalDateTime.now(Clock.systemUTC()));
        resultOutboxMapper.insert(row);
        session.commit();

        // Failing publisher: XADD against a broken Redis client.
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        when(broken.opsForStream()).thenThrow(new IllegalStateException("redis down"));
        ResultEventPublisher failingPublisher =
                new ResultEventPublisher(broken, new ObjectMapper());
        SubmissionResultDispatcher dispatcher =
                new SubmissionResultDispatcher(resultOutboxMapper, failingPublisher);

        int published = dispatcher.dispatch();
        session.commit();
        assertThat(published).isEqualTo(0);

        SubmissionResultOutboxRecord after = resultOutboxMapper.selectById(row.getId());
        assertThat(after).isNotNull();
        assertThat(after.getState()).isEqualTo("PENDING");
        assertThat(after.getAttempts()).isGreaterThan(0);
        assertThat(after.getLastError()).contains("redis down");
    }
}
