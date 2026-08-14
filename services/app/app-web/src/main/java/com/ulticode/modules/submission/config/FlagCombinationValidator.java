package com.ulticode.modules.submission.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * P0-2 / P1-1: validates that the {@code app.features.*} flag combination is
 * self-consistent at startup. Runs on {@link ApplicationReadyEvent} (after all
 * beans are wired) so a bad combination fails the app fast instead of silently
 * producing orphan submissions or a stuck cutover.
 *
 * <p>Design (per reviewer P1-1 guidance): assert against a small whitelist of
 * <b>illegal</b> combinations rather than enumerating legal ones. Legal
 * combinations — including the CI {@code features-off} profile where every
 * flag is {@code false} — pass through untouched.
 *
 * <p>Illegal combinations:
 * <ul>
 *   <li><b>F1 (hard fail)</b> {@code judge-queue.use-port=true} AND
 *       {@code use-judge-outbox=false}: the port is the outbox dispatcher's
 *       downstream; without the outbox there is no producer feeding the port,
 *       so submissions would be enqueued to RQueue (flag-off) while the worker
 *       polls the port (flag-on) — a split that strands submissions Pending
 *       forever.</li>
 *   <li><b>F2 (hard fail)</b> {@code judge-queue.use-port=true} AND
 *       {@code use-generation-fence=false}: with the fence off, rejudge and
 *       lease-recovery fall back to the legacy producer path, which calls
 *       {@code JudgeEnqueuePort}; the cutover adapter
 *       ({@code JudgeEnqueueAdapter}) intentionally skips the legacy RQueue
 *       once the Streams outbox is active, so the job is silently lost and
 *       the submission strands Pending forever.</li>
 *   <li><b>F3 (hard fail)</b> {@code app.runtime.role} is not one of
 *       {@code api} / {@code judge}: the worker and reaper beans are
 *       registered via {@code @ConditionalOnExpression} on that property, so
 *       a typo would start a runtime with no consumer and submissions would
 *       stall without any startup error.</li>
 *   <li><b>W1 (soft warn)</b> {@code judge-queue.use-port=true} AND
 *       {@code judge-queue.envelope-version=1}: the dispatcher
 *       ({@code JudgeOutboxDispatcher.toEnvelope}) currently hard-codes
 *       envelope version {@code 2} and ignores this flag, so the combination
 *       is a config lie rather than a runtime bug. Logged as a warning so a
 *       future envelope-version-aware dispatcher (ADR-005 §2.4) can upgrade
 *       this to a hard fail when it starts consuming the flag.</li>
 *   <li><b>W2 (soft warn)</b> {@code judge-queue.use-port=true} AND
 *       {@code judge-queue.cutover-at} in the past: the cutover watermark has
 *       already passed, so the config is stale. Logged as a warning; task #7
 *       removes {@code cutover-at} entirely once the cutover is complete.</li>
 * </ul>
 *
 * <p>Note on {@code envelope-version}: the field exists on
 * {@link FeatureFlagsProperties.JudgeQueue} but {@code JudgeOutboxDispatcher}
 * does not consume it (hard-codes v2). It is effectively dead config until
 * ADR-005 §2.4 ships envelope-version-aware writes. Tracked in task #7.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlagCombinationValidator {

    private final FeatureFlagsProperties flags;

    @org.springframework.beans.factory.annotation.Value("${app.runtime.role:api}")
    private String runtimeRole;

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        FeatureFlagsProperties.JudgeQueue jq = flags.getJudgeQueue();

        // F1 (hard fail): port needs the outbox as its producer.
        if (jq.isUsePort() && !flags.isUseJudgeOutbox()) {
            throw new IllegalStateException(
                    "Invalid feature flag combination: app.features.judge-queue.use-port=true "
                            + "requires app.features.use-judge-outbox=true (the port is the outbox "
                            + "dispatcher's downstream; without the outbox there is no producer).");
        }

        // F2 (hard fail): with the fence off, rejudge/lease-recovery call the
        // legacy enqueue port, which the cutover adapter skips once the
        // Streams outbox is active — the job is silently lost.
        if (jq.isUsePort() && !flags.isUseGenerationFence()) {
            throw new IllegalStateException(
                    "Invalid feature flag combination: app.features.judge-queue.use-port=true "
                            + "requires app.features.use-generation-fence=true (without the fence, "
                            + "rejudge and lease-recovery go through JudgeEnqueuePort, which the "
                            + "cutover adapter skips when the Streams outbox is active — the job "
                            + "would be silently lost).");
        }

        // F3 (hard fail): an unrecognized runtime role would register neither
        // the worker nor the reaper (both are role-gated), stalling every
        // submission with no startup error.
        if (!"api".equals(runtimeRole) && !"judge".equals(runtimeRole)) {
            throw new IllegalStateException(
                    "Invalid app.runtime.role='" + runtimeRole
                            + "'; expected 'api' or 'judge' (the role gates the judge worker "
                            + "and Streams reaper bean registration).");
        }

        // W1 (soft warn): envelope-version=1 with port on is a config lie —
        // dispatcher hard-codes v2 today.
        if (jq.isUsePort() && jq.getEnvelopeVersion() == 1) {
            log.warn("app.features.judge-queue.envelope-version=1 with use-port=true: dispatcher "
                    + "currently hard-codes envelope version 2 and ignores this flag (dead config). "
                    + "Will become a hard fail once ADR-005 §2.4 envelope-version-aware dispatch ships.");
        }

        // W2 (soft warn): stale cutover-at (past) with port on.
        LocalDateTime cutoverAt = jq.getCutoverAt();
        if (jq.isUsePort() && cutoverAt != null && cutoverAt.isBefore(LocalDateTime.now())) {
            log.warn("app.features.judge-queue.cutover-at={} is in the past but use-port=true: "
                    + "stale cutover config; clear cutover-at or wait for task #7 to remove it.",
                    cutoverAt);
        }

        log.info("Feature flag combination validated: use-judge-outbox={}, use-generation-fence={}, "
                        + "judge-queue.use-port={}, judge-queue.envelope-version={}, judge-queue.cutover-at={}",
                flags.isUseJudgeOutbox(), flags.isUseGenerationFence(),
                jq.isUsePort(), jq.getEnvelopeVersion(), jq.getCutoverAt());
    }
}
