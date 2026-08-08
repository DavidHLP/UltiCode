package com.ulticode.modules.submission.sandbox;

import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.sandbox.SandboxOutcomeClassifier.SandboxInfraFailure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SandboxOutcomeClassifier}.
 *
 * <p>The classifier is the single source of truth for the
 * exit-code / output / cause → {@link SandboxInfraFailure} →
 * {@link SubmissionStatus} mapping. These tests pin the behavioral
 * contract so the previously-hidden infra failures (real docker /
 * OCI errors that used to be scrubbed by {@code sanitizeSandboxOutput}
 * and flattened to a generic "Runtime error") are now explicitly
 * asserted.
 */
@DisplayName("SandboxOutcomeClassifier")
class SandboxOutcomeClassifierTest {

    private final SandboxOutcomeClassifier classifier = new SandboxOutcomeClassifier();

    // ── classify ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("classify")
    class Classify {

        @Test
        @DisplayName("exit 0 with empty output is NONE (happy path)")
        void happyPath_none() {
            assertThat(classifier.classify(0, "", null, /* compile */ false))
                    .isEqualTo(SandboxInfraFailure.NONE);
        }

        @Test
        @DisplayName("exit 0 with harness output is NONE (happy path)")
        void happyPath_withOutput_none() {
            String harnessEnvelope = "{\"harness_version\":\"1.0\",\"results\":[]}";
            assertThat(classifier.classify(0, harnessEnvelope, null, false))
                    .isEqualTo(SandboxInfraFailure.NONE);
        }

        @Test
        @DisplayName("exit 137 with no harness envelope is OUT_OF_MEMORY")
        void exit137_oom() {
            assertThat(classifier.classify(137, "", null, false))
                    .isEqualTo(SandboxInfraFailure.OUT_OF_MEMORY);
        }

        @Test
        @DisplayName("exit 137 with envelope is still OUT_OF_MEMORY (signals precede everything else)")
        void exit137_takesPrecedence_overCompile() {
            assertThat(classifier.classify(137, "anything", null, /* compile */ true))
                    .isEqualTo(SandboxInfraFailure.OUT_OF_MEMORY);
        }

        @Test
        @DisplayName("exit 125 (docker never started container) is OCI_ERROR — seccomp profile missing")
        void exit125_seccompProfileMissing_ociError() {
            // Real fingerprint from docker when --security-opt seccomp=<path>
            // points at a non-existent file. Previously masked as user RE.
            String evidence = "docker: opening seccomp profile (/repo/docker/sandbox/seccomp-profile.json) "
                    + "failed: open .../seccomp-profile.json: no such file or directory";
            assertThat(classifier.classify(125, evidence, null, false))
                    .isEqualTo(SandboxInfraFailure.OCI_ERROR);
        }

        @Test
        @DisplayName("exit 125 with empty output is still OCI_ERROR (125 is unambiguous infra)")
        void exit125_emptyOutput_ociError() {
            assertThat(classifier.classify(125, "", null, false))
                    .isEqualTo(SandboxInfraFailure.OCI_ERROR);
        }

        @Test
        @DisplayName("exit 125 outranks a compile signal (infra before user-code classification)")
        void exit125_outranksCompile() {
            assertThat(classifier.classify(125, "Solution.java:1: error", null, /* compile */ true))
                    .isEqualTo(SandboxInfraFailure.OCI_ERROR);
        }

        @Test
        @DisplayName("'Cannot fork' in stdout is FORK_LIMIT")
        void cannotFork_forkLimit() {
            assertThat(classifier.classify(1, "sh: 0: Cannot fork", null, false))
                    .isEqualTo(SandboxInfraFailure.FORK_LIMIT);
        }

        @Test
        @DisplayName("'Resource temporarily unavailable' is FORK_LIMIT")
        void resourceTemp_forkLimit() {
            assertThat(classifier.classify(1,
                    "docker: Error response from daemon: Resource temporarily unavailable.", null, false))
                    .isEqualTo(SandboxInfraFailure.FORK_LIMIT);
        }

        @Test
        @DisplayName("'fork: Cannot allocate memory' is FORK_LIMIT")
        void forkAllocMem_forkLimit() {
            assertThat(classifier.classify(1, "bash: fork: Cannot allocate memory", null, false))
                    .isEqualTo(SandboxInfraFailure.FORK_LIMIT);
        }

