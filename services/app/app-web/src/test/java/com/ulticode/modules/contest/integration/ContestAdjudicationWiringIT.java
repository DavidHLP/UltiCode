package com.ulticode.modules.contest.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.ulticode.app.config.MybatisPlusConfig;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ulticode.app.api.event.SubmissionJudgedEvent;
import com.ulticode.app.api.service.SubmissionGenerationReadPort;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.contest.entity.ContestAdjudicationReceipt;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.ContestProblemResult;
import com.ulticode.modules.contest.mapper.ContestAdjudicationReceiptMapper;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.ContestProblemResultMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.contest.mapper.FirstSolveRecordMapper;
import com.ulticode.modules.contest.mapper.ScoringRuleMapper;
import com.ulticode.modules.contest.scoring.ContestRankingCacheEvictor;
import com.ulticode.modules.contest.scoring.IcpcStrategy;
import com.ulticode.modules.contest.scoring.IoiStrategy;
import com.ulticode.modules.contest.scoring.ScoreStrategy;
import com.ulticode.modules.contest.scoring.ScoringStrategyResolver;
import com.ulticode.modules.contest.service.impl.ContestAdjudicationServiceImpl;

/**
 * Real production-graph evidence for contest adjudication.
 *
 * <p>The service and contest mappers are Spring/MyBatis beans; only external
 * ports are mocked. The test deliberately uses the full production constructor
 * rather than the compatibility constructor used by narrow unit fixtures.</p>
 */
@SpringBootTest(
        classes = {
                ContestAdjudicationServiceImpl.class,
                ContestAdjudicationTestBeans.class,
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                TransactionAutoConfiguration.class,
                MybatisPlusAutoConfiguration.class,
                JacksonAutoConfiguration.class,
                ScoringStrategyResolver.class,
                ScoreStrategy.class,
                IcpcStrategy.class,
                IoiStrategy.class
        },
        properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=none"
        })
