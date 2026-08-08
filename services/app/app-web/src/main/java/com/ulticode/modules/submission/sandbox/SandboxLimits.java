package com.ulticode.modules.submission.sandbox;

/**
 * Per-language effective resource limits (ADR-002 §2.2).
 *
 * <p>Each {@link LanguageProfile} returns one of these from
 * {@link LanguageProfile#effectiveLimits(SandboxJob)} so the executor
 * can layer language-specific tuning (e.g. JVM heap for Java,
 * pre-allocated compile cache for C++) on top of the per-run values
 * carried by the {@link SandboxJob}. The executor always honors
 * {@code SandboxJob.timeoutSeconds} and {@code SandboxJob.memoryMb}
 * as hard upper bounds; the profile's limits are allowed to be more
 * generous but never more restrictive than the job.
 *
 * <p>Adding a new limit dimension (e.g. open-files cap) is a
 * non-breaking change for this record as long as new fields come with
 * sensible defaults and are documented in the ADR; treat the existing
 * fields as the durable contract.
 */
public record SandboxLimits(
        int timeoutSeconds,
        int memoryMb
) {
}