        @Test
        @DisplayName("'pids-limit reached' is FORK_LIMIT")
        void pidsLimitReached_forkLimit() {
            assertThat(classifier.classify(1, "pids-limit reached", null, false))
                    .isEqualTo(SandboxInfraFailure.FORK_LIMIT);
        }

        @Test
        @DisplayName("'OCI runtime' line is OCI_ERROR (the previously-hidden case)")
        void ociRuntime_ociError() {
            String stderr = "level=error msg=\"OCI runtime exec failed\"";
            assertThat(classifier.classify(1, stderr, null, false))
                    .isEqualTo(SandboxInfraFailure.OCI_ERROR);
        }

        @Test
        @DisplayName("non-null cause is LAUNCH_FAILURE even if exitCode == 0")
        void nonNullCause_launchFailure() {
            Throwable launchErr = new IOException("Cannot run program: No such file");
            assertThat(classifier.classify(0, "", launchErr, false))
                    .isEqualTo(SandboxInfraFailure.LAUNCH_FAILURE);
        }

        @Test
        @DisplayName("non-null cause wins over exitCode=137")
        void nonNullCause_wins_overExitCode() {
            // Defensive: even if the executor hands us exit=137 + cause
            // (it shouldn't, but the contract is "cause implies LAUNCH_FAILURE
            // unconditionally"), cause wins so the failure category is
            // unambiguous.
            assertThat(classifier.classify(137, "", new IOException("x"), false))
                    .isEqualTo(SandboxInfraFailure.LAUNCH_FAILURE);
        }

        @Test
        @DisplayName("non-fork, non-OCI, non-137, non-launch, non-compile → RUNTIME_ERROR")
        void genericNonZero_runtimeError() {
            assertThat(classifier.classify(2, "Traceback (most recent call last):", null, false))
                    .isEqualTo(SandboxInfraFailure.RUNTIME_ERROR);
        }

        @Test
        @DisplayName("compileFailure=true with no infra signal → COMPILE_ERROR")
        void compileFailure_compileError() {
            String compileOut = "solution.cpp:5:1: error: expected ';' before 'return'";
            assertThat(classifier.classify(2, compileOut, null, /* compile */ true))
                    .isEqualTo(SandboxInfraFailure.COMPILE_ERROR);
        }

        @Test
        @DisplayName("compileFailure=true is overridden by FORK_LIMIT signal (infra wins)")
        void forkSignal_wins_overCompile() {
            assertThat(classifier.classify(1, "sh: Cannot fork", null, /* compile */ true))
                    .isEqualTo(SandboxInfraFailure.FORK_LIMIT);
        }

        @Test
        @DisplayName("compileFailure=true is overridden by OCI_ERROR signal")
        void ociSignal_wins_overCompile() {
            String stderr = "level=error msg=\"OCI runtime create failed\"";
            assertThat(classifier.classify(1, stderr, null, /* compile */ true))
                    .isEqualTo(SandboxInfraFailure.OCI_ERROR);
        }

        @Test
        @DisplayName("null stdout is tolerated (does not NPE)")
        void nullStdout_tolerated() {
            assertThat(classifier.classify(1, null, null, false))
                    .isEqualTo(SandboxInfraFailure.RUNTIME_ERROR);
        }
    }

    // ── toStatus ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toStatus")
    class ToStatus {

        @Test
        @DisplayName("infra failures map to SANDBOX_ERROR (single source of truth)")
        void infra_toSandboxError() {
            assertThat(classifier.toStatus(SandboxInfraFailure.OUT_OF_MEMORY))
                    .isEqualTo(SubmissionStatus.SANDBOX_ERROR);
            assertThat(classifier.toStatus(SandboxInfraFailure.FORK_LIMIT))
                    .isEqualTo(SubmissionStatus.SANDBOX_ERROR);
            assertThat(classifier.toStatus(SandboxInfraFailure.OCI_ERROR))
                    .isEqualTo(SubmissionStatus.SANDBOX_ERROR);
            assertThat(classifier.toStatus(SandboxInfraFailure.LAUNCH_FAILURE))
                    .isEqualTo(SubmissionStatus.SANDBOX_ERROR);
        }

