package com.ulticode.modules.queue.port;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Versioned envelope for judge job dispatches (ADR-003 M3c, §2.4).
 *
 * <p>Unified record carrying either a v1 legacy job (no fence) or a v2
 * fence-aware job ({@code generation} + {@code attemptId}). v1 dispatches
 * leave {@link #generation} and {@link #attemptId} {@code null}; v2 populates
 * them from the outbox row. Workers branch on {@link #version()} to decide
 * whether to run {@code acquireLease} (v2) or the legacy
 * {@code updateSubmissionResult} path (v1).
 *
 * <p><b>Why a unified record instead of a {@code sealed JudgeJobEnvelope
 * permits JudgeJobV1, JudgeJobV2}</b> (as the ADR §2.4 prose suggests):
 * avoids a forced refactor of the existing
 * {@link com.ulticode.modules.queue.job.JudgeJob} POJO that the legacy
 * {@code RQueue} path still uses in parallel. The discriminator field
 * {@code version} is enough for the dispatcher to encode and the worker to
 * decode. M3c-3 worker upgrade is the natural place to migrate to sealed
 * subtypes if a v3 envelope needs extra fields.
 *
 * <p>JSON contract:
 * <pre>
 * {
 *   "version": 1 | 2,
 *   "id": "uuid",
 *   "submissionId": "uuid",
 *   "problemId": "string",
 *   "userId": "uuid",
 *   "language": "java",
 *   "code": "...",
 *   "timeLimitMs": 2000,
 *   "memoryLimitKb": 262144,
 *   "generation": 1,          // v2 only
 *   "attemptId": "uuid"       // v2 only
 * }
 * </pre>
 * {@code generation} and {@code attemptId} are omitted from JSON for v1
 * (Jackson {@code @JsonInclude(NON_NULL)}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JudgeJobEnvelope(
        @JsonProperty("version") int version,
        @JsonProperty("id") String id,
        @JsonProperty("submissionId") String submissionId,
        @JsonProperty("problemId") String problemId,
        @JsonProperty("userId") String userId,
        @JsonProperty("language") String language,
        @JsonProperty("code") String code,
        @JsonProperty("timeLimitMs") int timeLimitMs,
        @JsonProperty("memoryLimitKb") int memoryLimitKb,
        /** v2 only — fence generation from the outbox row. {@code null} for v1. */
        @JsonProperty("generation") Long generation,
        /** v2 only — per-attempt UUID for the lease. {@code null} for v1. */
        @JsonProperty("attemptId") String attemptId) {

    public static final int VERSION_1 = 1;
    public static final int VERSION_2 = 2;

    /**
     * True if this envelope carries a fence-aware v2 payload (generation +
     * attemptId populated) and the worker must run the CAS lease + fence
     * write path (ADR-003 M3b).
     */
    public boolean isFenceAware() {
        return version >= VERSION_2 && generation != null && attemptId != null;
    }
}
