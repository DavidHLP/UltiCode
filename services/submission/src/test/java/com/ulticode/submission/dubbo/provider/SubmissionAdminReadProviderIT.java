package com.ulticode.submission.dubbo.provider;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.submission.api.dto.LanguageCountDTO;
import com.ulticode.submission.api.dto.StatusCountDTO;
import com.ulticode.submission.api.dto.SubmissionAdminQueryDTO;
import com.ulticode.submission.api.dto.SubmissionAdminRowDTO;
import com.ulticode.submission.api.dto.SubmissionDashboardChartDataDTO;
import com.ulticode.app.api.service.ProblemTitleLookupPort;
import com.ulticode.submission.api.dto.SubmissionUserReferenceCountDTO;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.mapper.SubmissionReconciliationReadMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SPLIT-004 slice-5: Submission owner admin read provider against a real
 * MySQL container.
 *
 * <p>Verifies that {@code backend-submission} serves the Admin read seam
 * ({@code SubmissionAdminReadPort}) from the {@code submission} schema:
 * list/detail/search pagination, status/language counts, distinct-language
 * options, and range counters. The problem-title search pre-fetch is routed
 * through the {@link ProblemTitleLookupPort} seam (Dubbo to backend-app in
 * production; mocked here) per DEC-011. The App provider (group=backend-app)
 * remains the active Admin route until the read-routing cutover slice; this
 * IT proves the capability, not the switch.
 */