@MapperScan("com.ulticode.modules.contest.mapper")
@Testcontainers
@DisplayName("Contest adjudication production wiring")
class ContestAdjudicationWiringIT {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode")
            .withUsername("test")
            .withPassword("test")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(migrationPath("V20260602_120000__Create_All_Tables.sql")),
                    "/docker-entrypoint-initdb.d/001-base.sql")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(migrationPath("V20260613110000__Add_Submission_Generation_And_Lease.sql")),
                    "/docker-entrypoint-initdb.d/002-submission-generation.sql")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(migrationPath("V20260810110000__Create_Contest_Adjudication_Receipts.sql")),
                    "/docker-entrypoint-initdb.d/003-adjudication-receipts.sql")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(migrationPath("V20260810120000__Add_Contest_Finishing_And_Rating_Receipt.sql")),
                    "/docker-entrypoint-initdb.d/004-finishing.sql")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(migrationPath("V20260810130000__Harden_Contest_Admission_And_Registration.sql")),
                    "/docker-entrypoint-initdb.d/005-admission.sql")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(migrationPath("V20260810140000__Align_Contest_Tie_Breaker_Enum.sql")),
                    "/docker-entrypoint-initdb.d/006-tie-breaker.sql")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(migrationPath("V20260810150000__Add_Contest_Relational_Guards.sql")),
                    "/docker-entrypoint-initdb.d/007-relational-guards.sql")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(migrationPath("V20260810160000__Align_Contest_Timestamp_Columns.sql")),
                    "/docker-entrypoint-initdb.d/008-timestamps.sql");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ContestAdjudicationServiceImpl adjudicationService;

    @Autowired
    private ContestMapper contestMapper;

    @Autowired
    private ContestParticipantMapper participantMapper;

    @Autowired
    private ContestProblemMapper problemMapper;

    @Autowired
    private ContestSubmissionMapper submissionMapper;

    @Autowired
    private ContestProblemResultMapper problemResultMapper;

    @Autowired
    private ContestAdjudicationReceiptMapper receiptMapper;

    @Autowired
    private FirstSolveRecordMapper firstSolveRecordMapper;

    @Autowired
    private ScoringRuleMapper scoringRuleMapper;

    @MockBean
    private ContestRankingCacheEvictor rankingCacheEvictor;

    @MockBean
    private UuidGenerator uuidGenerator;

    @MockBean
    private SubmissionGenerationReadPort submissionGenerationReadPort;

    @BeforeEach
    void configureExternalPorts() {
        when(submissionGenerationReadPort.findGenerationForUpdate(anyString())).thenReturn(1L);
        when(uuidGenerator.newId()).thenAnswer(invocation -> UUID.randomUUID().toString());
    }

    @Test
    @DisplayName("real service persists one receipt, first-solve bonus, and aggregates")
    void acceptedVerdictPersistsThroughProductionMappers() {
        Fixture fixture = insertFixture(7);

        adjudicationService.applyJudgeResult(fixture.event(1));

        assertThat(receiptMapper.selectCount(new QueryWrapper<ContestAdjudicationReceipt>()
                .eq("submission_id", fixture.submissionId()))).isEqualTo(1);
        assertThat(participantMapper.selectById(fixture.participantId()).getAttemptCount()).isEqualTo(1);
        assertThat(participantMapper.selectById(fixture.participantId()).getTotalScore()).isEqualTo(107);
        assertThat(contestMapper.selectById(fixture.contestId()).getSubmissionCount()).isEqualTo(1);
        assertThat(contestMapper.selectById(fixture.contestId()).getParticipantCount()).isEqualTo(1);
        assertThat(submissionMapper.selectById(fixture.contestSubmissionId()).getIsAccepted()).isTrue();
        assertThat(problemMapper.selectById(fixture.problemId()).getSubmissionCount()).isEqualTo(1);
        assertThat(problemMapper.selectById(fixture.problemId()).getSolvedCount()).isEqualTo(1);
        assertThat(firstSolveRecordMapper.findByContestIdAndProblemId(fixture.contestId(), fixture.problemKey()))
                .isPresent();

        ContestProblemResult result = problemResultMapper
                .findByParticipantIdAndContestProblemId(fixture.participantId(), fixture.problemId())
                .orElseThrow();
        assertThat(result.getScore()).isEqualTo(107);
        assertThat(result.getTimeBonus()).isEqualTo(7);
        assertThat(result.getIsFirstSolve()).isTrue();
        assertThat(scoringRuleMapper.selectById(fixture.scoringRuleId()).getFirstSolveBonus()).isEqualTo(7);
    }

    @Test
    @DisplayName("same generation replay is a durable no-op")
    void duplicateVerdictDoesNotDoubleCount() {
        Fixture fixture = insertFixture(7);

        adjudicationService.applyJudgeResult(fixture.event(1));
        adjudicationService.applyJudgeResult(fixture.event(1));

        ContestParticipant participant = participantMapper.selectById(fixture.participantId());
        assertThat(receiptMapper.selectCount(new QueryWrapper<ContestAdjudicationReceipt>()
                .eq("submission_id", fixture.submissionId()))).isEqualTo(1);
        assertThat(participant.getAttemptCount()).isEqualTo(1);
        assertThat(participant.getTotalScore()).isEqualTo(107);
        assertThat(contestMapper.selectById(fixture.contestId()).getSubmissionCount()).isEqualTo(1);
        assertThat(contestMapper.selectById(fixture.contestId()).getParticipantCount()).isEqualTo(1);
        assertThat(problemMapper.selectById(fixture.problemId()).getSubmissionCount()).isEqualTo(1);
        assertThat(problemMapper.selectById(fixture.problemId()).getSolvedCount()).isEqualTo(1);
        assertThat(problemResultMapper.findByParticipantIdAndContestProblemId(
                fixture.participantId(), fixture.problemId())).hasValueSatisfying(result ->
                assertThat(result.getAttempts()).isEqualTo(1));
    }

    @Test
    @DisplayName("generation mismatch is rejected before any receipt or score write")
    void staleGenerationDoesNotScore() {
        Fixture fixture = insertFixture(7);
        when(submissionGenerationReadPort.findGenerationForUpdate(fixture.submissionId())).thenReturn(2L);

        adjudicationService.applyJudgeResult(fixture.event(1));

        assertThat(receiptMapper.selectCount(new QueryWrapper<ContestAdjudicationReceipt>()
                .eq("submission_id", fixture.submissionId()))).isZero();
        assertThat(participantMapper.selectById(fixture.participantId()).getAttemptCount()).isZero();
        assertThat(submissionMapper.selectById(fixture.contestSubmissionId()).getIsAccepted()).isFalse();
        assertThat(contestMapper.selectById(fixture.contestId()).getSubmissionCount()).isZero();
        assertThat(contestMapper.selectById(fixture.contestId()).getParticipantCount()).isZero();
        assertThat(problemMapper.selectById(fixture.problemId()).getSubmissionCount()).isZero();
        assertThat(problemMapper.selectById(fixture.problemId()).getSolvedCount()).isZero();
        assertThat(problemResultMapper.findByParticipantIdAndContestProblemId(
                fixture.participantId(), fixture.problemId())).isEmpty();
    }

    private void update(String sql, Object... parameters) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new AssertionError("fixture SQL failed", e);
        }
    }

    private Fixture insertFixture(int firstSolveBonus) {
        String contestId = id();
        String participantId = id();
        String problemId = id();
        String contestSubmissionId = id();
        String submissionId = id();
        String scoringRuleId = id();
        String userId = id();
        long problemKey = Math.abs(UUID.randomUUID().getMostSignificantBits());
        LocalDateTime now = LocalDateTime.of(2026, 8, 10, 12, 0);

        update("""
                INSERT INTO contest_scoring_rules
                    (id, name, base_score_per_problem, time_bonus_per_minute,
                     wrong_answer_penalty, time_limit_penalty, first_solve_bonus,
                     full_score_bonus, is_default, is_active, created_at, updated_at)
                VALUES (?, 'IT rule', 100, 1, 20, 0, ?, 0, 0, 1, ?, ?)
                """, scoringRuleId, firstSolveBonus, now, now);
        update("""
                INSERT INTO contests
                    (id, title, slug, contest_type, start_time, duration_minutes,
                     status, scoring_mode, tie_breaker, scoring_rule_id,
                     registered_count, participant_count, submission_count,
                     is_rated, is_visible, is_deleted, updated_at)
                VALUES (?, 'Wiring IT', ?, 'ICPC', ?, 120, 'RUNNING', 'SCORE',
                        'LAST_SOLVE_TIME', ?, 1, 0, 0, 1, 1, 0, ?)
                """, contestId, "wiring-" + contestId, now.minusMinutes(5), scoringRuleId, now);
        update("""
                INSERT INTO contest_participants
                    (id, contest_id, user_id, status, registered_at, is_virtual,
                     total_penalty, total_score, total_attempts, attempt_count,
                     total_time, created_at, updated_at)
                VALUES (?, ?, ?, 'STARTED', ?, 0, 0, 0, 0, 0, 0, ?, ?)
                """, participantId, contestId, userId, now, now, now);
        update("""
                INSERT INTO contest_problems
                    (id, contest_id, problem_id, problem_index, score,
                     solved_count, submission_count, created_at, updated_at)
                VALUES (?, ?, ?, 'A', 100, 0, 0, ?, ?)
                """, problemId, contestId, problemKey, now, now);
        update("""
                INSERT INTO submissions
                    (id, problem_id, user_id, language, code, status, runtime, generation)
                VALUES (?, ?, ?, 'java', 'class Main {}', 'Accepted', 1, 1)
                """, submissionId, problemKey, userId);
        update("""
                INSERT INTO contest_submissions
                    (id, submission_id, contest_id, contest_problem_id,
                     participant_id, submitted_at, time_from_start, is_accepted)
                VALUES (?, ?, ?, ?, ?, ?, 42, 0)
                """, contestSubmissionId, submissionId, contestId, problemId, participantId, now);
        return new Fixture(contestId, participantId, problemId, contestSubmissionId,
                submissionId, scoringRuleId, userId, problemKey, now);
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private static Path migrationPath(String filename) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("init-db/migrations").resolve(filename);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Migration not found: " + filename);
    }

    private record Fixture(
            String contestId,
            String participantId,
            String problemId,
            String contestSubmissionId,
            String submissionId,
            String scoringRuleId,
            String userId,
            long problemKey,
            LocalDateTime judgedAt) {

        SubmissionJudgedEvent event(long generation) {
            return new SubmissionJudgedEvent(
                    this,
                    submissionId,
                    userId,
                    problemKey,
                    "Accepted",
                    true,
                    0,
                    judgedAt,
                    generation,
                    1,
                    1.0,
                    contestId);
        }
    }

}

@TestConfiguration(proxyBeanMethods = false)
class ContestAdjudicationTestBeans {

    @Bean
    Clock clock() {
        return Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC);
    }

    @Bean
    MetaObjectHandler metaObjectHandler() {
        return new MybatisPlusConfig.AutoFillMetaObjectHandler();
    }
}
