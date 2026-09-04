package com.ulticode.modules.submission.sandbox;

/**
 * Immutable descriptor of a single sandbox execution request.
 *
 * <p>This is the input to {@link SandboxExecutor#run} and
 * {@link SandboxExecutor#runBatch}. It is built by the runtime-private
 * JudgeRunRequest plus submission state, then handed to the port; the port
 * never sees transport DTOs.
 *
 * <h2>Field contract</h2>
 * <ul>
 *   <li>{@code runId} — opaque per-execution UUID. Used as a correlation
 *       key for log lines and as the prefix for the per-run workspace
 *       directory inside the sandbox image. Required.</li>
 *   <li>{@code userId} — opaque user identifier. Propagated for audit
 *       logs; the executor does not enforce authorization at this
 *       layer (the caller does).</li>
 *   <li>{@code submissionId} — UUID of the parent submission. Required
 *       even for {@code /run} (preview) requests, where the caller
 *       generates a synthetic one — keeps the audit shape uniform.</li>
 *   <li>{@code submissionGeneration} — monotonic counter from
 *       {@code submissions.generation} (ADR-003). The sandbox records
 *       it in logs so a stale worker can be detected after a rejudge.
 *       For {@code /run} requests where the submission does not yet
 *       exist, callers pass {@code 0L}.</li>
 *   <li>{@code languageId} — canonical language id matching a registered
 *       {@link LanguageProfile#languageId()} (e.g. {@code "java"},
 *       {@code "python"}, {@code "javascript"}, {@code "c"},
 *       {@code "cpp"}). Lowercased and trimmed by the caller; the port
 *       does not re-normalize.</li>
 *   <li>{@code code} — the user-supplied source code, UTF-8. The port
 *       treats it as opaque bytes; the language profile is responsible
 *       for writing it into the workspace.</li>
 *   <li>{@code timeoutSeconds} — per-run soft wall-clock limit. The
 *       executor forwards it to the harness (which can use it for
 *       {@code Thread.interrupt} inside the worker thread) and to the
 *       docker {@code --stop-timeout}. Required.</li>
 *   <li>{@code memoryMb} — per-run RSS cap. The executor forwards it to
 *       the docker {@code --memory} flag. Required.</li>
 * </ul>
 *
 * <p>Implementations MUST treat this record as deeply immutable; do not
 * add mutable collections to it.
 */
public record SandboxJob(
        String runId,
        String userId,
        String submissionId,
        long submissionGeneration,
        String languageId,
        String code,
        int timeoutSeconds,
        int memoryMb
) {
}