@Testcontainers
@DisplayName("SPLIT-004 slice-5: Submission owner admin read provider")
class SubmissionAdminReadProviderIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("submission")
            .withUsername("submission_rw")
            .withPassword("submission-pw");

    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession session;
    private SubmissionMapper submissionMapper;
    private SubmissionReconciliationReadMapper reconciliationMapper;
    private SubmissionAdminReadProvider provider;
    private SubmissionReconciliationReadProvider reconciliationProvider;

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
        configuration.addMapper(SubmissionReconciliationReadMapper.class);
        // Mirrors the production MybatisPlusConfig bean: selectPage needs the
        // pagination interceptor to issue COUNT and LIMIT.
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
        reconciliationMapper = session.getMapper(SubmissionReconciliationReadMapper.class);
        session.getConnection().createStatement().execute("DELETE FROM submissions");
        session.commit();

        ProblemTitleLookupPort problemRead = mock(ProblemTitleLookupPort.class);
        when(problemRead.searchProblemIdsByTitle("Two Sum")).thenReturn(List.of(101L));
        when(problemRead.searchProblemIdsByTitle("missing")).thenReturn(List.of());
        provider = new SubmissionAdminReadProvider(
                submissionMapper, problemRead, new ObjectMapper());
        reconciliationProvider = new SubmissionReconciliationReadProvider(reconciliationMapper);
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
    @DisplayName("search returns paginated rows with the legacy filter semantics")
    void searchPaginatesAndFilters() {
        insertRow("sub-1", 101L, "user-1", "python", "Accepted", 12,
                LocalDateTime.of(2026, 8, 1, 10, 0));
        insertRow("sub-2", 102L, "user-2", "java", "Wrong_Answer", 40,
                LocalDateTime.of(2026, 8, 2, 10, 0));
        insertRow("sub-3", 101L, "user-1", "python", "Accepted", 8,
                LocalDateTime.of(2026, 8, 3, 10, 0));
        session.commit();

        SubmissionAdminQueryDTO query = new SubmissionAdminQueryDTO();
        query.setProblemId(101L);
        PageResult<SubmissionAdminRowDTO> page = provider.search(query, 1, 10);

        assertThat(page.getItems()).hasSize(2);
        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getItems().stream().map(SubmissionAdminRowDTO::id))
                .containsExactlyInAnyOrder("sub-1", "sub-3");
    }

    @Test
    @DisplayName("search by problem title resolves via the narrow lookup seam")
    void searchByProblemTitleUsesProblemSeam() {
        insertRow("sub-1", 101L, "user-1", "python", "Accepted", 12,
                LocalDateTime.of(2026, 8, 1, 10, 0));
        session.commit();

        SubmissionAdminQueryDTO query = new SubmissionAdminQueryDTO();
        query.setSearch("Two Sum");
        PageResult<SubmissionAdminRowDTO> page = provider.search(query, 1, 10);

        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getItems().get(0).id()).isEqualTo("sub-1");
    }

    @Test
    @DisplayName("detail row ships code and distribution bins")
    void findByIdShipsDetail() {
        insertRow("sub-1", 101L, "user-1", "python", "Accepted", 12,
                LocalDateTime.of(2026, 8, 1, 10, 0));
        session.commit();

        SubmissionAdminRowDTO dto = provider.findById("sub-1");

        assertThat(dto).isNotNull();
        assertThat(dto.code()).isEqualTo("print(1)");
        assertThat(dto.codeLength()).isEqualTo(8);
        assertThat(provider.findById("missing")).isNull();
    }

    @Test
    @DisplayName("counts, language options, and distinct-user range read from the owner schema")
    void countsAndOptions() {
        insertRow("sub-1", 101L, "user-1", "python", "Accepted", 12,
                LocalDateTime.of(2026, 8, 1, 10, 0));
        insertRow("sub-2", 102L, "user-2", "java", "Wrong_Answer", 40,
                LocalDateTime.of(2026, 8, 2, 10, 0));
        session.commit();

        assertThat(provider.countAll()).isEqualTo(2);
        assertThat(provider.countByStatus("Accepted")).isEqualTo(1);
        assertThat(provider.countCreatedSince(LocalDateTime.of(2026, 8, 2, 0, 0))).isEqualTo(1);

        List<StatusCountDTO> statuses = provider.countByStatus();
        assertThat(statuses).hasSize(2);
        assertThat(statuses.stream().map(StatusCountDTO::getStatus))
                .containsExactlyInAnyOrder("Accepted", "Wrong_Answer");

        List<LanguageCountDTO> languages = provider.countByLanguage();
        assertThat(languages).hasSize(2);

        assertThat(provider.findDistinctLanguages()).containsExactlyInAnyOrder("python", "java");
        assertThat(provider.countDistinctUsersInRange(
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 3, 0, 0)))
                .isEqualTo(2);
        assertThat(provider.countSubmissionsInRange(LocalDateTime.of(2026, 8, 2, 0, 0))).isEqualTo(1);
        assertThat(provider.countAcceptedSubmissionsInRange(
                LocalDateTime.of(2026, 8, 1, 0, 0))).isEqualTo(1);

        var dashboard = provider.loadDashboardStats(LocalDateTime.of(2026, 8, 3, 12, 0));
        assertThat(dashboard.total()).isEqualTo(2);
        assertThat(dashboard.today()).isEqualTo(0);
        assertThat(dashboard.week()).isEqualTo(2);
        assertThat(dashboard.month()).isEqualTo(2);
        assertThat(dashboard.acceptanceRate()).isEqualTo(50.0);

        List<SubmissionDashboardChartDataDTO> chart = provider.loadDashboardChartData(
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 3, 0, 0),
                "day");
        assertThat(chart).extracting(SubmissionDashboardChartDataDTO::date)
                .containsExactly("2026-08-01", "2026-08-02");
    }
    @Test
    @DisplayName("reconciliation facts support full and incremental bounded scans")
    void reconciliationFactsSupportFullAndIncrementalWindows() {
        insertRow("sub-1", 101L, "user-1", "python", "Accepted", 12,
                LocalDateTime.of(2026, 8, 1, 10, 0));
        insertRow("sub-2", 101L, "user-1", "python", "Wrong_Answer", 20,
                LocalDateTime.of(2026, 8, 2, 10, 0));
        insertRow("sub-3", 102L, "user-2", "java", "Accepted", 8,
                LocalDateTime.of(2026, 8, 3, 10, 0));
        session.commit();

        List<SubmissionUserReferenceCountDTO> full =
                reconciliationProvider.findUserReferenceCounts("", null, 500);
        assertThat(full).extracting(SubmissionUserReferenceCountDTO::accountId)
                .containsExactly("user-1", "user-2");
        assertThat(full).extracting(SubmissionUserReferenceCountDTO::rowCount)
                .containsExactly(2L, 1L);

        List<SubmissionUserReferenceCountDTO> incremental =
                reconciliationProvider.findUserReferenceCounts(
                        "", LocalDateTime.of(2026, 8, 2, 0, 0), 500);
        assertThat(incremental).extracting(SubmissionUserReferenceCountDTO::accountId)
                .containsExactly("user-1", "user-2");
        assertThat(incremental).extracting(SubmissionUserReferenceCountDTO::rowCount)
                .containsExactly(1L, 1L);
    }
}
