package com.ulticode.modules.submission.fence;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency test for the generation fence under rejudge + stale-worker
 * interleaving (ADR-003 M3b, F2 (d)). Drives the CAS SQL directly against a
 * real MySQL 8.0 to assert that only the latest generation's verdict lands,
 * regardless of the order in which stale workers wake up.
 */
@Testcontainers
@DisplayName("Rejudge / stale-worker concurrency (MySQL)")
class RejudgeConcurrencyIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_test")
            .withUsername("test")
            .withPassword("test");

    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession session;
    private SubmissionMapper submissionMapper;

    @BeforeAll
    static void setUpSchema() throws Exception {
        DataSource dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());

        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
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
                    KEY idx_lease_expiry (status, judging_lease_expires_at)
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
    void setUp() throws java.sql.SQLException {
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

    private void insertAccepted(String id, long generation) {
        Submission s = new Submission();
        s.setId(id);
        s.setProblemId(1L);
        s.setUserId("user-1");
        s.setLanguage("java");
        s.setCode("code");
        s.setStatus("Accepted");
        s.setRuntime(10);
        s.setMemory(5.0);
        s.setCreatedAt(LocalDateTime.now());
        s.setTestDetails(new java.util.ArrayList<>());
        s.setGeneration(generation);
        submissionMapper.insert(s);
    }

    /**
     * Insert a Pending row at the given generation (no attempt / lease). Used to
     * set up a clean acquireLease path, since acquireLease requires
     * {@code status='Pending'}.
     */
    private void insertPending(String id, long generation) {
        Submission s = new Submission();
        s.setId(id);
        s.setProblemId(1L);
        s.setUserId("user-1");
        s.setLanguage("java");
        s.setCode("code");
        s.setStatus("Pending");
        s.setRuntime(0);
        s.setMemory(0.0);
        s.setCreatedAt(LocalDateTime.now());
        s.setTestDetails(new java.util.ArrayList<>());
        s.setGeneration(generation);
        submissionMapper.insert(s);
    }

    /**
     * Insert a row already in JUDGING with a held attempt + future lease. Used
     * to set up the force-lease-expiry scenario without relying on acquireLease
     * (which requires status=Pending).
     */
    private void insertJudging(String id, long generation, String attemptId) {
        Submission s = new Submission();
        s.setId(id);
        s.setProblemId(1L);
        s.setUserId("user-1");
        s.setLanguage("java");
        s.setCode("code");
        s.setStatus("Judging");
        s.setRuntime(0);
        s.setMemory(0.0);
        s.setCreatedAt(LocalDateTime.now());
        s.setTestDetails(new java.util.ArrayList<>());
        s.setGeneration(generation);
        s.setCurrentAttemptId(attemptId);
        s.setJudgingLeaseExpiresAt(LocalDateTime.now().plusMinutes(5));
        submissionMapper.insert(s);
    }

    @Test
    @DisplayName("two sequential rejudges bump generation twice; only gen-3 verdict lands")
    void sequentialRejudgesBumpGeneration() {
        insertAccepted("sub-conc-1", 1L);
        session.commit();

        // Rejudge #1: gen 1 -> 2
        submissionMapper.bumpGenerationAndReset("sub-conc-1", 1L, 2L);
        session.commit();
        // Rejudge #2: gen 2 -> 3
        submissionMapper.bumpGenerationAndReset("sub-conc-1", 2L, 3L);
        session.commit();

        Submission current = submissionMapper.selectById("sub-conc-1");
        assertThat(current.getGeneration()).isEqualTo(3L);
        assertThat(current.getStatus()).isEqualTo("Pending");

        // A worker acquires at the latest generation and writes a verdict.
        submissionMapper.acquireLease("sub-conc-1", "att-final", 3L, 60L);
        int written = submissionMapper.writeVerdictFenced(
                "sub-conc-1", 3L, "att-final", "Wrong Answer", 50, 6.0, null);
        assertThat(written).isEqualTo(1);
        session.commit();

        Submission finalRow = submissionMapper.selectById("sub-conc-1");
        assertThat(finalRow.getStatus()).isEqualTo("Wrong Answer");
        assertThat(finalRow.getRuntime()).isEqualTo(50);
        assertThat(finalRow.getGeneration()).isEqualTo(3L);
    }

    @Test
    @DisplayName("stale worker from gen 1 cannot overwrite after two bumps to gen 3")
    void staleWorkerCannotOverwrite() {
        insertAccepted("sub-conc-2", 1L);
        // Simulate worker-1 acquiring at gen 1.
        submissionMapper.acquireLease("sub-conc-2", "att-1", 1L, 60L);
        session.commit();

        // Two rejudges happen while worker-1 is "down".
        submissionMapper.bumpGenerationAndReset("sub-conc-2", 1L, 2L);
        submissionMapper.bumpGenerationAndReset("sub-conc-2", 2L, 3L);
        session.commit();

        // Worker-3 (latest) acquires and writes.
        submissionMapper.acquireLease("sub-conc-2", "att-3", 3L, 60L);
        submissionMapper.writeVerdictFenced("sub-conc-2", 3L, "att-3", "Time Limit Exceeded", 3000, 7.0, null);
        session.commit();

        // Stale worker-1 wakes up and tries to write its verdict -> fence rejects.
        int staleWrite = submissionMapper.writeVerdictFenced(
                "sub-conc-2", 1L, "att-1", "Accepted", 5, 1.0, null);
        assertThat(staleWrite).isEqualTo(0);
        session.commit();

        // The latest verdict survives.
        Submission finalRow = submissionMapper.selectById("sub-conc-2");
        assertThat(finalRow.getStatus()).isEqualTo("Time Limit Exceeded");
        assertThat(finalRow.getRuntime()).isEqualTo(3000);
    }

    @Test
    @DisplayName("forceLeaseExpiry arms the row for reaper recovery without bumping generation")
    void forceLeaseExpiryDoesNotBumpGeneration() {
        // Start from a JUDGING row holding an active lease (not Pending, so
        // acquireLease is not the right setup path).
        insertJudging("sub-conc-3", 1L, "att-judging");
        session.commit();

        // Admin rejudge on a JUDGING row forces expiry but does NOT bump gen.
        int forced = submissionMapper.forceLeaseExpiry("sub-conc-3", "att-judging");
        assertThat(forced).isEqualTo(1);
        session.commit();

        Submission row = submissionMapper.selectById("sub-conc-3");
        // Status stays Judging (reaper will flip it), generation unchanged.
        assertThat(row.getStatus()).isEqualTo("Judging");
        assertThat(row.getGeneration()).isEqualTo(1L);
        // Lease is now in the past -> reaper will pick it up. We assert
        // notNull + reaper visibility rather than a JVM-clock comparison, since
        // forceLeaseExpiry stamps NOW() (DB clock) and the JVM clock may differ
        // from the container timezone.
        assertThat(row.getJudgingLeaseExpiresAt()).isNotNull();

        // Reaper then bumps.
        var expired = submissionMapper.selectExpiredJudgingForUpdate(20);
        assertThat(expired).hasSize(1);
        int bumped = submissionMapper.bumpGenerationAndReset("sub-conc-3", 1L, 2L);
        assertThat(bumped).isEqualTo(1);
        session.commit();

        Submission recovered = submissionMapper.selectById("sub-conc-3");
        assertThat(recovered.getStatus()).isEqualTo("Pending");
        assertThat(recovered.getGeneration()).isEqualTo(2L);
    }

    /**
     * C1 regression: the fenced-rejudge JUDGING branch must NOT undo the forced
     * lease expiry. The original code called {@code updateById(submission)}
     * after {@code forceLeaseExpiry}; because {@code submission} still held the
     * pre-expiry future lease and MyBatis-Plus's default {@code NOT_NULL}
     * strategy re-writes non-null fields, the future lease was restored and the
     * reaper never picked the row up. The fix uses a targeted
     * {@code bumpRetryCount} update that leaves the lease columns alone.
     *
     * <p>This test reproduces the full rejudgeFenced JUDGING-branch write
     * sequence (force expiry, bump retry, then re-read) and asserts the lease
     * stays expired so the reaper can recover the row. It also reproduces the
     * buggy path (updateById with the stale future lease) as a negative control
     * to document the failure the fix prevents.
     */
    @Test
    @DisplayName("C1: rejudgeFenced JUDGING branch keeps the forced lease expiry (reaper-visible)")
    void rejudgeFencedJudgingBranchKeepsForcedExpiry() {
        // A JUDGING row holding an active (future) lease, gen 1, retry 2.
        insertJudging("sub-c1", 1L, "att-c1");
        try (var stmt = session.getConnection().createStatement()) {
            stmt.execute("UPDATE submissions SET retry_count = 2 WHERE id = 'sub-c1'");
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
        session.commit();

        // Mirror rejudgeFenced's JUDGING branch (post-fix):
        //   1. forceLeaseExpiry (DB CAS stamps lease = NOW()-1s)
        //   2. bumpRetryCount (targeted update; does NOT touch lease columns)
        int forced = submissionMapper.forceLeaseExpiry("sub-c1", "att-c1");
        assertThat(forced).isEqualTo(1);
        int bumped = submissionMapper.bumpRetryCount("sub-c1", 1);
        assertThat(bumped).isEqualTo(1);
        session.commit();

        Submission row = submissionMapper.selectById("sub-c1");
        assertThat(row.getStatus()).isEqualTo("Judging");
        assertThat(row.getGeneration()).isEqualTo(1L);
        assertThat(row.getRetryCount()).isEqualTo(3);
        // The lease must now be in the past so the reaper can pick it up.
        // selectExpiredJudgingForUpdate filters judging_lease_expires_at < NOW(),
        // so a non-empty result here is the direct proof the forced expiry was
        // preserved (not restored to the future by an updateById).
        var expired = submissionMapper.selectExpiredJudgingForUpdate(20);
        assertThat(expired).extracting(Submission::getId).contains("sub-c1");
        session.rollback(); // release the FOR UPDATE lock without consuming the row
    }

    /**
     * C1 negative control: demonstrates the original bug directly. Drives the
     * buggy write sequence (forceLeaseExpiry THEN updateById with the stale
     * future lease still on the entity) and proves the reaper can no longer see
     * the row. This documents why the targeted {@code bumpRetryCount} is
     * required; the positive test above proves the fix works.
     */
    @Test
    @DisplayName("C1 negative control: updateById with stale future lease restores the lease (the bug)")
    void updateByIdRestoresStaleFutureLeaseIsTheBug() {
        insertJudging("sub-c1-bug", 1L, "att-bug");
        session.commit();

        // Load the entity BEFORE forcing expiry — it carries the future lease,
        // exactly as rejudgeFenced receives it from selectById at line 294.
        Submission staleEntity = submissionMapper.selectById("sub-c1-bug");
        // Force the lease into the past (DB CAS; does not refresh staleEntity).
        submissionMapper.forceLeaseExpiry("sub-c1-bug", "att-bug");
        session.commit();

        // Buggy path: updateById re-writes every non-null field on staleEntity,
        // including the stale future judgingLeaseExpiresAt -> lease restored.
        submissionMapper.updateById(staleEntity);
        session.commit();

        // After the buggy updateById the reaper CANNOT see the row (lease back
        // in the future) — the permanent-stuck-JUDGING failure F2.
        var expired = submissionMapper.selectExpiredJudgingForUpdate(20);
        assertThat(expired).extracting(Submission::getId).doesNotContain("sub-c1-bug");
        session.rollback();
    }

    /**
     * F1 positive: a fenced rejudge on a terminal row must leave the DB at
     * Pending + new generation so a worker can acquire the lease. The fixed
     * terminal branch is {@code bumpGenerationAndReset} (sets Pending + new
     * gen + clears lease) followed by {@code bumpRetryCount} — it must NOT call
     * {@code updateById(submission)}, which would write the stale terminal
     * status + old generation back over the reset.
     */
    @Test
    @DisplayName("F1: terminal rejudge leaves row Pending+newGen; worker acquires lease")
    void terminalRejudgeKeepsPendingResetSoWorkerCanAcquire() {
        insertAccepted("sub-f1", 1L);
        session.commit();

        // Mirror rejudgeFenced's terminal branch (post-F1 fix):
        //   1. bumpGenerationAndReset (Pending + new gen + lease cleared)
        //   2. bumpRetryCount (targeted; does NOT touch status / generation / lease)
        int bumped = submissionMapper.bumpGenerationAndReset("sub-f1", 1L, 2L);
        assertThat(bumped).isEqualTo(1);
        int retried = submissionMapper.bumpRetryCount("sub-f1", 1);
        assertThat(retried).isEqualTo(1);
        session.commit();

        Submission row = submissionMapper.selectById("sub-f1");
        assertThat(row.getStatus()).isEqualTo("Pending");
        assertThat(row.getGeneration()).isEqualTo(2L);

        // The worker must be able to acquire the Pending lease at the new gen.
        int acquired = submissionMapper.acquireLease("sub-f1", "att-f1", 2L, 60L);
        assertThat(acquired).isEqualTo(1);
        session.commit();
    }

    /**
     * F1 negative control: demonstrates the original bug. After
     * {@code bumpGenerationAndReset} sets Pending+newGen, an
     * {@code updateById(staleEntity)} writes the loaded entity's stale terminal
     * status + old generation back, so the row is no longer Pending and the
     * worker's {@code acquireLease} (WHERE status='Pending') fails.
     */
    @Test
    @DisplayName("F1 negative control: updateById after bump clobbers Pending reset (the bug)")
    void updateByIdAfterBumpClobbersPendingResetIsTheBug() {
        insertAccepted("sub-f1-bug", 1L);
        // Load the entity BEFORE the bump — it carries the stale Accepted
        // status + gen 1, exactly as rejudgeFenced receives it from selectById.
        Submission staleEntity = submissionMapper.selectById("sub-f1-bug");
        session.commit();

        submissionMapper.bumpGenerationAndReset("sub-f1-bug", 1L, 2L);
        session.commit();

        // Buggy path: updateById re-writes the stale Accepted status + gen 1.
        submissionMapper.updateById(staleEntity);
        session.commit();

        Submission row = submissionMapper.selectById("sub-f1-bug");
        // The row is NOT Pending anymore (clobbered back to Accepted) — the bug.
        assertThat(row.getStatus()).isEqualTo("Accepted");
        // The worker cannot acquire a lease because status != Pending.
        int acquired = submissionMapper.acquireLease("sub-f1-bug", "att-bug", 2L, 60L);
        assertThat(acquired).isEqualTo(0);
        session.commit();
    }

    /**
     * F2: {@code forceLeaseExpiry} must NULL {@code current_attempt_id} so the
     * still-running worker's next {@code renewLease} AND {@code writeVerdictFenced}
     * fail immediately. The original SQL only stamped the lease into the past but
     * left the attempt id intact, so a worker heartbeating before the reaper
     * sweep could renew / land a verdict and silently overwrite the rejudge.
     */
    @Test
    @DisplayName("F2: forceLeaseExpiry revokes attempt so renewLease and writeVerdictFenced fail")
    void forceLeaseExpiryRevokesAttempt() {
        insertJudging("sub-f2", 1L, "att-f2");
        session.commit();

        int forced = submissionMapper.forceLeaseExpiry("sub-f2", "att-f2");
        assertThat(forced).isEqualTo(1);
        session.commit();

        // The attempt id must be gone — the worker's very next renewLease CAS
        // (WHERE current_attempt_id = #{attemptId}) fails immediately.
        int renewed = submissionMapper.renewLease("sub-f2", "att-f2", 60L);
        assertThat(renewed).isEqualTo(0);

        // And a writeVerdictFenced with the old attempt is rejected too — the
        // rejudge cannot be lost before the reaper bumps the generation.
        int written = submissionMapper.writeVerdictFenced(
                "sub-f2", 1L, "att-f2", "Accepted", 10, 5.0, null);
        assertThat(written).isEqualTo(0);
        session.commit();
    }

    /**
     * F2 no-op guard: {@code forceLeaseExpiry} against a row whose attempt was
     * already cleared (e.g. a prior reaper bump recovered it) is a no-op and
     * does not error.
     */
    @Test
    @DisplayName("F2: forceLeaseExpiry on an already-recovered row is a no-op")
    void forceLeaseExpiryNoOpWhenAttemptAlreadyCleared() {
        insertJudging("sub-f2-noop", 1L, "att-noop");
        session.commit();
        // Simulate a prior reaper bump clearing the attempt + resetting to Pending.
        submissionMapper.bumpGenerationAndReset("sub-f2-noop", 1L, 2L);
        session.commit();

        // The stale rejudge for the old attempt is a harmless no-op.
        int forced = submissionMapper.forceLeaseExpiry("sub-f2-noop", "att-noop");
        assertThat(forced).isEqualTo(0);
        session.commit();
    }

    /**
     * F4: {@code writeVerdictFencedWithStats} writes the verdict + performance
     * stats in a single CAS. A concurrent rejudge that bumps the generation
     * after the verdict lands cannot be clobbered — there is no second unfenced
     * {@code updateById} anymore. This test proves (a) the stats CAS lands when
     * the fence matches, and (b) a stale worker's stats CAS is rejected after a
     * generation bump (so the rejudge survives).
     */
    @Test
    @DisplayName("F4: writeVerdictFencedWithStats lands stats atomically; stale gen rejected")
    void writeVerdictFencedWithStatsIsAtomicAndFenced() {
        // Start from Pending (acquireLease requires status='Pending'). The
        // worker then acquires the lease at gen 1.
        insertPending("sub-f4", 1L);
        int acquired = submissionMapper.acquireLease("sub-f4", "att-f4", 1L, 60L);
        assertThat(acquired).isEqualTo(1);
        session.commit();

        // Verdict + stats land in ONE CAS at gen 1.
        int written = submissionMapper.writeVerdictFencedWithStats(
                "sub-f4", 1L, "att-f4", "Accepted", 42, 8.0, null,
                95.0, 90.0, "[{\"bin\":\"10\",\"count\":1}]", "[{\"bin\":\"8\",\"count\":1}]");
        assertThat(written).isEqualTo(1);
        session.commit();

        Submission landed = submissionMapper.selectById("sub-f4");
        assertThat(landed.getStatus()).isEqualTo("Accepted");
        assertThat(landed.getRuntime()).isEqualTo(42);
        assertThat(landingStatsSaved(landed)).isTrue();

        // Admin rejudge bumps gen 1 -> 2 (Accepted is rejudgeable). This clears
        // current_attempt_id + sets Pending, simulating the rejudge that races
        // the in-flight stats write.
        int bumped = submissionMapper.bumpGenerationAndReset("sub-f4", 1L, 2L);
        assertThat(bumped).isEqualTo(1);
        session.commit();

        // A stale gen-1 worker (the old att-f4) tries to re-write its stats —
        // the fence rejects it because the generation moved to 2 AND the
        // attempt was cleared by the bump. This is the exact window F4 closes:
        // there is no unfenced second updateById that could clobber the rejudge.
        int staleWrite = submissionMapper.writeVerdictFencedWithStats(
                "sub-f4", 1L, "att-f4", "Accepted", 42, 8.0, null,
                95.0, 90.0, "[{\"bin\":\"10\",\"count\":1}]", null);
        assertThat(staleWrite).isEqualTo(0);
        session.commit();

        // A NEW worker at gen 2 acquires and writes a different verdict + stats.
        submissionMapper.acquireLease("sub-f4", "att-f4b", 2L, 60L);
        int written2 = submissionMapper.writeVerdictFencedWithStats(
                "sub-f4", 2L, "att-f4b", "Wrong Answer", 99, 12.0, null,
                50.0, 40.0, null, null);
        assertThat(written2).isEqualTo(1);
        session.commit();

        Submission finalRow = submissionMapper.selectById("sub-f4");
        assertThat(finalRow.getStatus()).isEqualTo("Wrong Answer");
        assertThat(finalRow.getRuntime()).isEqualTo(99);
        assertThat(finalRow.getGeneration()).isEqualTo(2L);
        // The gen-2 stats won; the stale gen-1 stats did not leak through.
        assertThat(finalRow.getRuntimePercentile()).isEqualTo(50.0);
    }

    /**
     * Confirm the percentile / bin columns persisted through the stats CAS.
     */
    private boolean landingStatsSaved(Submission s) {
        return s.getRuntimePercentile() != null && s.getMemoryPercentile() != null
                && s.getRuntimeDistBinsMs() != null && s.getMemoryDistBinsMb() != null;
    }
}
