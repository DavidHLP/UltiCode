package com.ulticode.submission.dubbo.provider;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.app.api.service.SubmissionUserReadPort;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.projection.DefaultSubmissionProjection;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SPLIT-004 slice-6: Submission owner user-facing read provider against a
 * real MySQL container.
 *
 * {@code SubmissionReadPort.toVO} from the {@code submission} schema with
 * the P0-1 projection applied locally, and enriches user/problem summaries
 * through the App/Auth-owned seams ({@link SubmissionUserReadPort},
 * {@link ProblemFactsPort} — Dubbo in production, mocked here) per DEC-011.
 * Normal App and Contest reads route to this owner provider.
 */
@Testcontainers
@DisplayName("SPLIT-004 slice-6: Submission owner user-facing read provider")
class SubmissionReadProviderIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("submission")
            .withUsername("submission_rw")
            .withPassword("submission-pw");

    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession session;
    private SubmissionMapper submissionMapper;

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
                CREATE TABLE submissions (
                    id VARCHAR(64) PRIMARY KEY,
                    problem_id BIGINT NOT NULL,
                    user_id VARCHAR(64) NOT NULL,
                    language VARCHAR(32) NOT NULL,
                    code LONGTEXT NOT NULL,
                    status VARCHAR(64) NOT NULL,
                    runtime INT NULL,
                    memory DOUBLE NULL,
                    notes VARCHAR(500) NULL,
                    retry_count INT NULL DEFAULT 0,
                    generation BIGINT NOT NULL DEFAULT 1,
                    current_attempt_id VARCHAR(64) NULL,
                    judging_lease_expires_at DATETIME NULL,
                    created_at DATETIME NOT NULL,
                    runtime_percentile DOUBLE NULL,
                    memory_percentile DOUBLE NULL,
                    test_details JSON NULL,
                    runtimeDistBinsMs JSON NULL,
                    memoryDistBinsMb JSON NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setCacheEnabled(false);
        configuration.addMapper(SubmissionMapper.class);
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        configuration.addInterceptor(interceptor);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        sqlSessionFactory = factoryBean.getObject();
    }

    @BeforeEach
    void setUp() throws Exception {
        session = sqlSessionFactory.openSession(false);
        submissionMapper = session.getMapper(SubmissionMapper.class);
        session.getConnection().createStatement().execute("DELETE FROM submissions");
        session.commit();
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.rollback();
            session.close();
        }
    }

    private SubmissionReadProvider newProvider() {
        SubmissionUserReadPort userRead = mock(SubmissionUserReadPort.class);
        when(userRead.findById("user-1")).thenReturn(
                new SubmissionUserReadPort.UserSummary("user-1", "alice", "Alice", "avatar-1"));
        when(userRead.findAllById(any())).thenReturn(Map.of(
                "user-1", new SubmissionUserReadPort.UserSummary("user-1", "alice", "Alice", "avatar-1")));

        ProblemFactsPort problemFacts = mock(ProblemFactsPort.class);
        when(problemFacts.findDisplayFacts(101L)).thenReturn(
                new ProblemFactsPort.ProblemDisplayFacts(101L, "Two Sum", "two-sum"));
        when(problemFacts.findDisplayFactsBatch(any())).thenReturn(Map.of(
                101L, new ProblemFactsPort.ProblemDisplayFacts(101L, "Two Sum", "two-sum")));

        SubmissionProjection projection = new DefaultSubmissionProjection(
                submissionMapper, userRead, problemFacts, new com.fasterxml.jackson.databind.ObjectMapper());
        return new SubmissionReadProvider(submissionMapper, projection, problemFacts);
    }

    private void insertRow(String id, Long problemId, String userId, String status) {
        Submission s = new Submission();
        s.setId(id);
        s.setProblemId(problemId);
        s.setUserId(userId);
        s.setLanguage("python");
        s.setCode("print(1)");
        s.setStatus(status);
        s.setRuntime(12);
        s.setMemory(1.5);
        s.setCreatedAt(LocalDateTime.of(2026, 8, 1, 10, 0));
        submissionMapper.insert(s);
    }

    @Test
    @DisplayName("toVO reads from the submission schema and enriches user/problem summaries")
    void toVOReadsAndEnriches() {
        insertRow("sub-1", 101L, "user-1", "Accepted");
        session.commit();

        SubmissionVO vo = newProvider().toVO("sub-1");

        assertThat(vo).isNotNull();
        assertThat(vo.getId()).isEqualTo("sub-1");
        assertThat(vo.getStatus()).isEqualTo("Accepted");
        assertThat(vo.getUser()).isNotNull();
        assertThat(vo.getUser().getUsername()).isEqualTo("alice");
        assertThat(vo.getUser().getAvatar()).isEqualTo("avatar-1");
        assertThat(vo.getProblem()).isNotNull();
        assertThat(vo.getProblem().getTitle()).isEqualTo("Two Sum");
        assertThat(vo.getProblem().getSlug()).isEqualTo("two-sum");
    }

    @Test
    @DisplayName("toVO returns null for a missing submission")
    void toVOMissingReturnsNull() {
        assertThat(newProvider().toVO("missing")).isNull();
    }

    @Test
    @DisplayName("toVOs reads a bounded batch in caller order")
    void toVOsReadsBatchInInputOrder() {
        insertRow("sub-1", 101L, "user-1", "Accepted");
        insertRow("sub-2", 101L, "user-1", "Wrong Answer");
        session.commit();

        List<SubmissionVO> result = newProvider().toVOs(List.of("sub-2", "sub-1"));

        assertThat(result).extracting(SubmissionVO::getId)
                .containsExactly("sub-2", "sub-1");
        assertThat(result).extracting(SubmissionVO::getProblem)
                .allMatch(problem -> problem != null && problem.getTitle().equals("Two Sum"));
    }

    @Test
    @DisplayName("toVOs keeps oversized input bounded internally")
    void toVOsKeepsOversizedInputBounded() {
        List<String> ids = java.util.stream.IntStream.range(0, 101)
                .mapToObj(i -> "submission-" + i)
                .toList();

        assertThat(newProvider().toVOs(ids)).isEmpty();
    }
}
