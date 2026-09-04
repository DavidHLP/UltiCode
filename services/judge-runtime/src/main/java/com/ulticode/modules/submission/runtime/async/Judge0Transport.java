package com.ulticode.modules.submission.runtime.async;

/** Internal Judge0 Adapter seam; vendor status details never cross the adapter. */
interface Judge0Transport {

    String submit(Submission submission);

    Poll poll(String token);

    void cancel(String token);

    record Submission(
            int languageId,
            String sourceCode,
            String stdin,
            String expectedOutput,
            long timeoutMs,
            long memoryLimitKb,
            int maxOutputBytes) {
    }

    record Poll(
            Status status,
            String stdout,
            String stderr,
            String compileOutput,
            String message,
            long elapsedMs,
            long memoryBytes) {
    }

    enum Status {
        QUEUED,
        RUNNING,
        ACCEPTED,
        WRONG_ANSWER,
        TIME_LIMIT_EXCEEDED,
        MEMORY_LIMIT_EXCEEDED,
        COMPILE_ERROR,
        RUNTIME_ERROR,
        OUTPUT_LIMIT_EXCEEDED,
        FAILED
    }
}
