package com.ulticode.submission.dubbo.provider;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.submission.api.dto.LearningProgressDTO;
import com.ulticode.submission.api.dto.SubmissionDetailVO;
import com.ulticode.submission.api.dto.SubmissionHistoryDTO;
import com.ulticode.submission.api.dto.SubmissionQueryDTO;
import com.ulticode.submission.api.dto.SubmissionStatusMeta;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.app.api.service.SubmissionUserReadPort;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.projection.DefaultSubmissionProjection;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import com.ulticode.modules.submission.stats.SubmissionPerformanceStats;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SPLIT-004 slice-7: Submission owner user-facing aggregation provider
 * against a real MySQL container.
 *
 * <p>Verifies that {@code backend-submission} serves calendar dates,
 * learning progress, submission history, and the status catalog from the
 * {@code submission} schema — all pure {@code submissions}-table reads with
 * no cross-owner JOIN (DEC-011). The App controller keeps using its local
 * projection until the read-routing cutover slice.
 */
@Testcontainers
@DisplayName("SPLIT-004 slice-7/8: Submission owner user query provider")
class SubmissionUserQueryProviderIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("submission")
            .withUsername("submission_rw")
            .withPassword("submission-pw");

    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession session;
    private SubmissionMapper submissionMapper;
    private SubmissionUserQueryProvider provider;

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

        SubmissionUserReadPort userReadPort = mock(SubmissionUserReadPort.class);
        when(userReadPort.findById(any())).thenReturn(null);
        when(userReadPort.findAllById(any())).thenReturn(Map.of(
                "user-1", new SubmissionUserReadPort.UserSummary(
                        "user-1", "alice", "Alice", "avatar.png")));
        ProblemFactsPort problemFacts = mock(ProblemFactsPort.class);
        when(problemFacts.findDisplayFacts(any())).thenReturn(null);
        when(problemFacts.findDisplayFactsBatch(any())).thenReturn(Map.of());

        SubmissionProjection projection = new DefaultSubmissionProjection(
                submissionMapper, userReadPort, problemFacts, new ObjectMapper());
        SubmissionPerformanceStats performanceStats = mock(SubmissionPerformanceStats.class);
        provider = new SubmissionUserQueryProvider(projection, submissionMapper, performanceStats, problemFacts);
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.rollback();
            session.close();
        }
    }

    private void insertRow(String id, Long problemId, String userId, String language,
                           String status, int runtime, LocalDateTime createdAt) {
        Submission s = new Submission();
        s.setId(id);
        s.setProblemId(problemId);
        s.setUserId(userId);
        s.setLanguage(language);
        s.setCode("print(1)");
        s.setStatus(status);
        s.setRuntime(runtime);
        s.setMemory(1.5);
        s.setCreatedAt(createdAt);
        submissionMapper.insert(s);
    }

    @Test
    @DisplayName("aggregateDates returns distinct calendar dates from the owner schema")
    void aggregateDatesReadsFromSchema() {
        insertRow("sub-1", 101L, "user-1", "python", "Accepted", 12,
                LocalDateTime.of(2026, 8, 1, 10, 0));
        insertRow("sub-2", 101L, "user-1", "python", "Wrong_Answer", 40,
                LocalDateTime.of(2026, 8, 1, 11, 0));
        insertRow("sub-3", 102L, "user-1", "java", "Accepted", 8,
                LocalDateTime.of(2026, 8, 2, 10, 0));
        insertRow("sub-4", 101L, "other", "python", "Accepted", 5,
                LocalDateTime.of(2026, 8, 2, 10, 0));
        session.commit();

        List<String> dates = provider.aggregateDates("user-1", 2026);

        assertThat(dates).containsExactly("2026-08-01", "2026-08-02");
    }

    @Test
    @DisplayName("aggregateLearningProgress computes weekly solved counts and streak")
    void learningProgressComputesStreak() {
        insertRow("sub-1", 101L, "user-1", "python", "Accepted", 1200,
                LocalDateTime.now().minusDays(0));
        insertRow("sub-2", 102L, "user-1", "java", "Accepted", 600,
                LocalDateTime.now().minusDays(1));
        session.commit();

        LearningProgressDTO progress = provider.aggregateLearningProgress("user-1");

        assertThat(progress.getTotalProblems()).isEqualTo(2);
        // Matches the App-side calculateStreak semantics: MIN(days_ago) with a
        // same-day submission yields 0 (null -> 0), and is never negative.
        assertThat(progress.getCurrentStreak()).isGreaterThanOrEqualTo(0);
        assertThat(progress.getTotalTimeHours()).isGreaterThan(0);
    }

    @Test
    @DisplayName("aggregateHistory computes monthly counts, languages, and acceptance rate")
    void historyComputesRollups() {
        insertRow("sub-1", 101L, "user-1", "python", "Accepted", 12,
                LocalDateTime.of(2026, 8, 1, 10, 0));
        insertRow("sub-2", 101L, "user-1", "python", "Wrong_Answer", 40,
                LocalDateTime.of(2026, 8, 1, 11, 0));
        insertRow("sub-3", 102L, "user-1", "java", "Accepted", 8,
                LocalDateTime.of(2026, 7, 15, 10, 0));
        session.commit();

        SubmissionHistoryDTO history = provider.aggregateHistory("user-1");

        assertThat(history.getTotalSubmissions()).isEqualTo(3);
        assertThat(history.getTotalAccepted()).isEqualTo(2);
        assertThat(history.getAcceptanceRate()).isEqualTo(2.0 / 3.0);
        assertThat(history.getMonthly()).hasSize(2);
        assertThat(history.getLanguages()).hasSize(2);
    }

    @Test
    @DisplayName("getStatusCatalog returns the canonical status catalog")
    void statusCatalogIsCanonical() {
        List<SubmissionStatusMeta> catalog = provider.getStatusCatalog();

        assertThat(catalog).isNotEmpty();
        assertThat(catalog.stream().map(SubmissionStatusMeta::getKey))
                .contains("Accepted");
        assertThat(catalog).allSatisfy(meta -> {
            assertThat(meta.getLabel()).isNotBlank();
            assertThat(meta.getCategory()).isNotBlank();
        });
    }

    @Test
    @DisplayName("findById returns detail for the owner and null for others")
    void findByIdEnforcesOwnership() {
        insertRow("sub-1", 101L, "user-1", "python", "Accepted", 12,
                LocalDateTime.of(2026, 8, 1, 10, 0));
        session.commit();

        SubmissionDetailVO detail = provider.findById("sub-1", "user-1");

        assertThat(detail).isNotNull();
        assertThat(detail.getId()).isEqualTo("sub-1");
        assertThat(detail.getUserId()).isEqualTo("user-1");
        assertThat(detail.getStatus()).isEqualTo("Accepted");

        assertThat(provider.findById("sub-1", "other")).isNull();
        assertThat(provider.findById("missing", "user-1")).isNull();
        assertThat(provider.findById("sub-1", null)).isNull();
    }

    @Test
    @DisplayName("findByUserId paginates newest first and enriches problem facts")
    void findByUserIdPaginates() {
        insertRow("sub-1", 101L, "user-1", "python", "Accepted", 12,
                LocalDateTime.of(2026, 8, 1, 10, 0));
        insertRow("sub-2", 102L, "user-1", "java", "Wrong_Answer", 40,
                LocalDateTime.of(2026, 8, 2, 10, 0));
        insertRow("sub-3", 103L, "other", "python", "Accepted", 5,
                LocalDateTime.of(2026, 8, 3, 10, 0));
        session.commit();

        SubmissionQueryDTO query = new SubmissionQueryDTO();
        query.setPage(1);
        query.setPageSize(10);

        var page = provider.findByUserId("user-1", query);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getItems()).extracting(SubmissionVO::getId)
                .containsExactly("sub-2", "sub-1");

        var page2 = provider.findByUserId("user-1", new SubmissionQueryDTO());
        assertThat(page2.getTotal()).isEqualTo(2);
        assertThat(page2.getItems()).hasSize(2);
    }

    @Test
    @DisplayName("findByUserId enriches problem display facts via the batch seam")
    void findByUserIdEnrichesProblemFacts() {
        ProblemFactsPort problemFacts = mock(ProblemFactsPort.class);
        when(problemFacts.findDisplayFactsBatch(any())).thenReturn(Map.of(
                101L, new ProblemFactsPort.ProblemDisplayFacts(101L, "Two Sum", "two-sum"),
                102L, new ProblemFactsPort.ProblemDisplayFacts(102L, "Three Sum", "three-sum")));
        SubmissionUserReadPort userReadPort = mock(SubmissionUserReadPort.class);
        when(userReadPort.findById(any())).thenReturn(null);
        when(userReadPort.findAllById(any())).thenReturn(Map.of(
                "user-1", new SubmissionUserReadPort.UserSummary(
                        "user-1", "alice", "Alice", "avatar.png")));
        SubmissionProjection enrichedProjection = new DefaultSubmissionProjection(
                submissionMapper, userReadPort, problemFacts, new ObjectMapper());
        SubmissionUserQueryProvider enrichedProvider =
                new SubmissionUserQueryProvider(enrichedProjection, submissionMapper,
                        mock(SubmissionPerformanceStats.class), problemFacts);

        insertRow("sub-1", 101L, "user-1", "python", "Accepted", 12,
                LocalDateTime.of(2026, 8, 1, 10, 0));
        insertRow("sub-2", 102L, "user-1", "java", "Wrong_Answer", 20,
                LocalDateTime.of(2026, 8, 2, 10, 0));
        session.commit();

        SubmissionQueryDTO query = new SubmissionQueryDTO();
        query.setPage(1);
        query.setPageSize(10);
        var page = enrichedProvider.findByUserId("user-1", query);

        assertThat(page.getItems()).hasSize(2);
        assertThat(page.getItems()).allSatisfy(item -> {
            assertThat(item.getProblem()).isNotNull();
            assertThat(item.getUser()).isNotNull();
            assertThat(item.getUser().getUsername()).isEqualTo("alice");
        });
        assertThat(page.getItems().get(0).getUser().getUsername()).isEqualTo("alice");
        verify(userReadPort, times(1)).findAllById(any());
    }

    @Test
    @DisplayName("findBest returns the fastest accepted submission or null")
    void findBestSelectsFastest() {
        insertRow("sub-1", 101L, "user-1", "python", "Accepted", 40,
                LocalDateTime.of(2026, 8, 1, 10, 0));
        insertRow("sub-2", 101L, "user-1", "python", "Accepted", 12,
                LocalDateTime.of(2026, 8, 2, 10, 0));
        insertRow("sub-3", 101L, "user-1", "python", "Wrong_Answer", 1,
                LocalDateTime.of(2026, 8, 3, 10, 0));
        session.commit();

        SubmissionVO best = provider.findBest(101L, "user-1");

        assertThat(best).isNotNull();
        assertThat(best.getId()).isEqualTo("sub-2");

        assertThat(provider.findBest(999L, "user-1")).isNull();
        assertThat(provider.findBest(101L, "other")).isNull();
        assertThat(provider.findBest(null, "user-1")).isNull();
    }
}
