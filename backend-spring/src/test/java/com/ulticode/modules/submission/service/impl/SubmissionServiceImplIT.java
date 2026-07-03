package com.ulticode.modules.submission.service.impl;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.queue.service.QueueService;
import com.ulticode.modules.submission.dto.CreateSubmissionDTO;
import com.ulticode.modules.submission.dto.SubmissionDetailVO;
import com.ulticode.modules.submission.dto.SubmissionListItemVO;
import com.ulticode.modules.submission.dto.SubmissionQueryDTO;
import com.ulticode.modules.submission.dto.SubmissionVO;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
@ExtendWith(MockitoExtension.class)
@DisplayName("SubmissionServiceImpl - Integration Tests")
class SubmissionServiceImplIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    private static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private static DataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;

    private SqlSession session;
    private SubmissionMapper submissionMapper;
    private UserMapper userMapper;
    private ProblemMapper problemMapper;

    @Mock
    private QueueService queueService;
    @Mock
    private com.ulticode.modules.submission.port.ContestSubmissionPort contestSubmissionPort;
    @Mock
    private com.ulticode.modules.achievement.service.AchievementTriggerService achievementTriggerService;
    @Mock
    private com.ulticode.modules.notification.service.NotificationService notificationService;
    @Mock
    private com.ulticode.modules.notification.service.NotificationDispatchService notificationDispatchService;
    @Mock
    private com.ulticode.modules.notification.dispatcher.NotificationDispatcher notificationDispatcher;
    @Mock
    private SubmissionProjection submissionProjection;

    private SubmissionServiceImpl submissionService;

    private static final String USER_ID = "user-it-1";
    private static final Long PROBLEM_ID = 1L;
    private static final String LANGUAGE = "java";
    private static final String CODE = "public class Main { public static void main(String[] args) {} }";

    @BeforeAll
    static void setUpSchema() throws Exception {
        // Create DataSource from Testcontainers MySQL
        dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                mysql.getJdbcUrl(),
                mysql.getUsername(),
                mysql.getPassword()
        );

        // Execute DDL to create tables
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("SET FOREIGN_KEY_CHECKS = 0");

            // Users table (minimal subset matching V1 migration)
            stmt.execute("""
                CREATE TABLE users (
                    id varchar(40) NOT NULL,
                    username varchar(120) NOT NULL,
                    name varchar(120) DEFAULT NULL,
                    email varchar(255) DEFAULT NULL,
                    avatar varchar(255) DEFAULT NULL,
                    password varchar(255) DEFAULT NULL,
                    bio text,
                    company varchar(255) DEFAULT NULL,
                    github varchar(255) DEFAULT NULL,
                    joined_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    location varchar(255) DEFAULT NULL,
                    twitter varchar(255) DEFAULT NULL,
                    website varchar(255) DEFAULT NULL,
                    preferred_language varchar(50) DEFAULT NULL,
                    role enum('USER','MODERATOR','ADMIN','SUPER_ADMIN') NOT NULL DEFAULT 'USER',
                    is_active tinyint(1) NOT NULL DEFAULT '1',
                    is_banned tinyint(1) NOT NULL DEFAULT '0',
                    banned_until datetime(3) DEFAULT NULL,
                    banned_reason text,
                    last_login_at datetime(3) DEFAULT NULL,
                    created_by varchar(40) DEFAULT NULL,
                    updated_by varchar(40) DEFAULT NULL,
                    is_deleted tinyint(1) NOT NULL DEFAULT '0',
                    deleted_at datetime DEFAULT NULL,
                    deleted_by varchar(40) DEFAULT NULL,
                    password_reset_token_hash varchar(255) DEFAULT NULL,
                    password_reset_expires_at datetime(3) DEFAULT NULL,
                    PRIMARY KEY (id),
                    UNIQUE KEY users_username_key (username)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

            // Problems table (minimal subset matching V1 migration)
            stmt.execute("""
                CREATE TABLE problems (
                    id bigint NOT NULL,
                    slug varchar(120) NOT NULL,
                    title varchar(255) NOT NULL,
                    difficulty enum('Easy','Medium','Hard') NOT NULL DEFAULT 'Easy',
                    acceptance_rate decimal(5,2) NOT NULL DEFAULT '0.00',
                    status enum('solved','attempted','todo') NOT NULL DEFAULT 'todo',
                    is_premium tinyint(1) NOT NULL DEFAULT '0',
                    has_solution tinyint(1) NOT NULL DEFAULT '0',
                    completed_time date DEFAULT NULL,
                    is_published tinyint(1) NOT NULL DEFAULT '1',
                    published_at datetime(3) DEFAULT NULL,
                    published_by varchar(40) DEFAULT NULL,
                    is_deleted tinyint(1) NOT NULL DEFAULT '0',
                    deleted_at datetime(3) DEFAULT NULL,
                    deleted_by varchar(40) DEFAULT NULL,
                    flag_notes text,
                    flag_reason text,
                    flag_reported_at datetime(3) DEFAULT NULL,
                    flag_reported_by varchar(40) DEFAULT NULL,
                    flag_reviewed_at datetime(3) DEFAULT NULL,
                    flag_reviewed_by varchar(40) DEFAULT NULL,
                    flag_status enum('PENDING','REVIEWED','RESOLVED','DISMISSED') DEFAULT NULL,
                    is_flagged tinyint(1) NOT NULL DEFAULT '0',
                    created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    version int NOT NULL DEFAULT '1',
                    PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

            // Submissions table (matching V1 + V18 migration)
            stmt.execute("""
                CREATE TABLE submissions (
                    id varchar(40) NOT NULL,
                    problem_id bigint NOT NULL,
                    user_id varchar(40) NOT NULL,
                    language varchar(50) NOT NULL,
                    code text NOT NULL,
                    status varchar(40) NOT NULL,
                    runtime int NOT NULL DEFAULT '0',
                    memory double NOT NULL DEFAULT '0',
                    notes text,
                    retry_count int NOT NULL DEFAULT '0',
                    created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    runtime_percentile double DEFAULT NULL,
                    memory_percentile double DEFAULT NULL,
                    test_details json DEFAULT NULL,
                    memoryDistBinsMb json DEFAULT NULL,
                    runtimeDistBinsMs json DEFAULT NULL,
                    generation bigint NOT NULL DEFAULT 1,
                    current_attempt_id varchar(40) DEFAULT NULL,
                    judging_lease_expires_at datetime(3) DEFAULT NULL,
                    PRIMARY KEY (id),
                    KEY submissions_user_id_fkey (user_id),
                    CONSTRAINT submissions_problem_id_fkey FOREIGN KEY (problem_id) REFERENCES problems (id) ON DELETE CASCADE ON UPDATE CASCADE,
                    CONSTRAINT submissions_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE ON UPDATE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

            stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
        }

        // Create MyBatis-Plus SqlSessionFactory
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setCacheEnabled(false);
        configuration.addMapper(SubmissionMapper.class);
        configuration.addMapper(UserMapper.class);
        configuration.addMapper(ProblemMapper.class);

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
        // Open a session that stays open for the duration of the test
        session = sqlSessionFactory.openSession(false);

        submissionMapper = session.getMapper(SubmissionMapper.class);
        userMapper = session.getMapper(UserMapper.class);
        problemMapper = session.getMapper(ProblemMapper.class);

        // Clean tables between tests
        session.getConnection().createStatement().execute("SET FOREIGN_KEY_CHECKS = 0");
        session.getConnection().createStatement().execute("DELETE FROM submissions");
        session.getConnection().createStatement().execute("DELETE FROM problems");
        session.getConnection().createStatement().execute("DELETE FROM users");
        session.getConnection().createStatement().execute("SET FOREIGN_KEY_CHECKS = 1");
        session.commit();

        // Create service with real mappers + mocked queueService
        com.fasterxml.jackson.databind.ObjectMapper objectMapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        // ADR-003 M3a/M3b: pass null outbox mapper + flag-off properties so the
        // legacy submit path is exercised (flag-off is byte-for-byte identical
        // to the pre-fence behavior). meterRegistry null = metrics no-op.
        com.ulticode.common.config.FeatureFlagsProperties flags =
                new com.ulticode.common.config.FeatureFlagsProperties();
        com.ulticode.modules.submission.stats.DefaultSubmissionPerformanceStats performanceStats =
                new com.ulticode.modules.submission.stats.DefaultSubmissionPerformanceStats(submissionMapper);
        // Write surface now lives behind SubmissionWritePort. Wire the real
        // DefaultSubmissionWritePort adapter so the existing submit IT
        // assertions exercise the extracted write logic end-to-end through
        // the facade delegate against Testcontainers MySQL.
        com.ulticode.modules.submission.port.DefaultSubmissionWritePort writePort =
                new com.ulticode.modules.submission.port.DefaultSubmissionWritePort(
                        submissionMapper, userMapper, problemMapper, objectMapper,
                        submissionProjection, performanceStats,
                        queueService, contestSubmissionPort,
                        achievementTriggerService, notificationDispatchService,
                        notificationDispatcher,
                        null, flags, null, null);
        submissionService = new SubmissionServiceImpl(
                submissionMapper, submissionProjection, performanceStats, writePort);
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.rollback();
            session.close();
        }
    }

    private User createTestUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("ittest");
        user.setPassword("encoded-password");
        user.setEmail("it@test.com");
        user.setRole("USER");
        user.setIsActive(true);
        user.setIsBanned(false);
        user.setJoinedAt(LocalDateTime.now());
        return user;
    }

    private Problem createTestProblem() {
        Problem problem = new Problem();
        problem.setId(PROBLEM_ID);
        problem.setSlug("it-problem");
        problem.setTitle("IT Problem");
        problem.setDifficulty("Easy");
        problem.setIsDeleted(false);
        problem.setCreatedAt(LocalDateTime.now());
        problem.setUpdatedAt(LocalDateTime.now());
        return problem;
    }

    private CreateSubmissionDTO createDTO() {
        CreateSubmissionDTO dto = new CreateSubmissionDTO();
        dto.setProblemId(PROBLEM_ID);
        dto.setLanguage(LANGUAGE);
        dto.setCode(CODE);
        return dto;
    }

    @Nested
    @DisplayName("submit() - integration")
    class SubmitIntegration {

        @Test
        @DisplayName("valid submission persists to MySQL and returns Pending status")
        void submit_validSubmission_persistsToMySQL() {
            // Arrange: insert user and problem via mapper
            userMapper.insert(createTestUser());
            problemMapper.insert(createTestProblem());
            when(queueService.enqueueJudgeJob(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("job-it-1");

            // Act
            SubmissionVO result = submissionService.submit(USER_ID, createDTO());

            // Assert: submission persisted to real MySQL
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("Pending");
            assertThat(result.getLanguage()).isEqualTo(LANGUAGE);
            assertThat(result.getProblemId()).isEqualTo(PROBLEM_ID);
            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getId()).isNotBlank();

            // Verify row exists in DB via direct mapper query
            Submission persisted = submissionMapper.selectById(result.getId());
            assertThat(persisted).isNotNull();
            assertThat(persisted.getLanguage()).isEqualTo(LANGUAGE);
            assertThat(persisted.getCode()).isEqualTo(CODE);
            assertThat(persisted.getStatus()).isEqualTo("Pending");
            assertThat(persisted.getUserId()).isEqualTo(USER_ID);
            assertThat(persisted.getProblemId()).isEqualTo(PROBLEM_ID);

            // Verify queue was called
            verify(queueService).enqueueJudgeJob(
                    eq(result.getId()),
                    eq(String.valueOf(PROBLEM_ID)),
                    eq(USER_ID),
                    eq(LANGUAGE),
                    eq(CODE));
        }

        @Test
        @DisplayName("enqueue failure marks submission as System Error in MySQL")
        void submit_enqueueFails_submissionMarkedSystemError() {
            // Arrange
            userMapper.insert(createTestUser());
            problemMapper.insert(createTestProblem());
            doThrow(new RuntimeException("Queue unavailable"))
                    .when(queueService).enqueueJudgeJob(anyString(), anyString(), anyString(), anyString(), anyString());

            // Act
            SubmissionVO result = submissionService.submit(USER_ID, createDTO());

            // Assert: submission saved with System Error status
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("System Error");

            // Verify DB row also has System Error
            Submission persisted = submissionMapper.selectById(result.getId());
            assertThat(persisted).isNotNull();
            assertThat(persisted.getStatus()).isEqualTo("System Error");
            assertThat(persisted.getNotes()).contains("Judge queue unavailable");
        }
    }

    @Nested
    @DisplayName("findByProblemId() - integration")
    class FindByProblemIdIntegration {

        @Test
        @DisplayName("returns paginated submissions for current user without mapper errors")
        void findByProblemId_persistedSubmission_returnsListItemsFromMySQL() {
            userMapper.insert(createTestUser());
            problemMapper.insert(createTestProblem());

            Submission submission = new Submission();
            submission.setId("sub-it-problem-1");
            submission.setUserId(USER_ID);
            submission.setProblemId(PROBLEM_ID);
            submission.setLanguage(LANGUAGE);
            submission.setCode(CODE);
            submission.setStatus("Accepted");
            submission.setRuntime(42);
            submission.setMemory(16.5);
            submission.setRetryCount(0);
            submission.setCreatedAt(LocalDateTime.now());
            submission.setTestDetails(new ArrayList<>());
            submissionMapper.insert(submission);

            SubmissionQueryDTO query = new SubmissionQueryDTO();
            query.setPage(1);
            query.setPageSize(10);

            var result = submissionService.findByProblemId(PROBLEM_ID, USER_ID, query);

            assertThat(result).isNotNull();
            assertThat(result.getItems()).hasSize(1);

            SubmissionListItemVO item = result.getItems().get(0);
            assertThat(item.getId()).isEqualTo("sub-it-problem-1");
            assertThat(item.getLanguage()).isEqualTo(LANGUAGE);
            assertThat(item.getStatus()).isEqualTo("Accepted");
            assertThat(item.getProblem()).isNotNull();
            assertThat(item.getProblem().getId()).isEqualTo(PROBLEM_ID);
            assertThat(item.getProblem().getTitle()).isEqualTo("IT Problem");
            assertThat(item.getProblem().getSlug()).isEqualTo("it-problem");
        }
    }

    @Nested
    @DisplayName("findById() - integration")
    class FindByIdIntegration {

        @Test
        @DisplayName("persisted submission returns SubmissionVO from MySQL")
        void findById_persistedSubmission_returnsFromMySQL() {
            // Arrange: insert user, problem, and submission via mapper
            userMapper.insert(createTestUser());
            problemMapper.insert(createTestProblem());

            Submission submission = new Submission();
            submission.setId("sub-it-find-1");
            submission.setUserId(USER_ID);
            submission.setProblemId(PROBLEM_ID);
            submission.setLanguage(LANGUAGE);
            submission.setCode(CODE);
            submission.setStatus("Accepted");
            submission.setRuntime(42);
            submission.setMemory(16.5);
            submission.setRetryCount(0);
            submission.setCreatedAt(LocalDateTime.now());
            submission.setTestDetails(new ArrayList<>());
            submissionMapper.insert(submission);

            // Act
            SubmissionDetailVO result = submissionService.findById("sub-it-find-1", USER_ID);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo("sub-it-find-1");
            assertThat(result.getLanguage()).isEqualTo(LANGUAGE);
            assertThat(result.getCode()).isEqualTo(CODE);
            assertThat(result.getStatus()).isEqualTo("Accepted");
            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getProblemId()).isEqualTo(PROBLEM_ID);
            assertThat(result.getProblem()).isNotNull();
            assertThat(result.getProblem().getTitle()).isEqualTo("IT Problem");
            assertThat(result.getUser()).isNotNull();
            assertThat(result.getUser().getUsername()).isEqualTo("ittest");
        }

        @Test
        @DisplayName("non-existent submission throws SUBMISSION_NOT_FOUND")
        void findById_nonExistent_throwsNotFoundException() {
            userMapper.insert(createTestUser());
            problemMapper.insert(createTestProblem());

            assertThatThrownBy(() -> submissionService.findById("nonexistent-id", USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.SUBMISSION_NOT_FOUND));
        }

        @Test
        @DisplayName("wrong user throws SUBMISSION_NOT_FOUND (access control)")
        void findById_wrongUser_throwsNotFoundException() {
            // Arrange: create submission belonging to USER_ID
            userMapper.insert(createTestUser());
            problemMapper.insert(createTestProblem());

            Submission submission = new Submission();
            submission.setId("sub-it-access-1");
            submission.setUserId(USER_ID);
            submission.setProblemId(PROBLEM_ID);
            submission.setLanguage(LANGUAGE);
            submission.setCode(CODE);
            submission.setStatus("Accepted");
            submission.setRetryCount(0);
            submission.setCreatedAt(LocalDateTime.now());
            submission.setTestDetails(new ArrayList<>());
            submissionMapper.insert(submission);

            // Act & Assert: different user cannot access
            assertThatThrownBy(() -> submissionService.findById("sub-it-access-1", "other-user-id"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.SUBMISSION_NOT_FOUND));
        }
    }
}
