package com.ulticode.modules.submission.enums;

import lombok.Getter;

/**
 * Canonical submission status enum.
 * <p>
 * Each value carries:
 * <ul>
 *   <li>{@code displayName} — exact string stored in the {@code submissions.status} column
 *       (must match existing seed/test data, e.g. {@code "Compile Error"} with a space).</li>
 *   <li>{@code category} — coarse filter category used by the admin UI
 *       ({@code pending}, {@code accepted}, {@code error}, {@code system}).</li>
 *   <li>{@code terminal} — whether the submission has reached a final state and will
 *       no longer be re-evaluated automatically.</li>
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

    PENDING("Pending", "pending", false),
    JUDGING("Judging", "pending", false),
    ACCEPTED("Accepted", "accepted", true),
    WRONG_ANSWER("Wrong Answer", "error", true),
    TIME_LIMIT_EXCEEDED("Time Limit Exceeded", "error", true),
    MEMORY_LIMIT_EXCEEDED("Memory Limit Exceeded", "error", true),
    OUTPUT_LIMIT_EXCEEDED("Output Limit Exceeded", "error", true),
    PRESENTATION_ERROR("Presentation Error", "error", true),
    RUNTIME_ERROR("Runtime Error", "error", true),
    COMPILE_ERROR("Compile Error", "error", true),
    SYSTEM_ERROR("System Error", "system", true);

    private final String displayName;
    private final String category;
    private final boolean terminal;

    SubmissionStatus(String displayName, String category, boolean terminal) {
        this.displayName = displayName;
        this.category = category;
        this.terminal = terminal;
    }

    /**
     * Map a database value back to its enum constant.
     *
     * @param dbValue the value stored in {@code submissions.status}
     * @return the matching constant, or {@code null} if {@code dbValue} is unknown.
     *         Callers should treat unknown values as legacy data and not crash the request.
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
