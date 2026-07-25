package com.ulticode.modules.submission.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * Canonical submission status enum (ADR-001).
 * <p>
 * Each value carries:
 * <ul>
 *   <li>{@code displayName} — exact string stored in the {@code submissions.status} column
 *       and shipped over JSON to the frontends (must match existing seed/test data,
 *       e.g. {@code "Compile Error"} with a space). This is the durable wire contract.</li>
 *   <li>{@code category} — coarse filter category used by the admin UI
 *       ({@code pending}, {@code accepted}, {@code error}, {@code system}).</li>
 *   <li>{@code terminal} — whether the submission has reached a final state and will
 *       no longer be re-evaluated automatically.</li>
 *   <li>{@code severity} — case-level reduction priority used by {@code VerdictResolver}
 *       (higher means worse; {@code ACCEPTED}/{@code PENDING}/{@code JUDGING} are 0).
 *       Not persisted, not shipped on the wire.</li>
 *   <li>{@code kind} — coarse lifecycle classification ({@code IN_FLIGHT},
 *       {@code TERMINAL_GOOD}, {@code TERMINAL_BAD}, {@code TERMINAL_INFRA}); used by
 *       reducers and ranking logic to decide whether a status is a real verdict.</li>
 * </ul>
 * <p>
 * <b>Contract invariants</b> (changes require a new ADR):
 * <ul>
 *   <li>{@code displayName} (a.k.a. wire value) is the durable string for DB rows and
 *       JSON payloads; <b>never</b> rename or recase.</li>
 *   <li>{@code name()} / {@code ordinal()} are <b>not</b> wire contracts; do not
 *       depend on them across process boundaries.</li>
 *   <li>{@code severity()} is JVM-internal only.</li>
 * </ul>
 * <p>
 * This enum unifies the three previously divergent sources of truth:
 * (a) the hard-coded 7-entry list in {@code AdminSubmissionServiceImpl.getStatuses()},
 * (b) the {@code byStatus} aggregation in {@code getStatistics()} (11 entries), and
 * (c) the raw strings in the {@code submissions} table. Admin filter dropdowns and
 * statistics cards now derive from the same source.
 */
@Getter
public enum SubmissionStatus {

    /** Submission accepted by the API, waiting in the judge queue. */
    PENDING("Pending", "pending", false, 0, Kind.IN_FLIGHT),

    /** Worker has claimed the submission and is executing test cases. */
    JUDGING("Judging", "pending", false, 0, Kind.IN_FLIGHT),

    /** All test cases passed within limits. */
    ACCEPTED("Accepted", "accepted", true, 0, Kind.TERMINAL_GOOD),

    /** Output matches semantically but format (whitespace, trailing newline) differs. */
    PRESENTATION_ERROR("Presentation Error", "error", true, 1, Kind.TERMINAL_BAD),

    /** At least one test case produced output that does not match expected. */
    WRONG_ANSWER("Wrong Answer", "error", true, 2, Kind.TERMINAL_BAD),

    /** Execution exceeded the per-case wall-clock time limit. */
    TIME_LIMIT_EXCEEDED("Time Limit Exceeded", "error", true, 3, Kind.TERMINAL_BAD),

    /** Execution exceeded the cgroup memory limit. */
    MEMORY_LIMIT_EXCEEDED("Memory Limit Exceeded", "error", true, 4, Kind.TERMINAL_BAD),

    /** Program produced more stdout/stderr than the sandbox output budget. */
    OUTPUT_LIMIT_EXCEEDED("Output Limit Exceeded", "error", true, 4, Kind.TERMINAL_BAD),

    /** Non-zero exit code, uncaught exception, signal, or other runtime fault. */
    RUNTIME_ERROR("Runtime Error", "error", true, 5, Kind.TERMINAL_BAD),

    /** Source code failed to compile; does not participate in case-level reduction. */
    COMPILE_ERROR("Compile Error", "error", true, 6, Kind.TERMINAL_BAD),

    /**
     * Sandbox infrastructure failure (fork failure, cgroup/seccomp/PID pressure,
     * docker daemon failure). Not caused by user code; counts as infra-side issue.
     */
    SANDBOX_ERROR("Sandbox Error", "system", true, 7, Kind.TERMINAL_INFRA),

    /** Generic system/infrastructure error not categorized above. */
    SYSTEM_ERROR("System Error", "system", true, 8, Kind.TERMINAL_INFRA);

    /**
     * Coarse lifecycle classification.
     * <ul>
     *   <li>{@link #IN_FLIGHT} — submission is still being processed; cannot be reduced.</li>
     *   <li>{@link #TERMINAL_GOOD} — final state, user code accepted.</li>
     *   <li>{@link #TERMINAL_BAD} — final state, user code rejected (their bug).</li>
     *   <li>{@link #TERMINAL_INFRA} — final state, our sandbox/system failed (not user fault).</li>
     * </ul>
     */
    public enum Kind {
        IN_FLIGHT,
        TERMINAL_GOOD,
        TERMINAL_BAD,
        TERMINAL_INFRA
    }

    private final String displayName;
    private final String category;
    private final boolean terminal;
    private final int severity;
    private final Kind kind;

    SubmissionStatus(String displayName, String category, boolean terminal,
                     int severity, Kind kind) {
        this.displayName = displayName;
        this.category = category;
        this.terminal = terminal;
        this.severity = severity;
        this.kind = kind;
    }

    /**
     * Serialize this enum to its wire value (used by Jackson and any caller that
     * needs the persistent string form). This is the same value as
     * {@link #getDisplayName()} and the value stored in {@code submissions.status}.
     *
     * @return the wire string; never {@code null}
     */
    @JsonValue
    public String wireValue() {
        return displayName;
    }

    /**
     * Deserialize a wire value back into its enum constant. Used by Jackson when
     * reading JSON payloads. Throws {@link IllegalArgumentException} if the value
     * is unknown so callers cannot silently process garbage.
     *
     * @param wire the wire string (e.g. {@code "Wrong Answer"}); must not be {@code null}
     * @return the matching enum constant
     * @throws IllegalArgumentException if {@code wire} is {@code null} or does not match any constant
     */
    @JsonCreator
    public static SubmissionStatus fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("SubmissionStatus wire value is null");
        }
        for (SubmissionStatus s : values()) {
            if (s.displayName.equals(wire)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown SubmissionStatus wire value: " + wire);
    }

    /**
     * Map a database value back to its enum constant. Lenient: returns {@code null}
     * for unknown legacy values so MyBatis row mapping does not crash on historic data.
     * <p>
     * Internally delegates to {@link #fromWire(String)} but swallows the unknown-value
     * exception. New code should prefer {@link #fromWire(String)} for fail-fast behavior.
     *
     * @param dbValue the value stored in {@code submissions.status}; may be {@code null}
     * @return the matching constant, or {@code null} if {@code dbValue} is {@code null}
     *         or unknown. Callers should treat unknown values as legacy data and not
     *         crash the request.
     */
    public static SubmissionStatus fromDbName(String dbValue) {
        if (dbValue == null) {
            return null;
        }
        for (SubmissionStatus s : values()) {
            if (s.displayName.equals(dbValue)) {
                return s;
            }
        }
        return null;
    }
}
