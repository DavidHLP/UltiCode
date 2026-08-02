package com.ulticode.modules.submission.enums;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ulticode.domain.submission.enums.SubmissionStatus;

/**
 * User-facing metadata catalog for {@link SubmissionStatus}.
 *
 * <p>The enum owns the durable contract (displayName, category, terminal, severity, kind).
 * This catalog owns the user-facing strings (description, suggestion) and the
 * display sort order — the things the frontend filter dropdowns and admin
 * statistics cards display. Splitting these lets the enum stay a pure contract
 * value while the catalog carries the presentation layer.
 *
 * <p>Replacement for the 11 hand-typed {@code SubmissionStatusMeta} blocks that
 * lived in {@code SubmissionServiceImpl.getStatuses()}. Each entry is keyed by
 * its enum constant so {@link #forStatus(SubmissionStatus)} is O(1); iteration
 * order is the enum's {@link SubmissionStatus#values()} order, which matches
 * the lifecycle (in-flight → terminal-good → terminal-bad → terminal-infra).
 *
 * <p>Adding a new status requires one entry here and one entry in the enum.
 * Drift is impossible: the enum is the source of identity, this catalog is
 * the source of user-facing strings, and {@link
 * com.ulticode.modules.submission.projection.DefaultSubmissionProjection#getStatusCatalog()}
 * streams the two together.
 *
 * @author ulticode
 */
public final class SubmissionStatusCatalog {

    private SubmissionStatusCatalog() {}

    /**
     * One catalog entry — the user-facing strings the enum deliberately does
     * not carry. {@code severity} is the UI severity bucket
     * ({@code info}/{@code success}/{@code warning}/{@code error}); {@code
     * sortOrder} is the display order used by the public {@code /submissions/statuses}
     * endpoint (lower numbers first).
     */
    public record Entry(String description, String suggestion, String severity, int sortOrder) {}

    private static final Map<SubmissionStatus, Entry> CATALOG;

    static {
        Map<SubmissionStatus, Entry> m = new LinkedHashMap<>();
        m.put(SubmissionStatus.PENDING,
                new Entry("Submission is waiting to be judged",
                        "Please wait for the judging to complete",
                        "info", 0));
        m.put(SubmissionStatus.JUDGING,
                new Entry("Submission is being judged",
                        "Please wait for the judging to complete",
                        "info", 1));
        m.put(SubmissionStatus.ACCEPTED,
                new Entry("All test cases passed",
                        "Congratulations! Your solution is correct.",
                        "success", 2));
        m.put(SubmissionStatus.WRONG_ANSWER,
                new Entry("Your output was incorrect",
                        "Check your algorithm and edge cases",
                        "error", 3));
        m.put(SubmissionStatus.TIME_LIMIT_EXCEEDED,
                new Entry("Your program took too long to execute",
                        "Optimize your algorithm or reduce unnecessary operations",
                        "error", 4));
        m.put(SubmissionStatus.MEMORY_LIMIT_EXCEEDED,
                new Entry("Your program used too much memory",
                        "Optimize memory usage or use more efficient data structures",
                        "error", 5));
        m.put(SubmissionStatus.OUTPUT_LIMIT_EXCEEDED,
                new Entry("Your program produced too much output",
                        "Check for infinite loops that produce output",
                        "error", 6));
        m.put(SubmissionStatus.RUNTIME_ERROR,
                new Entry("Your program crashed during execution",
                        "Check for division by zero, null pointer, array out of bounds, etc.",
                        "error", 7));
        m.put(SubmissionStatus.COMPILE_ERROR,
                new Entry("Your code failed to compile",
                        "Check syntax errors and make sure your code is valid",
                        "error", 8));
        m.put(SubmissionStatus.PRESENTATION_ERROR,
                new Entry("Your output format is incorrect",
                        "Check for extra spaces, newlines, or formatting issues",
                        "warning", 9));
        m.put(SubmissionStatus.SANDBOX_ERROR,
                new Entry("The judging sandbox encountered an internal error",
                        "Please retry; if the issue persists contact support",
                        "error", 10));
        m.put(SubmissionStatus.SYSTEM_ERROR,
                new Entry("An error occurred on our end",
                        "Please try again later or contact support",
                        "error", 11));
        CATALOG = Collections.unmodifiableMap(m);
    }

    /**
     * Look up the catalog entry for a status. Every enum constant has an entry;
     * unknown statuses (should never happen) return a defensive default.
     */
    public static Entry forStatus(SubmissionStatus status) {
        Entry e = CATALOG.get(status);
        if (e != null) {
            return e;
        }
        return new Entry("", "", "info", Integer.MAX_VALUE);
    }

    /**
     * Iterate the catalog in enum-declaration order. The enum's natural order
     * (in-flight → terminal-good → terminal-bad → terminal-infra) is also the
     * user-facing lifecycle order; the per-entry {@code sortOrder} field is the
     * secondary knob for the admin filter dropdown.
     */
    public static Iterable<Map.Entry<SubmissionStatus, Entry>> entries() {
        return CATALOG.entrySet();
    }
}