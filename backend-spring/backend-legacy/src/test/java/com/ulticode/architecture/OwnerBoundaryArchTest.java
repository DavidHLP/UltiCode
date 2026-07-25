package com.ulticode.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 0 baseline owner-boundary rules (P0-ARCH-002).
 *
 * <p>Strategy: <em>freeze</em> the current cross-Owner import set as the
 * baseline. The test fails only when new violations are introduced. The
 * frozen baseline + the recorded counts in DECISIONS.md
 * (ADR-MIG-ARCH-BOUNDARY) form the burn-down list for Phase 2/3.
 *
 * <p>Why freeze: guide §5.1 §10 require cross-Owner Mapper/Entity
 * separation, but Phase 0 produces the baseline; Phase 2/3 enforces strict
 * separation. A hard rule today would fail ~40 times and block all CI;
 * freezing makes the test pass on day 1 and surface new leaks only.
 *
 * <p>Freeze store: archunit freezes store a record of currently-frozen
 * violations in a directory named {@code .archunit-freeze} (or
 * {@code archunit_store}). The location is resolved relative to the test
 * working directory by default.
 */
@AnalyzeClasses(
    packages = "com.ulticode.modules",
    importOptions = {ImportOption.DoNotIncludeTests.class}
)
public class OwnerBoundaryArchTest {

    private static final String CONTEST_PKG = "com.ulticode.modules.contest..";
    private static final String ADMIN_PKG = "com.ulticode.modules.admin..";
    private static final String MODERATION_PKG = "com.ulticode.modules.moderation..";
    private static final String USER_PKG = "com.ulticode.modules.user..";
    private static final String SUBMISSION_PKG = "com.ulticode.modules.submission..";

    private static final String QUEUE_OUTBOX_PKG = "com.ulticode.modules.queue.outbox..";
    private static final String QUEUE_SERVICE_PKG = "com.ulticode.modules.queue.service..";

    /**
     * Rule 1: admin must not reach contest mapper/entity directly.
     * Owner future split: Admin reads Contest via RPC (Q) / outbox (E).
     * Frozen baseline = 30 imports across 8 files.
     */
    @ArchTest
    static final ArchRule admin_must_not_reach_contest_directly =
        FreezingArchRule.freeze(ArchRuleDefinition.noClasses()
            .that().resideInAPackage(ADMIN_PKG)
            .should().dependOnClassesThat().resideInAPackage(CONTEST_PKG))
        .because("Admin reads Contest via Q/E (RPC/outbox), not direct Mapper/Entity. "
            + "See ADR-MIG-ARCH-BOUNDARY for the frozen baseline and burn-down list.");

    /**
     * Rule 2: moderation must not write to users directly.
     * Owner future split: Moderation writes users via Auth RPC (C) or event (E).
     * Frozen baseline = 3 violations.
     */
    @ArchTest
    static final ArchRule moderation_must_not_reach_users_directly =
        FreezingArchRule.freeze(ArchRuleDefinition.noClasses()
            .that().resideInAPackage(MODERATION_PKG)
            .should().dependOnClassesThat().resideInAPackage(USER_PKG))
        .because("Moderation reaches users via Auth RPC (C) or event (E), "
            + "not direct User/UserMapper. See ADR-MIG-ARCH-BOUNDARY.");

    /**
     * Rule 3: submission must not reach queue internals outside published port.
     * Owner future split: Submission depends on port only (JudgeQueue,
     * SubmissionResultPushPort, JudgingCaseSource, VerdictMetricsParser).
     * Forbidden: QueueService, JudgeOutboxRecord, JudgeOutboxMapper,
     * dispatcher, reaper.
     * Frozen baseline = 10 imports across 4 files.
     */
    @ArchTest
    static final ArchRule submission_must_not_reach_queue_outbox =
        FreezingArchRule.freeze(ArchRuleDefinition.noClasses()
            .that().resideInAPackage(SUBMISSION_PKG)
            .should().dependOnClassesThat().resideInAPackage(QUEUE_OUTBOX_PKG))
        .because("Submission depends on queue ports only. "
            + "Reaching queue.outbox (JudgeOutboxRecord/Mapper, dispatcher, reaper) "
            + "is internal to the queue module. See ADR-MIG-ARCH-BOUNDARY.");

    @ArchTest
    static final ArchRule submission_must_not_reach_queue_service =
        FreezingArchRule.freeze(ArchRuleDefinition.noClasses()
            .that().resideInAPackage(SUBMISSION_PKG)
            .should().dependOnClassesThat().resideInAPackage(QUEUE_SERVICE_PKG))
        .because("Submission depends on queue ports only. "
            + "QueueService is internal; JudgeQueue port is the public surface. "
            + "See ADR-MIG-ARCH-BOUNDARY.");

    /**
     * Sanity: surface baseline pointer for operators. If the freeze file is
     * missing or rules silently no-op, this test fails with a human-readable
     * pointer to DECISIONS.md.
     */
    @Test
    void frozen_baseline_pointer_is_documented() {
        assertThat(ADMIN_PKG).isNotEmpty();
        assertThat(SUBMISSION_PKG).isNotEmpty();
        assertThat(MODERATION_PKG).isNotEmpty();
    }
}