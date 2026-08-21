package com.ulticode.modules.submission.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Shared startup validator for the named local/external runtime modes.
 * Submission, App and Judge must reject the same unsafe flag combinations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlagCombinationValidator {

    private final FeatureFlagsProperties flags;

    @org.springframework.beans.factory.annotation.Value("${app.runtime.role:api}")
    private String runtimeRole;

    @org.springframework.beans.factory.annotation.Value("${app.runtime.mode:dev-lite}")
    private String runtimeMode = "dev-lite";

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        FeatureFlagsProperties.JudgeQueue judgeQueue = flags.getJudgeQueue();

        if (judgeQueue.isUsePort() && !flags.isUseJudgeOutbox()) {
            throw new IllegalStateException(
                    "Invalid feature flag combination: app.features.judge-queue.use-port=true "
                            + "requires app.features.use-judge-outbox=true.");
        }
        if (judgeQueue.isUsePort() && !flags.isUseGenerationFence()) {
            throw new IllegalStateException(
                    "Invalid feature flag combination: app.features.judge-queue.use-port=true "
                            + "requires app.features.use-generation-fence=true.");
        }
        if (!"api".equals(runtimeRole) && !"judge".equals(runtimeRole)) {
            throw new IllegalStateException(
                    "Invalid app.runtime.role='" + runtimeRole
                            + "'; expected 'api' or 'judge'.");
        }
        validateRuntimeMode(judgeQueue);

        LocalDateTime cutoverAt = judgeQueue.getCutoverAt();
        if (judgeQueue.isUsePort() && cutoverAt != null && cutoverAt.isBefore(LocalDateTime.now())) {
            log.warn("app.features.judge-queue.cutover-at={} is in the past; stale cutover config", cutoverAt);
        }
        log.info("Runtime mode validated: mode={}, role={}, use-judge-outbox={}, use-generation-fence={}, use-port={}",
                runtimeMode, runtimeRole, flags.isUseJudgeOutbox(), flags.isUseGenerationFence(),
                judgeQueue.isUsePort());
    }

    private void validateRuntimeMode(FeatureFlagsProperties.JudgeQueue judgeQueue) {
        if ("dev-lite".equals(runtimeMode)
                || "dev-full".equals(runtimeMode)
                || "external-full".equals(runtimeMode)) {
            if (!flags.isUseJudgeOutbox() || !flags.isUseGenerationFence() || !judgeQueue.isUsePort()) {
                throw new IllegalStateException(
                        "Runtime mode " + runtimeMode
                                + " requires use-judge-outbox, use-generation-fence and judge-queue.use-port=true.");
            }
            return;
        }
        if ("legacy-rollback".equals(runtimeMode)) {
            if (flags.isUseJudgeOutbox() || flags.isUseGenerationFence() || judgeQueue.isUsePort()) {
                throw new IllegalStateException(
                        "Runtime mode legacy-rollback requires all Judge Streams flags to be false.");
            }
            return;
        }
        throw new IllegalStateException(
                "Invalid app.runtime.mode='" + runtimeMode
                        + "'; expected dev-lite, dev-full, external-full or legacy-rollback.");
    }
}
