package com.ulticode.modules.submission.runtime.async;

import com.ulticode.modules.submission.sandbox.RunCaseResult;
import com.ulticode.modules.submission.sandbox.SandboxJob;
import com.ulticode.modules.submission.sandbox.TestCase;

/**
 * Runtime-owned asynchronous execution seam. Provider details stay behind
 * adapters; callers observe only queued/running/terminal state and a port
 * result.
 */
public interface AsyncSandboxExecutor {

    ExecutionHandle submit(ExecutionRequest request);

    ExecutionSnapshot poll(ExecutionHandle handle);

    void cancel(ExecutionHandle handle);
    enum Visibility {
        PUBLIC_PREVIEW,
        PRIVATE,
        HIDDEN
    }


    record ExecutionRequest(
            SandboxJob job,
            TestCase testCase,
            Visibility visibility,
            String idempotencyKey) {
        public ExecutionRequest(SandboxJob job, TestCase testCase, Visibility visibility) {
            this(job, testCase, visibility,
                    job == null || testCase == null
                            ? null : job.runId() + ":" + testCase.id());
        }

        public ExecutionRequest {
            if (job == null || testCase == null || visibility == null
                    || idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException(
                        "job, testCase, visibility and idempotencyKey are required");
            }
        }
        public String fingerprint() {
            try {
                java.security.MessageDigest digest =
                        java.security.MessageDigest.getInstance("SHA-256");
                return java.util.HexFormat.of().formatHex(
                        digest.digest((job + "|" + testCase + "|" + visibility)
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            } catch (java.security.NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
        }
    }

    record ExecutionHandle(String id) {
        public ExecutionHandle {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("execution handle id is required");
            }
        }
    }

    record ExecutionSnapshot(State state, RunCaseResult result, String error) {
        public ExecutionSnapshot {
            if (state == null) {
                throw new IllegalArgumentException("execution state is required");
            }
            if (state == State.COMPLETED && result == null) {
                throw new IllegalArgumentException("completed execution must carry a result");
            }
            if (state != State.COMPLETED && result != null) {
                throw new IllegalArgumentException("non-completed execution must not carry a result");
            }
        }

        public static ExecutionSnapshot queued() {
            return new ExecutionSnapshot(State.QUEUED, null, null);
        }

        public static ExecutionSnapshot running() {
            return new ExecutionSnapshot(State.RUNNING, null, null);
        }

        public static ExecutionSnapshot completed(RunCaseResult result) {
            return new ExecutionSnapshot(State.COMPLETED, result, null);
        }

        public static ExecutionSnapshot cancelled() {
            return new ExecutionSnapshot(State.CANCELLED, null, null);
        }

        public static ExecutionSnapshot failed(String error) {
            return new ExecutionSnapshot(State.FAILED, null,
                    error == null || error.isBlank() ? "execution failed" : error);
        }

        public static ExecutionSnapshot timedOut(String error) {
            return new ExecutionSnapshot(State.TIMED_OUT, null,
                    error == null || error.isBlank() ? "execution timed out" : error);
        }
    }

    enum State {
        QUEUED,
        RUNNING,
        COMPLETED,
        CANCELLED,
        TIMED_OUT,
        FAILED
    }
}