        @Test
        @DisplayName("compile failure maps to COMPILE_ERROR")
        void compile_toCompileError() {
            assertThat(classifier.toStatus(SandboxInfraFailure.COMPILE_ERROR))
                    .isEqualTo(SubmissionStatus.COMPILE_ERROR);
        }

        @Test
        @DisplayName("runtime error maps to RUNTIME_ERROR")
        void runtime_toRuntimeError() {
            assertThat(classifier.toStatus(SandboxInfraFailure.RUNTIME_ERROR))
                    .isEqualTo(SubmissionStatus.RUNTIME_ERROR);
        }

        @Test
        @DisplayName("null input falls back to RUNTIME_ERROR (defensive)")
        void null_fallbackToRuntime() {
            assertThat(classifier.toStatus(null))
                    .isEqualTo(SubmissionStatus.RUNTIME_ERROR);
        }
    }

    // ── delegate helpers (preserves the existing ForkDetectionTest contract) ─

    @Nested
    @DisplayName("looksLikeSandboxForkFailure / looksLikeDockerDaemonForkFailure")
    class ForkDelegates {

        @Test
        @DisplayName("busybox sh 'Cannot fork' is detected")
        void busybox_detected() {
            assertThat(classifier.looksLikeSandboxForkFailure("sh: 0: Cannot fork")).isTrue();
        }

        @Test
        @DisplayName("'pids-limit reached' is detected")
        void pids_detected() {
            assertThat(classifier.looksLikeSandboxForkFailure("pids-limit reached")).isTrue();
        }

        @Test
        @DisplayName("user traceback is NOT detected")
        void userTraceback_notDetected() {
            String traceback = "Traceback (most recent call last):\n"
                    + "  File \"/tmp/solution.py\", line 12\n"
                    + "AttributeError: input list is empty\n";
            assertThat(classifier.looksLikeSandboxForkFailure(traceback)).isFalse();
        }

        @Test
        @DisplayName("null / empty input is NOT detected")
        void nullOrEmpty_notDetected() {
            assertThat(classifier.looksLikeSandboxForkFailure(null)).isFalse();
            assertThat(classifier.looksLikeSandboxForkFailure("")).isFalse();
            assertThat(classifier.looksLikeSandboxForkFailure("   \n  ")).isFalse();
        }

        @Test
        @DisplayName("docker daemon delegate detects the daemon-side markers")
        void daemonDelegate_detected() {
            assertThat(classifier.looksLikeDockerDaemonForkFailure(
                    "Cannot fork / pids-limit reached")).isTrue();
            assertThat(classifier.looksLikeDockerDaemonForkFailure(
                    "WARNING: pids-limit not set, using docker default")).isFalse();
        }
    }

    // ── per-case status accessors (Spec C1: single owner of per-case mapping) ─

    @Nested
    @DisplayName("timeLimitExceeded")
    class TimeLimitExceeded {

        @Test
        @DisplayName("returns TIME_LIMIT_EXCEEDED")
        void returnsTle() {
            assertThat(classifier.timeLimitExceeded())
                    .isEqualTo(SubmissionStatus.TIME_LIMIT_EXCEEDED);
        }
    }

    @Nested
    @DisplayName("genericRuntimeError")
    class GenericRuntimeError {

        @Test
        @DisplayName("returns RUNTIME_ERROR")
        void returnsRuntimeError() {
            assertThat(classifier.genericRuntimeError())
                    .isEqualTo(SubmissionStatus.RUNTIME_ERROR);
        }
    }

    @Nested
    @DisplayName("unsupportedLanguage")
    class UnsupportedLanguage {

        @Test
        @DisplayName("returns SANDBOX_ERROR (matches pre-M2a and InMemorySandboxAdapterTest)")
        void returnsSandboxError() {
            assertThat(classifier.unsupportedLanguage())
                    .isEqualTo(SubmissionStatus.SANDBOX_ERROR);
        }
    }

    @Nested
    @DisplayName("applyMemoryCeiling")
    class ApplyMemoryCeiling {

        @Test
        @DisplayName("peak within ceiling and ACCEPTED → ACCEPTED (no remap)")
        void withinCeiling_accepted_unchanged() {
            assertThat(classifier.applyMemoryCeiling(
                    SubmissionStatus.ACCEPTED, /* peak */ 1024L, /* ceiling */ 4096L))
                    .isEqualTo(SubmissionStatus.ACCEPTED);
        }

        @Test
        @DisplayName("peak within ceiling and WA → WA (no remap)")
        void withinCeiling_wa_unchanged() {
            assertThat(classifier.applyMemoryCeiling(
                    SubmissionStatus.WRONG_ANSWER, /* peak */ 1024L, /* ceiling */ 4096L))
                    .isEqualTo(SubmissionStatus.WRONG_ANSWER);
        }

        @Test
        @DisplayName("peak exactly equals ceiling and ACCEPTED → ACCEPTED (boundary stays)")
        void peakEqualsCeiling_accepted_staysAccepted() {
            assertThat(classifier.applyMemoryCeiling(
                    SubmissionStatus.ACCEPTED, /* peak */ 4096L, /* ceiling */ 4096L))
                    .isEqualTo(SubmissionStatus.ACCEPTED);
        }

        @Test
        @DisplayName("peak over ceiling and ACCEPTED → MEMORY_LIMIT_EXCEEDED")
        void overCeiling_accepted_remapsToMle() {
            assertThat(classifier.applyMemoryCeiling(
                    SubmissionStatus.ACCEPTED, /* peak */ 8192L, /* ceiling */ 4096L))
                    .isEqualTo(SubmissionStatus.MEMORY_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("peak over ceiling and WA → MEMORY_LIMIT_EXCEEDED")
        void overCeiling_wa_remapsToMle() {
            assertThat(classifier.applyMemoryCeiling(
                    SubmissionStatus.WRONG_ANSWER, /* peak */ 8192L, /* ceiling */ 4096L))
                    .isEqualTo(SubmissionStatus.MEMORY_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("peak over ceiling and already-MLE → MLE (idempotent)")
        void overCeiling_alreadyMle_staysMle() {
            assertThat(classifier.applyMemoryCeiling(
                    SubmissionStatus.MEMORY_LIMIT_EXCEEDED,
                    /* peak */ 8192L, /* ceiling */ 4096L))
                    .isEqualTo(SubmissionStatus.MEMORY_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("peak over ceiling and TLE → TLE (backstop only reclassifies AC/WA)")
        void overCeiling_tle_staysTle() {
            assertThat(classifier.applyMemoryCeiling(
                    SubmissionStatus.TIME_LIMIT_EXCEEDED,
                    /* peak */ 8192L, /* ceiling */ 4096L))
                    .isEqualTo(SubmissionStatus.TIME_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("peak over ceiling and CE → CE (compile error wins)")
        void overCeiling_ce_staysCe() {
            assertThat(classifier.applyMemoryCeiling(
                    SubmissionStatus.COMPILE_ERROR,
                    /* peak */ 8192L, /* ceiling */ 4096L))
                    .isEqualTo(SubmissionStatus.COMPILE_ERROR);
        }

        @Test
        @DisplayName("peak over ceiling and SE → SE (sandbox error wins)")
        void overCeiling_se_staysSe() {
            assertThat(classifier.applyMemoryCeiling(
                    SubmissionStatus.SANDBOX_ERROR,
                    /* peak */ 8192L, /* ceiling */ 4096L))
                    .isEqualTo(SubmissionStatus.SANDBOX_ERROR);
        }

        @Test
        @DisplayName("ceiling disabled (<=0) → harness status unchanged even if peak huge")
        void disabledCeiling_overPeak_unchanged() {
            assertThat(classifier.applyMemoryCeiling(
                    SubmissionStatus.ACCEPTED,
                    /* peak */ Long.MAX_VALUE / 2, /* ceiling */ 0L))
                    .isEqualTo(SubmissionStatus.ACCEPTED);
            assertThat(classifier.applyMemoryCeiling(
                    SubmissionStatus.WRONG_ANSWER,
                    /* peak */ Long.MAX_VALUE / 2, /* ceiling */ -1L))
                    .isEqualTo(SubmissionStatus.WRONG_ANSWER);
        }

        @Test
        @DisplayName("peak zero and ACCEPTED → ACCEPTED (no negative-length guard needed)")
        void zeroPeak_accepted_staysAccepted() {
            assertThat(classifier.applyMemoryCeiling(
                    SubmissionStatus.ACCEPTED, /* peak */ 0L, /* ceiling */ 4096L))
                    .isEqualTo(SubmissionStatus.ACCEPTED);
        }
    }
}